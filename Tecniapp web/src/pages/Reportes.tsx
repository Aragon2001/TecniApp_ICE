import React, { useState } from 'react'
import { FileText, Download, FileSpreadsheet, AlertTriangle, Truck, Zap, Package, Users, Calendar } from 'lucide-react'
import { useAverias } from '../hooks/useAverias'
import { useVehiculos } from '../hooks/useVehiculos'
import { useLuminarias } from '../hooks/useLuminarias'
import { useInventario } from '../hooks/useInventario'
import { useUsuarios } from '../hooks/useUsuarios'
import { exportAveriasExcel, exportVehiculosExcel, exportUsuariosExcel, exportToPdf } from '../utils/exportUtils'
import { format, subDays, startOfMonth, endOfMonth } from 'date-fns'
import toast from 'react-hot-toast'
import { Spinner } from '../components/ui/Spinner'

interface ReportDef {
  id: string
  title: string
  description: string
  icon: React.ReactNode
  color: string
  border: string
}

const REPORTS: ReportDef[] = [
  {
    id: 'averias',
    title: 'Reporte de Averías',
    description: 'Historial completo de averías con estados, técnicos asignados y tiempos de resolución.',
    icon: <AlertTriangle size={24} />,
    color: 'text-amber-600',
    border: 'border-amber-200',
  },
  {
    id: 'vehiculos',
    title: 'Estado de Vehículos',
    description: 'Flota vehicular con kilometraje actual, mantenimientos próximos y estado de cada unidad.',
    icon: <Truck size={24} />,
    color: 'text-blue-600',
    border: 'border-blue-200',
  },
  {
    id: 'luminarias',
    title: 'Reporte de Luminarias',
    description: 'Registro de reparaciones de luminarias con localización y estado de resolución.',
    icon: <Zap size={24} />,
    color: 'text-yellow-600',
    border: 'border-yellow-200',
  },
  {
    id: 'inventario',
    title: 'Inventario General',
    description: 'Stock de materiales y herramientas por vehículo y agencia con cantidades actuales.',
    icon: <Package size={24} />,
    color: 'text-purple-600',
    border: 'border-purple-200',
  },
  {
    id: 'usuarios',
    title: 'Directorio de Usuarios',
    description: 'Lista de técnicos y supervisores con roles, agencias y vehículos asignados.',
    icon: <Users size={24} />,
    color: 'text-green-600',
    border: 'border-green-200',
  },
  {
    id: 'programacion',
    title: 'Programación de Tareas',
    description: 'Actividades programadas por subregión con fechas de asignación y ejecución.',
    icon: <Calendar size={24} />,
    color: 'text-indigo-600',
    border: 'border-indigo-200',
  },
]

export default function Reportes() {
  const { averias, loading: lAverias } = useAverias({})
  const { vehiculos, loading: lVehiculos } = useVehiculos()
  const { luminarias, loading: lLuminarias } = useLuminarias()
  const { inventario, loading: lInventario } = useInventario()
  const { usuarios, loading: lUsuarios } = useUsuarios()

  const [dateFrom, setDateFrom] = useState(() => format(startOfMonth(new Date()), 'yyyy-MM-dd'))
  const [dateTo, setDateTo] = useState(() => format(endOfMonth(new Date()), 'yyyy-MM-dd'))
  const [generating, setGenerating] = useState<string | null>(null)

  const loading = lAverias || lVehiculos || lLuminarias || lInventario || lUsuarios

  const filteredAverias = averias.filter(a => {
    if (!a.fechaInicioMillis) return true
    const d = new Date(a.fechaInicioMillis).toISOString().slice(0, 10)
    return d >= dateFrom && d <= dateTo
  })

  async function handleExcel(reportId: string) {
    setGenerating(reportId + '-xlsx')
    try {
      if (reportId === 'averias') {
        exportAveriasExcel(filteredAverias)
      } else if (reportId === 'vehiculos') {
        exportVehiculosExcel(vehiculos)
      } else if (reportId === 'usuarios') {
        exportUsuariosExcel(usuarios)
      } else if (reportId === 'luminarias') {
        const rows = luminarias.map((l: any) => ({
          ID: l.id ?? '',
          Localización: l.localizacion ?? '',
          Circuito: l.circuito ?? '',
          Estado: l.estado ?? '',
          'Fecha Reporte': l.fechaReporte ?? '',
          Observación: l.observacion ?? '',
        }))
        exportToPdf(rows, `luminarias_${dateFrom}_${dateTo}`, 'Reporte de Luminarias')
      } else if (reportId === 'inventario') {
        const rows = inventario.map((i: any) => ({
          Código: i.codigo ?? '',
          Descripción: i.descripcion ?? '',
          Cantidad: i.cantidad ?? 0,
          Unidad: i.unidad ?? '',
          Vehículo: i.vehiculoId ?? '',
          Agencia: i.agencia ?? '',
        }))
        const wb = (await import('xlsx')).default
        const ws = wb.utils.json_to_sheet(rows)
        const book = wb.utils.book_new()
        wb.utils.book_append_sheet(book, ws, 'Inventario')
        wb.writeFile(book, `inventario_${dateFrom}_${dateTo}.xlsx`)
      }
      toast.success('Reporte generado exitosamente')
    } catch (e) {
      toast.error('Error al generar el reporte')
    } finally {
      setGenerating(null)
    }
  }

  async function handlePdf(reportId: string) {
    setGenerating(reportId + '-pdf')
    try {
      if (reportId === 'averias') {
        const rows = filteredAverias.map(a => ({
          'Caso ID': a.caseId,
          Estado: a.estado,
          Agencia: a.nombreAgencia || a.agencia || '',
          Localización: a.localizacion,
          Técnico: a.tecnicoAsignadoNombre,
          Clientes: a.clientesAfectados ?? 0,
        }))
        exportToPdf(rows, `averias_${dateFrom}_${dateTo}`, 'Reporte de Averías', [
          { id: 'reportType', title: 'Tipo', data: 'Averías' },
          { id: 'period', title: 'Período', data: `${dateFrom} — ${dateTo}` },
          { id: 'total', title: 'Total', data: String(filteredAverias.length) },
        ])
      } else if (reportId === 'vehiculos') {
        const rows = vehiculos.map(v => ({
          Placa: v.placa,
          Tipo: v.tipo ?? '',
          Agencia: v.agencia ?? '',
          'KM Actual': v.kmActual ?? 0,
          Estado: v.estado ?? '',
        }))
        exportToPdf(rows, 'vehiculos', 'Estado de Vehículos')
      } else if (reportId === 'usuarios') {
        const rows = usuarios.map(u => ({
          Nombre: u.nombre ?? u.email,
          Email: u.email,
          Rol: u.rol ?? '',
          Agencia: u.agencia ?? '',
          Vehículo: u.vehiculoId ?? '',
        }))
        exportToPdf(rows, 'usuarios', 'Directorio de Usuarios')
      }
      toast.success('PDF generado exitosamente')
    } catch (e) {
      toast.error('Error al generar el PDF')
    } finally {
      setGenerating(null)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Reportes y Exportación</h1>
        <p className="text-sm text-gray-500 mt-0.5">Genera y exporta reportes en Excel y PDF</p>
      </div>

      {/* Date range filter */}
      <div className="bg-white rounded-xl shadow-card p-4 flex flex-wrap items-center gap-4">
        <div className="flex items-center gap-2 text-sm text-gray-600">
          <Calendar size={16} className="text-[#003087]" />
          <span className="font-medium">Período</span>
        </div>
        <div className="flex items-center gap-2">
          <input
            type="date"
            value={dateFrom}
            onChange={e => setDateFrom(e.target.value)}
            className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]"
          />
          <span className="text-gray-400">—</span>
          <input
            type="date"
            value={dateTo}
            onChange={e => setDateTo(e.target.value)}
            className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]"
          />
        </div>
        <div className="flex gap-2 flex-wrap">
          {[
            { label: 'Hoy', from: format(new Date(), 'yyyy-MM-dd'), to: format(new Date(), 'yyyy-MM-dd') },
            { label: 'Últimos 7 días', from: format(subDays(new Date(), 6), 'yyyy-MM-dd'), to: format(new Date(), 'yyyy-MM-dd') },
            { label: 'Este mes', from: format(startOfMonth(new Date()), 'yyyy-MM-dd'), to: format(endOfMonth(new Date()), 'yyyy-MM-dd') },
          ].map(p => (
            <button
              key={p.label}
              onClick={() => { setDateFrom(p.from); setDateTo(p.to) }}
              className={`text-xs px-3 py-1.5 rounded-lg border font-medium transition-colors ${
                dateFrom === p.from && dateTo === p.to
                  ? 'bg-[#003087] text-white border-[#003087]'
                  : 'bg-white text-gray-600 border-gray-200 hover:border-[#003087]'
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>
        {loading && <Spinner size="sm" />}
      </div>

      {/* Averías count for selected period */}
      {!lAverias && (
        <div className="text-sm text-gray-500 bg-white rounded-xl shadow-card px-5 py-3">
          <span className="font-semibold text-gray-800">{filteredAverias.length}</span> averías en el período seleccionado
        </div>
      )}

      {/* Report cards grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        {REPORTS.map(report => {
          const genXlsx = generating === report.id + '-xlsx'
          const genPdf = generating === report.id + '-pdf'
          const supportsExcel = ['averias', 'vehiculos', 'usuarios', 'luminarias', 'inventario'].includes(report.id)
          const supportsPdf = ['averias', 'vehiculos', 'usuarios'].includes(report.id)
          return (
            <div
              key={report.id}
              className={`bg-white rounded-xl shadow-card border-t-4 ${report.border} p-5 flex flex-col gap-4`}
            >
              <div className="flex items-start gap-3">
                <div className={`${report.color} mt-0.5`}>{report.icon}</div>
                <div>
                  <h3 className="font-semibold text-gray-900 text-sm">{report.title}</h3>
                  <p className="text-xs text-gray-500 mt-0.5 leading-relaxed">{report.description}</p>
                </div>
              </div>
              <div className="flex gap-2 pt-1 border-t border-gray-100">
                {supportsExcel && (
                  <button
                    onClick={() => handleExcel(report.id)}
                    disabled={!!generating || loading}
                    className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 bg-emerald-50 text-emerald-700 hover:bg-emerald-100 rounded-lg text-xs font-semibold transition-colors disabled:opacity-50"
                  >
                    {genXlsx ? <Spinner size="sm" /> : <FileSpreadsheet size={14} />}
                    Excel
                  </button>
                )}
                {supportsPdf && (
                  <button
                    onClick={() => handlePdf(report.id)}
                    disabled={!!generating || loading}
                    className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 bg-red-50 text-red-700 hover:bg-red-100 rounded-lg text-xs font-semibold transition-colors disabled:opacity-50"
                  >
                    {genPdf ? <Spinner size="sm" /> : <FileText size={14} />}
                    PDF
                  </button>
                )}
                {!supportsExcel && !supportsPdf && (
                  <div className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 bg-gray-50 text-gray-400 rounded-lg text-xs font-medium">
                    <Download size={14} />
                    Próximamente
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>

      {/* Tips */}
      <div className="bg-blue-50 border border-blue-100 rounded-xl p-4">
        <p className="text-xs text-blue-700 font-medium mb-1">Información sobre los reportes</p>
        <ul className="text-xs text-blue-600 space-y-1 list-disc list-inside">
          <li>Los reportes de averías aplican el filtro de período seleccionado arriba.</li>
          <li>Los reportes de vehículos y usuarios exportan todos los registros disponibles.</li>
          <li>Los archivos Excel incluyen encabezado ICE con fecha y logo institucional.</li>
          <li>Los PDFs incluyen resumen estadístico al inicio del documento.</li>
        </ul>
      </div>
    </div>
  )
}
