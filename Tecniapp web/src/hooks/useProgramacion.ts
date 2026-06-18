import { useState, useEffect } from 'react';
import { ref, onValue, off } from 'firebase/database';
import { rtdbProgramacion } from '../firebase/config';

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

export function useProgramacion(): UseProgramacionResult {
  const [programaciones, setProgramaciones] = useState<Programacion[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!rtdbProgramacion) {
      setError('Firebase RTDB (programacion) no configurado');
      setLoading(false);
      return;
    }

    // Path: /programaciones/{subregion}/{vehiculoId}/{programacionId}
    const progRef = ref(rtdbProgramacion, '/programaciones');

    const unsubscribe = onValue(
      progRef,
      (snapshot) => {
        try {
          const data = snapshot.val();
          if (!data) {
            setProgramaciones([]);
            setLoading(false);
            return;
          }

          const list: Programacion[] = [];

          // Flatten nested structure: subregion → vehiculoId → programacionId
          Object.entries(data).forEach(([subregion, subregionData]: [string, any]) => {
            if (typeof subregionData !== 'object' || subregionData === null) return;
            Object.entries(subregionData).forEach(
              ([vehiculoId, vehiculoData]: [string, any]) => {
                if (typeof vehiculoData !== 'object' || vehiculoData === null) return;
                Object.entries(vehiculoData).forEach(
                  ([progId, progData]: [string, any]) => {
                    if (typeof progData !== 'object' || progData === null) return;
                    list.push({
                      id: progId,
                      subregion,
                      vehiculoId,
                      ...progData,
                    });
                  }
                );
              }
            );
          });

          // Sort by fecha descending
          list.sort((a, b) => {
            const da = a.fecha || '';
            const db = b.fecha || '';
            return db.localeCompare(da);
          });

          setProgramaciones(list);
          setLoading(false);
        } catch (err) {
          setError('Error al procesar programaciones');
          setLoading(false);
        }
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );

    return () => {
      off(progRef);
    };
  }, []);

  return { programaciones, loading, error };
}
