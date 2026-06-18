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
import { ref, get } from 'firebase/database'
import { auth, rtdbUsers } from '../firebase/config'
import type { Usuario } from '../types'

// ─── Context shape ─────────────────────────────────────────────────────────────

interface AuthContextValue {
  /** Parsed TecniApp user profile fetched from RTDB */
  user: Usuario | null
  /** Raw Firebase Auth user */
  firebaseUser: FirebaseUser | null
  /** True while the auth state is being determined on mount */
  loading: boolean
  /** Sign in with email + password, then load the user profile */
  login: (email: string, password: string) => Promise<void>
  /** Sign out and clear local state */
  logout: () => Promise<void>
  /** Convenience flag */
  isAdmin: boolean
  /** Convenience flag (admins are also considered supervisors) */
  isSupervisor: boolean
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

// ─── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Convert an e-mail address to the key used in Firebase RTDB.
 * The Android app stores user records under a key where dots in the
 * e-mail address are replaced with commas:
 *   "user@example.com" → "user@example,com"
 */
function emailToKey(email: string): string {
  return email.replace(/\./g, ',')
}

/** Fetch a TecniApp user record from RTDB `users/{emailKey}`. */
async function fetchUsuario(email: string): Promise<Usuario | null> {
  try {
    const key = emailToKey(email)
    const snapshot = await get(ref(rtdbUsers, `users/${key}`))
    if (!snapshot.exists()) return null
    const data = snapshot.val() as Partial<Usuario>
    return {
      uid: data.uid ?? '',
      email: data.email ?? email,
      nombre: data.nombre ?? '',
      apellidos: data.apellidos ?? '',
      cedula: data.cedula ?? '',
      telefono: data.telefono ?? '',
      region: data.region ?? '',
      regionNombre: data.regionNombre ?? '',
      subregion: data.subregion ?? '',
      subregionNombre: data.subregionNombre ?? '',
      agenciaId: data.agenciaId ?? '',
      agencia: data.agencia ?? '',
      placaVehiculo: data.placaVehiculo ?? '',
      rol: data.rol ?? 'tecnico',
      fotoUrl: data.fotoUrl ?? '',
    }
  } catch (err) {
    console.error('[AuthContext] Failed to fetch user profile:', err)
    return null
  }
}

// ─── Provider ─────────────────────────────────────────────────────────────────

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [firebaseUser, setFirebaseUser] = useState<FirebaseUser | null>(null)
  const [user, setUser] = useState<Usuario | null>(null)
  const [loading, setLoading] = useState(true)

  // Subscribe to Firebase Auth state once on mount
  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (fbUser) => {
      setFirebaseUser(fbUser)

      if (fbUser?.email) {
        const profile = await fetchUsuario(fbUser.email)
        setUser(profile)
      } else {
        setUser(null)
      }

      setLoading(false)
    })

    return unsubscribe
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    setLoading(true)
    try {
      const credential = await signInWithEmailAndPassword(auth, email, password)
      // onAuthStateChanged will fire and populate `user` automatically,
      // but we also fetch here so the caller gets the resolved profile
      // immediately if needed.
      const profile = await fetchUsuario(credential.user.email ?? email)
      setUser(profile)
    } finally {
      setLoading(false)
    }
  }, [])

  const logout = useCallback(async () => {
    await signOut(auth)
    setUser(null)
    setFirebaseUser(null)
  }, [])

  const isAdmin = user?.rol === 'admin'
  const isSupervisor = user?.rol === 'supervisor' || isAdmin

  const value: AuthContextValue = {
    user,
    firebaseUser,
    loading,
    login,
    logout,
    isAdmin,
    isSupervisor,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return ctx
}
