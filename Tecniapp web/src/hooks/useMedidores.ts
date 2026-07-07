import { useState, useCallback } from 'react'
import { ref, get } from 'firebase/database'
import { rtdbMain } from '../firebase/config'
import { useAuth } from '../context/AuthContext'
import { db } from '../lib/db'

export interface Medidor {
  id: string
  medidorNumber: string
  calle?: string
  cliente?: string
  localizacion?: string | number
  metros?: string
  poste?: string
  pueblo?: string
  subregion?: string
  [key: string]: any
}

export type MedidorEstado =
  | { tipo: 'idle' }
  | { tipo: 'cargando' }
  | { tipo: 'encontrado'; medidor: Medidor }
  | { tipo: 'no_encontrado'; numero: string }
  | { tipo: 'error'; mensaje: string }

interface UseMedidoresResult {
  estado: MedidorEstado
  buscarMedidor: (numero: string) => Promise<void>
  limpiar: () => void
}

export function useMedidores(): UseMedidoresResult {
  const { user } = useAuth()
  const [estado, setEstado] = useState<MedidorEstado>({ tipo: 'idle' })

  const buscarMedidor = useCallback(async (numero: string) => {
    const trimmed = numero.trim().toUpperCase()
    if (!trimmed) return

    setEstado({ tipo: 'cargando' })

    try {
      const cachedAll = await db.medidores.toArray()
      if (cachedAll.length > 0) {
        const found = cachedAll.find((m: any) => m.id === trimmed || m.medidorNumber === trimmed)
        if (found) {
          setEstado({ tipo: 'encontrado', medidor: found as Medidor })
          return
        }
        if (!navigator.onLine) {
          setEstado({ tipo: 'no_encontrado', numero: trimmed })
          return
        }
      }
    } catch {
      // Dexie error — fall through to Firebase
    }

    if (!navigator.onLine) {
      setEstado({ tipo: 'error', mensaje: 'Sin conexión y sin datos en caché' })
      return
    }

    if (!rtdbMain) {
      setEstado({ tipo: 'error', mensaje: 'Firebase no configurado' })
      return
    }

    try {
      const userSubregion = user?.subregion?.trim()

      if (userSubregion) {
        const snap = await get(ref(rtdbMain, `/Medidores/${userSubregion}/${trimmed}`))
        if (snap.exists()) {
          const data = snap.val()
          if (typeof data === 'object' && data !== null) {
            const medidor: Medidor = { id: trimmed, medidorNumber: trimmed, subregion: userSubregion, ...data }
            await db.medidores.put(medidor)
            setEstado({ tipo: 'encontrado', medidor })
            return
          }
        }
      }

      const allSnap = await get(ref(rtdbMain, '/Medidores'))
      if (allSnap.exists()) {
        const allData = allSnap.val() as Record<string, any>
        for (const [sub, subData] of Object.entries(allData)) {
          if (typeof subData !== 'object' || subData === null) continue
          const medData = subData[trimmed]
          if (medData && typeof medData === 'object') {
            const medidor: Medidor = { id: trimmed, medidorNumber: trimmed, subregion: sub, ...medData }
            await db.medidores.put(medidor)
            setEstado({ tipo: 'encontrado', medidor })
            return
          }
        }
      }

      setEstado({ tipo: 'no_encontrado', numero: trimmed })
    } catch (err: any) {
      setEstado({ tipo: 'error', mensaje: err.message || 'Error al consultar el medidor' })
    }
  }, [user])

  const limpiar = useCallback(() => {
    setEstado({ tipo: 'idle' })
  }, [])

  return { estado, buscarMedidor, limpiar }
}
