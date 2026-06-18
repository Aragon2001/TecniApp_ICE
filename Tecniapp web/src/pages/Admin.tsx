import React, { useState, useEffect } from 'react'
import { Settings, Database, RefreshCw, CheckCircle2, AlertTriangle, Wifi, WifiOff, Info, Activity } from 'lucide-react'
import { ref as rtdbRef, get, onValue, off } from 'firebase/database'
import { collection, getCountFromServer } from 'firebase/firestore'
import {
  rtdbAverias, rtdbUsers, rtdbGeneral, rtdbMain,
  rtdbInventario, rtdbLuminarias, rtdbProgramacion, rtdbMateriales, db
} from '../firebase/config'
import { Spinner } from '../components/ui/Spinner'
import { useAuth } from '../context/AuthContext'

interface DbInfo {
  label: string
  key: string
  path: string
  db: any
  count: number | null
  status: 'ok' | 'error' | 'loading'
}

const APP_VERSION = '1.0.0'
const ANDROID_VERSION = '2.7.0 (schema v27)'

function useConnectionStatus() {
  const [online, setOnline] = useState(navigator.onLine)
  useEffect(() => {
    const on = () => setOnline(true)
    const off_ = () => setOnline(false)
    window.addEventListener('online', on)
    window.addEventListener('offline', off_)
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off_) }
  }, [])
  return online
}

export default function Admin() {
  const { user, isAdmin } = useAuth()
  const online = useConnectionStatus()
  const [dbInfos, setDbInfos] = useState<DbInfo[]>([
    { label: 'Averías RTDB', key: 'averias', path: '/averias', db: rtdbAverias, count: null, status: 'loading' },
    { label: 'Usuarios RTDB', key: 'users', path: '/users', db: rtdbUsers, count: null, status: 'loading' },
    { label: 'Vehículos RTDB', key: 'vehiculos', path: '/vehiculos', db: rtdbGeneral, count: null, status: 'loading' },
    { label: 'Medidores RTDB', key: 'medidores', path: '/medidores', db: rtdbMain, count: null, status: 'loading' },
    { label: 'Inventario RTDB', key: 'inventario', path: '/inventario', db: rtdbInventario, count: null, status: 'loading' },
    { label: 'Luminarias RTDB', key: 'luminarias', path: '/luminarias', db: rtdbLuminarias, count: null, status: 'loading' },
    { label: 'Programación RTDB', key: 'programaciones', path: '/programaciones', db: rtdbProgramacion, count: null, status: 'loading' },
    { label: 'Materiales RTDB', key: 'materiales', path: '/materiales', db: rtdbMateriales, count: null, status: 'loading' },
  ])
  const [firestoreCount, setFirestoreCount] = useState<number | null>(null)
  const [firestoreStatus, setFirestoreStatus] = useState<'loading' | 'ok' | 'error'>('loading')
  const [refreshing, setRefreshing] = useState(false)
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date())

  async function loadCounts() {
    setRefreshing(true)
    // RTDB counts
    setDbInfos(prev => prev.map(d => ({ ...d, status: 'loading', count: null })))

    await Promise.allSettled(
      dbInfos.map(async (info, i) => {
        try {
          const snap = await get(rtdbRef(info.db, info.path))
          const count = snap.exists() ? Object.keys(snap.val()).length : 0
          setDbInfos(prev => prev.map((d, j) => j === i ? { ...d, count, status: 'ok' } : d))
        } catch {
          setDbInfos(prev => prev.map((d, j) => j === i ? { ...d, count: null, status: 'error' } : d))
        }
      })
    )

    // Firestore planillas
    try {
      const snap = await getCountFromServer(collection(db, 'pm_planillas'))
      setFirestoreCount(snap.data().count)
      setFirestoreStatus('ok')
    } catch {
      setFirestoreStatus('error')
    }

    setLastRefresh(new Date())
    setRefreshing(false)
  }

  useEffect(() => { loadCounts() }, [])

  if (!isAdmin) {
    return (
      <div className="bg-amber-50 border border-amber-200 rounded-xl p-8 text-center">
        <AlertTriangle size={32} className="text-amber-400 mx-auto mb-3" />
        <p className="text-amber-700 font-medium">Acceso restringido a administradores</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Panel de Administración</h1>
          <p className="text-sm text-gray-500 mt-0.5">Estado del sistema, bases de datos y configuración</p>
        </div>
        <button
          onClick={loadCounts}
          disabled={refreshing}
          className="flex items-center gap-2 px-4 py-2 bg-[#003087] text-white rounded-xl text-sm font-semibold hover:bg-[#002070] transition-colors disabled:opacity-60"
        >
          {refreshing ? <Spinner size="sm" /> : <RefreshCw size={15} />}
          Actualizar
        </button>
      </div>

      {/* System status */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white rounded-xl shadow-card p-5 flex items-center gap-4">
          {online ? (
            <Wifi size={22} className="text-emerald-500" />
          ) : (
            <WifiOff size={22} className="text-red-500" />
          )}
          <div>
            <p className="text-xs text-gray-500">Conexión</p>
            <p className={`text-sm font-semibold ${online ? 'text-emerald-600' : 'text-red-600'}`}>
              {online ? 'En línea' : 'Sin conexión'}
            </p>
          </div>
        </div>
        <div className="bg-white rounded-xl shadow-card p-5 flex items-center gap-4">
          <Info size={22} className="text-[#003087]" />
          <div>
            <p className="text-xs text-gray-500">Versión Web</p>
            <p className="text-sm font-semibold text-gray-800">v{APP_VERSION}</p>
          </div>
        </div>
        <div className="bg-white rounded-xl shadow-card p-5 flex items-center gap-4">
          <Activity size={22} className="text-purple-500" />
          <div>
            <p className="text-xs text-gray-500">App Android</p>
            <p className="text-sm font-semibold text-gray-800">{ANDROID_VERSION}</p>
          </div>
        </div>
      </div>

      {/* Admin info */}
      <div className="bg-[#003087] rounded-xl p-5 text-white">
        <p className="text-xs text-white/60 uppercase tracking-wide font-medium mb-1">Sesión actual</p>
        <p className="font-semibold">{user?.nombre ?? user?.email}</p>
        <p className="text-sm text-white/70 mt-0.5">{user?.email}</p>
        <p className="text-xs text-white/50 mt-2">
          Última actualización de conteos: {lastRefresh.toLocaleTimeString('es-CR')}
        </p>
      </div>

      {/* Firebase RTDB instances */}
      <div className="bg-white rounded-xl shadow-card overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center gap-2">
          <Database size={16} className="text-[#003087]" />
          <h2 className="font-semibold text-gray-900 text-sm">Firebase Realtime Database</h2>
          <span className="ml-auto text-xs text-gray-400">{dbInfos.length} instancias</span>
        </div>
        <div className="divide-y divide-gray-50">
          {dbInfos.map(info => (
            <div key={info.key} className="px-5 py-3.5 flex items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                {info.status === 'loading' ? (
                  <Spinner size="sm" />
                ) : info.status === 'ok' ? (
                  <CheckCircle2 size={16} className="text-emerald-500" />
                ) : (
                  <AlertTriangle size={16} className="text-red-400" />
                )}
                <div>
                  <p className="text-sm font-medium text-gray-800">{info.label}</p>
                  <p className="text-xs text-gray-400 font-mono">{info.path}</p>
                </div>
              </div>
              <div className="text-right">
                {info.status === 'loading' ? (
                  <span className="text-xs text-gray-400">Verificando...</span>
                ) : info.status === 'error' ? (
                  <span className="text-xs text-red-500">Error de acceso</span>
                ) : (
                  <span className="text-sm font-bold text-gray-900">{info.count?.toLocaleString() ?? '—'}</span>
                )}
                {info.status === 'ok' && (
                  <p className="text-[10px] text-gray-400">registros raíz</p>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Firestore */}
      <div className="bg-white rounded-xl shadow-card overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center gap-2">
          <Database size={16} className="text-orange-500" />
          <h2 className="font-semibold text-gray-900 text-sm">Cloud Firestore</h2>
        </div>
        <div className="px-5 py-3.5 flex items-center justify-between">
          <div className="flex items-center gap-3">
            {firestoreStatus === 'loading' ? (
              <Spinner size="sm" />
            ) : firestoreStatus === 'ok' ? (
              <CheckCircle2 size={16} className="text-emerald-500" />
            ) : (
              <AlertTriangle size={16} className="text-red-400" />
            )}
            <div>
              <p className="text-sm font-medium text-gray-800">pm_planillas</p>
              <p className="text-xs text-gray-400">Módulo PM — planillas de trabajo</p>
            </div>
          </div>
          <div className="text-right">
            {firestoreStatus === 'loading' ? (
              <span className="text-xs text-gray-400">Verificando...</span>
            ) : firestoreStatus === 'error' ? (
              <span className="text-xs text-red-500">Error de acceso</span>
            ) : (
              <span className="text-sm font-bold text-gray-900">{firestoreCount?.toLocaleString() ?? '—'}</span>
            )}
            {firestoreStatus === 'ok' && (
              <p className="text-[10px] text-gray-400">documentos</p>
            )}
          </div>
        </div>
      </div>

      {/* Firebase Rules reminder */}
      <div className="bg-amber-50 border border-amber-200 rounded-xl p-4">
        <div className="flex items-start gap-3">
          <AlertTriangle size={16} className="text-amber-500 flex-shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-medium text-amber-800">Reglas de seguridad de Firebase</p>
            <p className="text-xs text-amber-700 mt-1 leading-relaxed">
              Asegúrese de que las reglas de Firebase Realtime Database estén configuradas para
              permitir acceso autenticado. Las reglas en producción deben restringir escritura
              a usuarios con rol de administrador o supervisor.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
