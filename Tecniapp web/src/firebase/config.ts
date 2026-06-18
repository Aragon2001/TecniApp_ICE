import { initializeApp, getApps, FirebaseApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'
import { getDatabase } from 'firebase/database'
import { getFunctions } from 'firebase/functions'

// Primary Firebase configuration (used for Auth, Firestore, and default RTDB)
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || '',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || '',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || '',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || '',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '',
  appId: import.meta.env.VITE_FIREBASE_APP_ID || '',
  databaseURL: import.meta.env.VITE_DB_MAIN || '',
}

// Initialize primary app (avoid duplicate initialization)
const primaryApp: FirebaseApp =
  getApps().find((a) => a.name === '[DEFAULT]') ?? initializeApp(firebaseConfig)

// Helper to get or create a named Firebase app for a secondary database
function getOrCreateApp(name: string, databaseURL: string): FirebaseApp {
  const existing = getApps().find((a) => a.name === name)
  if (existing) return existing
  return initializeApp({ ...firebaseConfig, databaseURL }, name)
}

// Secondary app instances — one per Realtime Database URL
const appAverias = getOrCreateApp(
  'averias',
  import.meta.env.VITE_DB_AVERIAS || ''
)
const appUsers = getOrCreateApp(
  'users',
  import.meta.env.VITE_DB_USERS || ''
)
const appGeneral = getOrCreateApp(
  'general',
  import.meta.env.VITE_DB_GENERAL || ''
)
const appPersonal = getOrCreateApp(
  'personal',
  import.meta.env.VITE_DB_PERSONAL || ''
)
const appMateriales = getOrCreateApp(
  'materiales',
  import.meta.env.VITE_DB_MATERIALES || ''
)
const appInventario = getOrCreateApp(
  'inventario',
  import.meta.env.VITE_DB_INVENTARIO || ''
)
const appLuminarias = getOrCreateApp(
  'luminarias',
  import.meta.env.VITE_DB_LUMINARIAS || ''
)
const appProgramacion = getOrCreateApp(
  'programacion',
  import.meta.env.VITE_DB_PROGRAMACION || ''
)

// ─── Exports ────────────────────────────────────────────────────────────────

/** Firebase Authentication (primary app) */
export const auth = getAuth(primaryApp)

/** Cloud Firestore — PM module (planillas, work logs, SAP orders) */
export const db = getFirestore(primaryApp)

/** Cloud Functions */
export const functions = getFunctions(primaryApp)

// Realtime Database instances
/** Default RTDB — Medidores, Localizaciones, Pueblos */
export const rtdbMain = getDatabase(primaryApp)

/** tecniapp-ice-averias — Outages (averías) */
export const rtdbAverias = getDatabase(appAverias)

/** tecniapp-ice-user — Users, FCM tokens */
export const rtdbUsers = getDatabase(appUsers)

/** tecniapp-ice-datosgenerales — Agencias, Subregiones, Vehiculos */
export const rtdbGeneral = getDatabase(appGeneral)

/** tecniapp-ice-personal — Tecnicos */
export const rtdbPersonal = getDatabase(appPersonal)

/** tecniapp-ice-materiales — Materials catalogue */
export const rtdbMateriales = getDatabase(appMateriales)

/** tecniapp-ice-inventario — Vehicle inventory */
export const rtdbInventario = getDatabase(appInventario)

/** tecniapp-ice-luminarias — Luminaria repairs */
export const rtdbLuminarias = getDatabase(appLuminarias)

/** tecniapp-ice-programacion — Task scheduling */
export const rtdbProgramacion = getDatabase(appProgramacion)

export default primaryApp
