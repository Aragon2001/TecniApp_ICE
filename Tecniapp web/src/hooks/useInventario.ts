import { useState, useEffect } from 'react';
import { ref, onValue, off, update } from 'firebase/database';
import { rtdbInventario } from '../firebase/config';

export interface InventarioItem {
  id: string;               // = codigoMaterial (Firebase key)
  vehiculoId: string;
  codigoMaterial?: string;
  descripcionMaterial?: string;
  cantidadDisponible?: number;
  categoria?: string;
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

export function useInventario(): UseInventarioResult {
  const [inventario, setInventario] = useState<InventarioItem[]>([]);
  const [inventarioPorVehiculo, setInventarioPorVehiculo] = useState<InventarioPorVehiculo>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!rtdbInventario) {
      setError('Firebase RTDB (inventario) no configurado');
      setLoading(false);
      return;
    }

    // Structure: /inventario/{vehiculoId}/{codigoMaterial}
    const inventarioRef = ref(rtdbInventario, '/inventario');

    onValue(
      inventarioRef,
      (snapshot) => {
        try {
          const data = snapshot.val();
          if (!data) {
            setInventario([]);
            setInventarioPorVehiculo({});
            setLoading(false);
            return;
          }

          const list: InventarioItem[] = [];
          const byVehiculo: InventarioPorVehiculo = {};

          Object.entries(data).forEach(([vehiculoId, vehiculoItems]: [string, any]) => {
            if (typeof vehiculoItems !== 'object' || vehiculoItems === null) return;
            const items: InventarioItem[] = Object.entries(vehiculoItems).map(
              ([codigoMaterial, itemData]: [string, any]) => ({
                id: codigoMaterial,
                vehiculoId,
                codigoMaterial,
                ...itemData,
              })
            );
            list.push(...items);
            byVehiculo[vehiculoId] = items;
          });

          list.sort((a, b) =>
            (a.descripcionMaterial || a.codigoMaterial || '').localeCompare(
              b.descripcionMaterial || b.codigoMaterial || '',
              'es'
            )
          );
          setInventario(list);
          setInventarioPorVehiculo(byVehiculo);
          setLoading(false);
        } catch (err) {
          setError('Error al procesar inventario');
          setLoading(false);
        }
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );

    return () => {
      off(inventarioRef);
    };
  }, []);

  return { inventario, inventarioPorVehiculo, loading, error };
}

export async function updateCantidadInventario(
  vehiculoId: string,
  codigoMaterial: string,
  cantidadDisponible: number
): Promise<void> {
  if (!rtdbInventario) throw new Error('Firebase RTDB (inventario) no configurado');
  await update(ref(rtdbInventario, `inventario/${vehiculoId}/${codigoMaterial}`), {
    cantidadDisponible,
    updatedAt: Date.now(),
  });
}
