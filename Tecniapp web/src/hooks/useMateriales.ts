import { useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ref, get, update, set, remove, push } from 'firebase/database'
import { rtdbMateriales } from '../firebase/config'
import { queryClient } from '../lib/queryClient'
import { db, dexieRead, dexieWrite, dexieInvalidate } from '../lib/db'
import { addToQueue } from '../lib/syncQueue'
import toast from 'react-hot-toast'

export interface Material {
  id: string
  nombre?: string
  codigo?: string
  descripcion?: string
  unidad?: string
  categoria?: string
  precio?: number
  [key: string]: any
}

async function fetchMateriales(): Promise<Material[]> {
  const cached = await dexieRead<Material>(db.materiales, 'materiales')
  if (cached !== null) return cached

  if (!rtdbMateriales) return []
  const snapshot = await get(ref(rtdbMateriales, '/materiales'))
  const data = snapshot.val()
  if (!data) { await dexieWrite(db.materiales, 'materiales', []); return [] }

  const list: Material[] = Object.entries(data).map(([key, value]: [string, any]) => ({ id: key, ...value }))
  list.sort((a, b) => (a.nombre || '').localeCompare(b.nombre || '', 'es'))
  await dexieWrite(db.materiales, 'materiales', list)
  return list
}

export function useMateriales() {
  const { data = [], isLoading, error } = useQuery({
    queryKey: ['materiales'],
    queryFn: fetchMateriales,
    staleTime: 60 * 60_000,
  })

  const searchMaterial = useCallback((query: string): Material[] => {
    if (!query.trim()) return data
    const q = query.toLowerCase()
    return data.filter((m) =>
      m.nombre?.toLowerCase().includes(q) ||
      m.codigo?.toLowerCase().includes(q) ||
      m.descripcion?.toLowerCase().includes(q) ||
      m.categoria?.toLowerCase().includes(q)
    )
  }, [data])

  return { materiales: data, loading: isLoading, error: error ? (error as Error).message : null, searchMaterial }
}

export async function updateMaterial(materialId: string, updates: Partial<Material>): Promise<void> {
  const payload = { ...updates, updatedAt: Date.now() }
  delete payload.id

  if (!navigator.onLine) {
    await db.materiales.update(materialId, payload)
    await addToQueue({ collectionKey: 'materiales', entityId: materialId, rtdbPath: `/materiales/${materialId}`, payload })
    queryClient.setQueryData<Material[]>(['materiales'], (old) =>
      old ? old.map((m) => (m.id === materialId ? { ...m, ...payload } : m)) : old
    )
    toast('Cambio guardado localmente.', { icon: '📶' })
    return
  }
  if (!rtdbMateriales) throw new Error('Firebase RTDB (materiales) no configurado')
  await update(ref(rtdbMateriales, `/materiales/${materialId}`), payload)
  await dexieInvalidate('materiales')
  queryClient.invalidateQueries({ queryKey: ['materiales'] })
}

export async function createMaterial(data: Omit<Material, 'id'>): Promise<string> {
  if (!rtdbMateriales) throw new Error('Firebase RTDB (materiales) no configurado')
  const newRef = push(ref(rtdbMateriales, '/materiales'))
  await set(newRef, { ...data, creadoAt: Date.now() })
  await dexieInvalidate('materiales')
  queryClient.invalidateQueries({ queryKey: ['materiales'] })
  return newRef.key!
}

export async function deleteMaterial(materialId: string): Promise<void> {
  if (!navigator.onLine) {
    await db.materiales.delete(materialId)
    await addToQueue({ collectionKey: 'materiales', entityId: materialId, rtdbPath: `/materiales/${materialId}`, payload: {}, operation: 'delete' } as any)
    queryClient.setQueryData<Material[]>(['materiales'], (old) => old?.filter((m) => m.id !== materialId))
    toast('Eliminación guardada localmente.', { icon: '📶' })
    return
  }
  if (!rtdbMateriales) throw new Error('Firebase RTDB (materiales) no configurado')
  await remove(ref(rtdbMateriales, `/materiales/${materialId}`))
  await dexieInvalidate('materiales')
  queryClient.invalidateQueries({ queryKey: ['materiales'] })
}
