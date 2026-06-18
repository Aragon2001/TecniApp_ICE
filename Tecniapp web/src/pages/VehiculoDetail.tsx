import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, Truck, AlertTriangle, CheckCircle2, Clock, ChevronRight, Gauge, Calendar } from 'lucide-react'
import { useVehiculo } from '../hooks/useVehiculos'
import { formatDate, formatDateTime } from '../utils/dateUtils'
import { vehiculoEstadoLabel, formatKm, parseJson } from '../utils/formatUtils'
import { Spinner } from '../components/ui/Spinner'

export default function VehiculoDetail() {
  const { vehiculoId } = useParams<{ vehiculoId: string }>()
  const { vehiculo, loading, error } = useVehiculo(vehiculoId ?? null)

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  if (error || !vehiculo) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-xl p-8 text-center">
        <AlertTriangle size={32} className="text-red-400 mx-auto mb-3" />
        <p className="text-red-600">{error ?? 'Vehículo no encontrado'}</p>
        <Link to="/vehiculos" className="mt-4 inline-block text-sm text-[#0066CC] hover:underline">
          ← Volver a Vehículos
        </Link>
      </div>
    )
  }

  const estado = (vehiculo.estado ?? 'OPTIMO') as 'OPTIMO' | 'ATENCION' | 'VENCIDO'
  const kmRestante = (vehiculo.mantenimientoProximo ?? 0) - (vehiculo.kmActual ?? 0)
  const pct = vehiculo.mantenimientoProximo
    ? Math.min(100, Math.max(0, ((vehiculo.kmActual ?? 0) / vehiculo.mantenimientoProximo) * 100))
    : 0

  const estadoStyle = {
    OPTIMO: { bg: 'bg-emerald-100', text: 'text-emerald-700', icon: <CheckCircle2 size={16} className="text-emerald-500" />, banner: null },
    ATENCION: {
      bg: 'bg-amber-100', text: 'text-amber-700',
      icon: <Clock size={16} className="text-amber-500" />,
      banner: `bg-amber-50 border-amber-200 text-amber-700`,
    },
    VENCIDO: {
      bg: 'bg-red-100', text: 'text-red-700',
      icon: <AlertTriangle size={16} className="text-red-500" />,
      banner: `bg-red-50 border-red-200 text-red-700`,
    },
  }[estado] ?? { bg: 'bg-gray-100', text: 'text-gray-700', icon: null, banner: null }

  const registros = parseJson<any[]>(vehiculo.registrosDiariosJson) ?? []

  return (
    <div className="space-y-5 max-w-4xl">
      {/* Breadcrumb */}
      <div>
        <div className="flex items-center gap-2 text-sm text-gray-500 mb-2">
          <Link to="/vehiculos" className="hover:text-[#003087] flex items-center gap-1">
            <ArrowLeft size={14} /> Vehículos
          </Link>
          <ChevronRight size={12} />
          <span className="font-mono text-gray-700">{vehiculo.placa}</span>
        </div>
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-[#003087] rounded-xl flex items-center justify-center">
            <Truck size={20} className="text-white" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-gray-900 font-mono">{vehiculo.placa}</h1>
            <p className="text-sm text-gray-500">{vehiculo.tipo ?? 'Vehículo'} • {vehiculo.agencia ?? '—'}</p>
          </div>
          <span className={`ml-2 flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-semibold ${estadoStyle.bg} ${estadoStyle.text}`}>
            {estadoStyle.icon}
            {vehiculoEstadoLabel(estado)}
          </span>
        </div>
      </div>

      {/* Alert banners */}
      {estado === 'VENCIDO' && (
        <div className="bg-red-50 border border-red-200 rounded-xl px-5 py-3 flex items-center gap-3">
          <AlertTriangle size={18} className="text-red-500 flex-shrink-0" />
          <p className="text-sm font-medium text-red-700">
            ¡Mantenimiento Vencido! Este vehículo superó el límite de {formatKm(vehiculo.mantenimientoProximo)}. Programar mantenimiento urgente.
          </p>
        </div>
      )}
      {estado === 'ATENCION' && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl px-5 py-3 flex items-center gap-3">
          <Clock size={18} className="text-amber-500 flex-shrink-0" />
          <p className="text-sm font-medium text-amber-700">
            Mantenimiento próximo en {formatKm(Math.abs(kmRestante))}. Programar servicio preventivo.
          </p>
        </div>
      )}

      {/* Cards grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {/* Identificación */}
        <div className="bg-white rounded-xl shadow-card p-5">
          <div className="flex items-center gap-2 mb-4 pb-3 border-b border-gray-100">
            <Truck size={16} className="text-[#003087]" />
            <h3 className="font-semibold text-gray-800 text-sm">Identificación</h3>
          </div>
          <div className="space-y-2.5">
            <InfoRow label="ID Vehículo" value={vehiculo.vehiculoId} mono />
            <InfoRow label="Placa" value={vehiculo.placa} highlight />
            <InfoRow label="Tipo" value={vehiculo.tipo ?? '—'} />
            <InfoRow label="Agencia" value={vehiculo.agencia ?? '—'} />
            <InfoRow label="Subregión" value={vehiculo.subregion ?? '—'} />
          </div>
        </div>

        {/* Kilometraje y mantenimiento */}
        <div className="bg-white rounded-xl shadow-card p-5">
          <div className="flex items-center gap-2 mb-4 pb-3 border-b border-gray-100">
            <Gauge size={16} className="text-[#003087]" />
            <h3 className="font-semibold text-gray-800 text-sm">Kilometraje y Mantenimiento</h3>
          </div>
          <div className="space-y-2.5">
            <InfoRow label="KM Actual" value={formatKm(vehiculo.kmActual)} highlight />
            <InfoRow label="Orimetro" value={vehiculo.orimetroActual != null ? `${vehiculo.orimetroActual} hrs` : '—'} />
            <InfoRow label="Último Mant." value={formatDate(vehiculo.mantenimientoUltimo)} />
            <InfoRow
              label="Próx. Mant."
              value={vehiculo.mantenimientoProximo ? formatKm(vehiculo.mantenimientoProximo) : '—'}
            />
            {kmRestante !== 0 && vehiculo.mantenimientoProximo && (
              <InfoRow
                label={kmRestante > 0 ? 'Restante' : 'Vencido'}
                value={`${formatKm(Math.abs(kmRestante))} ${kmRestante < 0 ? 'pasado' : ''}`}
                highlight
              />
            )}
          </div>

          {/* Progress bar */}
          {vehiculo.mantenimientoProximo && vehiculo.mantenimientoProximo > 0 && (
            <div className="mt-4">
              <div className="w-full bg-gray-100 rounded-full h-2">
                <div
                  className={`h-2 rounded-full transition-all ${
                    estado === 'VENCIDO' ? 'bg-red-500' : estado === 'ATENCION' ? 'bg-amber-400' : 'bg-emerald-500'
                  }`}
                  style={{ width: `${pct}%` }}
                />
              </div>
              <div className="flex justify-between text-[10px] text-gray-400 mt-1">
                <span>0</span>
                <span className={`font-medium ${estado !== 'OPTIMO' ? 'text-amber-600' : 'text-gray-500'}`}>
                  {pct.toFixed(0)}% del límite
                </span>
                <span>{formatKm(vehiculo.mantenimientoProximo)}</span>
              </div>
            </div>
          )}
        </div>

        {/* Registro diario */}
        <div className="bg-white rounded-xl shadow-card p-5">
          <div className="flex items-center gap-2 mb-4 pb-3 border-b border-gray-100">
            <Calendar size={16} className="text-[#003087]" />
            <h3 className="font-semibold text-gray-800 text-sm">Registro Diario</h3>
          </div>
          <div className="space-y-2.5">
            <InfoRow label="Fecha" value={vehiculo.registroFecha ?? '—'} />
            <InfoRow label="KM Inicial" value={formatKm(vehiculo.registroInicial)} />
            <InfoRow label="KM Final" value={formatKm(vehiculo.registroFinal)} />
            <InfoRow
              label="Estado"
              value={vehiculo.registroCerrado ? 'Cerrado' : 'Abierto'}
              highlight
            />
          </div>
        </div>

        {/* Última actualización */}
        <div className="bg-white rounded-xl shadow-card p-5">
          <div className="flex items-center gap-2 mb-4 pb-3 border-b border-gray-100">
            <Clock size={16} className="text-[#003087]" />
            <h3 className="font-semibold text-gray-800 text-sm">Sincronización</h3>
          </div>
          <div className="space-y-2.5">
            <InfoRow label="Última actualización" value={formatDateTime(vehiculo.updatedAt)} />
          </div>
        </div>
      </div>

      {/* Daily logs history */}
      {registros.length > 0 && (
        <div className="bg-white rounded-xl shadow-card p-5">
          <h3 className="font-semibold text-gray-800 text-sm mb-4">Historial de Registros Diarios</h3>
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50">
                <tr>
                  {['Fecha', 'KM Inicio', 'KM Final', 'Recorrido', 'Cerrado'].map((h) => (
                    <th key={h} className="px-3 py-2 text-left text-xs font-semibold text-gray-500">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {registros.slice(0, 20).map((r: any, i: number) => (
                  <tr key={i} className="hover:bg-gray-50">
                    <td className="px-3 py-2 text-gray-700">{r.fecha ?? formatDate(r.timestamp)}</td>
                    <td className="px-3 py-2 text-gray-600">{formatKm(r.kmInicial ?? r.inicio)}</td>
                    <td className="px-3 py-2 text-gray-600">{formatKm(r.kmFinal ?? r.final)}</td>
                    <td className="px-3 py-2 font-medium text-gray-800">
                      {r.kmInicial && r.kmFinal ? formatKm(r.kmFinal - r.kmInicial) : '—'}
                    </td>
                    <td className="px-3 py-2">
                      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${r.cerrado ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'}`}>
                        {r.cerrado ? 'Sí' : 'No'}
                      </span>
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

function InfoRow({ label, value, mono, highlight }: { label: string; value: any; mono?: boolean; highlight?: boolean }) {
  return (
    <div className="flex items-start gap-3">
      <span className="text-xs text-gray-500 w-28 flex-shrink-0 pt-0.5">{label}</span>
      <span className={`text-sm flex-1 ${mono ? 'font-mono' : ''} ${highlight ? 'font-semibold text-gray-900' : 'text-gray-700'}`}>
        {value ?? '—'}
      </span>
    </div>
  )
}
