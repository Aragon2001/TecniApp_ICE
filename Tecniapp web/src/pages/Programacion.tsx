import { useState, useMemo } from 'react'
import { Calendar, Search, Filter, MapPin, CheckCircle2, Trash2, X, Plus } from 'lucide-react'
import { useProgramacion, deleteProgramacion, completarProgramacion, createProgramacion, type Programacion } from '../hooks/useProgramacion'
import { useAuth } from '../context/AuthContext'
import { formatDate } from '../utils/dateUtils'
import { Spinner } from '../components/ui/Spinner'
import toast from 'react-hot-toast'

const ESTADO_BADGE: Record<string, string> = {
  PENDIENTE: 'bg-amber-100 text-amber-700',
  EN_PROCESO: 'bg-blue-100 text-blue-700',
  COMPLETADA: 'bg-green-100 text-green-700',
  CANCELADA: 'bg-gray-100 text-gray-500',
}

interface ModalNuevaProps {
  onClose: () => void
}

function ModalNueva({ onClose }: ModalNuevaProps) {
  const { user } = useAuth()
  const [form, setForm] = useState({
    subregion: user?.subregion ?? '',
    vehiculoId: user?.placaVehiculo ?? '',
    actividad: '',
    localizacion: '',
    descripcion: '',
    fecha: new Date().toISOString().slice(0, 10),
    estado: 'PENDIENTE',
  })
  const [saving, setSaving] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.subregion || !form.vehiculoId) {
      toast.error('Subregión y vehículo son requeridos')
      return
    }
    setSaving(true)
    try {
      await createProgramacion(form.subregion, form.vehiculoId, {
        actividad: form.actividad,
        localizacion: form.localizacion,
        descripcion: form.descripcion,
        fecha: form.fecha,
        estado: form.estado,
        tecnicoNombre: `${user?.nombre ?? ''} ${user?.apellidos ?? ''}`.trim(),
        tecnicoUid: user?.uid ?? '',
      })
      toast.success('Programación registrada')
      onClose()
    } catch {
      toast.error('Error al crear programación')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-md shadow-xl max-h-[90vh] overflow-y-auto">
        <div className="p-5 border-b border-slate-100 flex items-center justify-between sticky top-0 bg-white">
          <h3 className="font-bold text-slate-800">Nueva Programación</h3>
          <button onClick={onClose}><X size={18} className="text-slate-400" /></button>
        </div>
        <form onSubmit={handleSubmit} className="p-5 space-y-3">
          {[
            { key: 'subregion', label: 'Subregión *' },
            { key: 'vehiculoId', label: 'Vehículo *' },
            { key: 'actividad', label: 'Actividad' },
            { key: 'localizacion', label: 'Localización' },
          ].map(({ key, label }) => (
            <div key={key}>
              <label className="text-xs font-semibold text-slate-600 block mb-1">{label}</label>
              <input
                type="text"
                value={(form as any)[key]}
                onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
              />
            </div>
          ))}
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Descripción</label>
            <textarea
              value={form.descripcion}
              onChange={e => setForm(f => ({ ...f, descripcion: e.target.value }))}
              rows={2}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] resize-none"
            />
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Fecha</label>
            <input
              type="date"
              value={form.fecha}
              onChange={e => setForm(f => ({ ...f, fecha: e.target.value }))}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
            />
          </div>
          <div className="flex gap-2 pt-1">
            <button type="button" onClick={onClose} className="flex-1 py-2.5 border border-slate-200 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-50">
              Cancelar
            </button>
            <button
              type="submit"
              disabled={saving}
              className="flex-1 py-2.5 bg-[#003087] text-white rounded-xl text-sm font-semibold hover:bg-[#002070] disabled:opacity-60 flex items-center justify-center gap-2"
            >
              {saving ? <Spinner size="sm" className="text-white" /> : <Plus size={16} />}
              Crear
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function Programacion() {
  const { user } = useAuth()
  const esGestor = user?.rol === 'supervisor' || user?.rol === 'admin'
  const { programaciones, loading, error } = useProgramacion()
  const [search, setSearch] = useState('')
  const [estadoFilter, setEstadoFilter] = useState('')
  const [subregionFilter, setSubregionFilter] = useState('')
  const [showNueva, setShowNueva] = useState(false)
  const [saving, setSaving] = useState<string | null>(null)

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
        String(p.descripcion ?? '').toLowerCase().includes(q) ||
        String(p.vehiculoId ?? '').toLowerCase().includes(q)
      )
    }
    return list
  }, [programaciones, estadoFilter, subregionFilter, search])

  async function handleCompletar(prog: Programacion) {
    setSaving(prog.id)
    try {
      await completarProgramacion(prog)
      toast.success('Programación completada')
    } catch {
      toast.error('Error al completar')
    } finally {
      setSaving(null)
    }
  }

  async function handleEliminar(prog: Programacion) {
    if (!confirm('¿Eliminar esta programación?')) return
    setSaving(prog.id)
    try {
      await deleteProgramacion(prog)
      toast.success('Programación eliminada')
    } catch {
      toast.error('Error al eliminar')
    } finally {
      setSaving(null)
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between flex-wrap gap-3">
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
        <button
          onClick={() => setShowNueva(true)}
          className="flex items-center gap-1.5 px-4 py-2 bg-[#003087] text-white text-sm font-semibold rounded-xl hover:bg-[#002070] transition-colors"
        >
          <Plus size={16} /> Nueva Programación
        </button>
      </div>

      <div className="bg-white rounded-xl shadow-card p-4 flex flex-wrap gap-3">
        <div className="flex-1 min-w-[200px] relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="Buscar por actividad, localización, vehículo..."
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
                  {['Actividad', 'Localización', 'Vehículo', 'Subregión', 'Estado', 'Fecha', 'Técnico', 'Acciones'].map((h) => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {filtered.map((prog: any) => (
                  <tr key={prog.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-sm text-gray-800 font-medium max-w-[150px] truncate">
                      {prog.actividad ?? prog.descripcion ?? '—'}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600 max-w-[140px] truncate">
                      <div className="flex items-center gap-1">
                        {prog.localizacion && <MapPin size={11} className="text-gray-400 flex-shrink-0" />}
                        {prog.localizacion ?? '—'}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600 font-mono">{prog.vehiculoId ?? '—'}</td>
                    <td className="px-4 py-3 text-xs text-gray-500">{prog.subregion ?? '—'}</td>
                    <td className="px-4 py-3">
                      {prog.estado && (
                        <span className={`inline-block text-xs font-semibold px-2.5 py-0.5 rounded-full ${ESTADO_BADGE[prog.estado] ?? 'bg-gray-100 text-gray-500'}`}>
                          {prog.estado}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">
                      {prog.fecha ?? formatDate(prog.fechaAsignacion)}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500 max-w-[120px] truncate">
                      {prog.tecnicoNombre ?? '—'}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1">
                        {prog.estado !== 'COMPLETADA' && prog.estado !== 'CANCELADA' && (
                          <button
                            onClick={() => handleCompletar(prog)}
                            disabled={saving === prog.id}
                            className="p-1.5 text-green-600 hover:bg-green-50 rounded transition-colors"
                            title="Marcar completada"
                          >
                            {saving === prog.id ? <Spinner size="sm" /> : <CheckCircle2 size={14} />}
                          </button>
                        )}
                        {esGestor && (
                          <button
                            onClick={() => handleEliminar(prog)}
                            disabled={saving === prog.id}
                            className="p-1.5 text-red-500 hover:bg-red-50 rounded transition-colors"
                            title="Eliminar"
                          >
                            <Trash2 size={14} />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showNueva && <ModalNueva onClose={() => setShowNueva(false)} />}
    </div>
  )
}
