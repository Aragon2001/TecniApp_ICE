import { useQuery } from '@tanstack/react-query'
import { ref, get, update } from 'firebase/database'
import { rtdbAverias } from '../firebase/config'
import { queryClient } from '../lib/queryClient'
import { db, dexieRead, dexieWrite, dexieInvalidate } from '../lib/db'
import { addToQueue } from '../lib/syncQueue'
import toast from 'react-hot-toast'

export interface Averia {
  id: string
  casoId?: string
  descripcion?: string
  tipoAveria?: string
  estado: 'PENDIENTE' | 'ASIGNADA' | 'EN_ATENCION' | 'RESUELTA' | 'ANULADA'
  agencia?: string
  localizacion?: string
  vehiculoId?: string
  tecnicoUid?: string
  tecnicoNombre?: string
  fechaCreacion?: number
  fechaAsignacion?: number
  fechaResolucion?: number
  latitud?: number
  longitud?: number
  prioridad?: string
  circuito?: string
  [key: string]: any
}

interface UseAveriasFilter {
  estado?: string
  agencia?: string
  search?: string
}

async function fetchAverias(): Promise<Averia[]> {
  // Try Dexie first
  const cached = await dexieRead<Averia>(db.averias, 'averias')
  if (cached !== null) return cached

  if (!rtdbAverias) return []
  const snapshot = await get(ref(rtdbAverias, '/averias'))
  const data = snapshot.val()
  if (!data) {
    await dexieWrite(db.averias, 'averias', [])
    return []
  }
  const list: Averia[] = Object.entries(data).map(([key, value]: [string, any]) => ({
    id: key,
    ...value,
  }))
  await dexieWrite(db.averias, 'averias', list)
  return list
}

export function useAverias(filters?: UseAveriasFilter) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['averias'],
    queryFn: fetchAverias,
    staleTime: 60_000,
  })

  let list = data ?? []

  if (filters?.estado) {
    list = list.filter((a) => a.estado === filters.estado)
  }
  if (filters?.agencia) {
    list = list.filter((a) =>
      a.agencia?.toLowerCase().includes(filters.agencia!.toLowerCase())
    )
  }
  if (filters?.search) {
    const q = filters.search.toLowerCase()
    list = list.filter(
      (a) =>
        a.descripcion?.toLowerCase().includes(q) ||
        a.tipoAveria?.toLowerCase().includes(q) ||
        a.localizacion?.toLowerCase().includes(q) ||
        a.agencia?.toLowerCase().includes(q) ||
        a.id?.toLowerCase().includes(q)
    )
  }

  list = [...list].sort((a, b) => (b.fechaCreacion || 0) - (a.fechaCreacion || 0))

  return {
    averias: list,
    loading: isLoading,
    error: error ? (error as Error).message : null,
  }
}

export function useAveria(caseId: string | null) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['averias', caseId],
    queryFn: async () => {
      if (!caseId) return null

      // Check Dexie meta freshness first
      const meta = await db.meta.get('averias')
      if (meta && Date.now() - meta.cachedAt < 60_000) {
        const item = await db.averias.get(caseId)
        if (item) return item as Averia
      }

      if (!rtdbAverias) return null
      const snapshot = await get(ref(rtdbAverias, `/averias/${caseId}`))
      const data = snapshot.val()
      return data ? ({ id: caseId, ...data } as Averia) : null
    },
    enabled: !!caseId,
    staleTime: 60_000,
  })

  return { averia: data ?? null, loading: isLoading, error: error ? (error as Error).message : null }
}

export async function updateAveriaEstado(
  caseId: string,
  updates: Partial<Averia>
): Promise<void> {
  const payload = { ...updates, updatedAt: Date.now() }

  if (!navigator.onLine) {
    // Optimistic update in Dexie
    await db.averias.update(caseId, payload)
    await addToQueue({
      collectionKey: 'averias',
      entityId: caseId,
      rtdbPath: `/averias/${caseId}`,
      payload,
    })
    // Update React Query cache optimistically
    queryClient.setQueryData<Averia[]>(['averias'], (old) =>
      old ? old.map((a) => (a.id === caseId ? { ...a, ...payload } : a)) : old
    )
    queryClient.setQueryData<Averia>(['averias', caseId], (old) =>
      old ? { ...old, ...payload } : old
    )
    toast('Cambio guardado localmente. Se sincronizará al recuperar conexión.', { icon: '📶' })
    return
  }

  if (!rtdbAverias) throw new Error('Firebase RTDB (averias) no configurado')
  await update(ref(rtdbAverias, `/averias/${caseId}`), payload)
  await dexieInvalidate('averias')
  queryClient.invalidateQueries({ queryKey: ['averias'] })
}

export async function assignAveria(
  caseId: string,
  vehiculoId: string,
  tecnicoUid: string,
  tecnicoNombre: string
): Promise<void> {
  const payload: Partial<Averia> & Record<string, any> = {
    estado: 'ASIGNADA' as const,
    vehiculoId,
    tecnicoUid,
    tecnicoNombre,
    fechaAsignacion: Date.now(),
    updatedAt: Date.now(),
  }

  if (!navigator.onLine) {
    await db.averias.update(caseId, payload)
    await addToQueue({
      collectionKey: 'averias',
      entityId: caseId,
      rtdbPath: `/averias/${caseId}`,
      payload,
    })
    queryClient.setQueryData<Averia[]>(['averias'], (old) =>
      old ? old.map((a) => (a.id === caseId ? { ...a, ...payload } as Averia : a)) : old
    )
    toast('Asignación guardada localmente. Se sincronizará al recuperar conexión.', { icon: '📶' })
    return
  }

  if (!rtdbAverias) throw new Error('Firebase RTDB (averias) no configurado')
  await update(ref(rtdbAverias, `/averias/${caseId}`), payload)
  await dexieInvalidate('averias')
  queryClient.invalidateQueries({ queryKey: ['averias'] })
}
