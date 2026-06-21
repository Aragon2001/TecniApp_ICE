import { useState, useEffect } from 'react';
import { ref, onValue, off } from 'firebase/database';
import { rtdbUsers } from '../firebase/config';

export interface Usuario {
  id: string;           // Firebase key (email with dots replaced by commas)
  email?: string;
  nombre?: string;
  apellidos?: string;
  cedula?: string;
  telefono?: string;
  rol?: string;         // 'admin' | 'supervisor' | 'tecnico'
  agencia?: string;
  agenciaId?: string;
  region?: string;
  regionNombre?: string;
  subregion?: string;
  subregionNombre?: string;
  placaVehiculo?: string;
  vehiculoId?: string;
  uid?: string;
  fotoUrl?: string;
  activo?: boolean;
  [key: string]: any;
}

interface UseUsuariosResult {
  usuarios: Usuario[];
  loading: boolean;
  error: string | null;
}

// Firebase key encoding: commas → dots (reverses the Android emailToKey encoding)
function keyToEmail(key: string): string {
  return key.replace(/,/g, '.');
}

export function useUsuarios(): UseUsuariosResult {
  const [usuarios, setUsuarios] = useState<Usuario[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!rtdbUsers) {
      setError('Firebase RTDB (users) no configurado');
      setLoading(false);
      return;
    }

    const usersRef = ref(rtdbUsers, '/users');

    onValue(
      usersRef,
      (snapshot) => {
        try {
          const data = snapshot.val();
          if (!data) {
            setUsuarios([]);
            setLoading(false);
            return;
          }

          const list: Usuario[] = Object.entries(data).map(
            ([key, value]: [string, any]) => ({
              id: key,
              email: value.email || keyToEmail(key),
              ...value,
            })
          );

          list.sort((a, b) =>
            (a.nombre || a.email || '').localeCompare(b.nombre || b.email || '', 'es')
          );
          setUsuarios(list);
          setLoading(false);
        } catch (err) {
          setError('Error al procesar usuarios');
          setLoading(false);
        }
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );

    return () => {
      off(usersRef);
    };
  }, []);

  return { usuarios, loading, error };
}
