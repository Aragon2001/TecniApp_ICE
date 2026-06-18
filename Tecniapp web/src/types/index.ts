// ─── Averías ─────────────────────────────────────────────────────────────────

export type AveriaEstado =
  | 'PENDIENTE'
  | 'ASIGNADA'
  | 'EN_ATENCION'
  | 'RESUELTA'
  | 'ANULADA'

export interface Averia {
  caseId: string
  estado: AveriaEstado
  region: string
  provincia: string
  agencia: string
  nombreAgencia: string
  agenciaTag: string
  nise: string
  causa: string
  observaciones: string
  lat: number
  lng: number
  clientesAfectados: number
  fechaInicioMillis: number
  horaInicioMillis: number
  horaFinalMillis: number
  atencionHoraInicioMillis: number
  atencionHoraFinalMillis: number
  horaLlegadaMillis: number
  kilometrajeInicio: number
  kilometrajeLlegada: number
  kilometrajeFinal: number
  vehiculoAsignado: string
  tecnicoAsignadoUid: string
  tecnicoAsignadoNombre: string
  atendidoPorUid: string
  atendidoPorNombre: string
  materialesTexto: string
  materialesDetalleJson: string
  tecnicosAtendieronJson: string
  numeroMedidor: string
  medidorCalle: string
  medidorPueblo: string
  medidorMetros: string
  medidorPoste: string
  tipoAfectacion: string
  localizacion: string
  direccion: string
  cliente: string
  evidenciasJson: string
  estadoClor: string
  causaClor: string
  observacionesClor: string
  isSynced: boolean
  lastUpdated: number
}

// ─── Medidores ────────────────────────────────────────────────────────────────

export interface Medidor {
  medidorNumber: string
  calle: string
  metros: string
  poste: string
  cliente: string
  localizacion: string
  pueblo: string
  subregion: string
}

// ─── Vehículos ────────────────────────────────────────────────────────────────

export type VehiculoEstado = 'OPTIMO' | 'ATENCION' | 'VENCIDO'

export interface Vehiculo {
  vehiculoId: string
  placaRaw: string
  placa: string
  tipo: string
  agencia: string
  subregion: string
  kmActual: number
  orimetroActual: number
  registroFecha: string
  registroInicial: number
  registroFinal: number
  registroCerrado: boolean
  mantenimientoUltimo: number
  mantenimientoProximo: number
  registrosDiariosJson: string
  updatedAt: number
  estado?: VehiculoEstado
}

// ─── Luminarias ───────────────────────────────────────────────────────────────

export type LuminariaEstado = 'PENDIENTE' | 'REPARADA'

export interface LuminariaReparacion {
  id: string
  vehiculoId: string
  localizacion: string
  cliente: string
  contacto: string
  observaciones: string
  materialesJson: string
  estado: LuminariaEstado
  ejecutorNombre: string
  ejecutorCedula: string
  fechaRegistro: number
  fechaCarga: number
  fechaReparacion: number
}

// ─── Inventario ───────────────────────────────────────────────────────────────

export interface InventarioItem {
  id: string
  vehiculoId: string
  codigoMaterial: string
  descripcionMaterial: string
  cantidadDisponible: number
}

// ─── Programación ─────────────────────────────────────────────────────────────

export interface Programacion {
  programacionId: string
  vehiculoId: string
  placa: string
  localizacion: string
  circuito: string
  cuenta: string
  actividad: string
  descripcion: string
  lat: number
  lng: number
  estado: string
  observaciones: string
  fechaAsignacion: number
  fechaEjecucion: number
  supervisorId: string
  tecnicoId: string
  subregion: string
  updatedAt: number
}

// ─── Usuarios ─────────────────────────────────────────────────────────────────

export type UserRole = 'tecnico' | 'supervisor' | 'admin'

export interface Usuario {
  uid: string
  email: string
  nombre: string
  apellidos: string
  cedula: string
  telefono: string
  region: string
  regionNombre: string
  subregion: string
  subregionNombre: string
  agenciaId: string
  agencia: string
  placaVehiculo: string
  rol: UserRole
  fotoUrl: string
}

// ─── PM Module ────────────────────────────────────────────────────────────────

export type SyncStatus = 'PENDING' | 'SYNCING' | 'SYNCED' | 'ERROR'

export interface Planilla {
  planillaId: string
  fechaDia: string
  uidTecnico: string
  regionKey: string
  subregionKey: string
  mode: string
  totalHorasMin: number
  logIdsCsv: string
  pdfLocalPath: string
  pdfRemoteUrl: string
  createdAt: number
  updatedAt: number
  syncStatus: SyncStatus
}

export interface WorkLog {
  logId: string
  groupId: string
  ordenSap: number
  ordenSapManual: string
  regionKey: string
  subregionKey: string
  circuitoId: string
  agenciaId: string
  modulo: string
  uidTecnico: string
  fechaDia: string
  horaEntraMin: number
  horaSaleMin: number
  pausaMin: number
  horasOrdMin: number
  observaciones: string
  createdAt: number
  updatedAt: number
  syncStatus: SyncStatus
}

export interface ActivityGroup {
  groupId: string
  ordenSap: number
  ordenSapManual: string
  descripcionOrden: string
  regionKey: string
  subregionKey: string
  circuitoId: string
  agenciaId: string
  modulo: string
  uidTecnico: string
  fechaDia: string
  createdAt: number
  updatedAt: number
  syncStatus: SyncStatus
}

export interface OrdenSap {
  ordenSap: number
  descripcion: string
  regionKey: string
  subregionKey: string
  circuitoId: string
  agenciaId: string
  modulo: string
}

// ─── Reference / Catalogue Data ───────────────────────────────────────────────

export interface Agencia {
  id: string
  nombre: string
  regionId: string
  subregion: string
}

export interface Region {
  id: string
  nombre: string
}

export interface Subregion {
  id: string
  nombre: string
  regionId: string
}

export interface Material {
  id: string
  codigo: string
  descripcion: string
  unidad: string
}

export interface Tecnico {
  cedula: string
  nombre: string
}

export interface Localizacion {
  id: string
  calle: string
  pueblo: string
  direccion: string
  latitud: number
  longitud: number
  alPoste: string
  delPoste: string
  subregion: string
}

// ─── Dashboard ────────────────────────────────────────────────────────────────

export interface DashboardStats {
  totalAverias: number
  averiasPendientes: number
  averiasAsignadas: number
  averiasEnAtencion: number
  averiasResueltas: number
  averiasAnuladas: number
  totalVehiculos: number
  vehiculosOptimos: number
  vehiculosAtencion: number
  vehiculosVencidos: number
  totalLuminarias: number
  luminariasResueltas: number
  luminariasPendientes: number
}

// ─── Utility types ────────────────────────────────────────────────────────────

/** Generic record keyed by Firebase push-key or custom ID */
export type FirebaseMap<T> = Record<string, T>

/** Pagination helper */
export interface PaginationState {
  page: number
  pageSize: number
  total: number
}

/** Filter state shared by list views */
export interface ListFilters {
  search: string
  estado?: string
  agencia?: string
  subregion?: string
  region?: string
  dateFrom?: string
  dateTo?: string
}
