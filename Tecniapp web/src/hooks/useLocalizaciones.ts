import { useMemo, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ref, get, update, set, remove, push } from 'firebase/database'
import { rtdbMain } from '../firebase/config'
import { queryClient } from '../lib/queryClient'
import { db, dexieRead, dexieWrite, dexieInvalidate } from '../lib/db'
import { addToQueue } from '../lib/syncQueue'
import toast from 'react-hot-toast'

export interface Localizacion {
  id: string
  nombre?: string
  codigo?: string
  agencia?: string
  subregion?: string
  tipo?: string
  latitud?: number
  longitud?: number
  circuito?: string
  [key: string]: any
}

async function fetchLocalizaciones(): Promise<Localizacion[]> {
  const cached = await dexieRead<Localizacion>(db.localizaciones, 'localizaciones')
  if (cached !== null) return cached

  if (!rtdbMain) return []
  const snapshot = await get(ref(rtdbMain, '/localizaciones'))
  const data = snapshot.val()
  if (!data) { await dexieWrite(db.localizaciones, 'localizaciones', []); return [] }

  const list: Localizacion[] = Object.entries(data).map(([key, value]: [string, any]) => ({ id: key, ...value }))
  list.sort((a, b) => (a.nombre || '').localeCompare(b.nombre || '', 'es'))
  await dexieWrite(db.localizaciones, 'localizaciones', list)
  return list
}

export function useLocalizaciones() {
  const { data = [], isLoading, error } = useQuery({
    queryKey: ['localizaciones'],
    queryFn: fetchLocalizaciones,
    staleTime: 60 * 60_000,
  })

  const searchLocalizacion = useCallback((query: string): Localizacion[] => {
    if (!query.trim()) return data
    const q = query.toLowerCase()
    return data.filter((l) =>
      l.nombre?.toLowerCase().includes(q) ||
      l.codigo?.toLowerCase().includes(q) ||
      l.agencia?.toLowerCase().includes(q) ||
      l.circuito?.toLowerCase().includes(q) ||
      l.id?.toLowerCase().includes(q)
    )
  }, [data])

  const localizacionesBySubregion = useMemo(() => {
    const map: Record<string, Localizacion[]> = {}
    data.forEach((l) => {
      const sub = l.subregion || 'Sin subregión'
      if (!map[sub]) map[sub] = []
      map[sub].push(l)
    })
    return map
  }, [data])

  return { localizaciones: data, loading: isLoading, error: error ? (error as Error).message : null, searchLocalizacion, localizacionesBySubregion }
}

// ── Mutations ────────────────────────────────────────────────────────────────

export async function updateLocalizacion(locId: string, updates: Partial<Localizacion>): Promise<void> {
  const payload = { ...updates, updatedAt: Date.now() }
  delete payload.id

  if (!navigator.onLine) {
    await db.localizaciones.update(locId, payload)
    await addToQueue({ collectionKey: 'localizaciones', entityId: locId, rtdbPath: `/localizaciones/${locId}`, payload })
    queryClient.setQueryData<Localizacion[]>(['localizaciones'], (old) =>
      old ? old.map((l) => (l.id === locId ? { ...l, ...payload } : l)) : old
    )
    toast('Cambio guardado localmente.', { icon: '📶' })
    return
  }
  if (!rtdbMain) throw new Error('Firebase RTDB no configurado')
  await update(ref(rtdbMain, `/localizaciones/${locId}`), payload)
  await dexieInvalidate('localizaciones')
  queryClient.invalidateQueries({ queryKey: ['localizaciones'] })
}

export async function createLocalizacion(data: Omit<Localizacion, 'id'>): Promise<string> {
  if (!rtdbMain) throw new Error('Firebase RTDB no configurado')
  const newRef = push(ref(rtdbMain, '/localizaciones'))
  await set(newRef, { ...data, creadoAt: Date.now() })
  await dexieInvalidate('localizaciones')
  queryClient.invalidateQueries({ queryKey: ['localizaciones'] })
  return newRef.key!
}

export async function deleteLocalizacion(locId: string): Promise<void> {
  if (!navigator.onLine) {
    await db.localizaciones.delete(locId)
    await addToQueue({ collectionKey: 'localizaciones', entityId: locId, rtdbPath: `/localizaciones/${locId}`, payload: {}, operation: 'delete' } as any)
    queryClient.setQueryData<Localizacion[]>(['localizaciones'], (old) => old?.filter((l) => l.id !== locId))
    toast('Eliminación guardada localmente.', { icon: '📶' })
    return
  }
  if (!rtdbMain) throw new Error('Firebase RTDB no configurado')
  await remove(ref(rtdbMain, `/localizaciones/${locId}`))
  await dexieInvalidate('localizaciones')
  queryClient.invalidateQueries({ queryKey: ['localizaciones'] })
}
