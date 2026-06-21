import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
} from 'react'
import {
  User as FirebaseUser,
  signInWithEmailAndPassword,
  signOut,
  onAuthStateChanged,
} from 'firebase/auth'
import { ref, get, query, orderByChild, equalTo } from 'firebase/database'
import { auth, rtdbUsers, rtdbGeneral } from '../firebase/config'
import type { Usuario } from '../types'

// ─── Vehiculo info (from rtdbGeneral /vehiculos) ──────────────────────────────

export interface VehiculoUsuario {
  vehiculoId: string
  placa: string
  tipo?: string
  marca?: string
  modelo?: string
  anio?: number
  agencia?: string
  subregion?: string
  kmActual?: number
  mantenimientoProximo?: number
  tecnicoAsignado?: string
  [key: string]: any
}

// ─── Context shape ─────────────────────────────────────────────────────────────

interface AuthContextValue {
  user: Usuario | null
  vehiculo: VehiculoUsuario | null
  firebaseUser: FirebaseUser | null
  loading: boolean
  /** True while the RTDB user profile is being fetched (after auth resolves) */
  profileLoading: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  isAdmin: boolean
  isSupervisor: boolean
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

// ─── Helpers ───────────────────────────────────────────────────────────────────

function emailToKey(email: string): string {
  return email.replace(/\./g, ',')
}

/**
 * Read a field supporting both snake_case (Firebase/Android) and camelCase.
 * Android uses @PropertyName annotations to map e.g. agencia_id ↔ agenciaId.
 */
function field(data: Record<string, any>, snake: string, camel?: string): string {
  return data[snake] ?? (camel ? data[camel] : undefined) ?? ''
}

/**
 * Fetch TecniApp user profile from RTDB.
 * Tries email key first (dots→commas), then UID, then /usuarios/{uid}.
 */
async function fetchUsuario(email: string, uid?: string): Promise<Usuario | null> {
  if (!rtdbUsers) return null
  try {
    const emailKey = emailToKey(email)
    const paths = [
      `users/${emailKey}`,
      uid ? `users/${uid}` : null,
      uid ? `usuarios/${uid}` : null,
      `usuarios/${emailKey}`,
    ].filter(Boolean) as string[]

    let data: Record<string, any> | null = null
    for (const path of paths) {
      const snap = await get(ref(rtdbUsers, path))
      if (snap.exists()) { data = snap.val(); break }
    }

    if (!data) return null

    // Build apellidos from parts (Android stores primer_apellido + segundo_apellido)
    const primerApellido = data['primer_apellido'] ?? data['primerApellido'] ?? ''
    const segundoApellido = data['segundo_apellido'] ?? data['segundoApellido'] ?? ''
    const apellidosCombinados = [primerApellido, segundoApellido].filter(Boolean).join(' ').trim()

    return {
      uid: data.uid ?? uid ?? '',
      email: data.email ?? email,
      nombre: data.nombre ?? '',
      apellidos: (apellidosCombinados || data.apellidos) ?? '',
      cedula: data.cedula ?? '',
      telefono: data.telefono ?? '',
      region: data.region ?? '',
      // Firebase field: region_nombre (Android @PropertyName)
      regionNombre: field(data, 'region_nombre', 'regionNombre'),
      subregion: data.subregion ?? '',
      // Firebase field: subregion_nombre
      subregionNombre: field(data, 'subregion_nombre', 'subregionNombre'),
      // Firebase field: agencia_id
      agenciaId: field(data, 'agencia_id', 'agenciaId'),
      agencia: data.agencia ?? '',
      placaVehiculo: data.placaVehiculo ?? '',
      rol: (data.rol ?? 'tecnico') as Usuario['rol'],
      fotoUrl: data.fotoUrl ?? '',
    }
  } catch (err) {
    console.error('[AuthContext] fetchUsuario error:', err)
    return null
  }
}

/**
 * Find the vehicle in rtdbGeneral /vehiculos matching the user's plate.
 * Android stores placa as a Long; web stores as string — compare digits only.
 */
async function fetchVehiculo(placa: string): Promise<VehiculoUsuario | null> {
  if (!placa || !rtdbGeneral) return null
  try {
    const placaDigits = placa.replace(/\D/g, '')
    if (!placaDigits) return null

    // Try orderByChild first (fast if indexed), fall back to full scan
    const placaNum = Number(placaDigits)
    const candidates = await Promise.allSettled([
      get(query(ref(rtdbGeneral, '/vehiculos'), orderByChild('placa'), equalTo(placaNum))),
      get(query(ref(rtdbGeneral, '/vehiculos'), orderByChild('placa'), equalTo(placaDigits))),
    ])

    for (const result of candidates) {
      if (result.status === 'fulfilled' && result.value.exists()) {
        const entries = Object.entries(result.value.val() as Record<string, any>)
        if (entries.length > 0) {
          const [vehiculoId, v] = entries[0]
          return { vehiculoId, placa: String(v.placa ?? placa), ...v }
        }
      }
    }

    // Full scan fallback
    const all = await get(ref(rtdbGeneral, '/vehiculos'))
    if (!all.exists()) return null
    for (const [vehiculoId, v] of Object.entries(all.val() as Record<string, any>)) {
      const vPlacaDigits = String(v.placa ?? '').replace(/\D/g, '')
      if (vPlacaDigits === placaDigits) {
        return { vehiculoId, placa: String(v.placa ?? placa), ...v }
      }
    }
    return null
  } catch (err) {
    console.error('[AuthContext] fetchVehiculo error:', err)
    return null
  }
}

// ─── Provider ─────────────────────────────────────────────────────────────────

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [firebaseUser, setFirebaseUser] = useState<FirebaseUser | null>(null)
  const [user, setUser] = useState<Usuario | null>(null)
  const [vehiculo, setVehiculo] = useState<VehiculoUsuario | null>(null)
  const [loading, setLoading] = useState(true)
  const [profileLoading, setProfileLoading] = useState(false)

  async function loadProfile(fbUser: FirebaseUser) {
    setProfileLoading(true)
    try {
      const profile = await fetchUsuario(fbUser.email ?? '', fbUser.uid)
      setUser(profile)
      if (profile?.placaVehiculo) {
        fetchVehiculo(profile.placaVehiculo).then(setVehiculo).catch(console.error)
      } else {
        setVehiculo(null)
      }
    } finally {
      setProfileLoading(false)
    }
  }

  useEffect(() => {
    const fallbackTimer = setTimeout(() => setLoading(false), 10_000)

    const unsubscribe = onAuthStateChanged(auth, async (fbUser) => {
      clearTimeout(fallbackTimer)
      setFirebaseUser(fbUser)
      setLoading(false)

      if (fbUser) {
        loadProfile(fbUser)
      } else {
        setUser(null)
        setVehiculo(null)
      }
    })

    return () => { clearTimeout(fallbackTimer); unsubscribe() }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    setLoading(true)
    try {
      await signInWithEmailAndPassword(auth, email, password)
      // onAuthStateChanged fires and calls setLoading(false) + fetches profile
    } catch (err) {
      setLoading(false)
      throw err
    }
  }, [])

  const logout = useCallback(async () => {
    await signOut(auth)
    setUser(null)
    setVehiculo(null)
    setFirebaseUser(null)
  }, [])

  const isAdmin = user?.rol === 'admin'
  const isSupervisor = user?.rol === 'supervisor' || isAdmin

  return (
    <AuthContext.Provider value={{ user, vehiculo, firebaseUser, loading, profileLoading, login, logout, isAdmin, isSupervisor }}>
      {children}
    </AuthContext.Provider>
  )
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
