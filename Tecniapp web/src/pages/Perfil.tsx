import { User, Truck, Building2, MapPin, Phone, Mail, CreditCard, Shield, Gauge, Wrench, AlertCircle } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { Spinner } from '../components/ui/Spinner'

const ROL_LABEL: Record<string, string> = {
  admin: 'Administrador',
  supervisor: 'Supervisor',
  tecnico: 'Técnico',
}

const ROL_COLOR: Record<string, string> = {
  admin: 'bg-red-100 text-red-700 border-red-200',
  supervisor: 'bg-purple-100 text-purple-700 border-purple-200',
  tecnico: 'bg-blue-100 text-blue-700 border-blue-200',
}

function InfoRow({ icon, label, value }: { icon: React.ReactNode; label: string; value?: string | number }) {
  if (value === undefined || value === null || value === '') return null
  return (
    <div className="flex items-start gap-3 py-3 border-b border-slate-100 last:border-0">
      <div className="w-8 h-8 bg-slate-100 rounded-lg flex items-center justify-center flex-shrink-0 text-slate-500">
        {icon}
      </div>
      <div>
        <p className="text-xs text-slate-400 leading-tight">{label}</p>
        <p className="text-sm font-semibold text-slate-800 mt-0.5">{value}</p>
      </div>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      <div className="px-5 py-3 bg-slate-50 border-b border-slate-100">
        <h3 className="text-sm font-bold text-slate-700 uppercase tracking-wide">{title}</h3>
      </div>
      <div className="px-5">{children}</div>
    </div>
  )
}

export default function Perfil() {
  const { user, vehiculo, firebaseUser, profileLoading } = useAuth()

  // Still fetching profile from RTDB
  if (profileLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-3">
        <Spinner size="lg" className="text-[#003087]" />
        <p className="text-slate-500 text-sm">Cargando perfil...</p>
      </div>
    )
  }

  // Auth succeeded but RTDB profile not found — show what Firebase Auth knows
  if (!user && firebaseUser) {
    return (
      <div className="max-w-2xl mx-auto space-y-5">
        <div className="bg-amber-50 border border-amber-200 rounded-2xl p-5 flex items-start gap-3">
          <AlertCircle size={20} className="text-amber-500 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold text-amber-800">Perfil no encontrado en la base de datos</p>
            <p className="text-amber-700 text-sm mt-1">
              El usuario <strong>{firebaseUser.email}</strong> está autenticado pero no tiene un perfil completo en Firebase.
              Contacte al administrador para asignar agencia, región y vehículo.
            </p>
          </div>
        </div>
        <Section title="Datos de Autenticación">
          <InfoRow icon={<Mail size={16} />} label="Correo electrónico" value={firebaseUser.email ?? ''} />
          <InfoRow icon={<Shield size={16} />} label="UID Firebase" value={firebaseUser.uid} />
        </Section>
      </div>
    )
  }

  if (!user) return null

  const nombreCompleto = [user.nombre, user.apellidos].filter(Boolean).join(' ').trim() || 'Sin nombre'
  const rolLabel = ROL_LABEL[user.rol] ?? user.rol
  const rolColor = ROL_COLOR[user.rol] ?? ROL_COLOR['tecnico']

  return (
    <div className="max-w-2xl mx-auto space-y-5">
      {/* Hero card */}
      <div className="bg-[#003087] rounded-2xl p-6 text-white shadow-lg">
        <div className="flex items-center gap-4">
          <div className="w-16 h-16 bg-white/20 rounded-2xl flex items-center justify-center flex-shrink-0">
            <User size={32} className="text-white" />
          </div>
          <div className="flex-1 min-w-0">
            <h2 className="text-xl font-bold leading-tight truncate">{nombreCompleto}</h2>
            <p className="text-white/70 text-sm mt-0.5 truncate">{user.email}</p>
            <div className="mt-2 flex items-center gap-2 flex-wrap">
              <span className={`inline-flex items-center gap-1 text-xs font-bold px-2.5 py-1 rounded-full border ${rolColor}`}>
                <Shield size={11} /> {rolLabel}
              </span>
              {user.agencia && (
                <span className="text-xs bg-white/20 text-white px-2.5 py-1 rounded-full font-semibold">
                  {user.agencia}
                </span>
              )}
              {!user.agencia && (
                <span className="text-xs bg-amber-400/30 text-amber-200 px-2.5 py-1 rounded-full font-semibold">
                  Sin agencia asignada
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {/* Personal */}
        <Section title="Datos Personales">
          <InfoRow icon={<User size={16} />} label="Nombre completo" value={nombreCompleto} />
          <InfoRow icon={<CreditCard size={16} />} label="Cédula" value={user.cedula} />
          <InfoRow icon={<Phone size={16} />} label="Teléfono" value={user.telefono} />
          <InfoRow icon={<Mail size={16} />} label="Correo" value={user.email} />
          <InfoRow icon={<Shield size={16} />} label="Rol" value={rolLabel} />
        </Section>

        {/* Location */}
        <Section title="Región y Agencia">
          <InfoRow icon={<MapPin size={16} />} label="Región" value={user.regionNombre || user.region} />
          <InfoRow icon={<MapPin size={16} />} label="Subregión" value={user.subregionNombre || user.subregion} />
          <InfoRow icon={<Building2 size={16} />} label="Agencia" value={user.agencia} />
          <InfoRow icon={<Building2 size={16} />} label="ID Agencia" value={user.agenciaId} />
        </Section>
      </div>

      {/* Vehicle */}
      <Section title="Vehículo Asignado">
        {vehiculo ? (
          <>
            <InfoRow icon={<Truck size={16} />} label="Placa" value={vehiculo.placa} />
            <InfoRow icon={<Truck size={16} />} label="Tipo" value={vehiculo.tipo} />
            <InfoRow icon={<Wrench size={16} />} label="Marca" value={vehiculo.marca} />
            <InfoRow icon={<Wrench size={16} />} label="Modelo" value={vehiculo.modelo} />
            <InfoRow icon={<Wrench size={16} />} label="Año" value={vehiculo.anio} />
            <InfoRow icon={<Gauge size={16} />} label="Kilómetros actuales" value={vehiculo.kmActual != null ? vehiculo.kmActual.toLocaleString('es-CR') : undefined} />
            <InfoRow icon={<Gauge size={16} />} label="Próximo mantenimiento" value={vehiculo.mantenimientoProximo != null ? vehiculo.mantenimientoProximo.toLocaleString('es-CR') + ' km' : undefined} />
            <InfoRow icon={<Building2 size={16} />} label="Agencia del vehículo" value={vehiculo.agencia} />
          </>
        ) : user.placaVehiculo ? (
          <div className="py-5 flex items-center gap-3">
            <Spinner size="sm" className="text-[#003087]" />
            <div>
              <p className="text-sm text-slate-600">Cargando datos del vehículo</p>
              <p className="text-xs text-slate-400 font-mono">Placa: {user.placaVehiculo}</p>
            </div>
          </div>
        ) : (
          <div className="py-5 text-center">
            <Truck size={28} className="text-slate-300 mx-auto mb-2" />
            <p className="text-slate-400 text-sm">Sin vehículo asignado</p>
          </div>
        )}
      </Section>

      {/* Raw data for debugging */}
      <details className="bg-slate-50 rounded-xl border border-slate-200 p-4 text-xs text-slate-500">
        <summary className="cursor-pointer font-semibold text-slate-600 select-none">
          Datos crudos del perfil (diagnóstico)
        </summary>
        <pre className="mt-3 overflow-x-auto whitespace-pre-wrap break-all leading-relaxed">
          {JSON.stringify({ usuario: user, vehiculoId: vehiculo?.vehiculoId, vehiculo }, null, 2)}
        </pre>
      </details>
    </div>
  )
}
