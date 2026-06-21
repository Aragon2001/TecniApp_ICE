import { useState, useCallback } from 'react';
import { ref, get } from 'firebase/database';
import { rtdbMain } from '../firebase/config';
import { useAuth } from '../context/AuthContext';

export interface Medidor {
  id: string;
  medidorNumber: string;
  calle?: string;
  cliente?: string;
  localizacion?: string | number;
  metros?: string;
  poste?: string;
  pueblo?: string;
  subregion?: string;
  [key: string]: any;
}

export type MedidorEstado =
  | { tipo: 'idle' }
  | { tipo: 'cargando' }
  | { tipo: 'encontrado'; medidor: Medidor }
  | { tipo: 'no_encontrado'; numero: string }
  | { tipo: 'error'; mensaje: string };

interface UseMedidoresResult {
  estado: MedidorEstado;
  buscarMedidor: (numero: string) => Promise<void>;
  limpiar: () => void;
}

export function useMedidores(): UseMedidoresResult {
  const { user } = useAuth();
  const [estado, setEstado] = useState<MedidorEstado>({ tipo: 'idle' });

  const buscarMedidor = useCallback(async (numero: string) => {
    const trimmed = numero.trim().toUpperCase();
    if (!trimmed) return;
    if (!rtdbMain) {
      setEstado({ tipo: 'error', mensaje: 'Firebase no configurado' });
      return;
    }

    setEstado({ tipo: 'cargando' });

    try {
      const userSubregion = user?.subregion?.trim();

      // 1. Search in user's subregion first (targeted, cheap)
      if (userSubregion) {
        const snap = await get(ref(rtdbMain, `/Medidores/${userSubregion}/${trimmed}`));
        if (snap.exists()) {
          const data = snap.val();
          if (typeof data === 'object' && data !== null) {
            setEstado({
              tipo: 'encontrado',
              medidor: { id: trimmed, medidorNumber: trimmed, subregion: userSubregion, ...data },
            });
            return;
          }
        }
      }

      // 2. Fallback: search all subregions (for admins / cross-subregion)
      const allSnap = await get(ref(rtdbMain, '/Medidores'));
      if (allSnap.exists()) {
        const allData = allSnap.val() as Record<string, any>;
        for (const [sub, subData] of Object.entries(allData)) {
          if (typeof subData !== 'object' || subData === null) continue;
          const medData = subData[trimmed];
          if (medData && typeof medData === 'object') {
            setEstado({
              tipo: 'encontrado',
              medidor: { id: trimmed, medidorNumber: trimmed, subregion: sub, ...medData },
            });
            return;
          }
        }
      }

      setEstado({ tipo: 'no_encontrado', numero: trimmed });
    } catch (err: any) {
      setEstado({ tipo: 'error', mensaje: err.message || 'Error al consultar el medidor' });
    }
  }, [user]);

  const limpiar = useCallback(() => {
    setEstado({ tipo: 'idle' });
  }, []);

  return { estado, buscarMedidor, limpiar };
}
