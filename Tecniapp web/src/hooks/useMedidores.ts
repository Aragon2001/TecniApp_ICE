import { useState, useEffect, useCallback } from 'react';
import { ref, onValue, off } from 'firebase/database';
import { rtdbMain } from '../firebase/config';

export interface Medidor {
  id: string;
  numero?: string;
  tipo?: string;
  localizacion?: string;
  estado?: string;
  lectura?: number;
  ultimaLectura?: number;
  fechaInstalacion?: number;
  agencia?: string;
  cliente?: string;
  tarifa?: string;
  latitud?: number;
  longitud?: number;
  [key: string]: any;
}

interface UseMedidoresResult {
  medidores: Medidor[];
  loading: boolean;
  error: string | null;
  searchMedidor: (query: string) => Medidor[];
}

export function useMedidores(): UseMedidoresResult {
  const [medidores, setMedidores] = useState<Medidor[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!rtdbMain) {
      setError('Firebase RTDB (main) no configurado');
      setLoading(false);
      return;
    }

    const medidoresRef = ref(rtdbMain, '/medidores');

    const unsubscribe = onValue(
      medidoresRef,
      (snapshot) => {
        try {
          const data = snapshot.val();
          if (!data) {
            setMedidores([]);
            setLoading(false);
            return;
          }

          const list: Medidor[] = Object.entries(data).map(
            ([key, value]: [string, any]) => ({
              id: key,
              ...value,
            })
          );

          list.sort((a, b) => (a.numero || '').localeCompare(b.numero || '', 'es'));
          setMedidores(list);
          setLoading(false);
        } catch (err) {
          setError('Error al procesar medidores');
          setLoading(false);
        }
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );

    return () => {
      off(medidoresRef);
    };
  }, []);

  const searchMedidor = useCallback(
    (query: string): Medidor[] => {
      if (!query.trim()) return medidores;
      const q = query.toLowerCase();
      return medidores.filter(
        (m) =>
          m.numero?.toLowerCase().includes(q) ||
          m.localizacion?.toLowerCase().includes(q) ||
          m.cliente?.toLowerCase().includes(q) ||
          m.id?.toLowerCase().includes(q)
      );
    },
    [medidores]
  );

  return { medidores, loading, error, searchMedidor };
}
