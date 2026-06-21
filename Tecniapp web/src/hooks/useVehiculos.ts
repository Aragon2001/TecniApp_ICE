import { useState, useEffect } from 'react';
import { ref, onValue, off } from 'firebase/database';
import { rtdbGeneral } from '../firebase/config';

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

export function useVehiculos(): UseVehiculosResult {
  const [vehiculos, setVehiculos] = useState<Vehiculo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!rtdbGeneral) {
      setError('Firebase RTDB (general) no configurado');
      setLoading(false);
      return;
    }

    const vehiculosRef = ref(rtdbGeneral, '/vehiculos');

    onValue(
      vehiculosRef,
      (snapshot) => {
        try {
          const data = snapshot.val();
          if (!data) {
            setVehiculos([]);
            setLoading(false);
            return;
          }

          const list: Vehiculo[] = Object.entries(data).map(
            ([key, value]: [string, any]) => ({
              id: key,
              ...value,
              estado: computeEstado(value.kmActual, value.mantenimientoProximo),
            })
          );

          list.sort((a, b) => (a.placa || '').localeCompare(b.placa || '', 'es'));
          setVehiculos(list);
          setLoading(false);
        } catch (err) {
          setError('Error al procesar vehículos');
          setLoading(false);
        }
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );

    return () => {
      off(vehiculosRef);
    };
  }, []);

  return { vehiculos, loading, error };
}

interface UseVehiculoResult {
  vehiculo: Vehiculo | null;
  loading: boolean;
  error: string | null;
}

export function useVehiculo(vehiculoId: string | null): UseVehiculoResult {
  const [vehiculo, setVehiculo] = useState<Vehiculo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!vehiculoId) {
      setError('ID de vehículo no proporcionado');
      setLoading(false);
      return;
    }
    if (!rtdbGeneral) {
      setError('Firebase RTDB (general) no configurado');
      setLoading(false);
      return;
    }

    const vehiculoRef = ref(rtdbGeneral, `/vehiculos/${vehiculoId}`);

    onValue(
      vehiculoRef,
      (snapshot) => {
        const data = snapshot.val();
        if (!data) {
          setVehiculo(null);
          setError('Vehículo no encontrado');
        } else {
          setVehiculo({
            id: vehiculoId,
            ...data,
            estado: computeEstado(data.kmActual, data.mantenimientoProximo),
          });
          setError(null);
        }
        setLoading(false);
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );

    return () => {
      off(vehiculoRef);
    };
  }, [vehiculoId]);

  return { vehiculo, loading, error };
}
