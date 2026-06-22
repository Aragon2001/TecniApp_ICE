import { useLiveQuery } from 'dexie-react-hooks'
import { ref, update } from 'firebase/database'
import toast from 'react-hot-toast'
import { db, dexieInvalidate, type SyncQueueItem } from './db'
import { queryClient } from './queryClient'
import { rtdbAverias, rtdbInventario } from '../firebase/config'
import { DatabaseReference } from 'firebase/database'

const MAX_RETRIES = 3

function getRtdbRef(rtdbPath: string, collectionKey: string): DatabaseReference | null {
  const rtdbMap: Record<string, any> = {
    averias: rtdbAverias,
    luminarias: rtdbInventario,
    inventario: rtdbInventario,
  }
  const rtdb = rtdbMap[collectionKey]
  if (!rtdb) return null
  return ref(rtdb, rtdbPath)
}

export async function addToQueue(
  item: Omit<SyncQueueItem, 'id' | 'createdAt' | 'retryCount' | 'status'>
): Promise<void> {
  await db.syncQueue.add({
    ...item,
    createdAt: Date.now(),
    retryCount: 0,
    status: 'pending',
  })
}

export async function processQueue(): Promise<void> {
  const items = await db.syncQueue
    .where('status')
    .anyOf(['pending', 'error'])
    .filter((i) => i.retryCount < MAX_RETRIES)
    .toArray()

  if (items.length === 0) return

  for (const item of items) {
    const dbRef = getRtdbRef(item.rtdbPath, item.collectionKey)
    if (!dbRef) {
      await db.syncQueue.update(item.id!, {
        status: 'error',
        errorMessage: `No RTDB mapping for key: ${item.collectionKey}`,
        retryCount: item.retryCount + 1,
      })
      continue
    }

    try {
      await update(dbRef, item.payload)
      await db.syncQueue.delete(item.id!)
      await dexieInvalidate(item.collectionKey)
      queryClient.invalidateQueries({ queryKey: [item.collectionKey] })
      toast.success(`Sincronizado: ${item.collectionKey}`, { id: `sync-ok-${item.id}` })
    } catch (err: any) {
      await db.syncQueue.update(item.id!, {
        status: 'error',
        errorMessage: err?.message ?? 'Error desconocido',
        retryCount: item.retryCount + 1,
      })
      toast.error(`Error sincronizando: ${item.collectionKey}`, { id: `sync-err-${item.id}` })
    }
  }
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
