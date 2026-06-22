import { useQuery } from '@tanstack/react-query';
import { ref, get } from 'firebase/database';
import { rtdbUsers } from '../firebase/config';
import { db, dexieRead, dexieWrite } from '../lib/db';

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

async function fetchUsuarios(): Promise<Usuario[]> {
  const cached = await dexieRead<Usuario>(db.usuarios, 'usuarios');
  if (cached !== null) {
    return cached.sort((a, b) =>
      (a.displayName || a.email || '').localeCompare(b.displayName || b.email || '', 'es')
    );
  }

  if (!rtdbUsers) throw new Error('Firebase RTDB (users) no configurado');
  const snapshot = await get(ref(rtdbUsers, '/users'));
  const data = snapshot.val() ?? {};
  const records: Usuario[] = Object.entries(data).map(([key, value]: [string, any]) => ({
    id: key,
    email: value.email || keyToEmail(key),
    ...value,
  }));
  records.sort((a, b) =>
    (a.displayName || a.email || '').localeCompare(b.displayName || b.email || '', 'es')
  );

  await dexieWrite(db.usuarios, 'usuarios', records);
  return records;
}

export function useUsuarios(): UseUsuariosResult {
  const { data: usuarios = [], isLoading, error } = useQuery<Usuario[]>({
    queryKey: ['usuarios'],
    queryFn: fetchUsuarios,
    staleTime: 30 * 60_000,
  });

  return {
    usuarios,
    loading: isLoading,
    error: error ? (error as Error).message : null,
  };
}
