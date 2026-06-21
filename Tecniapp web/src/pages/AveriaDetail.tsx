import { useState, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  ArrowLeft, MapPin, User, Truck, Package, Clock, CheckCircle2,
  AlertTriangle, FileText, Camera, ExternalLink
} from 'lucide-react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'
import { ref, update } from 'firebase/database'
import { toast } from 'react-hot-toast'
import { rtdbAverias } from '../firebase/config'
import { useAveria } from '../hooks/useAverias'
import { useVehiculos } from '../hooks/useVehiculos'
import { useUsuarios } from '../hooks/useUsuarios'
import { AveriaEstadoBadge } from '../components/ui/AveriaEstadoBadge'
import { Spinner } from '../components/ui/Spinner'
import type { AveriaEstado } from '../types'

function InfoRow({ label, value, mono = false }: { label: string; value?: string | number | null; mono?: boolean }) {
  if (value === null || value === undefined || value === '') return null
  return (
    <div className="flex flex-col sm:flex-row sm:items-start gap-1 py-2 border-b border-slate-50 last:border-0">
      <span className="text-xs font-semibold text-slate-500 sm:w-36 flex-shrink-0">{label}</span>
      <span className={`text-sm text-slate-800 ${mono ? 'font-mono' : ''}`}>{String(value)}</span>
    </div>
  )
}

function TimelineItem({ label, millis, icon: Icon }: { label: string; millis?: number; icon: any }) {
  if (!millis) return null
  return (
    <div className="flex items-start gap-3">
      <div className="w-8 h-8 bg-[#003087]/10 rounded-lg flex items-center justify-center flex-shrink-0">
        <Icon size={14} className="text-[#003087]" />
      </div>
      <div>
        <p className="text-xs font-semibold text-slate-500">{label}</p>
        <p className="text-sm font-medium text-slate-800">
          {format(new Date(millis), 'dd/MM/yyyy HH:mm', { locale: es })}
        </p>
      </div>
    </div>
  )
}

export default function AveriaDetail() {
  const { caseId } = useParams<{ caseId: string }>()
  const navigate = useNavigate()
  // Subscribe directly to the single averia — no need to load all
  const { averia, loading } = useAveria(caseId ?? null)
  const { vehiculos } = useVehiculos()
  const { usuarios } = useUsuarios()
  const [showAssignModal, setShowAssignModal] = useState(false)
  const [assignVehiculo, setAssignVehiculo] = useState('')
  const [assignTecnico, setAssignTecnico] = useState('')
  const [saving, setSaving] = useState(false)

  const materiales = useMemo(() => {
    if (!averia?.materialesDetalleJson) return []
    try { return JSON.parse(averia.materialesDetalleJson) } catch { return [] }
  }, [averia])

  const tecnicos = useMemo(() => {
    if (!averia?.tecnicosAtendieronJson) return []
    try { return JSON.parse(averia.tecnicosAtendieronJson) } catch { return [] }
  }, [averia])

  const evidencias = useMemo(() => {
    if (!averia?.evidenciasJson) return []
    try { return JSON.parse(averia.evidenciasJson) } catch { return [] }
  }, [averia])

  const handleUpdateEstado = async (newEstado: AveriaEstado) => {
    if (!averia) return
    setSaving(true)
    const extra: Record<string, any> = { lastUpdated: Date.now() }
    if (newEstado === 'EN_ATENCION') extra.atencionHoraInicioMillis = Date.now()
    if (newEstado === 'RESUELTA') extra.horaFinalMillis = Date.now()
    try {
      // averia.id is the Firebase key (= caseId)
      await update(ref(rtdbAverias, `averias/${averia.id}`), { estado: newEstado, ...extra })
      toast.success(`Avería marcada como ${newEstado}`)
    } catch {
      toast.error('Error al actualizar el estado')
    } finally {
      setSaving(false)
    }
  }

  const handleAssign = async () => {
    if (!averia || !assignVehiculo || !assignTecnico) {
      toast.error('Seleccione vehículo y técnico')
      return
    }
    setSaving(true)
    const tecnico = usuarios.find(u => u.uid === assignTecnico || u.id === assignTecnico)
    try {
      await update(ref(rtdbAverias, `averias/${averia.id}`), {
        estado: 'ASIGNADA' as AveriaEstado,
        vehiculoAsignado: assignVehiculo,
        tecnicoAsignadoUid: assignTecnico,
        tecnicoAsignadoNombre: tecnico
          ? `${tecnico.nombre ?? ''} ${tecnico.apellidos ?? ''}`.trim()
          : assignTecnico,
        fechaAsignacion: Date.now(),
        lastUpdated: Date.now(),
      })
      toast.success('Avería asignada exitosamente')
      setShowAssignModal(false)
    } catch {
      toast.error('Error al asignar la avería')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Spinner size="lg" className="text-[#003087]" />
      </div>
    )
  }

  if (!averia) {
    return (
      <div className="text-center py-20">
        <AlertTriangle size={40} className="text-slate-300 mx-auto mb-3" />
        <p className="text-slate-500 font-medium">Avería no encontrada</p>
        <button
          onClick={() => navigate('/averias')}
          className="mt-4 text-sm text-[#0066CC] hover:underline"
        >
          ← Volver a Averías
        </button>
      </div>
    )
  }

  const isReadOnly = averia.estado === 'RESUELTA' || averia.estado === 'ANULADA'
  // Android stores GPS as lat/lng
  const mapsUrl = averia.lat && averia.lng
    ? `https://www.google.com/maps?q=${averia.lat},${averia.lng}`
    : null

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-start justify-between flex-wrap gap-3">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/averias')}
            className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
          >
            <ArrowLeft size={20} />
          </button>
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h2 className="text-xl font-bold text-slate-800 font-mono">
                Caso #{averia.caseId || averia.id}
              </h2>
              <AveriaEstadoBadge estado={averia.estado} size="md" />
            </div>
            <p className="text-slate-500 text-sm mt-0.5">
              {averia.fechaInicioMillis
                ? format(new Date(averia.fechaInicioMillis), "dd 'de' MMMM yyyy, HH:mm", { locale: es })
                : 'Fecha no disponible'}
            </p>
          </div>
        </div>

        {/* State machine actions */}
        <div className="flex items-center gap-2">
          {averia.estado === 'PENDIENTE' && (
            <button
              onClick={() => setShowAssignModal(true)}
              className="px-4 py-2 bg-[#003087] text-white text-sm font-semibold rounded-lg hover:bg-[#002266] transition-colors flex items-center gap-2"
            >
              <Truck size={14} /> Asignar
            </button>
          )}
          {averia.estado === 'ASIGNADA' && (
            <button
              onClick={() => handleUpdateEstado('EN_ATENCION')}
              disabled={saving}
              className="px-4 py-2 bg-orange-500 text-white text-sm font-semibold rounded-lg hover:bg-orange-600 transition-colors flex items-center gap-2"
            >
              {saving ? <Spinner size="sm" /> : null}
              Iniciar Atención
            </button>
          )}
          {averia.estado === 'EN_ATENCION' && (
            <div className="flex gap-2">
              <button
                onClick={() => handleUpdateEstado('RESUELTA')}
                disabled={saving}
                className="px-4 py-2 bg-green-600 text-white text-sm font-semibold rounded-lg hover:bg-green-700 transition-colors flex items-center gap-2"
              >
                <CheckCircle2 size={14} /> Marcar Resuelta
              </button>
              <button
                onClick={() => handleUpdateEstado('ANULADA')}
                disabled={saving}
                className="px-4 py-2 bg-slate-500 text-white text-sm font-semibold rounded-lg hover:bg-slate-600 transition-colors"
              >
                Anular
              </button>
            </div>
          )}
        </div>
      </div>

      {isReadOnly && (
        <div className={`rounded-xl p-4 flex items-center gap-3 ${averia.estado === 'RESUELTA' ? 'bg-green-50 border border-green-200' : 'bg-gray-50 border border-gray-200'}`}>
          <CheckCircle2 size={18} className={averia.estado === 'RESUELTA' ? 'text-green-600' : 'text-gray-400'} />
          <p className={`text-sm font-medium ${averia.estado === 'RESUELTA' ? 'text-green-700' : 'text-gray-600'}`}>
            {averia.estado === 'RESUELTA'
              ? 'Esta avería ha sido resuelta exitosamente.'
              : 'Esta avería fue anulada y está en modo solo lectura.'}
          </p>
        </div>
      )}

      {/* Two-column layout */}
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-5">
        {/* Left column */}
        <div className="space-y-4">
          {/* Información General */}
          <div className="bg-white rounded-xl border border-slate-100 p-5">
            <div className="flex items-center gap-2 mb-4">
              <FileText size={16} className="text-[#003087]" />
              <h3 className="text-base font-semibold text-slate-800">Información General</h3>
            </div>
            <InfoRow label="Tipo de Afectación" value={averia.tipoAfectacion} />
            <InfoRow label="Clientes Afectados" value={averia.clientesAfectados} />
            <InfoRow label="Agencia" value={averia.nombreAgencia || averia.agencia} />
            <InfoRow label="Región" value={averia.region} />
            <InfoRow label="Causa" value={averia.causa} />
            <InfoRow label="NISE" value={averia.nise} />
            {averia.observaciones && (
              <div className="mt-3 pt-3 border-t border-slate-100">
                <p className="text-xs font-semibold text-slate-500 mb-1">Observaciones</p>
                <p className="text-sm text-slate-700 whitespace-pre-wrap">{averia.observaciones}</p>
              </div>
            )}
          </div>

          {/* Ubicación */}
          <div className="bg-white rounded-xl border border-slate-100 p-5">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <MapPin size={16} className="text-[#003087]" />
                <h3 className="text-base font-semibold text-slate-800">Ubicación</h3>
              </div>
              {mapsUrl && (
                <a href={mapsUrl} target="_blank" rel="noopener noreferrer"
                  className="flex items-center gap-1 text-xs text-[#0066CC] hover:underline">
                  <ExternalLink size={12} /> Ver en Maps
                </a>
              )}
            </div>
            <InfoRow label="Localización" value={averia.localizacion} />
            <InfoRow label="Dirección" value={averia.direccion} />
            {averia.lat && averia.lng && (
              <InfoRow label="Coordenadas" value={`${Number(averia.lat).toFixed(6)}, ${Number(averia.lng).toFixed(6)}`} mono />
            )}
          </div>

          {/* Medidor */}
          {(averia.numeroMedidor || averia.medidorCalle) && (
            <div className="bg-white rounded-xl border border-slate-100 p-5">
              <h3 className="text-base font-semibold text-slate-800 mb-4">Datos del Medidor</h3>
              <InfoRow label="Número" value={averia.numeroMedidor} mono />
              <InfoRow label="Calle" value={averia.medidorCalle} />
              <InfoRow label="Pueblo" value={averia.medidorPueblo} />
              <InfoRow label="Poste" value={averia.medidorPoste} />
              <InfoRow label="Metros" value={averia.medidorMetros} />
            </div>
          )}

          {/* Datos CLOR */}
          {(averia.estadoClor || averia.causaClor) && (
            <div className="bg-blue-50 rounded-xl border border-blue-100 p-5">
              <h3 className="text-base font-semibold text-slate-800 mb-4">Datos CLOR</h3>
              <InfoRow label="Estado CLOR" value={averia.estadoClor} />
              <InfoRow label="Causa CLOR" value={averia.causaClor} />
              <InfoRow label="Observaciones" value={averia.observacionesClor} />
            </div>
          )}
        </div>

        {/* Right column */}
        <div className="space-y-4">
          {/* Timeline */}
          <div className="bg-white rounded-xl border border-slate-100 p-5">
            <div className="flex items-center gap-2 mb-4">
              <Clock size={16} className="text-[#003087]" />
              <h3 className="text-base font-semibold text-slate-800">Cronología</h3>
            </div>
            <div className="space-y-4">
              <TimelineItem label="Inicio del incidente" millis={averia.fechaInicioMillis || averia.horaInicioMillis} icon={AlertTriangle} />
              <TimelineItem label="Hora de llegada" millis={averia.horaLlegadaMillis} icon={MapPin} />
              <TimelineItem label="Inicio de atención" millis={averia.atencionHoraInicioMillis} icon={User} />
              <TimelineItem label="Fin de atención" millis={averia.atencionHoraFinalMillis} icon={CheckCircle2} />
              <TimelineItem label="Hora final" millis={averia.horaFinalMillis} icon={CheckCircle2} />
              {averia.fechaAsignacion && (
                <TimelineItem label="Asignación" millis={averia.fechaAsignacion} icon={Truck} />
              )}
            </div>
          </div>

          {/* Asignación */}
          <div className="bg-white rounded-xl border border-slate-100 p-5">
            <div className="flex items-center gap-2 mb-4">
              <User size={16} className="text-[#003087]" />
              <h3 className="text-base font-semibold text-slate-800">Asignación</h3>
            </div>
            <InfoRow label="Vehículo" value={averia.vehiculoAsignado} mono />
            <InfoRow label="Técnico Asignado" value={averia.tecnicoAsignadoNombre} />
            <InfoRow label="Atendido Por" value={averia.atendidoPorNombre} />
          </div>

          {/* Kilometraje */}
          {(averia.kilometrajeInicio > 0 || averia.kilometrajeLlegada > 0) && (
            <div className="bg-white rounded-xl border border-slate-100 p-5">
              <div className="flex items-center gap-2 mb-4">
                <Truck size={16} className="text-[#003087]" />
                <h3 className="text-base font-semibold text-slate-800">Kilometraje</h3>
              </div>
              <InfoRow label="Inicio" value={averia.kilometrajeInicio > 0 ? `${Number(averia.kilometrajeInicio).toLocaleString()} km` : null} />
              <InfoRow label="Llegada" value={averia.kilometrajeLlegada > 0 ? `${Number(averia.kilometrajeLlegada).toLocaleString()} km` : null} />
              <InfoRow label="Final" value={averia.kilometrajeFinal > 0 ? `${Number(averia.kilometrajeFinal).toLocaleString()} km` : null} />
              {averia.kilometrajeInicio > 0 && averia.kilometrajeFinal > 0 && (
                <InfoRow
                  label="Distancia recorrida"
                  value={`${(averia.kilometrajeFinal - averia.kilometrajeInicio).toLocaleString()} km`}
                />
              )}
            </div>
          )}

          {/* Materiales */}
          {materiales.length > 0 && (
            <div className="bg-white rounded-xl border border-slate-100 p-5">
              <div className="flex items-center gap-2 mb-4">
                <Package size={16} className="text-[#003087]" />
                <h3 className="text-base font-semibold text-slate-800">Materiales Utilizados</h3>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-xs">
                  <thead>
                    <tr className="border-b border-slate-100">
                      <th className="pb-2 text-left text-slate-500 font-semibold">Código</th>
                      <th className="pb-2 text-left text-slate-500 font-semibold">Material</th>
                      <th className="pb-2 text-right text-slate-500 font-semibold">Cantidad</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50">
                    {materiales.map((m: any, i: number) => (
                      <tr key={i}>
                        <td className="py-1.5 text-slate-400 font-mono">{m.codigo || '—'}</td>
                        <td className="py-1.5 text-slate-700">{m.descripcion || m.material || m.name || JSON.stringify(m)}</td>
                        <td className="py-1.5 text-slate-600 text-right font-medium">{m.cantidad || m.quantity || 1}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Técnicos que atendieron */}
          {tecnicos.length > 0 && (
            <div className="bg-white rounded-xl border border-slate-100 p-5">
              <h3 className="text-base font-semibold text-slate-800 mb-3">Técnicos que Atendieron</h3>
              <div className="space-y-2">
                {tecnicos.map((t: any, i: number) => (
                  <div key={i} className="flex items-center gap-2">
                    <div className="w-7 h-7 bg-[#003087]/10 rounded-full flex items-center justify-center">
                      <User size={12} className="text-[#003087]" />
                    </div>
                    <div>
                      <span className="text-sm text-slate-700">{t.nombre || t.name || '—'}</span>
                      {t.cedula && <span className="text-xs text-slate-400 ml-2">{t.cedula}</span>}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Evidencias */}
          {evidencias.length > 0 && (
            <div className="bg-white rounded-xl border border-slate-100 p-5">
              <div className="flex items-center gap-2 mb-3">
                <Camera size={16} className="text-[#003087]" />
                <h3 className="text-base font-semibold text-slate-800">
                  Evidencias ({evidencias.length})
                </h3>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                {evidencias.map((e: any, i: number) => {
                  const url = typeof e === 'string' ? e : (e.uri || e.url)
                  return url ? (
                    <a key={i} href={url} target="_blank" rel="noopener noreferrer"
                      className="block rounded-lg overflow-hidden border border-slate-100 hover:border-[#0066CC] transition-colors">
                      <img
                        src={url}
                        alt={`Evidencia ${i + 1}`}
                        className="w-full h-24 object-cover bg-slate-50"
                        onError={(e) => {
                          const img = e.target as HTMLImageElement
                          img.src = ''
                          img.parentElement!.style.display = 'none'
                        }}
                      />
                    </a>
                  ) : null
                })}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Assignment Modal */}
      {showAssignModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setShowAssignModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-bold text-slate-800 mb-5">Asignar Avería</h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-semibold text-slate-700 mb-1.5">Vehículo</label>
                <select
                  value={assignVehiculo}
                  onChange={e => setAssignVehiculo(e.target.value)}
                  className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] bg-white"
                >
                  <option value="">Seleccionar vehículo...</option>
                  {vehiculos.map(v => (
                    <option key={v.id} value={v.placaRaw || v.placa}>
                      {v.placaRaw || v.placa} — {v.tipo}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-semibold text-slate-700 mb-1.5">Técnico</label>
                <select
                  value={assignTecnico}
                  onChange={e => setAssignTecnico(e.target.value)}
                  className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] bg-white"
                >
                  <option value="">Seleccionar técnico...</option>
                  {usuarios
                    .filter(u => u.rol === 'tecnico' || u.rol === 'supervisor')
                    .map(u => (
                      <option key={u.id} value={u.uid || u.id}>
                        {u.nombre} {u.apellidos}
                      </option>
                    ))}
                </select>
              </div>
            </div>

            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={() => setShowAssignModal(false)}
                className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors font-medium"
              >
                Cancelar
              </button>
              <button
                onClick={handleAssign}
                disabled={saving || !assignVehiculo || !assignTecnico}
                className="px-5 py-2 bg-[#003087] text-white text-sm font-semibold rounded-lg hover:bg-[#002266] transition-colors disabled:opacity-60 flex items-center gap-2"
              >
                {saving && <Spinner size="sm" className="text-white" />}
                Confirmar Asignación
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
