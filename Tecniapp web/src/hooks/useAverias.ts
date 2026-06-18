import { useState, useEffect, useCallback } from 'react';
import { ref, onValue, update, off } from 'firebase/database';
import { rtdbAverias } from '../firebase/config';

export interface Averia {
  id: string;
  casoId?: string;
  descripcion?: string;
  tipoAveria?: string;
  estado: 'PENDIENTE' | 'ASIGNADA' | 'EN_ATENCION' | 'RESUELTA' | 'ANULADA';
  agencia?: string;
  localizacion?: string;
  vehiculoId?: string;
  tecnicoUid?: string;
  tecnicoNombre?: string;
  fechaCreacion?: number;
  fechaAsignacion?: number;
  fechaResolucion?: number;
  latitud?: number;
  longitud?: number;
  prioridad?: string;
  circuito?: string;
  [key: string]: any;
}

interface UseAveriasFilter {
  estado?: string;
  agencia?: string;
  search?: string;
}

interface UseAveriasResult {
  averias: Averia[];
  loading: boolean;
  error: string | null;
}

export function useAverias(filters?: UseAveriasFilter): UseAveriasResult {
  const [averias, setAverias] = useState<Averia[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!rtdbAverias) {
      setError('Firebase RTDB (averias) no configurado');
      setLoading(false);
      return;
    }

    const averiasRef = ref(rtdbAverias, '/averias');

    const unsubscribe = onValue(
      averiasRef,
      (snapshot) => {
        try {
          const data = snapshot.val();
          if (!data) {
            setAverias([]);
            setLoading(false);
            return;
          }

          let list: Averia[] = Object.entries(data).map(([key, value]: [string, any]) => ({
            id: key,
            ...value,
          }));

          // Apply filters
          if (filters?.estado) {
            list = list.filter((a) => a.estado === filters.estado);
          }
          if (filters?.agencia) {
            list = list.filter((a) =>
              a.agencia?.toLowerCase().includes(filters.agencia!.toLowerCase())
            );
          }
          if (filters?.search) {
            const q = filters.search.toLowerCase();
            list = list.filter(
              (a) =>
                a.descripcion?.toLowerCase().includes(q) ||
                a.tipoAveria?.toLowerCase().includes(q) ||
                a.localizacion?.toLowerCase().includes(q) ||
                a.agencia?.toLowerCase().includes(q) ||
                a.id?.toLowerCase().includes(q)
            );
          }

          // Sort: newest first
          list.sort((a, b) => (b.fechaCreacion || 0) - (a.fechaCreacion || 0));

          setAverias(list);
          setLoading(false);
        } catch (err) {
          setError('Error al procesar averías');
          setLoading(false);
        }
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );

    return () => {
      off(averiasRef);
    };
  // Re-subscribe when filter values change
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters?.estado, filters?.agencia, filters?.search]);

  return { averias, loading, error };
}

// Single averia hook
export function useAveria(caseId: string | null) {
  const [averia, setAveria] = useState<Averia | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!caseId || !rtdbAverias) {
      setLoading(false);
      return;
    }

    const averiaRef = ref(rtdbAverias, `/averias/${caseId}`);

    const unsubscribe = onValue(
      averiaRef,
      (snapshot) => {
        const data = snapshot.val();
        if (data) {
          setAveria({ id: caseId, ...data });
        } else {
          setAveria(null);
        }
        setLoading(false);
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );

    return () => {
      off(averiaRef);
    };
  }, [caseId]);

  return { averia, loading, error };
}

// Update averia estado
export async function updateAveriaEstado(
  caseId: string,
  updates: Partial<Averia>
): Promise<void> {
  if (!rtdbAverias) throw new Error('Firebase RTDB (averias) no configurado');
  const averiaRef = ref(rtdbAverias, `/averias/${caseId}`);
  await update(averiaRef, {
    ...updates,
    updatedAt: Date.now(),
  });
}

// Assign averia to a vehicle/technician
export async function assignAveria(
  caseId: string,
  vehiculoId: string,
  tecnicoUid: string,
  tecnicoNombre: string
): Promise<void> {
  if (!rtdbAverias) throw new Error('Firebase RTDB (averias) no configurado');
  const averiaRef = ref(rtdbAverias, `/averias/${caseId}`);
  await update(averiaRef, {
    estado: 'ASIGNADA',
    vehiculoId,
    tecnicoUid,
    tecnicoNombre,
    fechaAsignacion: Date.now(),
    updatedAt: Date.now(),
  });
}
