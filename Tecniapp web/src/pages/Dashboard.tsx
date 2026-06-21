import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, Clock, Truck, Lightbulb, Activity } from 'lucide-react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend, LineChart, Line,
} from 'recharts'
import { format, subDays, startOfDay } from 'date-fns'
import { es } from 'date-fns/locale'
import { useAverias } from '../hooks/useAverias'
import { useVehiculos } from '../hooks/useVehiculos'
import { useLuminarias } from '../hooks/useLuminarias'
import { useStats } from '../hooks/useStats'
import { AveriaEstadoBadge } from '../components/ui/AveriaEstadoBadge'
import type { AveriaEstado } from '../types'

const ESTADO_COLORS: Record<AveriaEstado, string> = {
  PENDIENTE: '#f59e0b',
  ASIGNADA: '#3b82f6',
  EN_ATENCION: '#f97316',
  RESUELTA: '#22c55e',
  ANULADA: '#94a3b8',
}

const PIE_COLORS = ['#003087','#0066CC','#FFB800','#22c55e','#f97316','#ef4444','#8b5cf6','#06b6d4']

function SkeletonCard() {
  return (
    <div className="bg-white rounded-xl border border-slate-100 p-5 animate-pulse">
      <div className="h-4 bg-slate-100 rounded w-1/2 mb-3" />
      <div className="h-8 bg-slate-100 rounded w-1/3 mb-2" />
      <div className="h-3 bg-slate-100 rounded w-2/3" />
    </div>
  )
}

export default function Dashboard() {
  const navigate = useNavigate()
  const { averias, loading: loadingA } = useAverias({})
  const { loading: loadingV } = useVehiculos()
  const { loading: loadingL } = useLuminarias()
  const { stats, loading: loadingStats } = useStats()

  // Averías por estado for bar chart
  const estadoChartData = useMemo(() => {
    const estados: AveriaEstado[] = ['PENDIENTE', 'ASIGNADA', 'EN_ATENCION', 'RESUELTA', 'ANULADA']
    const labels: Record<AveriaEstado, string> = {
      PENDIENTE: 'Pendiente',
      ASIGNADA: 'Asignada',
      EN_ATENCION: 'En Atención',
      RESUELTA: 'Resuelta',
      ANULADA: 'Anulada',
    }
    return estados.map(estado => ({
      name: labels[estado],
      count: averias.filter(a => a.estado === estado).length,
      color: ESTADO_COLORS[estado],
    }))
  }, [averias])

  // Averías por agencia for pie chart
  const agenciaChartData = useMemo(() => {
    const counts: Record<string, number> = {}
    averias.forEach(a => {
      const key = a.nombreAgencia || a.agencia || 'Sin Agencia'
      counts[key] = (counts[key] || 0) + 1
    })
    return Object.entries(counts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 8)
      .map(([name, value]) => ({ name, value }))
  }, [averias])

  // Last 7 days trend
  const trendData = useMemo(() => {
    const days = Array.from({ length: 7 }, (_, i) => {
      const date = subDays(new Date(), 6 - i)
      const dayStart = startOfDay(date).getTime()
      const dayEnd = dayStart + 86400000
      return {
        name: format(date, 'EEE dd', { locale: es }),
        averias: averias.filter(a => a.fechaInicioMillis >= dayStart && a.fechaInicioMillis < dayEnd).length,
      }
    })
    return days
  }, [averias])

  // Recent averías (last 10)
  const recentAverias = useMemo(() => averias.slice(0, 10), [averias])

  const loading = loadingA || loadingV || loadingL || loadingStats

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">Dashboard</h2>
          <p className="text-slate-500 text-sm mt-0.5">
            {format(new Date(), "EEEE, d 'de' MMMM yyyy", { locale: es })}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
          <span className="text-xs text-slate-500 font-medium">En tiempo real</span>
        </div>
      </div>

      {/* Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {loading ? (
          <>
            <SkeletonCard /><SkeletonCard /><SkeletonCard /><SkeletonCard />
          </>
        ) : (
          <>
            <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Total Averías</p>
                  <p className="text-3xl font-bold text-slate-800 mt-1">{stats.totalAverias}</p>
                  <div className="flex flex-wrap gap-1 mt-2">
                    <span className="text-[10px] bg-amber-50 text-amber-700 px-1.5 py-0.5 rounded-full font-medium">
                      {stats.averiasPendientes} pend.
                    </span>
                    <span className="text-[10px] bg-orange-50 text-orange-700 px-1.5 py-0.5 rounded-full font-medium">
                      {stats.averiasEnAtencion} atend.
                    </span>
                    <span className="text-[10px] bg-green-50 text-green-700 px-1.5 py-0.5 rounded-full font-medium">
                      {stats.averiasResueltas} res.
                    </span>
                  </div>
                </div>
                <div className="bg-blue-100 p-3 rounded-xl flex-shrink-0">
                  <AlertTriangle size={20} className="text-[#003087]" />
                </div>
              </div>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Averías Pendientes</p>
                  <p className="text-3xl font-bold text-amber-600 mt-1">{stats.averiasPendientes}</p>
                  <p className="text-xs text-slate-400 mt-1">
                    {stats.averiasAsignadas} asignadas
                  </p>
                </div>
                <div className="bg-amber-100 p-3 rounded-xl flex-shrink-0">
                  <Clock size={20} className="text-amber-600" />
                </div>
              </div>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Vehículos Activos</p>
                  <p className="text-3xl font-bold text-slate-800 mt-1">{stats.totalVehiculos}</p>
                  <div className="flex flex-wrap gap-1 mt-2">
                    <span className="text-[10px] bg-green-50 text-green-700 px-1.5 py-0.5 rounded-full font-medium">
                      {stats.vehiculosOptimos} óptimo
                    </span>
                    <span className="text-[10px] bg-amber-50 text-amber-700 px-1.5 py-0.5 rounded-full font-medium">
                      {stats.vehiculosAtencion} atenc.
                    </span>
                    <span className="text-[10px] bg-red-50 text-red-700 px-1.5 py-0.5 rounded-full font-medium">
                      {stats.vehiculosVencidos} venc.
                    </span>
                  </div>
                </div>
                <div className="bg-green-100 p-3 rounded-xl flex-shrink-0">
                  <Truck size={20} className="text-green-600" />
                </div>
              </div>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Luminarias Pendientes</p>
                  <p className="text-3xl font-bold text-orange-500 mt-1">{stats.luminariasPendientes}</p>
                  <p className="text-xs text-slate-400 mt-1">
                    {stats.luminariasResueltas} reparadas
                  </p>
                </div>
                <div className="bg-orange-100 p-3 rounded-xl flex-shrink-0">
                  <Lightbulb size={20} className="text-orange-500" />
                </div>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        {/* Bar chart - Averías por Estado */}
        <div className="xl:col-span-2 bg-white rounded-xl shadow-sm border border-slate-100 p-5">
          <h3 className="text-base font-semibold text-slate-800 mb-4">Averías por Estado</h3>
          {loading ? (
            <div className="h-56 bg-slate-50 rounded-xl animate-pulse" />
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={estadoChartData} barSize={36}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#64748b' }} />
                <YAxis tick={{ fontSize: 11, fill: '#64748b' }} />
                <Tooltip
                  contentStyle={{ background: '#1e293b', border: 'none', borderRadius: 8, color: '#f8fafc', fontSize: 12 }}
                  cursor={{ fill: '#f8fafc' }}
                />
                <Bar dataKey="count" name="Averías" radius={[4, 4, 0, 0]}>
                  {estadoChartData.map((entry, index) => (
                    <Cell key={index} fill={entry.color} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Pie chart - por Agencia */}
        <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5">
          <h3 className="text-base font-semibold text-slate-800 mb-4">Top Agencias</h3>
          {loading ? (
            <div className="h-56 bg-slate-50 rounded-xl animate-pulse" />
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={agenciaChartData}
                  cx="50%"
                  cy="50%"
                  innerRadius={50}
                  outerRadius={80}
                  dataKey="value"
                  paddingAngle={2}
                >
                  {agenciaChartData.map((_, index) => (
                    <Cell key={index} fill={PIE_COLORS[index % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ background: '#1e293b', border: 'none', borderRadius: 8, color: '#f8fafc', fontSize: 12 }}
                />
                <Legend iconSize={8} wrapperStyle={{ fontSize: 11 }} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Trend + Recent */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        {/* Line chart - 7-day trend */}
        <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5">
          <h3 className="text-base font-semibold text-slate-800 mb-4">Averías Últimos 7 Días</h3>
          {loading ? (
            <div className="h-40 bg-slate-50 rounded-xl animate-pulse" />
          ) : (
            <ResponsiveContainer width="100%" height={160}>
              <LineChart data={trendData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis dataKey="name" tick={{ fontSize: 10, fill: '#64748b' }} />
                <YAxis tick={{ fontSize: 10, fill: '#64748b' }} />
                <Tooltip
                  contentStyle={{ background: '#1e293b', border: 'none', borderRadius: 8, color: '#f8fafc', fontSize: 12 }}
                />
                <Line
                  type="monotone"
                  dataKey="averias"
                  stroke="#003087"
                  strokeWidth={2}
                  dot={{ fill: '#003087', r: 3 }}
                  activeDot={{ r: 5 }}
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Recent Averías table */}
        <div className="xl:col-span-2 bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
            <h3 className="text-base font-semibold text-slate-800">Averías Recientes</h3>
            <button
              onClick={() => navigate('/averias')}
              className="text-xs text-[#0066CC] hover:underline font-medium"
            >
              Ver todas →
            </button>
          </div>
          {loading ? (
            <div className="p-5 space-y-3 animate-pulse">
              {[1,2,3,4,5].map(i => <div key={i} className="h-10 bg-slate-100 rounded-lg" />)}
            </div>
          ) : recentAverias.length === 0 ? (
            <div className="p-10 text-center">
              <Activity size={32} className="text-slate-300 mx-auto mb-2" />
              <p className="text-slate-400 text-sm">No hay averías registradas</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-100">
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Caso</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Estado</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider hidden md:table-cell">Agencia</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider hidden lg:table-cell">Clientes</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider hidden xl:table-cell">Fecha</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {recentAverias.map((averia) => (
                    <tr
                      key={averia.caseId}
                      onClick={() => navigate(`/averias/${averia.caseId}`)}
                      className="hover:bg-slate-50 cursor-pointer transition-colors"
                    >
                      <td className="px-4 py-3 font-mono text-xs font-semibold text-[#003087]">
                        {averia.caseId}
                      </td>
                      <td className="px-4 py-3">
                        <AveriaEstadoBadge estado={averia.estado} />
                      </td>
                      <td className="px-4 py-3 text-slate-600 hidden md:table-cell text-xs">
                        {averia.nombreAgencia || averia.agencia || '-'}
                      </td>
                      <td className="px-4 py-3 text-slate-600 hidden lg:table-cell text-xs">
                        {averia.clientesAfectados || 0}
                      </td>
                      <td className="px-4 py-3 text-slate-400 text-xs hidden xl:table-cell">
                        {averia.fechaInicioMillis
                          ? format(new Date(averia.fechaInicioMillis), 'dd/MM/yyyy', { locale: es })
                          : '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
