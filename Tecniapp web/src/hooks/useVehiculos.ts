import { useQuery } from '@tanstack/react-query';
import { ref, get } from 'firebase/database';
import { rtdbGeneral } from '../firebase/config';
import { db, dexieRead, dexieWrite } from '../lib/db';

export type VehiculoEstado = 'OPTIMO' | 'ATENCION' | 'VENCIDO';

export interface Vehiculo {
  id: string;
  placa?: string;
  marca?: string;
  modelo?: string;
  anio?: number;
  tipo?: string;
  agencia?: string;
  kmActual?: number;
  mantenimientoProximo?: number;
  tecnicoAsignado?: string;
  tecnicoUid?: string;
  activo?: boolean;
  estado: VehiculoEstado;
  [key: string]: any;
}

function computeEstado(kmActual?: number, mantenimientoProximo?: number): VehiculoEstado {
  if (kmActual == null || mantenimientoProximo == null) return 'OPTIMO';
  if (kmActual >= mantenimientoProximo) return 'VENCIDO';
  if (mantenimientoProximo - kmActual <= 500) return 'ATENCION';
  return 'OPTIMO';
}

interface UseVehiculosResult {
  vehiculos: Vehiculo[];
  loading: boolean;
  error: string | null;
}

async function fetchVehiculos(): Promise<Vehiculo[]> {
  const cached = await dexieRead<Vehiculo>(db.vehiculos, 'vehiculos');
  if (cached !== null) {
    return cached.sort((a, b) => (a.placa || '').localeCompare(b.placa || '', 'es'));
  }

  if (!rtdbGeneral) throw new Error('Firebase RTDB (general) no configurado');
  const snapshot = await get(ref(rtdbGeneral, '/vehiculos'));
  const data = snapshot.val() ?? {};
  const records: Vehiculo[] = Object.entries(data).map(([id, val]: [string, any]) => ({
    id,
    ...val,
    estado: computeEstado(val.kmActual, val.mantenimientoProximo),
  }));
  records.sort((a, b) => (a.placa || '').localeCompare(b.placa || '', 'es'));

  await dexieWrite(db.vehiculos, 'vehiculos', records);
  return records;
}

export function useVehiculos(): UseVehiculosResult {
  const { data: vehiculos = [], isLoading, error } = useQuery<Vehiculo[]>({
    queryKey: ['vehiculos'],
    queryFn: fetchVehiculos,
    staleTime: 10 * 60_000,
  });

  return {
    vehiculos,
    loading: isLoading,
    error: error ? (error as Error).message : null,
  };
}

interface UseVehiculoResult {
  vehiculo: Vehiculo | null;
  loading: boolean;
  error: string | null;
}

export function useVehiculo(vehiculoId: string | null): UseVehiculoResult {
  const { vehiculos, loading, error } = useVehiculos();
  const vehiculo = vehiculoId
    ? (vehiculos.find((v) => v.id === vehiculoId || v.placa === vehiculoId) ?? null)
    : null;
  return { vehiculo, loading, error };
}
