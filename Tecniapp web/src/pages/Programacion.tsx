import { useState, useMemo } from 'react'
import { Calendar, Search, Filter, MapPin } from 'lucide-react'
import { useProgramacion } from '../hooks/useProgramacion'
import { formatDate } from '../utils/dateUtils'
import { Spinner } from '../components/ui/Spinner'

export default function Programacion() {
  const { programaciones, loading, error } = useProgramacion()
  const [search, setSearch] = useState('')
  const [estadoFilter, setEstadoFilter] = useState('')
  const [subregionFilter, setSubregionFilter] = useState('')

  const subregiones = useMemo(() => {
    const s = new Set(programaciones.map((p: any) => p.subregion).filter(Boolean))
    return Array.from(s).sort() as string[]
  }, [programaciones])

  const estados = useMemo(() => {
    const s = new Set(programaciones.map((p: any) => p.estado).filter(Boolean))
    return Array.from(s).sort() as string[]
  }, [programaciones])

  const filtered = useMemo(() => {
    let list = [...programaciones]
    if (estadoFilter) list = list.filter((p: any) => p.estado === estadoFilter)
    if (subregionFilter) list = list.filter((p: any) => p.subregion === subregionFilter)
    if (search.trim()) {
      const q = search.toLowerCase()
      list = list.filter((p: any) =>
        String(p.actividad ?? '').toLowerCase().includes(q) ||
        String(p.localizacion ?? '').toLowerCase().includes(q) ||
        String(p.circuito ?? '').toLowerCase().includes(q) ||
        String(p.descripcion ?? '').toLowerCase().includes(q)
      )
    }
    return list
  }, [programaciones, estadoFilter, subregionFilter, search])

  return (
    <div className="space-y-5">
      <div>
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-gray-900">Programación de Tareas</h1>
          {!loading && (
            <span className="bg-[#003087] text-white text-xs font-bold px-2.5 py-1 rounded-full">
              {programaciones.length}
            </span>
          )}
        </div>
        <p className="text-sm text-gray-500 mt-0.5">Planificación y asignación de actividades de campo</p>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl shadow-card p-4 flex flex-wrap gap-3">
        <div className="flex-1 min-w-[200px] relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="Buscar por actividad, localización, circuito..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-4 py-2.5 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087] focus:border-transparent"
          />
        </div>
        <div className="flex items-center gap-2">
          <Filter size={14} className="text-gray-400" />
          <select
            value={estadoFilter}
            onChange={(e) => setEstadoFilter(e.target.value)}
            className="text-sm border border-gray-200 rounded-lg px-3 py-2.5 focus:outline-none focus:ring-2 focus:ring-[#003087] bg-white text-gray-700"
          >
            <option value="">Todos los estados</option>
            {estados.map((e) => <option key={e} value={e}>{e}</option>)}
          </select>
          <select
            value={subregionFilter}
            onChange={(e) => setSubregionFilter(e.target.value)}
            className="text-sm border border-gray-200 rounded-lg px-3 py-2.5 focus:outline-none focus:ring-2 focus:ring-[#003087] bg-white text-gray-700"
          >
            <option value="">Todas las subregiones</option>
            {subregiones.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      ) : error ? (
        <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-center text-red-600 text-sm">{error}</div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-xl shadow-card p-12 text-center">
          <Calendar size={40} className="text-gray-200 mx-auto mb-3" />
          <p className="text-gray-500">Sin programaciones registradas</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-card overflow-hidden">
          <div className="px-5 py-3 border-b border-gray-100">
            <span className="text-sm text-gray-500">
              <strong className="text-gray-800">{filtered.length}</strong> programaciones
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full">
              <thead className="bg-gray-50">
                <tr>
                  {['ID', 'Actividad', 'Localización', 'Circuito', 'Vehículo', 'Subregión', 'Estado', 'Fecha Asignación', 'Fecha Ejecución'].map((h) => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {filtered.map((prog: any) => (
                  <tr key={prog.programacionId ?? prog.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-xs font-mono text-gray-500">
                      {String(prog.programacionId ?? prog.id ?? '').slice(0, 10)}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-800 font-medium max-w-[150px] truncate">
                      {prog.actividad ?? '—'}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600 max-w-[140px] truncate">
                      <div className="flex items-center gap-1">
                        {prog.localizacion && <MapPin size={11} className="text-gray-400 flex-shrink-0" />}
                        {prog.localizacion ?? '—'}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">{prog.circuito ?? '—'}</td>
                    <td className="px-4 py-3 text-sm text-gray-600 font-mono">{prog.vehiculoId ?? prog.placa ?? '—'}</td>
                    <td className="px-4 py-3 text-xs text-gray-500">{prog.subregion ?? '—'}</td>
                    <td className="px-4 py-3">
                      {prog.estado && (
                        <span className="inline-block bg-blue-100 text-blue-700 text-xs font-semibold px-2.5 py-0.5 rounded-full">
                          {prog.estado}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">
                      {formatDate(prog.fechaAsignacion)}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">
                      {formatDate(prog.fechaEjecucion)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
