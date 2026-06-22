import { useQuery } from '@tanstack/react-query'
import { ref, get, update, set, remove, push } from 'firebase/database'
import { rtdbProgramacion } from '../firebase/config'
import { queryClient } from '../lib/queryClient'
import { db, dexieRead, dexieWrite, dexieInvalidate } from '../lib/db'
import { addToQueue } from '../lib/syncQueue'
import toast from 'react-hot-toast'

export interface Programacion {
  id: string
  subregion?: string
  vehiculoId?: string
  fecha?: string
  tipo?: string
  descripcion?: string
  estado?: string
  tecnicoUid?: string
  tecnicoNombre?: string
  localizacion?: string
  latitud?: number
  longitud?: number
  horaInicio?: string
  horaFin?: string
  materiales?: string[]
  observaciones?: string
  [key: string]: any
}

async function fetchProgramaciones(): Promise<Programacion[]> {
  const cached = await dexieRead<Programacion>(db.programaciones, 'programaciones')
  if (cached !== null) return cached

  if (!rtdbProgramacion) return []
  const snapshot = await get(ref(rtdbProgramacion, '/programaciones'))
  const data = snapshot.val()
  if (!data) { await dexieWrite(db.programaciones, 'programaciones', []); return [] }

  const list: Programacion[] = []
  // Flatten: /programaciones/{subregion}/{vehiculoId}/{id}
  Object.entries(data).forEach(([subregion, subData]: [string, any]) => {
    if (typeof subData !== 'object' || subData === null) return
    Object.entries(subData).forEach(([vehiculoId, vehiculoData]: [string, any]) => {
      if (typeof vehiculoData !== 'object' || vehiculoData === null) return
      Object.entries(vehiculoData).forEach(([progId, progData]: [string, any]) => {
        if (typeof progData !== 'object' || progData === null) return
        list.push({ id: progId, subregion, vehiculoId, ...progData })
      })
    })
  })

  list.sort((a, b) => (b.fecha || '').localeCompare(a.fecha || ''))
  await dexieWrite(db.programaciones, 'programaciones', list)
  return list
}

export function useProgramacion(filtros?: { subregion?: string; vehiculoId?: string; fecha?: string; estado?: string }) {
  const { data = [], isLoading, error } = useQuery({
    queryKey: ['programaciones'],
    queryFn: fetchProgramaciones,
    staleTime: 5 * 60_000,
  })

  let list = data
  if (filtros?.subregion) list = list.filter((p) => p.subregion === filtros.subregion)
  if (filtros?.vehiculoId) list = list.filter((p) => p.vehiculoId === filtros.vehiculoId)
  if (filtros?.fecha) list = list.filter((p) => p.fecha === filtros.fecha)
  if (filtros?.estado) list = list.filter((p) => p.estado === filtros.estado)

  return { programaciones: list, loading: isLoading, error: error ? (error as Error).message : null }
}

export function useProgramacionItem(progId: string | null) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['programaciones', progId],
    queryFn: async () => {
      if (!progId) return null
      const cached = await db.programaciones.get(progId)
      if (cached) return cached as Programacion
      return null
    },
    enabled: !!progId,
    staleTime: 5 * 60_000,
  })
  return { programacion: data ?? null, loading: isLoading, error: error ? (error as Error).message : null }
}

// ── Mutations ────────────────────────────────────────────────────────────────

export async function createProgramacion(
  subregion: string,
  vehiculoId: string,
  data: Omit<Programacion, 'id' | 'subregion' | 'vehiculoId'>
): Promise<string> {
  const payload = { ...data, subregion, vehiculoId, creadoAt: Date.now() }

  if (!navigator.onLine) {
    const tempId = `offline_${Date.now()}`
    const newItem: Programacion = { id: tempId, ...payload }
    await db.programaciones.add(newItem)
    await addToQueue({
      collectionKey: 'programaciones',
      entityId: tempId,
      rtdbPath: `/programaciones/${subregion}/${vehiculoId}/${tempId}`,
      payload,
      operation: 'set',
    } as any)
    queryClient.setQueryData<Programacion[]>(['programaciones'], (old) => old ? [newItem, ...old] : [newItem])
    toast('Programación guardada localmente.', { icon: '📶' })
    return tempId
  }

  if (!rtdbProgramacion) throw new Error('Firebase RTDB (programacion) no configurado')
  const newRef = push(ref(rtdbProgramacion, `/programaciones/${subregion}/${vehiculoId}`))
  await set(newRef, payload)
  await dexieInvalidate('programaciones')
  queryClient.invalidateQueries({ queryKey: ['programaciones'] })
  return newRef.key!
}

export async function updateProgramacion(prog: Programacion, updates: Partial<Programacion>): Promise<void> {
  const payload = { ...updates, updatedAt: Date.now() }
  delete payload.id
  delete payload.subregion
  delete payload.vehiculoId

  const rtdbPath = `/programaciones/${prog.subregion}/${prog.vehiculoId}/${prog.id}`

  if (!navigator.onLine) {
    await db.programaciones.update(prog.id, payload)
    await addToQueue({ collectionKey: 'programaciones', entityId: prog.id, rtdbPath, payload })
    queryClient.setQueryData<Programacion[]>(['programaciones'], (old) =>
      old ? old.map((p) => (p.id === prog.id ? { ...p, ...payload } : p)) : old
    )
    toast('Cambio guardado localmente.', { icon: '📶' })
    return
  }

  if (!rtdbProgramacion) throw new Error('Firebase RTDB (programacion) no configurado')
  await update(ref(rtdbProgramacion, rtdbPath), payload)
  await dexieInvalidate('programaciones')
  queryClient.invalidateQueries({ queryKey: ['programaciones'] })
}

export async function deleteProgramacion(prog: Programacion): Promise<void> {
  const rtdbPath = `/programaciones/${prog.subregion}/${prog.vehiculoId}/${prog.id}`

  if (!navigator.onLine) {
    await db.programaciones.delete(prog.id)
    await addToQueue({ collectionKey: 'programaciones', entityId: prog.id, rtdbPath, payload: {}, operation: 'delete' } as any)
    queryClient.setQueryData<Programacion[]>(['programaciones'], (old) => old?.filter((p) => p.id !== prog.id))
    toast('Eliminación guardada localmente.', { icon: '📶' })
    return
  }

  if (!rtdbProgramacion) throw new Error('Firebase RTDB (programacion) no configurado')
  await remove(ref(rtdbProgramacion, rtdbPath))
  await dexieInvalidate('programaciones')
  queryClient.invalidateQueries({ queryKey: ['programaciones'] })
}

export async function completarProgramacion(prog: Programacion, observaciones?: string): Promise<void> {
  return updateProgramacion(prog, { estado: 'COMPLETADA', observaciones: observaciones || prog.observaciones, fechaCompletado: Date.now() } as any)
}
