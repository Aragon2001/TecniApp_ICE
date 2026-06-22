import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ref, get, update, set, remove } from 'firebase/database'
import { rtdbInventario } from '../firebase/config'
import { queryClient } from '../lib/queryClient'
import { db, dexieRead, dexieWrite, dexieInvalidate } from '../lib/db'
import { addToQueue } from '../lib/syncQueue'
import toast from 'react-hot-toast'

export interface InventarioItem {
  id: string
  vehiculoId: string
  codigoMaterial?: string
  descripcionMaterial?: string
  cantidadDisponible?: number
  categoria?: string
  [key: string]: any
}

async function fetchInventario(): Promise<InventarioItem[]> {
  const cached = await dexieRead<InventarioItem>(db.inventario, 'inventario')
  if (cached !== null) return cached

  if (!rtdbInventario) return []
  const snapshot = await get(ref(rtdbInventario, '/inventario'))
  const data = snapshot.val()
  if (!data) { await dexieWrite(db.inventario, 'inventario', []); return [] }

  const list: InventarioItem[] = []
  Object.entries(data).forEach(([vehiculoId, vehiculoItems]: [string, any]) => {
    if (typeof vehiculoItems !== 'object' || vehiculoItems === null) return
    Object.entries(vehiculoItems).forEach(([codigoMaterial, itemData]: [string, any]) => {
      list.push({ id: `${vehiculoId}__${codigoMaterial}`, vehiculoId, codigoMaterial, ...itemData })
    })
  })
  list.sort((a, b) => (a.descripcionMaterial || a.codigoMaterial || '').localeCompare(b.descripcionMaterial || b.codigoMaterial || '', 'es'))
  await dexieWrite(db.inventario, 'inventario', list)
  return list
}

export function useInventario(vehiculoIdFiltro?: string) {
  const { data = [], isLoading, error } = useQuery({
    queryKey: ['inventario'],
    queryFn: fetchInventario,
    staleTime: 10 * 60_000,
  })

  const inventarioPorVehiculo = useMemo(() => {
    const map: Record<string, InventarioItem[]> = {}
    data.forEach((item) => {
      if (!map[item.vehiculoId]) map[item.vehiculoId] = []
      map[item.vehiculoId].push(item)
    })
    return map
  }, [data])

  const inventario = vehiculoIdFiltro ? (inventarioPorVehiculo[vehiculoIdFiltro] ?? []) : data

  return { inventario, inventarioPorVehiculo, loading: isLoading, error: error ? (error as Error).message : null }
}

// ── Mutations ────────────────────────────────────────────────────────────────

export async function updateCantidadInventario(
  vehiculoId: string,
  codigoMaterial: string,
  cantidadDisponible: number
): Promise<void> {
  const payload = { cantidadDisponible, updatedAt: Date.now() }
  const rtdbPath = `inventario/${vehiculoId}/${codigoMaterial}`
  const dexieId = `${vehiculoId}__${codigoMaterial}`

  if (!navigator.onLine) {
    await db.inventario.update(dexieId, payload)
    await addToQueue({ collectionKey: 'inventario', entityId: dexieId, rtdbPath, payload })
    queryClient.setQueryData<InventarioItem[]>(['inventario'], (old) =>
      old ? old.map((i) => (i.id === dexieId ? { ...i, ...payload } : i)) : old
    )
    toast('Cantidad actualizada localmente.', { icon: '📶' })
    return
  }
  if (!rtdbInventario) throw new Error('Firebase RTDB (inventario) no configurado')
  await update(ref(rtdbInventario, rtdbPath), payload)
  await dexieInvalidate('inventario')
  queryClient.invalidateQueries({ queryKey: ['inventario'] })
}

export async function agregarItemInventario(
  vehiculoId: string,
  codigoMaterial: string,
  data: Omit<InventarioItem, 'id' | 'vehiculoId'>
): Promise<void> {
  if (!rtdbInventario) throw new Error('Firebase RTDB (inventario) no configurado')
  const payload = { ...data, codigoMaterial, creadoAt: Date.now() }
  await set(ref(rtdbInventario, `inventario/${vehiculoId}/${codigoMaterial}`), payload)
  await dexieInvalidate('inventario')
  queryClient.invalidateQueries({ queryKey: ['inventario'] })
}

export async function eliminarItemInventario(vehiculoId: string, codigoMaterial: string): Promise<void> {
  const dexieId = `${vehiculoId}__${codigoMaterial}`
  if (!navigator.onLine) {
    await db.inventario.delete(dexieId)
    await addToQueue({ collectionKey: 'inventario', entityId: dexieId, rtdbPath: `inventario/${vehiculoId}/${codigoMaterial}`, payload: {}, operation: 'delete' } as any)
    queryClient.setQueryData<InventarioItem[]>(['inventario'], (old) => old?.filter((i) => i.id !== dexieId))
    toast('Eliminación guardada localmente.', { icon: '📶' })
    return
  }
  if (!rtdbInventario) throw new Error('Firebase RTDB (inventario) no configurado')
  await remove(ref(rtdbInventario, `inventario/${vehiculoId}/${codigoMaterial}`))
  await dexieInvalidate('inventario')
  queryClient.invalidateQueries({ queryKey: ['inventario'] })
}
