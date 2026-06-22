import { useLiveQuery } from 'dexie-react-hooks'
import { ref, update, set, remove } from 'firebase/database'
import toast from 'react-hot-toast'
import { db, dexieInvalidate, type SyncQueueItem } from './db'
import { queryClient } from './queryClient'
import {
  rtdbAverias,
  rtdbInventario,
  rtdbGeneral,
  rtdbUsers,
  rtdbMain,
  rtdbMateriales,
  rtdbProgramacion,
} from '../firebase/config'

const MAX_RETRIES = 3

// Maps collection keys to their RTDB instance
const RTDB_MAP: Record<string, any> = {
  averias: rtdbAverias,
  luminarias: rtdbInventario,
  inventario: rtdbInventario,
  vehiculos: rtdbGeneral,
  usuarios: rtdbUsers,
  localizaciones: rtdbMain,
  materiales: rtdbMateriales,
  programaciones: rtdbProgramacion,
  medidores: rtdbMain,
}

export type SyncOperation = 'update' | 'set' | 'delete'

export interface QueueItem extends Omit<SyncQueueItem, 'id' | 'createdAt' | 'retryCount' | 'status'> {
  operation?: SyncOperation
}

export async function addToQueue(item: QueueItem): Promise<void> {
  await db.syncQueue.add({
    ...item,
    createdAt: Date.now(),
    retryCount: 0,
    status: 'pending',
  } as SyncQueueItem)
}

export async function processQueue(): Promise<void> {
  const items = await db.syncQueue
    .where('status')
    .anyOf(['pending', 'error'])
    .filter((i) => i.retryCount < MAX_RETRIES)
    .toArray()

  if (items.length === 0) return

  for (const item of items) {
    const rtdb = RTDB_MAP[item.collectionKey]
    if (!rtdb) {
      await db.syncQueue.update(item.id!, {
        status: 'error',
        errorMessage: `Sin RTDB para: ${item.collectionKey}`,
        retryCount: item.retryCount + 1,
      })
      continue
    }

    const dbRef = ref(rtdb, item.rtdbPath)
    const operation: SyncOperation = (item as any).operation ?? 'update'

    try {
      if (operation === 'delete') {
        await remove(dbRef)
      } else if (operation === 'set') {
        await set(dbRef, item.payload)
      } else {
        await update(dbRef, item.payload)
      }

      await db.syncQueue.delete(item.id!)
      await dexieInvalidate(item.collectionKey)
      queryClient.invalidateQueries({ queryKey: [item.collectionKey] })
    } catch (err: any) {
      await db.syncQueue.update(item.id!, {
        status: 'error',
        errorMessage: err?.message ?? 'Error desconocido',
        retryCount: item.retryCount + 1,
      })
    }
  }

  // Show summary toast
  const remaining = await db.syncQueue.where('status').equals('error').count()
  const synced = items.length - remaining
  if (synced > 0) toast.success(`${synced} cambio${synced !== 1 ? 's' : ''} sincronizado${synced !== 1 ? 's' : ''}`)
  if (remaining > 0) toast.error(`${remaining} cambio${remaining !== 1 ? 's' : ''} pendiente${remaining !== 1 ? 's' : ''} por reintentar`)
}

export function usePendingCount(): number {
  return (
    useLiveQuery(
      () => db.syncQueue.where('status').anyOf(['pending', 'error']).count(),
      [],
      0
    ) ?? 0
  )
}
