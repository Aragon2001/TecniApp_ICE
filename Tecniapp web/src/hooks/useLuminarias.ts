import { useQuery } from '@tanstack/react-query';
import { ref, get, update } from 'firebase/database';
import toast from 'react-hot-toast';
import { rtdbInventario } from '../firebase/config';
import { queryClient } from '../lib/queryClient';
import { db, dexieRead, dexieWrite, dexieInvalidate } from '../lib/db';
import { addToQueue } from '../lib/syncQueue';

export interface Luminaria {
  id: string;
  numero?: string;
  tipo?: string;
  potencia?: number;
  estado?: string;
  localizacion?: string;
  latitud?: number;
  longitud?: number;
  agencia?: string;
  fechaInstalacion?: number;
  ultimoMantenimiento?: number;
  observaciones?: string;
  [key: string]: any;
}

interface UseLuminariasResult {
  luminarias: Luminaria[];
  loading: boolean;
  error: string | null;
}

async function fetchLuminarias(): Promise<Luminaria[]> {
  const cached = await dexieRead<Luminaria>(db.luminarias, 'luminarias');
  if (cached !== null) {
    return cached.sort((a, b) => (a.numero || '').localeCompare(b.numero || '', 'es'));
  }

  if (!rtdbInventario) throw new Error('Firebase RTDB (inventario) no configurado');
  const snapshot = await get(ref(rtdbInventario, '/luminarias'));
  const data = snapshot.val() ?? {};
  const records: Luminaria[] = Object.entries(data).map(([id, val]: [string, any]) => ({
    id,
    ...val,
  }));
  records.sort((a, b) => (a.numero || '').localeCompare(b.numero || '', 'es'));

  await dexieWrite(db.luminarias, 'luminarias', records);
  return records;
}

export function useLuminarias(): UseLuminariasResult {
  const { data: luminarias = [], isLoading, error } = useQuery<Luminaria[]>({
    queryKey: ['luminarias'],
    queryFn: fetchLuminarias,
    staleTime: 5 * 60_000,
  });

  return {
    luminarias,
    loading: isLoading,
    error: error ? (error as Error).message : null,
  };
}

export async function updateLuminariaEstado(
  luminariaId: string,
  estado: string,
  observaciones?: string
): Promise<void> {
  const payload = {
    estado,
    observaciones: observaciones || null,
    updatedAt: Date.now(),
  };

  if (!navigator.onLine) {
    await db.luminarias.update(luminariaId, payload);
    await addToQueue({
      collectionKey: 'luminarias',
      entityId: luminariaId,
      rtdbPath: `/luminarias/${luminariaId}`,
      payload,
    });
    queryClient.setQueryData<Luminaria[]>(['luminarias'], (old) =>
      old?.map((l) => (l.id === luminariaId ? { ...l, ...payload } : l)) ?? []
    );
    toast('Guardado sin conexión. Se sincronizará al restaurar internet.', {
      icon: '📶',
      duration: 5000,
    });
    return;
  }

  if (!rtdbInventario) throw new Error('Firebase RTDB (inventario) no configurado');
  await update(ref(rtdbInventario, `/luminarias/${luminariaId}`), payload);
  await dexieInvalidate('luminarias');
  queryClient.invalidateQueries({ queryKey: ['luminarias'] });
}
