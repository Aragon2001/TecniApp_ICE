import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ref, get, update } from 'firebase/database';
import toast from 'react-hot-toast';
import { rtdbAverias } from '../firebase/config';
import { queryClient } from '../lib/queryClient';
import { db, dexieRead, dexieWrite, dexieInvalidate } from '../lib/db';
import { addToQueue } from '../lib/syncQueue';

export interface Averia {
  id: string;
  casoId?: string;
  descripcion?: string;
  tipoAveria?: string;
  estado: 'PENDIENTE' | 'ASIGNADA' | 'EN_ATENCION' | 'RESUELTA' | 'ANULADA';
  agencia?: string;
  localizacion?: string;
  vehiculoId?: string;
  tecnicoUid?: string;
  tecnicoNombre?: string;
  fechaCreacion?: number;
  fechaAsignacion?: number;
  fechaResolucion?: number;
  latitud?: number;
  longitud?: number;
  prioridad?: string;
  circuito?: string;
  [key: string]: any;
}

interface UseAveriasFilter {
  estado?: string;
  agencia?: string;
  search?: string;
}

interface UseAveriasResult {
  averias: Averia[];
  loading: boolean;
  error: string | null;
}

async function fetchAverias(): Promise<Averia[]> {
  const cached = await dexieRead<Averia>(db.averias, 'averias');
  if (cached !== null) return cached;

  if (!rtdbAverias) throw new Error('Firebase RTDB (averias) no configurado');
  const snapshot = await get(ref(rtdbAverias, '/averias'));
  const data = snapshot.val() ?? {};
  const records: Averia[] = Object.entries(data).map(([id, val]: [string, any]) => ({
    id,
    ...val,
  }));

  await dexieWrite(db.averias, 'averias', records);
  return records;
}

export function useAverias(filters?: UseAveriasFilter): UseAveriasResult {
  const { data, isLoading, error } = useQuery<Averia[]>({
    queryKey: ['averias'],
    queryFn: fetchAverias,
    staleTime: 60_000,
  });

  const averias = useMemo(() => {
    if (!data) return [];
    let list = [...data];

    if (filters?.estado) {
      list = list.filter((a) => a.estado === filters.estado);
    }
    if (filters?.agencia) {
      list = list.filter((a) =>
        a.agencia?.toLowerCase().includes(filters.agencia!.toLowerCase())
      );
    }
    if (filters?.search) {
      const q = filters.search.toLowerCase();
      list = list.filter(
        (a) =>
          a.descripcion?.toLowerCase().includes(q) ||
          a.tipoAveria?.toLowerCase().includes(q) ||
          a.localizacion?.toLowerCase().includes(q) ||
          a.agencia?.toLowerCase().includes(q) ||
          a.id?.toLowerCase().includes(q)
      );
    }

    list.sort((a, b) => (b.fechaCreacion || 0) - (a.fechaCreacion || 0));
    return list;
  }, [data, filters?.estado, filters?.agencia, filters?.search]);

  return {
    averias,
    loading: isLoading,
    error: error ? (error as Error).message : null,
  };
}

async function fetchAveria(caseId: string): Promise<Averia | null> {
  const meta = await db.meta.get('averias');
  if (meta && Date.now() - meta.cachedAt < 60_000) {
    const record = await db.averias.get(caseId);
    if (record !== undefined) return record as Averia;
  }

  if (!rtdbAverias) throw new Error('Firebase RTDB (averias) no configurado');
  const snapshot = await get(ref(rtdbAverias, `/averias/${caseId}`));
  const data = snapshot.val();
  return data ? ({ id: caseId, ...data } as Averia) : null;
}

export function useAveria(caseId: string | null) {
  const { data: averia, isLoading, error } = useQuery<Averia | null>({
    queryKey: ['averia', caseId],
    queryFn: () => fetchAveria(caseId!),
    enabled: !!caseId,
    staleTime: 30_000,
  });

  return {
    averia: averia ?? null,
    loading: isLoading,
    error: error ? (error as Error).message : null,
  };
}

// ─── Mutaciones offline-first ────────────────────────────────────────────────

export async function updateAveriaEstado(
  caseId: string,
  updates: Partial<Averia>
): Promise<void> {
  const payload = { ...updates, updatedAt: Date.now() };

  if (!navigator.onLine) {
    await db.averias.update(caseId, payload);
    await addToQueue({
      collectionKey: 'averias',
      entityId: caseId,
      rtdbPath: `/averias/${caseId}`,
      payload,
    });
    queryClient.setQueryData<Averia[]>(['averias'], (old) =>
      old?.map((a) => (a.id === caseId ? { ...a, ...payload } : a)) ?? []
    );
    queryClient.setQueryData<Averia | null>(['averia', caseId], (old) =>
      old ? { ...old, ...payload } : null
    );
    toast('Guardado sin conexión. Se sincronizará al restaurar internet.', {
      icon: '📶',
      duration: 5000,
    });
    return;
  }

  if (!rtdbAverias) throw new Error('Firebase RTDB (averias) no configurado');
  await update(ref(rtdbAverias, `/averias/${caseId}`), payload);
  await dexieInvalidate('averias');
  queryClient.invalidateQueries({ queryKey: ['averias'] });
  queryClient.invalidateQueries({ queryKey: ['averia', caseId] });
}

export async function assignAveria(
  caseId: string,
  vehiculoId: string,
  tecnicoUid: string,
  tecnicoNombre: string
): Promise<void> {
  const payload = {
    estado: 'ASIGNADA',
    vehiculoId,
    tecnicoUid,
    tecnicoNombre,
    fechaAsignacion: Date.now(),
    updatedAt: Date.now(),
  };

  if (!navigator.onLine) {
    await db.averias.update(caseId, payload);
    await addToQueue({
      collectionKey: 'averias',
      entityId: caseId,
      rtdbPath: `/averias/${caseId}`,
      payload,
    });
    queryClient.setQueryData<Averia[]>(['averias'], (old) =>
      old?.map((a) => (a.id === caseId ? { ...a, ...payload } : a)) ?? []
    );
    queryClient.setQueryData<Averia | null>(['averia', caseId], (old) =>
      old ? { ...old, ...payload } : null
    );
    toast('Asignación guardada sin conexión. Se sincronizará al restaurar internet.', {
      icon: '📶',
      duration: 5000,
    });
    return;
  }

  if (!rtdbAverias) throw new Error('Firebase RTDB (averias) no configurado');
  await update(ref(rtdbAverias, `/averias/${caseId}`), payload);
  await dexieInvalidate('averias');
  queryClient.invalidateQueries({ queryKey: ['averias'] });
  queryClient.invalidateQueries({ queryKey: ['averia', caseId] });
}
