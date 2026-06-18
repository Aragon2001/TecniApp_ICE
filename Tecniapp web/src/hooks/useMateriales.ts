import { useState, useEffect } from 'react';
import { ref, onValue, off } from 'firebase/database';
import { rtdbMateriales } from '../firebase/config';

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

export function useMateriales(): UseMaterialesResult {
  const [materiales, setMateriales] = useState<Material[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!rtdbMateriales) {
      setError('Firebase RTDB (materiales) no configurado');
      setLoading(false);
      return;
    }

    const matsRef = ref(rtdbMateriales, '/materiales');

    const unsubscribe = onValue(
      matsRef,
      (snapshot) => {
        try {
          const data = snapshot.val();
          if (!data) {
            setMateriales([]);
            setLoading(false);
            return;
          }

          const list: Material[] = Object.entries(data).map(
            ([key, value]: [string, any]) => ({
              id: key,
              ...value,
            })
          );

          list.sort((a, b) => (a.nombre || '').localeCompare(b.nombre || '', 'es'));
          setMateriales(list);
          setLoading(false);
        } catch (err) {
          setError('Error al procesar materiales');
          setLoading(false);
        }
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );

    return () => {
      off(matsRef);
    };
  }, []);

  return { materiales, loading, error };
}
