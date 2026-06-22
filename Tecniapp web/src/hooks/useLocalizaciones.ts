import { useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ref, get } from 'firebase/database';
import { rtdbMain } from '../firebase/config';
import { db, dexieRead, dexieWrite } from '../lib/db';

export interface Localizacion {
  id: string;
  nombre?: string;
  codigo?: string;
  agencia?: string;
  subregion?: string;
  tipo?: string;
  latitud?: number;
  longitud?: number;
  circuito?: string;
  [key: string]: any;
}

interface UseLocalizacionesResult {
  localizaciones: Localizacion[];
  loading: boolean;
  error: string | null;
  searchLocalizacion: (query: string) => Localizacion[];
}

async function fetchLocalizaciones(): Promise<Localizacion[]> {
  const cached = await dexieRead<Localizacion>(db.localizaciones, 'localizaciones');
  if (cached !== null) {
    return cached.sort((a, b) => (a.nombre || '').localeCompare(b.nombre || '', 'es'));
  }

  if (!rtdbMain) throw new Error('Firebase RTDB no configurado');
  const snapshot = await get(ref(rtdbMain, '/localizaciones'));
  const data = snapshot.val() ?? {};
  const records: Localizacion[] = Object.entries(data).map(([id, val]: [string, any]) => ({
    id,
    ...val,
  }));
  records.sort((a, b) => (a.nombre || '').localeCompare(b.nombre || '', 'es'));

  await dexieWrite(db.localizaciones, 'localizaciones', records);
  return records;
}

export function useLocalizaciones(): UseLocalizacionesResult {
  const { data: localizaciones = [], isLoading, error } = useQuery<Localizacion[]>({
    queryKey: ['localizaciones'],
    queryFn: fetchLocalizaciones,
    staleTime: 60 * 60_000,
  });

  const searchLocalizacion = useCallback(
    (query: string): Localizacion[] => {
      if (!query.trim()) return localizaciones;
      const q = query.toLowerCase();
      return localizaciones.filter(
        (l) =>
          l.nombre?.toLowerCase().includes(q) ||
          l.codigo?.toLowerCase().includes(q) ||
          l.agencia?.toLowerCase().includes(q) ||
          l.circuito?.toLowerCase().includes(q) ||
          l.id?.toLowerCase().includes(q)
      );
    },
    [localizaciones]
  );

  return {
    localizaciones,
    loading: isLoading,
    error: error ? (error as Error).message : null,
    searchLocalizacion,
  };
}
