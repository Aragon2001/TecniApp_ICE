import React, { useState, useEffect, useMemo } from 'react'
import { ClipboardList, ChevronDown, ChevronRight, Calendar, Clock, Search, Download } from 'lucide-react'
import { collection, query, orderBy, getDocs, where, Timestamp } from 'firebase/firestore'
import { db } from '../firebase/config'
import { formatDate, minutesToHHMM } from '../utils/dateUtils'
import { Spinner } from '../components/ui/Spinner'
import { format, subDays, startOfMonth, endOfMonth } from 'date-fns'
import { es } from 'date-fns/locale'

interface WorkLog {
  id: string
  planillaId: string
  tecnicoId: string
  tecnicoNombre: string
  actividad: string
  ordenSap: string
  descripcion: string
  minutosEjecutados: number
  fecha: string
  agencia: string
  subregion: string
  estado: string
  createdAt: any
}

interface Planilla {
  id: string
  tecnicoId: string
  tecnicoNombre: string
  fecha: string
  agencia: string
  subregion: string
  vehiculoId: string
  totalMinutos: number
  totalActividades: number
  estado: string
  cerrada: boolean
  createdAt: any
  workLogs?: WorkLog[]
}

const ESTADO_STYLES: Record<string, string> = {
  ABIERTA: 'bg-blue-100 text-blue-700',
  CERRADA: 'bg-green-100 text-green-700',
  APROBADA: 'bg-emerald-100 text-emerald-700',
  RECHAZADA: 'bg-red-100 text-red-700',
}

export default function Planillas() {
  const [planillas, setPlanillas] = useState<Planilla[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [search, setSearch] = useState('')
  const [estadoFilter, setEstadoFilter] = useState('')
  const [dateFrom, setDateFrom] = useState(() => format(startOfMonth(new Date()), 'yyyy-MM-dd'))
  const [dateTo, setDateTo] = useState(() => format(endOfMonth(new Date()), 'yyyy-MM-dd'))

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError('')
      try {
        const q = query(
          collection(db, 'pm_planillas'),
          orderBy('fecha', 'desc')
        )
        const snap = await getDocs(q)
        const raw: Planilla[] = snap.docs.map(d => ({ id: d.id, ...d.data() } as Planilla))

        // Load work logs for each planilla
        const logsSnap = await getDocs(query(collection(db, 'pm_work_logs'), orderBy('fecha', 'desc')))
        const allLogs: WorkLog[] = logsSnap.docs.map(d => ({ id: d.id, ...d.data() } as WorkLog))

        const withLogs = raw.map(p => ({
          ...p,
          workLogs: allLogs.filter(l => l.planillaId === p.id),
        }))

        if (!cancelled) setPlanillas(withLogs)
      } catch (e: any) {
        if (!cancelled) setError(e.message ?? 'Error al cargar planillas')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => { cancelled = true }
  }, [])

  const filtered = useMemo(() => {
    let list = planillas
    if (estadoFilter) list = list.filter(p => p.estado === estadoFilter)
    if (dateFrom) list = list.filter(p => p.fecha >= dateFrom)
    if (dateTo) list = list.filter(p => p.fecha <= dateTo)
    if (search.trim()) {
      const q = search.toLowerCase()
      list = list.filter(p =>
        p.tecnicoNombre?.toLowerCase().includes(q) ||
        p.agencia?.toLowerCase().includes(q) ||
        p.vehiculoId?.toLowerCase().includes(q)
      )
    }
    return list
  }, [planillas, estadoFilter, dateFrom, dateTo, search])

  const totalMinutos = useMemo(() => filtered.reduce((s, p) => s + (p.totalMinutos ?? 0), 0), [filtered])
  const totalActividades = useMemo(() => filtered.reduce((s, p) => s + (p.totalActividades ?? 0), 0), [filtered])

  const toggleExpand = (id: string) => {
    setExpanded(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  return (
    <div className="space-y-5">
      <div>
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-gray-900">Planillas de Trabajo</h1>
          {!loading && (
            <span className="bg-[#003087] text-white text-xs font-bold px-2.5 py-1 rounded-full">
              {planillas.length}
            </span>
          )}
        </div>
        <p className="text-sm text-gray-500 mt-0.5">Registro de actividades diarias por técnico</p>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl shadow-card p-4 flex flex-wrap gap-3 items-center">
        <div className="flex-1 min-w-[200px] relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="Buscar por técnico, agencia, vehículo..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-full pl-9 pr-4 py-2.5 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087]"
          />
        </div>
        <select
          value={estadoFilter}
          onChange={e => setEstadoFilter(e.target.value)}
          className="text-sm border border-gray-200 rounded-lg px-3 py-2.5 focus:outline-none focus:ring-2 focus:ring-[#003087] bg-white text-gray-700"
        >
          <option value="">Todos los estados</option>
          {['ABIERTA', 'CERRADA', 'APROBADA', 'RECHAZADA'].map(s => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
        <div className="flex items-center gap-2 text-sm text-gray-500">
          <Calendar size={14} />
          <input
            type="date"
            value={dateFrom}
            onChange={e => setDateFrom(e.target.value)}
            className="border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]"
          />
          <span>—</span>
          <input
            type="date"
            value={dateTo}
            onChange={e => setDateTo(e.target.value)}
            className="border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]"
          />
        </div>
      </div>

      {/* Summary stats */}
      {!loading && filtered.length > 0 && (
        <div className="grid grid-cols-3 gap-4">
          {[
            { label: 'Planillas', value: filtered.length, color: 'bg-blue-50 text-blue-700' },
            { label: 'Actividades totales', value: totalActividades, color: 'bg-purple-50 text-purple-700' },
            { label: 'Horas totales', value: minutesToHHMM(totalMinutos), color: 'bg-emerald-50 text-emerald-700' },
          ].map(s => (
            <div key={s.label} className={`rounded-xl p-4 ${s.color}`}>
              <p className="text-xs font-medium opacity-70">{s.label}</p>
              <p className="text-2xl font-bold mt-1">{s.value}</p>
            </div>
          ))}
        </div>
      )}

      {loading ? (
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      ) : error ? (
        <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-center text-red-600 text-sm">{error}</div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-xl shadow-card p-12 text-center">
          <ClipboardList size={40} className="text-gray-200 mx-auto mb-3" />
          <p className="text-gray-500">Sin planillas en el período seleccionado</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-card overflow-hidden">
          <div className="divide-y divide-gray-100">
            {filtered.map(planilla => {
              const isOpen = expanded.has(planilla.id)
              return (
                <div key={planilla.id}>
                  {/* Planilla row */}
                  <button
                    onClick={() => toggleExpand(planilla.id)}
                    className="w-full px-5 py-4 flex items-center gap-4 hover:bg-gray-50 transition-colors text-left"
                  >
                    <span className="text-gray-400 flex-shrink-0">
                      {isOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                    </span>
                    <div className="flex-1 grid grid-cols-2 sm:grid-cols-4 gap-2">
                      <div>
                        <p className="text-xs text-gray-400">Técnico</p>
                        <p className="text-sm font-semibold text-gray-800 truncate">{planilla.tecnicoNombre ?? '—'}</p>
                      </div>
                      <div>
                        <p className="text-xs text-gray-400">Fecha</p>
                        <p className="text-sm text-gray-700">{planilla.fecha ?? '—'}</p>
                      </div>
                      <div>
                        <p className="text-xs text-gray-400">Agencia</p>
                        <p className="text-sm text-gray-600 truncate">{planilla.agencia ?? '—'}</p>
                      </div>
                      <div className="flex items-center gap-3">
                        <div>
                          <p className="text-xs text-gray-400">Horas</p>
                          <p className="text-sm font-mono font-semibold text-gray-800 flex items-center gap-1">
                            <Clock size={12} className="text-gray-400" />
                            {minutesToHHMM(planilla.totalMinutos ?? 0)}
                          </p>
                        </div>
                        {planilla.estado && (
                          <span className={`text-xs px-2.5 py-0.5 rounded-full font-semibold ${ESTADO_STYLES[planilla.estado] ?? 'bg-gray-100 text-gray-600'}`}>
                            {planilla.estado}
                          </span>
                        )}
                      </div>
                    </div>
                  </button>

                  {/* Expanded work logs */}
                  {isOpen && (
                    <div className="bg-gray-50 border-t border-gray-100 px-5 py-3">
                      {!planilla.workLogs?.length ? (
                        <p className="text-sm text-gray-400 py-2">Sin actividades registradas</p>
                      ) : (
                        <table className="w-full text-xs">
                          <thead>
                            <tr className="text-gray-400 font-semibold uppercase tracking-wide">
                              <th className="text-left py-1.5 pr-3">Actividad</th>
                              <th className="text-left py-1.5 pr-3">Orden SAP</th>
                              <th className="text-left py-1.5 pr-3">Descripción</th>
                              <th className="text-right py-1.5">Tiempo</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-200">
                            {planilla.workLogs.map(log => (
                              <tr key={log.id} className="hover:bg-white transition-colors">
                                <td className="py-2 pr-3 font-medium text-gray-700 max-w-[160px] truncate">{log.actividad ?? '—'}</td>
                                <td className="py-2 pr-3 font-mono text-blue-700">{log.ordenSap ?? '—'}</td>
                                <td className="py-2 pr-3 text-gray-500 max-w-[200px] truncate">{log.descripcion ?? '—'}</td>
                                <td className="py-2 text-right font-semibold text-gray-700">
                                  {minutesToHHMM(log.minutosEjecutados ?? 0)}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                          <tfoot>
                            <tr className="border-t border-gray-300">
                              <td colSpan={3} className="py-2 text-gray-500 font-semibold">Total</td>
                              <td className="py-2 text-right font-bold text-[#003087]">
                                {minutesToHHMM(planilla.workLogs.reduce((s, l) => s + (l.minutosEjecutados ?? 0), 0))}
                              </td>
                            </tr>
                          </tfoot>
                        </table>
                      )}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
