import { useQuery } from '@tanstack/react-query'
import { ref, get, update, set } from 'firebase/database'
import { rtdbUsers } from '../firebase/config'
import { queryClient } from '../lib/queryClient'
import { db, dexieRead, dexieWrite, dexieInvalidate } from '../lib/db'
import { addToQueue } from '../lib/syncQueue'
import toast from 'react-hot-toast'

export interface Usuario {
  id: string
  email?: string
  nombre?: string
  apellidos?: string
  cedula?: string
  telefono?: string
  rol?: string
  agencia?: string
  agenciaId?: string
  region?: string
  regionNombre?: string
  subregion?: string
  subregionNombre?: string
  placaVehiculo?: string
  vehiculoId?: string
  uid?: string
  fotoUrl?: string
  activo?: boolean
  [key: string]: any
}

// Firebase key encoding: dots → commas (Android emailToKey)
export function emailToKey(email: string): string {
  return email.replace(/\./g, ',')
}
function keyToEmail(key: string): string {
  return key.replace(/,/g, '.')
}

async function fetchUsuarios(): Promise<Usuario[]> {
  const cached = await dexieRead<Usuario>(db.usuarios, 'usuarios')
  if (cached !== null) return cached

  if (!rtdbUsers) return []
  const snapshot = await get(ref(rtdbUsers, '/users'))
  const data = snapshot.val()
  if (!data) { await dexieWrite(db.usuarios, 'usuarios', []); return [] }

  const list: Usuario[] = Object.entries(data).map(([key, value]: [string, any]) => ({
    id: key,
    email: value.email || keyToEmail(key),
    ...value,
  }))
  list.sort((a, b) => (a.nombre || a.email || '').localeCompare(b.nombre || b.email || '', 'es'))
  await dexieWrite(db.usuarios, 'usuarios', list)
  return list
}

export function useUsuarios() {
  const { data = [], isLoading, error } = useQuery({
    queryKey: ['usuarios'],
    queryFn: fetchUsuarios,
    staleTime: 30 * 60_000,
  })
  return { usuarios: data, loading: isLoading, error: error ? (error as Error).message : null }
}

export function useUsuario(uid: string | null) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['usuarios', uid],
    queryFn: async () => {
      if (!uid) return null
      const cached = await db.usuarios.get(uid)
      if (cached) return cached as Usuario
      if (!rtdbUsers) return null
      // Try by UID (stored as email-key)
      const snap = await get(ref(rtdbUsers, '/users'))
      const data = snap.val()
      if (!data) return null
      const entry = Object.entries(data).find(([, v]: [string, any]) => v.uid === uid || v.email === uid)
      if (!entry) return null
      const [key, val] = entry
      return { id: key, email: keyToEmail(key), ...(val as object) } as Usuario
    },
    enabled: !!uid,
    staleTime: 30 * 60_000,
  })
  return { usuario: data ?? null, loading: isLoading, error: error ? (error as Error).message : null }
}

// ── Mutations ────────────────────────────────────────────────────────────────

export async function updateUsuario(userKey: string, updates: Partial<Usuario>): Promise<void> {
  const payload = { ...updates, updatedAt: Date.now() }
  delete payload.id

  if (!navigator.onLine) {
    await db.usuarios.update(userKey, payload)
    await addToQueue({ collectionKey: 'usuarios', entityId: userKey, rtdbPath: `/users/${userKey}`, payload })
    queryClient.setQueryData<Usuario[]>(['usuarios'], (old) =>
      old ? old.map((u) => (u.id === userKey ? { ...u, ...payload } : u)) : old
    )
    toast('Cambio guardado localmente.', { icon: '📶' })
    return
  }
  if (!rtdbUsers) throw new Error('Firebase RTDB (users) no configurado')
  await update(ref(rtdbUsers, `/users/${userKey}`), payload)
  await dexieInvalidate('usuarios')
  queryClient.invalidateQueries({ queryKey: ['usuarios'] })
}

export async function createUsuario(email: string, data: Omit<Usuario, 'id'>): Promise<void> {
  if (!rtdbUsers) throw new Error('Firebase RTDB (users) no configurado')
  const key = emailToKey(email)
  await set(ref(rtdbUsers, `/users/${key}`), { ...data, email, creadoAt: Date.now() })
  await dexieInvalidate('usuarios')
  queryClient.invalidateQueries({ queryKey: ['usuarios'] })
}
