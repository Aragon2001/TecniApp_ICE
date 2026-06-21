import React, { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { Truck, AlertTriangle, CheckCircle2, Clock, Filter, Search } from 'lucide-react'
import { useVehiculos, VehiculoEstado } from '../hooks/useVehiculos'
import { vehiculoEstadoLabel, formatKm } from '../utils/formatUtils'
import { Spinner } from '../components/ui/Spinner'

const ESTADO_CONFIG: Record<VehiculoEstado, { bg: string; text: string; border: string; icon: React.ReactNode }> = {
  OPTIMO:   { bg: 'bg-emerald-50',  text: 'text-emerald-700', border: 'border-emerald-200', icon: <CheckCircle2 size={14} className="text-emerald-500" /> },
  ATENCION: { bg: 'bg-amber-50',    text: 'text-amber-700',   border: 'border-amber-200',   icon: <Clock size={14} className="text-amber-500" /> },
  VENCIDO:  { bg: 'bg-red-50',      text: 'text-red-700',     border: 'border-red-200',     icon: <AlertTriangle size={14} className="text-red-500" /> },
}

export default function Vehiculos() {
  const navigate = useNavigate()
  const { vehiculos, loading, error } = useVehiculos()
  const [search, setSearch] = useState('')
  const [estadoFilter, setEstadoFilter] = useState<VehiculoEstado | ''>('')
  const [agenciaFilter, setAgenciaFilter] = useState('')

  const agencias = useMemo(() => {
    const s = new Set(vehiculos.map((v: any) => v.agencia).filter(Boolean))
    return Array.from(s).sort() as string[]
  }, [vehiculos])

  const filtered = useMemo(() => {
    let list = [...vehiculos]
    if (estadoFilter) list = list.filter((v: any) => v.estado === estadoFilter)
    if (agenciaFilter) list = list.filter((v: any) => v.agencia === agenciaFilter)
    if (search.trim()) {
      const q = search.toLowerCase()
      list = list.filter((v: any) =>
        String(v.placa ?? '').toLowerCase().includes(q) ||
        String(v.tipo ?? '').toLowerCase().includes(q) ||
        String(v.agencia ?? '').toLowerCase().includes(q)
      )
    }
    return list
  }, [vehiculos, estadoFilter, agenciaFilter, search])

  const counts = useMemo(() => {
    return {
      OPTIMO: vehiculos.filter((v: any) => v.estado === 'OPTIMO').length,
      ATENCION: vehiculos.filter((v: any) => v.estado === 'ATENCION').length,
      VENCIDO: vehiculos.filter((v: any) => v.estado === 'VENCIDO').length,
    }
  }, [vehiculos])

  return (
    <div className="space-y-5">
      {/* Header */}
      <div>
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-gray-900">Flota de Vehículos</h1>
          {!loading && (
            <span className="bg-[#003087] text-white text-xs font-bold px-2.5 py-1 rounded-full">
              {vehiculos.length}
            </span>
          )}
        </div>
        <p className="text-sm text-gray-500 mt-0.5">Control de mantenimiento y estado de la flota</p>
      </div>

      {/* Estado pills */}
      <div className="flex flex-wrap gap-2">
        {(['OPTIMO', 'ATENCION', 'VENCIDO'] as VehiculoEstado[]).map((e) => {
          const c = ESTADO_CONFIG[e]
          const active = estadoFilter === e
          return (
            <button
              key={e}
              onClick={() => setEstadoFilter(active ? '' : e)}
              className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold border transition-all ${
                active ? `${c.bg} ${c.text} ${c.border}` : 'bg-white text-gray-500 border-gray-200 hover:border-gray-300'
              }`}
            >
              {c.icon}
              {vehiculoEstadoLabel(e)} ({counts[e]})
            </button>
          )
        })}
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl shadow-card p-4 flex flex-wrap gap-3">
        <div className="flex-1 min-w-[200px] relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="Buscar por placa, tipo, agencia..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-4 py-2.5 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087] focus:border-transparent"
          />
        </div>
        <div className="flex items-center gap-2">
          <Filter size={14} className="text-gray-400" />
          <select
            value={agenciaFilter}
            onChange={(e) => setAgenciaFilter(e.target.value)}
            className="text-sm border border-gray-200 rounded-lg px-3 py-2.5 focus:outline-none focus:ring-2 focus:ring-[#003087] bg-white text-gray-700"
          >
            <option value="">Todas las agencias</option>
            {agencias.map((ag) => <option key={ag} value={ag}>{ag}</option>)}
          </select>
        </div>
      </div>

      {/* Results */}
      {loading ? (
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      ) : error ? (
        <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-center text-red-600 text-sm">{error}</div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-xl shadow-card p-12 text-center">
          <Truck size={40} className="text-gray-200 mx-auto mb-3" />
          <p className="text-gray-500">Sin vehículos</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {filtered.map((v: any) => {
            const estado: VehiculoEstado = v.estado ?? 'OPTIMO'
            const c = ESTADO_CONFIG[estado]
            const kmRestante = (v.mantenimientoProximo ?? 0) - (v.kmActual ?? 0)
            const pct = v.mantenimientoProximo
              ? Math.min(100, Math.max(0, ((v.kmActual ?? 0) / v.mantenimientoProximo) * 100))
              : 0

            return (
              <div
                key={v.vehiculoId ?? v.id}
                onClick={() => navigate(`/vehiculos/${v.vehiculoId ?? v.id}`)}
                className="bg-white rounded-xl shadow-card p-5 cursor-pointer hover:shadow-card-hover transition-all group"
              >
                {/* Header */}
                <div className="flex items-start justify-between mb-4">
                  <div>
                    <p className="text-xl font-bold text-gray-900 font-mono">{v.placa}</p>
                    <p className="text-xs text-gray-500 mt-0.5">{v.tipo ?? 'Vehículo'}</p>
                  </div>
                  <span className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${c.bg} ${c.text} ${c.border}`}>
                    {c.icon}
                    {vehiculoEstadoLabel(estado)}
                  </span>
                </div>

                {/* Info */}
                <div className="space-y-1.5 text-sm mb-4">
                  <div className="flex justify-between text-xs">
                    <span className="text-gray-500">Agencia</span>
                    <span className="font-medium text-gray-700 truncate max-w-[140px]">{v.agencia ?? '—'}</span>
                  </div>
                  <div className="flex justify-between text-xs">
                    <span className="text-gray-500">KM Actual</span>
                    <span className="font-medium text-gray-700">{formatKm(v.kmActual)}</span>
                  </div>
                  <div className="flex justify-between text-xs">
                    <span className="text-gray-500">Próx. Mant.</span>
                    <span className={`font-medium ${estado === 'VENCIDO' ? 'text-red-600' : estado === 'ATENCION' ? 'text-amber-600' : 'text-gray-700'}`}>
                      {v.mantenimientoProximo ? formatKm(v.mantenimientoProximo) : '—'}
                    </span>
                  </div>
                  {kmRestante > 0 && (
                    <div className="flex justify-between text-xs">
                      <span className="text-gray-500">Restante</span>
                      <span className="font-medium text-emerald-600">{formatKm(kmRestante)}</span>
                    </div>
                  )}
                  {kmRestante < 0 && (
                    <div className="flex justify-between text-xs">
                      <span className="text-gray-500">Vencido</span>
                      <span className="font-medium text-red-600">{formatKm(Math.abs(kmRestante))} pasado</span>
                    </div>
                  )}
                </div>

                {/* KM progress bar */}
                {v.mantenimientoProximo > 0 && (
                  <div className="mb-3">
                    <div className="w-full bg-gray-100 rounded-full h-1.5">
                      <div
                        className={`h-1.5 rounded-full transition-all ${
                          estado === 'VENCIDO' ? 'bg-red-500' : estado === 'ATENCION' ? 'bg-amber-400' : 'bg-emerald-500'
                        }`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                    <div className="flex justify-between text-[10px] text-gray-400 mt-1">
                      <span>0 km</span>
                      <span>{formatKm(v.mantenimientoProximo)}</span>
                    </div>
                  </div>
                )}

                <div className="flex items-center justify-between pt-3 border-t border-gray-100">
                  <span className="text-xs text-gray-400">{v.subregion ?? '—'}</span>
                  <span className="text-xs text-[#0066CC] font-medium group-hover:underline">Ver detalle →</span>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
