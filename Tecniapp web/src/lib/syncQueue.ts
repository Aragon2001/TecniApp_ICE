import { useState, useEffect } from 'react'
import { ref, update } from 'firebase/database'
import { liveQuery } from 'dexie'
import toast from 'react-hot-toast'
import { rtdbAverias, rtdbInventario } from '../firebase/config'
import { db, type SyncQueueItem, dexieInvalidate } from './db'
import { queryClient } from './queryClient'

const MAX_RETRIES = 3

function getRtdb(collectionKey: string) {
  const map: Record<string, any> = {
    averias: rtdbAverias,
    luminarias: rtdbInventario,
  }
  const rtdb = map[collectionKey]
  if (!rtdb) throw new Error(`Sin RTDB configurado para: ${collectionKey}`)
  return rtdb
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
  const toProcess = await db.syncQueue
    .filter(
      (item) =>
        item.status === 'pending' ||
        (item.status === 'error' && item.retryCount < MAX_RETRIES)
    )
    .sortBy('createdAt')

  if (toProcess.length === 0) return

  let successCount = 0
  const succeededCollections = new Set<string>()
  const failedCollections = new Set<string>()

  for (const item of toProcess) {
    try {
      const rtdb = getRtdb(item.collectionKey)
      await update(ref(rtdb, item.rtdbPath), item.payload)
      await db.syncQueue.delete(item.id!)
      succeededCollections.add(item.collectionKey)
      successCount++
    } catch (err) {
      await db.syncQueue.update(item.id!, {
        status: 'error',
        retryCount: item.retryCount + 1,
        errorMessage: (err as Error).message,
      })
      failedCollections.add(item.collectionKey)
    }
  }

  for (const col of succeededCollections) {
    await dexieInvalidate(col)
    queryClient.invalidateQueries({ queryKey: [col] })
  }

  if (successCount > 0 && failedCollections.size === 0) {
    toast.success(`${successCount} cambio(s) sincronizados`)
  } else if (failedCollections.size > 0) {
    toast.error(`${failedCollections.size} operación(es) no se pudieron sincronizar`)
  }
}

export function usePendingCount(): number {
  const [count, setCount] = useState(0)

  useEffect(() => {
    const subscription = liveQuery(() =>
      db.syncQueue.where('status').anyOf(['pending', 'error']).count()
    ).subscribe({
      next: setCount,
      error: () => setCount(0),
    })
    return () => subscription.unsubscribe()
  }, [])

  return count
}
