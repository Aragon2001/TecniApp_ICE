/** Convert Firebase email key (commas) back to email (dots) */
export function keyToEmail(key: string): string {
  return key.replace(/,/g, '.')
}

/** Convert email to Firebase key (dots → commas) */
export function emailToKey(email: string): string {
  return email.replace(/\./g, ',')
}

/** Format a number as km distance: "12,345 km" */
export function formatKm(km: number | null | undefined): string {
  if (km == null) return '—'
  return `${km.toLocaleString('es-CR')} km`
}

/** Format integer with thousands separator */
export function formatNumber(n: number | null | undefined): string {
  if (n == null) return '—'
  return n.toLocaleString('es-CR')
}

/** Parse a JSON string safely; return null on error */
export function parseJson<T = unknown>(json: string | null | undefined): T | null {
  if (!json) return null
  try {
    return JSON.parse(json) as T
  } catch {
    return null
  }
}

/** Spanish label for UserRole */
export function rolLabel(rol: string): string {
  const map: Record<string, string> = {
    admin: 'Administrador',
    supervisor: 'Supervisor',
    tecnico: 'Técnico',
  }
  return map[rol] ?? rol
}

/** Spanish label for AveriaEstado */
export function estadoLabel(estado: string): string {
  const map: Record<string, string> = {
    PENDIENTE: 'Pendiente',
    ASIGNADA: 'Asignada',
    EN_ATENCION: 'En Atención',
    RESUELTA: 'Resuelta',
    ANULADA: 'Anulada',
  }
  return map[estado] ?? estado
}

/** Spanish label for VehiculoEstado */
export function vehiculoEstadoLabel(estado: string): string {
  const map: Record<string, string> = {
    OPTIMO: 'Óptimo',
    ATENCION: 'Atención',
    VENCIDO: 'Vencido',
  }
  return map[estado] ?? estado
}

/** Capitalize first letter */
export function capitalize(s: string): string {
  if (!s) return ''
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase()
}

/** Truncate a string to maxLen chars, appending "…" */
export function truncate(s: string, maxLen = 40): string {
  if (!s || s.length <= maxLen) return s
  return s.slice(0, maxLen) + '…'
}

/** Google Maps URL for lat/lng */
export function googleMapsUrl(lat: number, lng: number): string {
  return `https://www.google.com/maps?q=${lat},${lng}`
}
