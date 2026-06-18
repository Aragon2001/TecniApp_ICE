import { useState, useEffect } from 'react';
import { ref, onValue, off, update } from 'firebase/database';
import { rtdbInventario } from '../firebase/config';

export interface Luminaria {
  id: string;
  numero?: string;
  tipo?: string;
  potencia?: number;
  estado?: string;
  localizacion?: string;
  latitud?: number;
  longitud?: number;
  agencia?: string;
  fechaInstalacion?: number;
  ultimoMantenimiento?: number;
  observaciones?: string;
  [key: string]: any;
}

interface UseLuminariasResult {
  luminarias: Luminaria[];
  loading: boolean;
  error: string | null;
}

export function useLuminarias(): UseLuminariasResult {
  const [luminarias, setLuminarias] = useState<Luminaria[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!rtdbInventario) {
      setError('Firebase RTDB (inventario) no configurado');
      setLoading(false);
      return;
    }

    const lumRef = ref(rtdbInventario, '/luminarias');

    onValue(
      lumRef,
      (snapshot) => {
        try {
          const data = snapshot.val();
          if (!data) {
            setLuminarias([]);
            setLoading(false);
            return;
          }

          const list: Luminaria[] = Object.entries(data).map(
            ([key, value]: [string, any]) => ({
              id: key,
              ...value,
            })
          );

          list.sort((a, b) => (a.numero || '').localeCompare(b.numero || '', 'es'));
          setLuminarias(list);
          setLoading(false);
        } catch (err) {
          setError('Error al procesar luminarias');
          setLoading(false);
        }
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );

    return () => {
      off(lumRef);
    };
  }, []);

  return { luminarias, loading, error };
}

export async function updateLuminariaEstado(
  luminariaId: string,
  estado: string,
  observaciones?: string
): Promise<void> {
  if (!rtdbInventario) throw new Error('Firebase RTDB (inventario) no configurado');
  const lumRef = ref(rtdbInventario, `/luminarias/${luminariaId}`);
  await update(lumRef, {
    estado,
    observaciones: observaciones || null,
    updatedAt: Date.now(),
  });
}
