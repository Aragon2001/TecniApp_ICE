import { useState, useEffect, useCallback } from 'react';
import { ref, onValue, off } from 'firebase/database';
import { rtdbMain } from '../firebase/config';

export interface Localizacion {
  id: string;
  nombre?: string;
  codigo?: string;
  agencia?: string;
  subregion?: string;
  tipo?: string;
  latitud?: number;
  longitud?: number;
  circuito?: string;
  [key: string]: any;
}

interface UseLocalizacionesResult {
  localizaciones: Localizacion[];
  loading: boolean;
  error: string | null;
  searchLocalizacion: (query: string) => Localizacion[];
}

export function useLocalizaciones(): UseLocalizacionesResult {
  const [localizaciones, setLocalizaciones] = useState<Localizacion[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const db = rtdbMain;
    if (!db) {
      setError('Firebase RTDB no configurado');
      setLoading(false);
      return;
    }

    const locRef = ref(db, '/localizaciones');

    onValue(
      locRef,
      (snapshot) => {
        try {
          const data = snapshot.val();
          if (!data) {
            setLocalizaciones([]);
            setLoading(false);
            return;
          }

          const list: Localizacion[] = Object.entries(data).map(
            ([key, value]: [string, any]) => ({
              id: key,
              ...value,
            })
          );

          list.sort((a, b) => (a.nombre || '').localeCompare(b.nombre || '', 'es'));
          setLocalizaciones(list);
          setLoading(false);
        } catch (err) {
          setError('Error al procesar localizaciones');
          setLoading(false);
        }
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );

    return () => {
      off(locRef);
    };
  }, []);

  const searchLocalizacion = useCallback(
    (query: string): Localizacion[] => {
      if (!query.trim()) return localizaciones;
      const q = query.toLowerCase();
      return localizaciones.filter(
        (l) =>
          l.nombre?.toLowerCase().includes(q) ||
          l.codigo?.toLowerCase().includes(q) ||
          l.agencia?.toLowerCase().includes(q) ||
          l.circuito?.toLowerCase().includes(q) ||
          l.id?.toLowerCase().includes(q)
      );
    },
    [localizaciones]
  );

  return { localizaciones, loading, error, searchLocalizacion };
}
