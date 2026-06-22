import { useQuery } from '@tanstack/react-query';
import { ref, get } from 'firebase/database';
import { rtdbMateriales } from '../firebase/config';
import { db, dexieRead, dexieWrite } from '../lib/db';

export interface Material {
  id: string;
  nombre?: string;
  codigo?: string;
  descripcion?: string;
  unidad?: string;
  categoria?: string;
  precio?: number;
  [key: string]: any;
}

interface UseMaterialesResult {
  materiales: Material[];
  loading: boolean;
  error: string | null;
}

async function fetchMateriales(): Promise<Material[]> {
  const cached = await dexieRead<Material>(db.materiales, 'materiales');
  if (cached !== null) {
    return cached.sort((a, b) => (a.nombre || '').localeCompare(b.nombre || '', 'es'));
  }

  if (!rtdbMateriales) throw new Error('Firebase RTDB (materiales) no configurado');
  const snapshot = await get(ref(rtdbMateriales, '/materiales'));
  const data = snapshot.val() ?? {};
  const records: Material[] = Object.entries(data).map(([id, val]: [string, any]) => ({
    id,
    ...val,
  }));
  records.sort((a, b) => (a.nombre || '').localeCompare(b.nombre || '', 'es'));

  await dexieWrite(db.materiales, 'materiales', records);
  return records;
}

export function useMateriales(): UseMaterialesResult {
  const { data: materiales = [], isLoading, error } = useQuery<Material[]>({
    queryKey: ['materiales'],
    queryFn: fetchMateriales,
    staleTime: 60 * 60_000,
  });

  return {
    materiales,
    loading: isLoading,
    error: error ? (error as Error).message : null,
  };
}
