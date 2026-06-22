import { useQuery } from '@tanstack/react-query'
import { ref, get, update, set, remove, push } from 'firebase/database'
import { rtdbGeneral } from '../firebase/config'
import { queryClient } from '../lib/queryClient'
import { db, dexieRead, dexieWrite, dexieInvalidate } from '../lib/db'
import { addToQueue } from '../lib/syncQueue'
import toast from 'react-hot-toast'

export type VehiculoEstado = 'OPTIMO' | 'ATENCION' | 'VENCIDO'

export interface Vehiculo {
  id: string
  placa?: string
  marca?: string
  modelo?: string
  anio?: number
  tipo?: string
  agencia?: string
  kmActual?: number
  mantenimientoProximo?: number
  tecnicoAsignado?: string
  tecnicoUid?: string
  activo?: boolean
  estado: VehiculoEstado
  [key: string]: any
}

function computeEstado(kmActual?: number, mantenimientoProximo?: number): VehiculoEstado {
  if (kmActual == null || mantenimientoProximo == null) return 'OPTIMO'
  if (kmActual >= mantenimientoProximo) return 'VENCIDO'
  if (mantenimientoProximo - kmActual <= 500) return 'ATENCION'
  return 'OPTIMO'
}

async function fetchVehiculos(): Promise<Vehiculo[]> {
  const cached = await dexieRead<Vehiculo>(db.vehiculos, 'vehiculos')
  if (cached !== null) return cached

  if (!rtdbGeneral) return []
  const snapshot = await get(ref(rtdbGeneral, '/vehiculos'))
  const data = snapshot.val()
  if (!data) { await dexieWrite(db.vehiculos, 'vehiculos', []); return [] }

  const list: Vehiculo[] = Object.entries(data).map(([key, value]: [string, any]) => ({
    id: key,
    ...value,
    estado: computeEstado(value.kmActual, value.mantenimientoProximo),
  }))
  list.sort((a, b) => (a.placa || '').localeCompare(b.placa || '', 'es'))
  await dexieWrite(db.vehiculos, 'vehiculos', list)
  return list
}

export function useVehiculos() {
  const { data = [], isLoading, error } = useQuery({
    queryKey: ['vehiculos'],
    queryFn: fetchVehiculos,
    staleTime: 10 * 60_000,
  })
  return { vehiculos: data, loading: isLoading, error: error ? (error as Error).message : null }
}

export function useVehiculo(vehiculoId: string | null) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['vehiculos', vehiculoId],
    queryFn: async () => {
      if (!vehiculoId) return null
      const cached = await db.vehiculos.get(vehiculoId)
      if (cached) return cached as Vehiculo
      if (!rtdbGeneral) return null
      const snap = await get(ref(rtdbGeneral, `/vehiculos/${vehiculoId}`))
      const d = snap.val()
      return d ? ({ id: vehiculoId, ...d, estado: computeEstado(d.kmActual, d.mantenimientoProximo) } as Vehiculo) : null
    },
    enabled: !!vehiculoId,
    staleTime: 10 * 60_000,
  })
  return { vehiculo: data ?? null, loading: isLoading, error: error ? (error as Error).message : null }
}

// ── Mutations ────────────────────────────────────────────────────────────────

export async function updateVehiculo(vehiculoId: string, updates: Partial<Vehiculo>): Promise<void> {
  const payload = { ...updates, updatedAt: Date.now() }
  delete payload.id
  delete payload.estado

  if (!navigator.onLine) {
    await db.vehiculos.update(vehiculoId, payload)
    await addToQueue({ collectionKey: 'vehiculos', entityId: vehiculoId, rtdbPath: `/vehiculos/${vehiculoId}`, payload })
    queryClient.setQueryData<Vehiculo[]>(['vehiculos'], (old) =>
      old ? old.map((v) => (v.id === vehiculoId ? { ...v, ...payload, estado: computeEstado((payload.kmActual ?? v.kmActual), (payload.mantenimientoProximo ?? v.mantenimientoProximo)) } : v)) : old
    )
    toast('Cambio guardado localmente.', { icon: '📶' })
    return
  }
  if (!rtdbGeneral) throw new Error('Firebase RTDB (general) no configurado')
  await update(ref(rtdbGeneral, `/vehiculos/${vehiculoId}`), payload)
  await dexieInvalidate('vehiculos')
  queryClient.invalidateQueries({ queryKey: ['vehiculos'] })
}

export async function createVehiculo(data: Omit<Vehiculo, 'id' | 'estado'>): Promise<string> {
  if (!rtdbGeneral) throw new Error('Firebase RTDB (general) no configurado')
  const newRef = push(ref(rtdbGeneral, '/vehiculos'))
  const payload = { ...data, creadoAt: Date.now() }
  await set(newRef, payload)
  await dexieInvalidate('vehiculos')
  queryClient.invalidateQueries({ queryKey: ['vehiculos'] })
  return newRef.key!
}

export async function deleteVehiculo(vehiculoId: string): Promise<void> {
  if (!navigator.onLine) {
    await db.vehiculos.delete(vehiculoId)
    await addToQueue({ collectionKey: 'vehiculos', entityId: vehiculoId, rtdbPath: `/vehiculos/${vehiculoId}`, payload: {}, operation: 'delete' } as any)
    queryClient.setQueryData<Vehiculo[]>(['vehiculos'], (old) => old?.filter((v) => v.id !== vehiculoId))
    toast('Eliminación guardada localmente.', { icon: '📶' })
    return
  }
  if (!rtdbGeneral) throw new Error('Firebase RTDB (general) no configurado')
  await remove(ref(rtdbGeneral, `/vehiculos/${vehiculoId}`))
  await dexieInvalidate('vehiculos')
  queryClient.invalidateQueries({ queryKey: ['vehiculos'] })
}
