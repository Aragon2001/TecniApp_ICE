import { useState, useEffect } from 'react';
import { ref, onValue, off } from 'firebase/database';
import { rtdbUsers } from '../firebase/config';

export interface Usuario {
  id: string;
  email?: string;
  displayName?: string;
  role?: string;
  agencia?: string;
  activo?: boolean;
  photoURL?: string;
  lastLogin?: number;
  createdAt?: number;
  [key: string]: any;
}

interface UseUsuariosResult {
  usuarios: Usuario[];
  loading: boolean;
  error: string | null;
}

// Firebase key encoding: dots → commas
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

    const unsubscribe = onValue(
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
            (a.displayName || a.email || '').localeCompare(
              b.displayName || b.email || '',
              'es'
            )
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
