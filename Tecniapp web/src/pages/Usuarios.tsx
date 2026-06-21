import { useState, useMemo } from 'react'
import { Users, Search, Shield, Edit2, X, Save } from 'lucide-react'
import { ref, set } from 'firebase/database'
import { rtdbUsers } from '../firebase/config'
import { useUsuarios, type Usuario } from '../hooks/useUsuarios'
import { emailToKey, rolLabel } from '../utils/formatUtils'
import { Spinner } from '../components/ui/Spinner'
import { useAuth } from '../context/AuthContext'
import type { UserRole } from '../types'
import toast from 'react-hot-toast'

const ROL_BADGE: Record<UserRole, string> = {
  admin: 'bg-red-100 text-red-700',
  supervisor: 'bg-purple-100 text-purple-700',
  tecnico: 'bg-blue-100 text-blue-700',
}

interface EditForm {
  rol: UserRole
  agencia: string
  vehiculoId: string
  nombre: string
}

export default function Usuarios() {
  const { isAdmin } = useAuth()
  const { usuarios, loading, error } = useUsuarios()
  const [search, setSearch] = useState('')
  const [rolFilter, setRolFilter] = useState<UserRole | ''>('')
  const [editUser, setEditUser] = useState<Usuario | null>(null)
  const [form, setForm] = useState<EditForm>({ rol: 'tecnico', agencia: '', vehiculoId: '', nombre: '' })
  const [saving, setSaving] = useState(false)

  const filtered = useMemo(() => {
    let list = usuarios
    if (rolFilter) list = list.filter(u => u.rol === rolFilter)
    if (search.trim()) {
      const q = search.toLowerCase()
      list = list.filter(u =>
        u.email?.toLowerCase().includes(q) ||
        u.nombre?.toLowerCase().includes(q) ||
        u.agencia?.toLowerCase().includes(q)
      )
    }
    return list
  }, [usuarios, rolFilter, search])

  function openEdit(u: Usuario) {
    setEditUser(u)
    setForm({
      rol: (u.rol as UserRole) ?? 'tecnico',
      agencia: u.agencia ?? '',
      vehiculoId: u.vehiculoId ?? '',
      nombre: u.nombre ?? '',
    })
  }

  async function handleSave() {
    if (!editUser || !isAdmin) return
    setSaving(true)
    try {
      const key = emailToKey(editUser.email ?? '')
      await set(ref(rtdbUsers, `users/${key}/rol`), form.rol)
      await set(ref(rtdbUsers, `users/${key}/agencia`), form.agencia)
      await set(ref(rtdbUsers, `users/${key}/vehiculoId`), form.vehiculoId)
      await set(ref(rtdbUsers, `users/${key}/nombre`), form.nombre)
      toast.success('Usuario actualizado correctamente')
      setEditUser(null)
    } catch (e) {
      toast.error('Error al guardar cambios')
    } finally {
      setSaving(false)
    }
  }

  const rolCounts = useMemo(() => {
    const c: Record<string, number> = {}
    usuarios.forEach(u => { c[u.rol ?? 'tecnico'] = (c[u.rol ?? 'tecnico'] || 0) + 1 })
    return c
  }, [usuarios])

  return (
    <div className="space-y-5">
      <div>
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-gray-900">Usuarios</h1>
          {!loading && (
            <span className="bg-[#003087] text-white text-xs font-bold px-2.5 py-1 rounded-full">
              {usuarios.length}
            </span>
          )}
        </div>
        <p className="text-sm text-gray-500 mt-0.5">Gestión de técnicos, supervisores y administradores</p>
      </div>

      {/* Rol summary pills */}
      {!loading && (
        <div className="flex flex-wrap gap-2">
          {(['admin', 'supervisor', 'tecnico'] as UserRole[]).map(rol => (
            <button
              key={rol}
              onClick={() => setRolFilter(rolFilter === rol ? '' : rol)}
              className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border transition-all ${
                rolFilter === rol ? 'ring-2 ring-offset-1 ring-current' : ''
              } ${ROL_BADGE[rol]} border-transparent`}
            >
              <Shield size={11} />
              {rolLabel(rol)}
              <span className="bg-white/60 px-1.5 py-0.5 rounded-full">{rolCounts[rol] ?? 0}</span>
            </button>
          ))}
        </div>
      )}

      {/* Filters */}
      <div className="bg-white rounded-xl shadow-card p-4 flex flex-wrap gap-3">
        <div className="flex-1 min-w-[200px] relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="Buscar por nombre, correo o agencia..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-full pl-9 pr-4 py-2.5 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087]"
          />
        </div>
        <select
          value={rolFilter}
          onChange={e => setRolFilter(e.target.value as UserRole | '')}
          className="text-sm border border-gray-200 rounded-lg px-3 py-2.5 focus:outline-none focus:ring-2 focus:ring-[#003087] bg-white text-gray-700"
        >
          <option value="">Todos los roles</option>
          {(['admin', 'supervisor', 'tecnico'] as UserRole[]).map(r => (
            <option key={r} value={r}>{rolLabel(r)}</option>
          ))}
        </select>
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      ) : error ? (
        <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-center text-red-600 text-sm">{error}</div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-xl shadow-card p-12 text-center">
          <Users size={40} className="text-gray-200 mx-auto mb-3" />
          <p className="text-gray-500">Sin usuarios encontrados</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-card overflow-hidden">
          <div className="px-5 py-3 border-b border-gray-100">
            <span className="text-sm text-gray-500">
              <strong className="text-gray-800">{filtered.length}</strong> usuarios
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full">
              <thead className="bg-gray-50">
                <tr>
                  {['Nombre', 'Correo electrónico', 'Rol', 'Agencia', 'Vehículo', ...(isAdmin ? ['Acciones'] : [])].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {filtered.map(u => (
                  <tr key={u.email} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-sm font-medium text-gray-900">
                      <div className="flex items-center gap-2.5">
                        <div className="w-8 h-8 rounded-full bg-[#003087] text-white flex items-center justify-center text-xs font-bold flex-shrink-0">
                          {(u.nombre ?? u.email ?? '?').charAt(0).toUpperCase()}
                        </div>
                        <span className="truncate max-w-[150px]">{u.nombre ?? '—'}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600 font-mono text-xs">{u.email}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-block text-xs px-2.5 py-0.5 rounded-full font-semibold ${ROL_BADGE[(u.rol as UserRole) ?? 'tecnico']}`}>
                        {rolLabel(u.rol ?? 'tecnico')}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">{u.agencia ?? '—'}</td>
                    <td className="px-4 py-3 text-sm font-mono text-gray-500">{u.vehiculoId ?? '—'}</td>
                    {isAdmin && (
                      <td className="px-4 py-3">
                        <button
                          onClick={() => openEdit(u)}
                          className="p-1.5 text-[#0066CC] hover:bg-blue-50 rounded-lg transition-colors"
                          title="Editar usuario"
                        >
                          <Edit2 size={14} />
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Edit modal */}
      {editUser && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
              <h2 className="font-semibold text-gray-900">Editar Usuario</h2>
              <button onClick={() => setEditUser(null)} className="p-1.5 hover:bg-gray-100 rounded-lg">
                <X size={16} className="text-gray-500" />
              </button>
            </div>
            <div className="px-6 py-5 space-y-4">
              <div>
                <p className="text-xs text-gray-500 mb-1">Correo</p>
                <p className="text-sm font-mono text-gray-800 bg-gray-50 px-3 py-2 rounded-lg">{editUser.email}</p>
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600 block mb-1">Nombre</label>
                <input
                  type="text"
                  value={form.nombre}
                  onChange={e => setForm(f => ({ ...f, nombre: e.target.value }))}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]"
                />
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600 block mb-1">Rol</label>
                <select
                  value={form.rol}
                  onChange={e => setForm(f => ({ ...f, rol: e.target.value as UserRole }))}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#003087] bg-white"
                >
                  {(['admin', 'supervisor', 'tecnico'] as UserRole[]).map(r => (
                    <option key={r} value={r}>{rolLabel(r)}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600 block mb-1">Agencia</label>
                <input
                  type="text"
                  value={form.agencia}
                  onChange={e => setForm(f => ({ ...f, agencia: e.target.value }))}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]"
                />
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600 block mb-1">Vehículo asignado</label>
                <input
                  type="text"
                  value={form.vehiculoId}
                  onChange={e => setForm(f => ({ ...f, vehiculoId: e.target.value }))}
                  placeholder="Ej. A1234B"
                  className="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]"
                />
              </div>
            </div>
            <div className="flex gap-3 px-6 py-4 border-t border-gray-100">
              <button
                onClick={() => setEditUser(null)}
                className="flex-1 px-4 py-2.5 border border-gray-200 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors"
              >
                Cancelar
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-[#003087] text-white rounded-lg text-sm font-semibold hover:bg-[#002070] transition-colors disabled:opacity-60"
              >
                {saving ? <Spinner size="sm" /> : <Save size={14} />}
                Guardar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
