package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.BuildConfig

/**
 * Logger de diagnóstico para las rutas de sincronización (ver AUDITORIA.md §B3).
 *
 * Los niveles verbose/debug/info se emiten **solo en builds de depuración**
 * (`BuildConfig.DEBUG`), evitando I/O y construcción de strings en rutas calientes
 * de sincronización en producción. Warning y error se emiten siempre, porque son
 * útiles para diagnóstico de fallos reales (y para herramientas de crash reporting).
 *
 * Uso: reemplaza `Log.i(TAG, msg)` por `SyncLog.i(TAG, msg)` en el código de sync.
 * Sustituye a los logs ad-hoc con tags `[INV_DIAG]`, `[LUM_SYNC]`, `[SYNC_SUBREGION]`,
 * `[SYNC_FLOW]`, etc. dejados de una sesión de debugging.
 */
internal object SyncLog {

    @JvmStatic
    fun v(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.v(tag, msg)
    }

    @JvmStatic
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    @JvmStatic
    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    @JvmStatic
    fun v(tag: String, msg: String, tr: Throwable) {
        if (BuildConfig.DEBUG) Log.v(tag, msg, tr)
    }

    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable) {
        if (BuildConfig.DEBUG) Log.d(tag, msg, tr)
    }

    @JvmStatic
    fun i(tag: String, msg: String, tr: Throwable) {
        if (BuildConfig.DEBUG) Log.i(tag, msg, tr)
    }

    // Warning/error se conservan en todos los builds.
    @JvmStatic
    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable) {
        Log.w(tag, msg, tr)
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
    }

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable) {
        Log.e(tag, msg, tr)
    }
}
