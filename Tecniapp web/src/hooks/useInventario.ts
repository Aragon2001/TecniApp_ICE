import { useState, useEffect } from 'react';
import { ref, onValue, off } from 'firebase/database';
import { rtdbInventario } from '../firebase/config';

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

    const inventarioRef = ref(rtdbInventario, '/inventario');

    const unsubscribe = onValue(
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

          // Structure: /inventario/{vehiculoId}/{itemId}
          Object.entries(data).forEach(([vehiculoId, vehiculoItems]: [string, any]) => {
            if (typeof vehiculoItems === 'object' && vehiculoItems !== null) {
              const items: InventarioItem[] = Object.entries(vehiculoItems).map(
                ([itemId, itemData]: [string, any]) => ({
                  id: itemId,
                  vehiculoId,
                  ...itemData,
                })
              );
              list.push(...items);
              byVehiculo[vehiculoId] = items;
            }
          });

          list.sort((a, b) => (a.nombre || '').localeCompare(b.nombre || '', 'es'));
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
