import Dexie, { type Table } from 'dexie'

interface CollectionMeta {
  id: string
  cachedAt: number
}

export interface SyncQueueItem {
  id?: number
  collectionKey: string
  entityId: string
  rtdbPath: string
  payload: Record<string, any>
  createdAt: number
  retryCount: number
  status: 'pending' | 'error'
  errorMessage?: string
}

class TecniAppDB extends Dexie {
  averias!: Table<any, string>
  luminarias!: Table<any, string>
  medidores!: Table<any, string>
  inventario!: Table<any, string>
  vehiculos!: Table<any, string>
  usuarios!: Table<any, string>
  localizaciones!: Table<any, string>
  materiales!: Table<any, string>
  programaciones!: Table<any, string>
  meta!: Table<CollectionMeta, string>
  syncQueue!: Table<SyncQueueItem, number>

  constructor() {
    super('TecniAppICE')
    this.version(1).stores({
      averias: 'id, estado, agencia',
      luminarias: 'id, estado, agencia',
      medidores: 'id, numero, agencia',
      inventario: 'id, vehiculoId',
      vehiculos: 'id, placa, agencia',
      usuarios: 'id, email, role',
      localizaciones: 'id, nombre, agencia, subregion',
      materiales: 'id, nombre, categoria',
      programaciones: 'id, subregion, vehiculoId, fecha',
      meta: 'id',
    })
    this.version(2).stores({
      syncQueue: '++id, status, collectionKey, createdAt',
    })
  }
}

export const db = new TecniAppDB()

// TTLs aligned with React Query staleTime
export const DEXIE_TTL: Record<string, number> = {
  averias: 60_000,
  luminarias: 5 * 60_000,
  medidores: 10 * 60_000,
  inventario: 10 * 60_000,
  vehiculos: 10 * 60_000,
  usuarios: 30 * 60_000,
  localizaciones: 60 * 60_000,
  materiales: 60 * 60_000,
  programaciones: 5 * 60_000,
}

// Returns null on cache miss, array (possibly empty) on valid cache
export async function dexieRead<T>(
  table: Table<T, string>,
  collection: string
): Promise<T[] | null> {
  const ttl = DEXIE_TTL[collection] ?? 5 * 60_000
  const meta = await db.meta.get(collection)
  if (!meta || Date.now() - meta.cachedAt > ttl) return null
  return table.toArray()
}

export async function dexieWrite<T>(
  table: Table<T, string>,
  collection: string,
  records: T[]
): Promise<void> {
  await db.transaction('rw', table, db.meta, async () => {
    await table.clear()
    if (records.length > 0) await table.bulkPut(records)
    await db.meta.put({ id: collection, cachedAt: Date.now() })
  })
}

export async function dexieInvalidate(collection: string): Promise<void> {
  await db.meta.delete(collection)
}
