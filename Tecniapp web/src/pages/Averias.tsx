import { useState, useMemo, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, Search, Grid, List, X, Eye, Star, Building2 } from 'lucide-react'
import { format, formatDistanceToNow } from 'date-fns'
import { es } from 'date-fns/locale'
import { useAverias } from '../hooks/useAverias'
import { useAuth } from '../context/AuthContext'
import { AveriaEstadoBadge } from '../components/ui/AveriaEstadoBadge'
import type { AveriaEstado } from '../types'

const ESTADOS: { value: AveriaEstado | ''; label: string }[] = [
  { value: '', label: 'Todos los estados' },
  { value: 'PENDIENTE', label: 'Pendiente' },
  { value: 'ASIGNADA', label: 'Asignada' },
  { value: 'EN_ATENCION', label: 'En Atención' },
  { value: 'RESUELTA', label: 'Resuelta' },
  { value: 'ANULADA', label: 'Anulada' },
]

const ESTADO_BORDER: Record<AveriaEstado, string> = {
  PENDIENTE: 'border-l-amber-400',
  ASIGNADA: 'border-l-blue-400',
  EN_ATENCION: 'border-l-orange-400',
  RESUELTA: 'border-l-green-400',
  ANULADA: 'border-l-gray-300',
}

const ESTADO_BG: Record<AveriaEstado, string> = {
  PENDIENTE: 'bg-amber-50 text-amber-800',
  ASIGNADA: 'bg-blue-50 text-blue-800',
  EN_ATENCION: 'bg-orange-50 text-orange-800',
  RESUELTA: 'bg-green-50 text-green-800',
  ANULADA: 'bg-gray-50 text-gray-600',
}

export default function Averias() {
  const navigate = useNavigate()
  const { user, isAdmin, isSupervisor } = useAuth()
  const { averias, loading } = useAverias({})
  const [search, setSearch] = useState('')
  const [estadoFilter, setEstadoFilter] = useState<AveriaEstado | ''>('')
  const [agenciaFilter, setAgenciaFilter] = useState('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [viewMode, setViewMode] = useState<'table' | 'card'>('table')

  // ── Agencias favoritas (persistidas por usuario) ──────────────────────────
  const prefsKey = user?.uid ? `avprefs_${user.uid}` : null

  const [preferredAgencias, setPreferredAgencias] = useState<string[]>([])

  useEffect(() => {
    if (!prefsKey) return
    try {
      const stored = localStorage.getItem(prefsKey)
      if (stored) {
        setPreferredAgencias(JSON.parse(stored))
      } else if (user?.agencia) {
        // Primera vez: inicializar con la agencia propia del usuario
        const init = [user.agencia]
        setPreferredAgencias(init)
        localStorage.setItem(prefsKey, JSON.stringify(init))
      }
    } catch {
      setPreferredAgencias([])
    }
  }, [prefsKey, user?.agencia])

  const togglePreferred = (ag: string) => {
    if (!prefsKey) return
    const next = preferredAgencias.includes(ag)
      ? preferredAgencias.filter(a => a !== ag)
      : [...preferredAgencias, ag]
    setPreferredAgencias(next)
    localStorage.setItem(prefsKey, JSON.stringify(next))
  }

  // ── Agencias filtradas a la región del usuario (técnicos/supervisores) ────
  const agencias = useMemo(() => {
    const source =
      isAdmin || isSupervisor
        ? averias
        : averias.filter(a => !user?.region || a.region === user.region)
    const set = new Set(
      source.map((a: any) => a.nombreAgencia || a.agencia).filter(Boolean)
    )
    return Array.from(set as Set<string>).sort()
  }, [averias, user, isAdmin, isSupervisor])

  // ── Filtrado local ────────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    let list = averias
    if (search) {
      const q = search.toLowerCase()
      list = list.filter((a: any) =>
        (a.caseId || a.id)?.toLowerCase().includes(q) ||
        a.cliente?.toLowerCase().includes(q) ||
        a.localizacion?.toLowerCase().includes(q) ||
        a.causa?.toLowerCase().includes(q) ||
        a.tecnicoAsignadoNombre?.toLowerCase().includes(q) ||
        a.nombreAgencia?.toLowerCase().includes(q)
      )
    }
    if (estadoFilter) list = list.filter((a: any) => a.estado === estadoFilter)
    if (agenciaFilter)
      list = list.filter(
        (a: any) => (a.nombreAgencia || a.agencia) === agenciaFilter
      )
    if (dateFrom) {
      const from = new Date(dateFrom).getTime()
      list = list.filter((a: any) => (a.fechaInicioMillis ?? 0) >= from)
    }
    if (dateTo) {
      const to = new Date(dateTo).getTime() + 86400000
      list = list.filter((a: any) => (a.fechaInicioMillis ?? 0) <= to)
    }
    return list
  }, [averias, search, estadoFilter, agenciaFilter, dateFrom, dateTo])

  // Conteo por estado (sobre la lista filtrada)
  const counts = useMemo(() => {
    const c: Partial<Record<AveriaEstado, number>> = {}
    filtered.forEach((a: any) => { c[a.estado as AveriaEstado] = (c[a.estado as AveriaEstado] || 0) + 1 })
    return c
  }, [filtered])

  const hasFilters = search || estadoFilter || agenciaFilter || dateFrom || dateTo
  const clearFilters = () => {
    setSearch('')
    setEstadoFilter('')
    setAgenciaFilter('')
    setDateFrom('')
    setDateTo('')
  }

  const isCurrentAgenciaPreferred = preferredAgencias.includes(agenciaFilter)

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">Gestión de Averías</h2>
          <p className="text-slate-500 text-sm mt-0.5">
            {loading ? 'Cargando...' : `${averias.length} averías registradas`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setViewMode('table')}
            className={`p-2 rounded-lg transition-colors ${viewMode === 'table' ? 'bg-[#003087] text-white' : 'bg-white text-slate-500 hover:bg-slate-100 border border-slate-200'}`}
          >
            <List size={18} />
          </button>
          <button
            onClick={() => setViewMode('card')}
            className={`p-2 rounded-lg transition-colors ${viewMode === 'card' ? 'bg-[#003087] text-white' : 'bg-white text-slate-500 hover:bg-slate-100 border border-slate-200'}`}
          >
            <Grid size={18} />
          </button>
        </div>
      </div>

      {/* Filter bar */}
      <div className="bg-white rounded-xl border border-slate-100 p-4 space-y-3">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-5 gap-3">
          <div className="relative xl:col-span-2">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Buscar por caso, cliente, localización, causa..."
              className="w-full pl-9 pr-4 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
            />
          </div>
          <select
            value={estadoFilter}
            onChange={e => setEstadoFilter(e.target.value as AveriaEstado | '')}
            className="px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] bg-white"
          >
            {ESTADOS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>

          {/* Agencias select + botón de favorito */}
          <div className="flex gap-1.5 items-center">
            <select
              value={agenciaFilter}
              onChange={e => setAgenciaFilter(e.target.value)}
              className="flex-1 min-w-0 px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] bg-white"
            >
              <option value="">Todas las agencias</option>
              {agencias.map(a => <option key={a} value={a}>{a}</option>)}
            </select>
            {agenciaFilter && (
              <button
                onClick={() => togglePreferred(agenciaFilter)}
                title={isCurrentAgenciaPreferred ? 'Quitar de favoritos' : 'Agregar a favoritos'}
                className={`flex-shrink-0 p-2.5 rounded-lg border transition-colors ${
                  isCurrentAgenciaPreferred
                    ? 'bg-amber-50 border-amber-200 text-amber-500'
                    : 'bg-white border-slate-200 text-slate-400 hover:text-amber-400 hover:border-amber-200'
                }`}
              >
                <Star size={14} fill={isCurrentAgenciaPreferred ? 'currentColor' : 'none'} />
              </button>
            )}
          </div>

          <div className="flex gap-2">
            <input
              type="date"
              value={dateFrom}
              onChange={e => setDateFrom(e.target.value)}
              className="flex-1 px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
            />
            <input
              type="date"
              value={dateTo}
              onChange={e => setDateTo(e.target.value)}
              className="flex-1 px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
            />
          </div>
        </div>
        {hasFilters && (
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">{filtered.length} resultado(s)</span>
            <button
              onClick={clearFilters}
              className="flex items-center gap-1 text-xs text-red-600 hover:text-red-700 font-medium"
            >
              <X size={12} /> Limpiar filtros
            </button>
          </div>
        )}
      </div>

      {/* Estado pills */}
      <div className="flex flex-wrap gap-2">
        {(['PENDIENTE', 'ASIGNADA', 'EN_ATENCION', 'RESUELTA', 'ANULADA'] as AveriaEstado[]).map(estado => {
          const labels: Record<AveriaEstado, string> = {
            PENDIENTE: 'Pendiente', ASIGNADA: 'Asignada', EN_ATENCION: 'En Atención',
            RESUELTA: 'Resuelta', ANULADA: 'Anulada',
          }
          return (
            <button
              key={estado}
              onClick={() => setEstadoFilter(estadoFilter === estado ? '' : estado)}
              className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border transition-all ${
                estadoFilter === estado ? 'ring-2 ring-offset-1 ring-current' : ''
              } ${ESTADO_BG[estado]} border-transparent`}
            >
              <span>{labels[estado]}</span>
              <span className="bg-white/60 px-1.5 py-0.5 rounded-full">{counts[estado] || 0}</span>
            </button>
          )
        })}
      </div>

      {/* Agencias favoritas — acceso rápido */}
      {preferredAgencias.length > 0 && (
        <div className="flex flex-wrap gap-2 items-center">
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide flex items-center gap-1">
            <Building2 size={10} /> Agencias
          </span>
          {preferredAgencias.map(ag => (
            <button
              key={ag}
              onClick={() => setAgenciaFilter(agenciaFilter === ag ? '' : ag)}
              className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border transition-all ${
                agenciaFilter === ag
                  ? 'bg-[#003087] text-white border-[#003087]'
                  : 'bg-white text-slate-600 border-slate-200 hover:border-[#003087]/40 hover:bg-slate-50'
              }`}
            >
              {ag}
              <span
                role="button"
                onClick={e => { e.stopPropagation(); togglePreferred(ag) }}
                className="opacity-40 hover:opacity-100 transition-opacity ml-0.5"
                title="Quitar de favoritos"
              >
                <X size={10} />
              </span>
            </button>
          ))}
        </div>
      )}

      {/* Content */}
      {loading ? (
        <div className="bg-white rounded-xl border border-slate-100 p-5 space-y-3 animate-pulse">
          {[1, 2, 3, 4, 5, 6].map(i => <div key={i} className="h-12 bg-slate-100 rounded-lg" />)}
        </div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-100 p-16 text-center">
          <AlertTriangle size={40} className="text-slate-300 mx-auto mb-3" />
          <p className="text-slate-500 font-medium">No se encontraron averías</p>
          <p className="text-slate-400 text-sm mt-1">Ajuste los filtros para ver más resultados</p>
        </div>
      ) : viewMode === 'table' ? (
        <div className="bg-white rounded-xl border border-slate-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-100">
                  {['Caso ID', 'Estado', 'Agencia', 'Clientes', 'Localización', 'Técnico', 'Fecha', 'Tiempo', ''].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {filtered.map((averia: any) => (
                  <tr
                    key={averia.caseId || averia.id}
                    onClick={() => navigate(`/averias/${averia.caseId || averia.id}`)}
                    className="hover:bg-slate-50 cursor-pointer transition-colors"
                  >
                    <td className="px-4 py-3 font-mono text-xs font-bold text-[#003087] whitespace-nowrap">
                      {averia.caseId || averia.id}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <AveriaEstadoBadge estado={averia.estado} />
                    </td>
                    <td className="px-4 py-3 text-slate-600 text-xs max-w-[140px] truncate">
                      {averia.nombreAgencia || averia.agencia || '-'}
                    </td>
                    <td className="px-4 py-3 text-slate-600 text-center text-xs font-semibold">
                      {averia.clientesAfectados || 0}
                    </td>
                    <td className="px-4 py-3 text-slate-600 text-xs max-w-[150px] truncate">
                      {averia.localizacion || '-'}
                    </td>
                    <td className="px-4 py-3 text-slate-600 text-xs max-w-[130px] truncate">
                      {averia.tecnicoAsignadoNombre || '-'}
                    </td>
                    <td className="px-4 py-3 text-slate-400 text-xs whitespace-nowrap">
                      {averia.fechaInicioMillis
                        ? format(new Date(averia.fechaInicioMillis), 'dd/MM/yy HH:mm', { locale: es })
                        : '-'}
                    </td>
                    <td className="px-4 py-3 text-slate-400 text-xs whitespace-nowrap">
                      {averia.fechaInicioMillis
                        ? formatDistanceToNow(new Date(averia.fechaInicioMillis), { locale: es, addSuffix: true })
                        : '-'}
                    </td>
                    <td className="px-4 py-3">
                      <button
                        onClick={e => { e.stopPropagation(); navigate(`/averias/${averia.caseId || averia.id}`) }}
                        className="p-1.5 text-[#0066CC] hover:bg-blue-50 rounded-lg transition-colors"
                      >
                        <Eye size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
          {filtered.map((averia: any) => (
            <div
              key={averia.caseId || averia.id}
              onClick={() => navigate(`/averias/${averia.caseId || averia.id}`)}
              className={`bg-white rounded-xl border border-slate-100 border-l-4 ${ESTADO_BORDER[averia.estado as AveriaEstado]} p-4 cursor-pointer hover:shadow-md transition-shadow`}
            >
              <div className="flex items-start justify-between gap-2 mb-2">
                <span className="font-mono text-sm font-bold text-[#003087]">{averia.caseId || averia.id}</span>
                <AveriaEstadoBadge estado={averia.estado} />
              </div>
              <p className="text-sm font-medium text-slate-700 mb-1">{averia.nombreAgencia || averia.agencia || 'Sin agencia'}</p>
              <p className="text-xs text-slate-500 truncate mb-2">{averia.localizacion || 'Sin localización'}</p>
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>{averia.clientesAfectados || 0} clientes</span>
                <span>{averia.tecnicoAsignadoNombre || 'Sin asignar'}</span>
              </div>
              {averia.fechaInicioMillis > 0 && (
                <p className="text-[10px] text-slate-400 mt-2">
                  {formatDistanceToNow(new Date(averia.fechaInicioMillis), { locale: es, addSuffix: true })}
                </p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
