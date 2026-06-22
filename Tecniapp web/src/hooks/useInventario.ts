import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ref, get } from 'firebase/database';
import { rtdbInventario } from '../firebase/config';
import { db, dexieRead, dexieWrite } from '../lib/db';

export interface InventarioItem {
  id: string;
  nombre?: string;
  codigo?: string;
  cantidad?: number;
  unidad?: string;
  vehiculoId?: string;
  categoria?: string;
  descripcion?: string;
  ultimaActualizacion?: number;
  [key: string]: any;
}

interface InventarioPorVehiculo {
  [vehiculoId: string]: InventarioItem[];
}

interface UseInventarioResult {
  inventario: InventarioItem[];
  inventarioPorVehiculo: InventarioPorVehiculo;
  loading: boolean;
  error: string | null;
}

async function fetchInventario(): Promise<InventarioItem[]> {
  const cached = await dexieRead<InventarioItem>(db.inventario, 'inventario');
  if (cached !== null) {
    return cached.sort((a, b) => (a.nombre || '').localeCompare(b.nombre || '', 'es'));
  }

  if (!rtdbInventario) throw new Error('Firebase RTDB (inventario) no configurado');
  const snapshot = await get(ref(rtdbInventario, '/inventario'));
  const data = snapshot.val() ?? {};

  // Aplana estructura /inventario/{vehiculoId}/{itemId}
  const records: InventarioItem[] = [];
  Object.entries(data).forEach(([vehiculoId, vehiculoItems]: [string, any]) => {
    if (typeof vehiculoItems !== 'object' || vehiculoItems === null) return;
    Object.entries(vehiculoItems).forEach(([itemId, itemData]: [string, any]) => {
      records.push({ id: itemId, vehiculoId, ...itemData });
    });
  });
  records.sort((a, b) => (a.nombre || '').localeCompare(b.nombre || '', 'es'));

  await dexieWrite(db.inventario, 'inventario', records);
  return records;
}

export function useInventario(): UseInventarioResult {
  const { data: inventario = [], isLoading, error } = useQuery<InventarioItem[]>({
    queryKey: ['inventario'],
    queryFn: fetchInventario,
    staleTime: 10 * 60_000,
  });

  const inventarioPorVehiculo = useMemo(() => {
    const byVehiculo: InventarioPorVehiculo = {};
    inventario.forEach((item) => {
      if (!item.vehiculoId) return;
      if (!byVehiculo[item.vehiculoId]) byVehiculo[item.vehiculoId] = [];
      byVehiculo[item.vehiculoId].push(item);
    });
    return byVehiculo;
  }, [inventario]);

  return {
    inventario,
    inventarioPorVehiculo,
    loading: isLoading,
    error: error ? (error as Error).message : null,
  };
}
