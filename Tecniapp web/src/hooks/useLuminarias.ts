import { useState, useEffect, useMemo } from 'react';
import { ref, onValue, off, set, remove, push } from 'firebase/database';
import { rtdbLuminarias } from '../firebase/config';
import { useAuth } from '../context/AuthContext';

export interface Luminaria {
  id: string;
  _agenciaKey: string;
  _estadoSegment: 'pendientes' | 'reparadas';
  vehiculoId?: string;
  localizacion?: string;
  cliente?: string;
  contacto?: string;
  observaciones?: string;
  materialesJson?: string;
  estado: 'PENDIENTE' | 'REPARADA';
  ejecutorNombre?: string;
  ejecutorCedula?: string;
  fechaRegistro?: number;
  fechaCarga?: number;
  fechaReparacion?: number;
  [key: string]: any;
}

export interface LuminariaPermisos {
  puedeMarcarReparada: boolean;  // técnico
  puedeRegistrar: boolean;       // supervisor / admin
  puedeFiltrarVehiculo: boolean; // supervisor / admin
  esGestor: boolean;
}

interface UseLuminariasResult {
  pendientes: Luminaria[];
  reparadas: Luminaria[];
  todosVehiculoIds: string[];
  loading: boolean;
  error: string | null;
  agenciaKey: string;
  permisos: LuminariaPermisos;
}

export function useLuminarias(vehiculoFiltro?: string): UseLuminariasResult {
  const { user } = useAuth();
  const [raw, setRaw] = useState<Luminaria[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Match Android: uses usuario.agencia as the Firebase path key
  const agenciaKey = (user?.agencia?.trim() || user?.agenciaId?.trim()) ?? '';
  const esGestor = user?.rol === 'supervisor' || user?.rol === 'admin';
  // Técnico is locked to their own vehicle plate (used as vehiculoId match)
  const placaTecnico = !esGestor ? user?.placaVehiculo?.trim() : undefined;

  useEffect(() => {
    if (!rtdbLuminarias) {
      setError('Firebase RTDB (luminarias) no configurado');
      setLoading(false);
      return;
    }
    if (!agenciaKey) {
      setError('Usuario sin agencia asignada');
      setLoading(false);
      return;
    }

    setLoading(true);
    const lumRef = ref(rtdbLuminarias, `/luminarias/${agenciaKey}`);

    onValue(
      lumRef,
      (snapshot) => {
        const data = snapshot.val();
        if (!data) { setRaw([]); setLoading(false); return; }

        const list: Luminaria[] = [];
        (['pendientes', 'reparadas'] as const).forEach((seg) => {
          const segData = data[seg];
          if (!segData || typeof segData !== 'object') return;
          Object.entries(segData).forEach(([id, itemData]: [string, any]) => {
            if (typeof itemData !== 'object' || itemData === null) return;
            list.push({
              id,
              _agenciaKey: agenciaKey,
              _estadoSegment: seg,
              estado: seg === 'pendientes' ? 'PENDIENTE' : 'REPARADA',
              ...itemData,
            });
          });
        });

        list.sort((a, b) => (b.fechaRegistro || 0) - (a.fechaRegistro || 0));
        setRaw(list);
        setLoading(false);
      },
      (err) => { setError(err.message); setLoading(false); }
    );

    return () => off(lumRef);
  }, [agenciaKey]);

  const todosVehiculoIds = useMemo(() => {
    const ids = new Set(raw.map(l => l.vehiculoId).filter(Boolean) as string[]);
    return Array.from(ids).sort();
  }, [raw]);

  // Filter: técnico → only their vehicle; supervisor/admin → all or selected filter
  const filtered = useMemo(() => {
    let list = raw;
    if (!esGestor && placaTecnico) {
      list = list.filter(l => l.vehiculoId === placaTecnico);
    } else if (esGestor && vehiculoFiltro) {
      list = list.filter(l => l.vehiculoId === vehiculoFiltro);
    }
    return list;
  }, [raw, esGestor, placaTecnico, vehiculoFiltro]);

  const pendientes = useMemo(() => filtered.filter(l => l.estado === 'PENDIENTE'), [filtered]);
  const reparadas = useMemo(() => filtered.filter(l => l.estado === 'REPARADA'), [filtered]);

  const permisos: LuminariaPermisos = {
    puedeMarcarReparada: !esGestor,
    puedeRegistrar: esGestor,
    puedeFiltrarVehiculo: esGestor,
    esGestor,
  };

  return { pendientes, reparadas, todosVehiculoIds, loading, error, agenciaKey, permisos };
}

export async function marcarLuminariaReparada(
  lum: Luminaria,
  ejecutorNombre: string,
  ejecutorCedula?: string
): Promise<void> {
  if (!rtdbLuminarias) throw new Error('Firebase RTDB no configurado');
  if (lum._estadoSegment !== 'pendientes') return;

  const { id, _agenciaKey, _estadoSegment, estado, ...fields } = lum;
  const payload = {
    ...fields,
    estado: 'REPARADA',
    ejecutorNombre: ejecutorNombre.trim() || fields.ejecutorNombre || '',
    ejecutorCedula: ejecutorCedula || fields.ejecutorCedula || '',
    fechaReparacion: Date.now(),
  };

  await set(ref(rtdbLuminarias, `luminarias/${_agenciaKey}/reparadas/${id}`), payload);
  await remove(ref(rtdbLuminarias, `luminarias/${_agenciaKey}/pendientes/${id}`));
}

export async function registrarLuminariaPendiente(
  agenciaKey: string,
  data: {
    localizacion: string;
    vehiculoId: string;
    cliente?: string;
    contacto?: string;
    observaciones?: string;
    ejecutorNombre?: string;
    ejecutorCedula?: string;
  }
): Promise<void> {
  if (!rtdbLuminarias) throw new Error('Firebase RTDB no configurado');

  const newRef = push(ref(rtdbLuminarias, `luminarias/${agenciaKey}/pendientes`));
  await set(newRef, {
    ...data,
    localizacion: data.localizacion.trim(),
    estado: 'PENDIENTE',
    fechaRegistro: Date.now(),
    fechaCarga: Date.now(),
  });
}
