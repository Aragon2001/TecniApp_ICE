import { useMemo } from 'react'
import { useAverias } from './useAverias'
import { useVehiculos } from './useVehiculos'
import { useLuminarias } from './useLuminarias'
import type { DashboardStats } from '../types'

interface UseStatsResult {
  stats: DashboardStats
  loading: boolean
}

const DEFAULT_STATS: DashboardStats = {
  totalAverias: 0,
  averiasPendientes: 0,
  averiasAsignadas: 0,
  averiasEnAtencion: 0,
  averiasResueltas: 0,
  averiasAnuladas: 0,
  totalVehiculos: 0,
  vehiculosOptimos: 0,
  vehiculosAtencion: 0,
  vehiculosVencidos: 0,
  totalLuminarias: 0,
  luminariasResueltas: 0,
  luminariasPendientes: 0,
}

export function useStats(): UseStatsResult {
  const { averias, loading: loadingAverias } = useAverias({})
  const { vehiculos, loading: loadingVehiculos } = useVehiculos()
  const { pendientes: lumPendientes, reparadas: lumReparadas, loading: loadingLuminarias } = useLuminarias()

  const loading = loadingAverias || loadingVehiculos || loadingLuminarias

  const stats = useMemo<DashboardStats>(() => {
    return {
      totalAverias: averias.length,
      averiasPendientes: averias.filter((a) => a.estado === 'PENDIENTE').length,
      averiasAsignadas: averias.filter((a) => a.estado === 'ASIGNADA').length,
      averiasEnAtencion: averias.filter((a) => a.estado === 'EN_ATENCION').length,
      averiasResueltas: averias.filter((a) => a.estado === 'RESUELTA').length,
      averiasAnuladas: averias.filter((a) => a.estado === 'ANULADA').length,
      totalVehiculos: vehiculos.length,
      vehiculosOptimos: vehiculos.filter((v) => v.estado === 'OPTIMO').length,
      vehiculosAtencion: vehiculos.filter((v) => v.estado === 'ATENCION').length,
      vehiculosVencidos: vehiculos.filter((v) => v.estado === 'VENCIDO').length,
      totalLuminarias: lumPendientes.length + lumReparadas.length,
      luminariasResueltas: lumReparadas.length,
      luminariasPendientes: lumPendientes.length,
    }
  }, [averias, vehiculos, lumPendientes, lumReparadas])

  return { stats: loading ? DEFAULT_STATS : stats, loading }
}
