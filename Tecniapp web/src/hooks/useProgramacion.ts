import { useQuery } from '@tanstack/react-query';
import { ref, get } from 'firebase/database';
import { rtdbProgramacion } from '../firebase/config';
import { db, dexieRead, dexieWrite } from '../lib/db';

export interface Programacion {
  id: string;
  subregion?: string;
  vehiculoId?: string;
  fecha?: string;
  tipo?: string;
  descripcion?: string;
  estado?: string;
  tecnicoUid?: string;
  tecnicoNombre?: string;
  localizacion?: string;
  latitud?: number;
  longitud?: number;
  horaInicio?: string;
  horaFin?: string;
  materiales?: string[];
  observaciones?: string;
  [key: string]: any;
}

interface UseProgramacionResult {
  programaciones: Programacion[];
  loading: boolean;
  error: string | null;
}

async function fetchProgramaciones(): Promise<Programacion[]> {
  const cached = await dexieRead<Programacion>(db.programaciones, 'programaciones');
  if (cached !== null) {
    return cached.sort((a, b) => (b.fecha || '').localeCompare(a.fecha || ''));
  }

  if (!rtdbProgramacion) throw new Error('Firebase RTDB (programacion) no configurado');
  const snapshot = await get(ref(rtdbProgramacion, '/programaciones'));
  const data = snapshot.val() ?? {};

  // Aplana estructura: subregion → vehiculoId → programacionId
  const records: Programacion[] = [];
  Object.entries(data).forEach(([subregion, subregionData]: [string, any]) => {
    if (typeof subregionData !== 'object' || subregionData === null) return;
    Object.entries(subregionData).forEach(([vehiculoId, vehiculoData]: [string, any]) => {
      if (typeof vehiculoData !== 'object' || vehiculoData === null) return;
      Object.entries(vehiculoData).forEach(([progId, progData]: [string, any]) => {
        if (typeof progData !== 'object' || progData === null) return;
        records.push({ id: progId, subregion, vehiculoId, ...progData });
      });
    });
  });
  records.sort((a, b) => (b.fecha || '').localeCompare(a.fecha || ''));

  await dexieWrite(db.programaciones, 'programaciones', records);
  return records;
}

export function useProgramacion(): UseProgramacionResult {
  const { data: programaciones = [], isLoading, error } = useQuery<Programacion[]>({
    queryKey: ['programaciones'],
    queryFn: fetchProgramaciones,
    staleTime: 5 * 60_000,
  });

  return {
    programaciones,
    loading: isLoading,
    error: error ? (error as Error).message : null,
  };
}
