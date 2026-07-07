import { useState, useMemo } from 'react'
import { Lightbulb, Search, CheckCircle2, Plus, X, AlertCircle, ChevronDown, ChevronUp, Clock } from 'lucide-react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'
import { toast } from 'react-hot-toast'
import { useAuth } from '../context/AuthContext'
import {
  useLuminarias,
  marcarLuminariaReparada,
  registrarLuminariaPendiente,
  type Luminaria,
} from '../hooks/useLuminarias'
import { Spinner } from '../components/ui/Spinner'

// ─── Utilidades ──────────────────────────────────────────────────────────────

function fmtFecha(ts?: number) {
  if (!ts) return '-'
  return format(new Date(ts), "dd/MM/yy HH:mm", { locale: es })
}

// ─── Modal marcar reparada ────────────────────────────────────────────────────

interface ModalReparadaProps {
  lum: Luminaria
  defaultNombre: string
  defaultCedula: string
  onClose: () => void
  onConfirm: (nombre: string, cedula: string) => Promise<void>
}

function ModalReparada({ lum, defaultNombre, defaultCedula, onClose, onConfirm }: ModalReparadaProps) {
  const [nombre, setNombre] = useState(defaultNombre)
  const [cedula, setCedula] = useState(defaultCedula)
  const [saving, setSaving] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!nombre.trim()) { toast.error('Ingrese el nombre del ejecutor'); return }
    setSaving(true)
    await onConfirm(nombre.trim(), cedula.trim())
    setSaving(false)
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-end sm:items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-md shadow-xl">
        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
          <h3 className="font-bold text-slate-800">Confirmar Reparación</h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={18} /></button>
        </div>
        <div className="p-5">
          <div className="bg-slate-50 rounded-xl p-3 mb-4 text-sm space-y-1">
            <p><span className="text-slate-400">Localización:</span> <span className="font-mono font-semibold">{lum.localizacion || '-'}</span></p>
            {lum.cliente && <p><span className="text-slate-400">Cliente:</span> <span>{lum.cliente}</span></p>}
            {lum.vehiculoId && <p><span className="text-slate-400">Vehículo:</span> <span>{lum.vehiculoId}</span></p>}
          </div>
          <form onSubmit={handleSubmit} className="space-y-3">
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1">Ejecutor (quien reparó) *</label>
              <input
                type="text"
                value={nombre}
                onChange={e => setNombre(e.target.value)}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
                placeholder="Nombre completo"
                required
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1">Cédula</label>
              <input
                type="text"
                value={cedula}
                onChange={e => setCedula(e.target.value)}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
                placeholder="Cédula del ejecutor"
              />
            </div>
            <div className="flex gap-2 pt-1">
              <button type="button" onClick={onClose} className="flex-1 py-2.5 border border-slate-200 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-50">
                Cancelar
              </button>
              <button
                type="submit"
                disabled={saving}
                className="flex-1 py-2.5 bg-green-600 text-white rounded-xl text-sm font-semibold hover:bg-green-700 disabled:opacity-60 flex items-center justify-center gap-2"
              >
                {saving ? <Spinner size="sm" className="text-white" /> : <CheckCircle2 size={16} />}
                Marcar Reparada
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

// ─── Modal registrar pendiente ────────────────────────────────────────────────

interface ModalRegistrarProps {
  agenciaKey: string
  vehiculoIds: string[]
  onClose: () => void
  onRegistrado: () => void
}

function ModalRegistrar({ agenciaKey, vehiculoIds, onClose, onRegistrado }: ModalRegistrarProps) {
  const { user } = useAuth()
  const [form, setForm] = useState({
    localizacion: '',
    vehiculoId: vehiculoIds[0] || '',
    cliente: '',
    contacto: '',
    observaciones: '',
    ejecutorNombre: `${user?.nombre || ''} ${user?.apellidos || ''}`.trim(),
    ejecutorCedula: user?.cedula || '',
  })
  const [saving, setSaving] = useState(false)

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    const loc = form.localizacion.replace(/\D/g, '').slice(0, 12)
    if (!loc) { toast.error('Ingrese una localización válida'); return }
    if (!form.vehiculoId) { toast.error('Seleccione un vehículo'); return }
    setSaving(true)
    try {
      await registrarLuminariaPendiente(agenciaKey, { ...form, localizacion: loc })
      toast.success('Luminaria pendiente registrada')
      onRegistrado()
      onClose()
    } catch {
      toast.error('Error al registrar luminaria')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-end sm:items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-md shadow-xl max-h-[90vh] overflow-y-auto">
        <div className="p-5 border-b border-slate-100 flex items-center justify-between sticky top-0 bg-white">
          <h3 className="font-bold text-slate-800">Registrar Luminaria Pendiente</h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={18} /></button>
        </div>
        <form onSubmit={handleSubmit} className="p-5 space-y-3">
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Localización (12 dígitos) *</label>
            <input
              name="localizacion"
              type="text"
              inputMode="numeric"
              value={form.localizacion}
              onChange={handleChange}
              maxLength={12}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-sm font-mono focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
              placeholder="000000000000"
              required
            />
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Vehículo *</label>
            <select
              name="vehiculoId"
              value={form.vehiculoId}
              onChange={handleChange}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] bg-white"
              required
            >
              <option value="">Seleccionar vehículo</option>
              {vehiculoIds.map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Cliente</label>
            <input name="cliente" type="text" value={form.cliente} onChange={handleChange}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
              placeholder="Nombre del cliente" />
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Contacto</label>
            <input name="contacto" type="text" value={form.contacto} onChange={handleChange}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
              placeholder="Teléfono u otro contacto" />
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Observaciones</label>
            <textarea name="observaciones" value={form.observaciones} onChange={handleChange} rows={2}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] resize-none"
              placeholder="Descripción de la avería..." />
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Registrado por</label>
            <input name="ejecutorNombre" type="text" value={form.ejecutorNombre} onChange={handleChange}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
              placeholder="Nombre del responsable" />
          </div>
          <div className="flex gap-2 pt-1">
            <button type="button" onClick={onClose} className="flex-1 py-2.5 border border-slate-200 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-50">
              Cancelar
            </button>
            <button type="submit" disabled={saving}
              className="flex-1 py-2.5 bg-[#003087] text-white rounded-xl text-sm font-semibold hover:bg-[#002070] disabled:opacity-60 flex items-center justify-center gap-2">
              {saving ? <Spinner size="sm" className="text-white" /> : <Plus size={16} />}
              Registrar
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Tarjeta de luminaria ─────────────────────────────────────────────────────

interface LuminariaCardProps {
  lum: Luminaria
  onMarcarReparada?: (lum: Luminaria) => void
}

function LuminariaCard({ lum, onMarcarReparada }: LuminariaCardProps) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className={`bg-white rounded-xl border shadow-sm overflow-hidden transition-all ${lum.estado === 'PENDIENTE' ? 'border-amber-200' : 'border-green-200'}`}>
      {/* Stripe */}
      <div className={`h-1 ${lum.estado === 'PENDIENTE' ? 'bg-amber-400' : 'bg-green-400'}`} />

      <div className="p-4">
        {/* Top row */}
        <div className="flex items-start justify-between gap-2 mb-2">
          <div>
            <p className="font-mono font-bold text-[#003087] text-base leading-tight">
              {lum.localizacion || 'Sin localización'}
            </p>
            {lum.cliente && <p className="text-sm text-slate-600 mt-0.5">{lum.cliente}</p>}
          </div>
          <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border flex-shrink-0 ${
            lum.estado === 'PENDIENTE'
              ? 'bg-amber-50 text-amber-700 border-amber-200'
              : 'bg-green-50 text-green-700 border-green-200'
          }`}>
            {lum.estado === 'PENDIENTE' ? 'Pendiente' : 'Reparada'}
          </span>
        </div>

        {/* Info row */}
        <div className="flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-slate-400 mb-3">
          {lum.vehiculoId && <span>Vehículo: <span className="font-medium text-slate-600">{lum.vehiculoId}</span></span>}
          {lum.fechaRegistro && <span><Clock size={10} className="inline mr-0.5" />{fmtFecha(lum.fechaRegistro)}</span>}
          {lum.estado === 'REPARADA' && lum.ejecutorNombre && (
            <span>Ejecutor: <span className="font-medium text-slate-600">{lum.ejecutorNombre}</span></span>
          )}
          {lum.estado === 'REPARADA' && lum.fechaReparacion && (
            <span>Reparada: <span className="font-medium text-green-600">{fmtFecha(lum.fechaReparacion)}</span></span>
          )}
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2">
          {onMarcarReparada && lum.estado === 'PENDIENTE' && (
            <button
              onClick={() => onMarcarReparada(lum)}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-green-600 text-white text-xs font-semibold rounded-lg hover:bg-green-700 transition-colors"
            >
              <CheckCircle2 size={13} /> Marcar Reparada
            </button>
          )}
          <button
            onClick={() => setExpanded(e => !e)}
            className="flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600 ml-auto"
          >
            {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
            {expanded ? 'Menos' : 'Más'}
          </button>
        </div>

        {/* Expanded detail */}
        {expanded && (
          <div className="mt-3 pt-3 border-t border-slate-100 space-y-1.5 text-sm">
            {lum.contacto && <p><span className="text-xs text-slate-400">Contacto:</span> {lum.contacto}</p>}
            {lum.observaciones && <p><span className="text-xs text-slate-400">Observaciones:</span> {lum.observaciones}</p>}
            {lum.ejecutorCedula && <p><span className="text-xs text-slate-400">Cédula ejecutor:</span> {lum.ejecutorCedula}</p>}
            {lum.materialesJson && (() => {
              try {
                const mats = JSON.parse(lum.materialesJson)
                if (!Array.isArray(mats) || mats.length === 0) return null
                return (
                  <div>
                    <p className="text-xs text-slate-400 mb-1">Materiales:</p>
                    <div className="space-y-0.5">
                      {mats.map((m: any, i: number) => (
                        <div key={i} className="flex justify-between text-xs text-slate-600 py-0.5 border-b border-slate-50">
                          <span>{m.descripcion || m.material || JSON.stringify(m)}</span>
                          <span className="font-medium">{m.cantidad ?? 1}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )
              } catch { return null }
            })()}
          </div>
        )}
      </div>
    </div>
  )
}

// ─── Página principal ─────────────────────────────────────────────────────────

export default function Luminarias() {
  const { user } = useAuth()
  const [vehiculoFiltro, setVehiculoFiltro] = useState('')
  const [search, setSearch] = useState('')
  const [tab, setTab] = useState<'pendientes' | 'reparadas'>('pendientes')
  const [reparandoLum, setReparandoLum] = useState<Luminaria | null>(null)
  const [showRegistrar, setShowRegistrar] = useState(false)

  const { pendientes, reparadas, todosVehiculoIds, loading, error, agenciaKey, permisos } =
    useLuminarias(vehiculoFiltro || undefined)

  const lista = tab === 'pendientes' ? pendientes : reparadas

  const filtradas = useMemo(() => {
    if (!search.trim()) return lista
    const q = search.toLowerCase()
    return lista.filter(l =>
      l.localizacion?.toLowerCase().includes(q) ||
      l.cliente?.toLowerCase().includes(q) ||
      l.vehiculoId?.toLowerCase().includes(q) ||
      l.ejecutorNombre?.toLowerCase().includes(q) ||
      l.observaciones?.toLowerCase().includes(q)
    )
  }, [lista, search])

  const handleConfirmarReparada = async (nombre: string, cedula: string) => {
    if (!reparandoLum) return
    try {
      await marcarLuminariaReparada(reparandoLum, nombre, cedula)
      toast.success('Luminaria marcada como reparada')
      setReparandoLum(null)
    } catch {
      toast.error('Error al actualizar luminaria')
    }
  }

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-3">
        <Spinner size="lg" className="text-[#003087]" />
        <p className="text-slate-500 text-sm">Cargando luminarias de la agencia...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="max-w-lg mx-auto mt-10">
        <div className="bg-red-50 border border-red-100 rounded-2xl p-6 flex items-start gap-3">
          <AlertCircle size={20} className="text-red-500 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold text-red-700">No se pudieron cargar las luminarias</p>
            <p className="text-red-600 text-sm mt-1">{error}</p>
            {!agenciaKey && (
              <p className="text-slate-500 text-xs mt-2">El usuario no tiene una agencia asignada en su perfil.</p>
            )}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">Luminarias</h2>
          <p className="text-slate-500 text-sm mt-0.5">
            Agencia: <span className="font-semibold text-[#003087]">{agenciaKey || 'Sin agencia'}</span>
            {' · '}
            {permisos.esGestor ? 'Vista gestión' : `Vehículo: ${user?.placaVehiculo || 'sin asignar'}`}
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-amber-50 text-amber-700 text-sm font-semibold rounded-full border border-amber-200">
            <Lightbulb size={14} /> {pendientes.length} pendientes
          </span>
          <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-green-50 text-green-700 text-sm font-semibold rounded-full border border-green-200">
            <CheckCircle2 size={14} /> {reparadas.length} reparadas
          </span>
          {permisos.puedeRegistrar && (
            <button
              onClick={() => setShowRegistrar(true)}
              className="flex items-center gap-1.5 px-4 py-2 bg-[#003087] text-white text-sm font-semibold rounded-xl hover:bg-[#002070] transition-colors"
            >
              <Plus size={16} /> Registrar Pendiente
            </button>
          )}
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl border border-slate-100 p-4">
        <div className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Buscar localización, cliente, vehículo..."
              className="w-full pl-9 pr-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
            />
          </div>
          {permisos.puedeFiltrarVehiculo && todosVehiculoIds.length > 0 && (
            <select
              value={vehiculoFiltro}
              onChange={e => setVehiculoFiltro(e.target.value)}
              className="px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] bg-white"
            >
              <option value="">Todos los vehículos</option>
              {todosVehiculoIds.map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-200 gap-1">
        {([['pendientes', 'Pendientes', pendientes.length, 'amber'],
           ['reparadas', 'Reparadas', reparadas.length, 'green']] as const).map(([key, label, count, color]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border-b-2 transition-colors ${
              tab === key
                ? color === 'amber'
                  ? 'border-amber-500 text-amber-700'
                  : 'border-green-500 text-green-700'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {label}
            <span className={`text-xs px-1.5 py-0.5 rounded-full font-bold ${
              tab === key
                ? color === 'amber' ? 'bg-amber-100 text-amber-700' : 'bg-green-100 text-green-700'
                : 'bg-slate-100 text-slate-500'
            }`}>
              {count}
            </span>
          </button>
        ))}
      </div>

      {/* List */}
      {filtradas.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-100 p-14 text-center">
          <Lightbulb size={36} className="text-slate-200 mx-auto mb-3" />
          <p className="text-slate-500 font-medium">
            {search ? 'Sin resultados para la búsqueda' : `No hay luminarias ${tab === 'pendientes' ? 'pendientes' : 'reparadas'}`}
          </p>
          {!permisos.esGestor && !user?.placaVehiculo && (
            <p className="text-slate-400 text-xs mt-1">El usuario no tiene vehículo asignado.</p>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
          {filtradas.map(lum => (
            <LuminariaCard
              key={lum.id}
              lum={lum}
              onMarcarReparada={permisos.puedeMarcarReparada ? setReparandoLum : undefined}
            />
          ))}
        </div>
      )}

      {/* Modal: Marcar Reparada */}
      {reparandoLum && (
        <ModalReparada
          lum={reparandoLum}
          defaultNombre={`${user?.nombre || ''} ${user?.apellidos || ''}`.trim()}
          defaultCedula={user?.cedula || ''}
          onClose={() => setReparandoLum(null)}
          onConfirm={handleConfirmarReparada}
        />
      )}

      {/* Modal: Registrar Pendiente */}
      {showRegistrar && (
        <ModalRegistrar
          agenciaKey={agenciaKey}
          vehiculoIds={todosVehiculoIds}
          onClose={() => setShowRegistrar(false)}
          onRegistrado={() => {}}
        />
      )}
    </div>
  )
}
