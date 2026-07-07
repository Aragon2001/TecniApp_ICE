import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ref, get, set, remove, push } from 'firebase/database'
import { rtdbLuminarias } from '../firebase/config'
import { useAuth } from '../context/AuthContext'
import { queryClient } from '../lib/queryClient'
import { db, dexieRead, dexieWrite, dexieInvalidate } from '../lib/db'
import { addToQueue } from '../lib/syncQueue'
import toast from 'react-hot-toast'

export interface Luminaria {
  id: string
  _agenciaKey: string
  _estadoSegment: 'pendientes' | 'reparadas'
  vehiculoId?: string
  localizacion?: string
  cliente?: string
  contacto?: string
  observaciones?: string
  materialesJson?: string
  estado: 'PENDIENTE' | 'REPARADA'
  ejecutorNombre?: string
  ejecutorCedula?: string
  fechaRegistro?: number
  fechaCarga?: number
  fechaReparacion?: number
  [key: string]: any
}

export interface LuminariaPermisos {
  puedeMarcarReparada: boolean
  puedeRegistrar: boolean
  puedeFiltrarVehiculo: boolean
  esGestor: boolean
}

interface UseLuminariasResult {
  pendientes: Luminaria[]
  reparadas: Luminaria[]
  todosVehiculoIds: string[]
  loading: boolean
  error: string | null
  agenciaKey: string
  permisos: LuminariaPermisos
}

async function fetchLuminarias(agenciaKey: string): Promise<Luminaria[]> {
  const cached = await dexieRead<Luminaria>(db.luminarias, 'luminarias')
  if (cached !== null) return cached

  if (!rtdbLuminarias) return []
  const snapshot = await get(ref(rtdbLuminarias, `/luminarias/${agenciaKey}`))
  const data = snapshot.val()
  if (!data) {
    await dexieWrite(db.luminarias, 'luminarias', [])
    return []
  }

  const list: Luminaria[] = []
  ;(['pendientes', 'reparadas'] as const).forEach((seg) => {
    const segData = data[seg]
    if (!segData || typeof segData !== 'object') return
    Object.entries(segData).forEach(([id, itemData]: [string, any]) => {
      if (typeof itemData !== 'object' || itemData === null) return
      list.push({
        id,
        _agenciaKey: agenciaKey,
        _estadoSegment: seg,
        estado: seg === 'pendientes' ? 'PENDIENTE' : 'REPARADA',
        ...itemData,
      })
    })
  })

  list.sort((a, b) => (b.fechaRegistro || 0) - (a.fechaRegistro || 0))
  await dexieWrite(db.luminarias, 'luminarias', list)
  return list
}

export function useLuminarias(vehiculoFiltro?: string): UseLuminariasResult {
  const { user } = useAuth()
  const agenciaKey = (user?.agencia?.trim() || user?.agenciaId?.trim()) ?? ''
  const esGestor = user?.rol === 'supervisor' || user?.rol === 'admin'
  const placaTecnico = !esGestor ? user?.placaVehiculo?.trim() : undefined

  const { data = [], isLoading, error } = useQuery({
    queryKey: ['luminarias', agenciaKey],
    queryFn: () => fetchLuminarias(agenciaKey),
    enabled: !!agenciaKey,
    staleTime: 5 * 60_000,
  })

  const todosVehiculoIds = useMemo(() => {
    const ids = new Set(data.map((l) => l.vehiculoId).filter(Boolean) as string[])
    return Array.from(ids).sort()
  }, [data])

  const filtered = useMemo(() => {
    let list = data
    if (!esGestor && placaTecnico) {
      list = list.filter((l) => l.vehiculoId === placaTecnico)
    } else if (esGestor && vehiculoFiltro) {
      list = list.filter((l) => l.vehiculoId === vehiculoFiltro)
    }
    return list
  }, [data, esGestor, placaTecnico, vehiculoFiltro])

  const pendientes = useMemo(() => filtered.filter((l) => l.estado === 'PENDIENTE'), [filtered])
  const reparadas = useMemo(() => filtered.filter((l) => l.estado === 'REPARADA'), [filtered])

  const permisos: LuminariaPermisos = {
    puedeMarcarReparada: !esGestor,
    puedeRegistrar: esGestor,
    puedeFiltrarVehiculo: esGestor,
    esGestor,
  }

  return {
    pendientes,
    reparadas,
    todosVehiculoIds,
    loading: isLoading,
    error: error ? (error as Error).message : null,
    agenciaKey,
    permisos,
  }
}

export async function marcarLuminariaReparada(
  lum: Luminaria,
  ejecutorNombre: string,
  ejecutorCedula?: string
): Promise<void> {
  if (lum._estadoSegment !== 'pendientes') return

  const { id, _agenciaKey, _estadoSegment, estado, ...fields } = lum
  const payload: Partial<Luminaria> & Record<string, any> = {
    ...fields,
    estado: 'REPARADA' as const,
    ejecutorNombre: ejecutorNombre.trim() || fields.ejecutorNombre || '',
    ejecutorCedula: ejecutorCedula || fields.ejecutorCedula || '',
    fechaReparacion: Date.now(),
  }

  if (!navigator.onLine) {
    await db.luminarias.update(id, { ...payload, _estadoSegment: 'reparadas' })
    await addToQueue({
      collectionKey: 'luminarias',
      entityId: id,
      rtdbPath: `luminarias/${_agenciaKey}/reparadas/${id}`,
      payload,
    })
    queryClient.setQueryData<Luminaria[]>(['luminarias', _agenciaKey], (old) =>
      old
        ? old.map((l) =>
            l.id === id ? ({ ...l, ...payload, _estadoSegment: 'reparadas' as const } as Luminaria) : l
          )
        : old
    )
    toast('Reparación guardada localmente. Se sincronizará al recuperar conexión.', { icon: '📶' })
    return
  }

  if (!rtdbLuminarias) throw new Error('Firebase RTDB no configurado')
  await set(ref(rtdbLuminarias, `luminarias/${_agenciaKey}/reparadas/${id}`), payload)
  await remove(ref(rtdbLuminarias, `luminarias/${_agenciaKey}/pendientes/${id}`))
  await dexieInvalidate('luminarias')
  queryClient.invalidateQueries({ queryKey: ['luminarias'] })
}

export async function registrarLuminariaPendiente(
  agenciaKey: string,
  data: {
    localizacion: string
    vehiculoId: string
    cliente?: string
    contacto?: string
    observaciones?: string
    ejecutorNombre?: string
    ejecutorCedula?: string
  }
): Promise<void> {
  if (!rtdbLuminarias) throw new Error('Firebase RTDB no configurado')

  const newRef = push(ref(rtdbLuminarias, `luminarias/${agenciaKey}/pendientes`))
  await set(newRef, {
    ...data,
    localizacion: data.localizacion.trim(),
    estado: 'PENDIENTE',
    fechaRegistro: Date.now(),
    fechaCarga: Date.now(),
  })
  await dexieInvalidate('luminarias')
  queryClient.invalidateQueries({ queryKey: ['luminarias'] })
}
