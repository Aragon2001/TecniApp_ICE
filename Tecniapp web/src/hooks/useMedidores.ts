import { useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ref, get } from 'firebase/database';
import { rtdbMain } from '../firebase/config';
import { db, dexieRead, dexieWrite } from '../lib/db';

export interface Medidor {
  id: string;
  numero?: string;
  tipo?: string;
  localizacion?: string;
  estado?: string;
  lectura?: number;
  ultimaLectura?: number;
  fechaInstalacion?: number;
  agencia?: string;
  cliente?: string;
  tarifa?: string;
  latitud?: number;
  longitud?: number;
  [key: string]: any;
}

interface UseMedidoresResult {
  medidores: Medidor[];
  loading: boolean;
  error: string | null;
  searchMedidor: (query: string) => Medidor[];
}

async function fetchMedidores(): Promise<Medidor[]> {
  const cached = await dexieRead<Medidor>(db.medidores, 'medidores');
  if (cached !== null) {
    return cached.sort((a, b) => (a.numero || '').localeCompare(b.numero || '', 'es'));
  }

  if (!rtdbMain) throw new Error('Firebase RTDB (main) no configurado');
  const snapshot = await get(ref(rtdbMain, '/medidores'));
  const data = snapshot.val() ?? {};
  const records: Medidor[] = Object.entries(data).map(([id, val]: [string, any]) => ({
    id,
    ...val,
  }));
  records.sort((a, b) => (a.numero || '').localeCompare(b.numero || '', 'es'));

  await dexieWrite(db.medidores, 'medidores', records);
  return records;
}

export function useMedidores(): UseMedidoresResult {
  const { data: medidores = [], isLoading, error } = useQuery<Medidor[]>({
    queryKey: ['medidores'],
    queryFn: fetchMedidores,
    staleTime: 10 * 60_000,
  });

  const searchMedidor = useCallback(
    (query: string): Medidor[] => {
      if (!query.trim()) return medidores;
      const q = query.toLowerCase();
      return medidores.filter(
        (m) =>
          m.numero?.toLowerCase().includes(q) ||
          m.localizacion?.toLowerCase().includes(q) ||
          m.cliente?.toLowerCase().includes(q) ||
          m.id?.toLowerCase().includes(q)
      );
    },
    [medidores]
  );

  return {
    medidores,
    loading: isLoading,
    error: error ? (error as Error).message : null,
    searchMedidor,
  };
}
