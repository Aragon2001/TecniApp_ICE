import { format, formatDistanceToNow, fromUnixTime, isValid } from 'date-fns'
import { es } from 'date-fns/locale'

/** Format a millisecond timestamp as dd/MM/yyyy HH:mm */
export function formatDateTime(millis: number | null | undefined): string {
  if (!millis) return '—'
  try {
    const d = new Date(millis)
    if (!isValid(d)) return '—'
    return format(d, 'dd/MM/yyyy HH:mm', { locale: es })
  } catch {
    return '—'
  }
}

/** Format a millisecond timestamp as dd/MM/yyyy */
export function formatDate(millis: number | null | undefined): string {
  if (!millis) return '—'
  try {
    const d = new Date(millis)
    if (!isValid(d)) return '—'
    return format(d, 'dd/MM/yyyy', { locale: es })
  } catch {
    return '—'
  }
}

/** Format a millisecond timestamp as HH:mm */
export function formatTime(millis: number | null | undefined): string {
  if (!millis) return '—'
  try {
    const d = new Date(millis)
    if (!isValid(d)) return '—'
    return format(d, 'HH:mm', { locale: es })
  } catch {
    return '—'
  }
}

/** Relative time: "hace 3 minutos", "en 2 horas" */
export function formatRelative(millis: number | null | undefined): string {
  if (!millis) return '—'
  try {
    const d = new Date(millis)
    if (!isValid(d)) return '—'
    return formatDistanceToNow(d, { addSuffix: true, locale: es })
  } catch {
    return '—'
  }
}

/** Convert total minutes to HH:mm string */
export function minutesToHHMM(minutes: number): string {
  if (!minutes || minutes < 0) return '00:00'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/** Format a YYYY-MM-DD string as dd/MM/yyyy */
export function formatDateString(dateStr: string | null | undefined): string {
  if (!dateStr) return '—'
  try {
    const [y, m, d] = dateStr.split('-').map(Number)
    const date = new Date(y, m - 1, d)
    if (!isValid(date)) return dateStr
    return format(date, 'dd/MM/yyyy', { locale: es })
  } catch {
    return dateStr
  }
}

/** Get the last N days as YYYY-MM-DD strings, from oldest to newest */
export function getLastNDays(n: number): string[] {
  const days: string[] = []
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    days.push(format(d, 'yyyy-MM-dd'))
  }
  return days
}

/** Get display label for a YYYY-MM-DD string (e.g. "lun 12") */
export function shortDayLabel(dateStr: string): string {
  try {
    const [y, m, d] = dateStr.split('-').map(Number)
    return format(new Date(y, m - 1, d), 'EEE d', { locale: es })
  } catch {
    return dateStr
  }
}
