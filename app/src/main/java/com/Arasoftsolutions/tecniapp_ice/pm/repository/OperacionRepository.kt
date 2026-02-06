package com.Arasoftsolutions.tecniapp_ice.pm.repository

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.Arasoftsolutions.tecniapp_ice.pm.model.ActivityGroupInput
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncQueueType
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncStatus
import com.Arasoftsolutions.tecniapp_ice.pm.model.WorkLogInput
import com.Arasoftsolutions.tecniapp_ice.pm.model.dto.PlanillaDto
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.ActivityGroupEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.PlanillaEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.SyncQueueEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.WorkLogEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.mappers.toDto
import com.Arasoftsolutions.tecniapp_ice.pm.room.PmDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.io.File
import com.Arasoftsolutions.tecniapp_ice.pm.util.IdGenerator

class OperacionRepository(
    private val context: Context,
    private val database: PmDatabase,
    private val storageUploader: PlanillaStorageUploader = FakePlanillaStorageUploader(),
    planillasDbUrl: String = PLANILLAS_DB_URL
) {
    private val planillasDb: DatabaseReference = FirebaseDatabase.getInstance(planillasDbUrl).reference

    suspend fun createActivityGroup(input: ActivityGroupInput): ActivityGroupEntity {
        val ordenSapNormalized = normalizeOrdenSap(input.ordenSap, input.ordenSapManual)
            ?: throw IllegalArgumentException("Debe seleccionar una Orden SAP PM o ingresarla manualmente")

        val now = System.currentTimeMillis()
        val groupId = IdGenerator.deterministicId("group", input.uidTecnico, input.fechaDia, ordenSapNormalized.toString())

        val entity = ActivityGroupEntity(
            groupId = groupId,
            ordenSap = ordenSapNormalized,
            ordenSapManual = input.ordenSapManual,
            descripcionOrden = input.descripcionOrden,
            regionKey = input.regionKey,
            subregionKey = input.subregionKey,
            circuitoId = input.circuitoId,
            agenciaId = input.agenciaId,
            modulo = input.modulo,
            uidTecnico = input.uidTecnico,
            fechaDia = input.fechaDia,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
            retryCount = 0,
            lastError = null
        )

        database.activityGroupDao().upsert(entity)
        enqueueSync(SyncQueueType.GROUP, groupId, now)
        return entity
    }

    suspend fun addWorkLog(input: WorkLogInput): WorkLogEntity {
        val ordenSapNormalized = normalizeOrdenSap(input.ordenSap, input.ordenSapManual)
            ?: throw IllegalArgumentException("Debe seleccionar una Orden SAP PM o ingresarla manualmente")

        require(input.horaSaleMin > input.horaEntraMin) { "La hora de salida debe ser mayor a la hora de entrada" }
        val horasOrd = (input.horaSaleMin - input.horaEntraMin) - input.pausaMin
        require(horasOrd > 0) { "Las horas ordinarias deben ser mayores a cero" }

        val now = System.currentTimeMillis()
        val logId = IdGenerator.deterministicId("worklog", input.groupId, input.uidTecnico, input.fechaDia)

        val entity = WorkLogEntity(
            logId = logId,
            groupId = input.groupId,
            ordenSap = ordenSapNormalized,
            ordenSapManual = input.ordenSapManual,
            regionKey = input.regionKey,
            subregionKey = input.subregionKey,
            circuitoId = input.circuitoId,
            agenciaId = input.agenciaId,
            modulo = input.modulo,
            uidTecnico = input.uidTecnico,
            fechaDia = input.fechaDia,
            horaEntraMin = input.horaEntraMin,
            horaSaleMin = input.horaSaleMin,
            pausaMin = input.pausaMin,
            horasOrdMin = horasOrd,
            observaciones = input.observaciones,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
            retryCount = 0,
            lastError = null
        )

        database.workLogDao().upsert(entity)
        enqueueSync(SyncQueueType.WORKLOG, logId, now)
        return entity
    }

    suspend fun syncPendingGroups(regionKey: String, subregionKey: String) {
        val pending = database.activityGroupDao().getBySyncStatus(SyncStatus.PENDING)
        pending.forEach { group ->
            if (!shouldPushGroup(regionKey, subregionKey, group)) {
                markGroupError(group, IllegalStateException("Conflicto de updatedAt en ActivityGroup"))
                return@forEach
            }
            val dto = group.toDto()
            val updates = mapOf(
                groupPath(regionKey, subregionKey, group.groupId) to dto.toMap()
            )
            try {
                markGroupSyncing(group)
                planillasDb.updateChildren(updates).await()
                markGroupSynced(group)
                clearQueue(SyncQueueType.GROUP, group.groupId)
            } catch (ex: Exception) {
                markGroupError(group, ex)
                throw ex
            }
        }
    }

    suspend fun syncPendingWorkLogs(regionKey: String, subregionKey: String) {
        val pending = database.workLogDao().getBySyncStatus(SyncStatus.PENDING)
        pending.forEach { log ->
            if (!shouldPushWorkLog(regionKey, subregionKey, log)) {
                markWorkLogError(log, IllegalStateException("Conflicto de updatedAt en WorkLog"))
                return@forEach
            }
            val dto = log.toDto()
            val updates = buildWorkLogFanOut(regionKey, subregionKey, log, dto)
            try {
                markWorkLogSyncing(log)
                planillasDb.updateChildren(updates).await()
                markWorkLogSynced(log)
                clearQueue(SyncQueueType.WORKLOG, log.logId)
            } catch (ex: Exception) {
                markWorkLogError(log, ex)
                throw ex
            }
        }
    }

    suspend fun syncPendingPlanillas(regionKey: String, subregionKey: String) {
        val pending = database.planillaDao().getBySyncStatus(SyncStatus.PENDING)
        pending.forEach { planilla ->
            if (!shouldPushPlanilla(regionKey, subregionKey, planilla)) {
                markPlanillaError(planilla, IllegalStateException("Conflicto de updatedAt en Planilla"))
                return@forEach
            }
            val dto = PlanillaDto(
                planillaId = planilla.planillaId,
                fechaDia = planilla.fechaDia,
                uidTecnico = planilla.uidTecnico,
                regionKey = planilla.regionKey,
                subregionKey = planilla.subregionKey,
                mode = planilla.mode,
                totalHorasMin = planilla.totalHorasMin,
                logIds = planilla.logIdsCsv.split(',').filter { it.isNotBlank() },
                pdfRemoteUrl = planilla.pdfRemoteUrl,
                createdAt = planilla.createdAt,
                updatedAt = planilla.updatedAt
            )
            val updates = buildPlanillaFanOut(regionKey, subregionKey, dto)
            try {
                markPlanillaSyncing(planilla)
                planillasDb.updateChildren(updates).await()
                markPlanillaSynced(planilla)
                clearQueue(SyncQueueType.PLANILLA_PDF, planilla.planillaId)
            } catch (ex: Exception) {
                markPlanillaError(planilla, ex)
                throw ex
            }
        }
    }

    suspend fun generatePlanillaPdf(
        mode: String,
        uidTecnico: String,
        fechaDia: String,
        regionKey: String,
        subregionKey: String
    ): PlanillaEntity {
        val logs = database.workLogDao().getByUsuarioDia(uidTecnico, fechaDia)
        require(logs.isNotEmpty()) { "No hay worklogs para generar la planilla" }

        val totalHoras = logs.sumOf { it.horasOrdMin }
        val planillaId = IdGenerator.deterministicId("planilla", uidTecnico, fechaDia, mode)
        val pdfFile = createPdf(planillaId, uidTecnico, fechaDia, mode, logs)
        val remoteUrl = storageUploader.uploadPlanilla(planillaId, pdfFile)
        val now = System.currentTimeMillis()

        val entity = PlanillaEntity(
            planillaId = planillaId,
            fechaDia = fechaDia,
            uidTecnico = uidTecnico,
            regionKey = regionKey,
            subregionKey = subregionKey,
            mode = mode,
            totalHorasMin = totalHoras,
            logIdsCsv = logs.joinToString(",") { it.logId },
            pdfLocalPath = pdfFile.absolutePath,
            pdfRemoteUrl = remoteUrl,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
            retryCount = 0,
            lastError = null
        )

        database.planillaDao().upsert(entity)
        enqueueSync(SyncQueueType.PLANILLA_PDF, planillaId, now)
        return entity
    }

    private fun normalizeOrdenSap(ordenSap: String?, ordenSapManual: String?): Long? {
        val raw = ordenSap?.trim().takeIf { !it.isNullOrBlank() } ?: ordenSapManual?.trim()
        val numeric = raw?.filter { it.isDigit() }
        return numeric?.toLongOrNull()
    }

    private fun createPdf(
        planillaId: String,
        uidTecnico: String,
        fechaDia: String,
        mode: String,
        logs: List<WorkLogEntity>
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint().apply { textSize = 12f }

        var y = 40f
        canvas.drawText("Planilla $planillaId", 40f, y, paint)
        y += 20f
        canvas.drawText("Tecnico: $uidTecnico", 40f, y, paint)
        y += 20f
        canvas.drawText("Fecha: $fechaDia", 40f, y, paint)
        y += 20f
        canvas.drawText("Modo: $mode", 40f, y, paint)
        y += 20f

        logs.forEach { log ->
            val line = "Orden ${log.ordenSap} - ${log.horasOrdMin} min"
            canvas.drawText(line, 40f, y, paint)
            y += 16f
            if (y > 800f) {
                y = 40f
            }
        }

        document.finishPage(page)

        val dir = File(context.filesDir, "planillas")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "$planillaId.pdf")
        file.outputStream().use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    private suspend fun enqueueSync(type: SyncQueueType, entityId: String, now: Long) {
        val queueDao = database.syncQueueDao()
        val existing = queueDao.getByTypeEntity(type, entityId)
        if (existing != null) return
        val queue = SyncQueueEntity(
            queueId = IdGenerator.deterministicId("queue", type.name, entityId, now.toString()),
            type = type,
            entityId = entityId,
            status = SyncStatus.PENDING,
            retryCount = 0,
            lastError = null,
            createdAt = now,
            availableAt = now
        )
        queueDao.upsert(queue)
    }

    private suspend fun clearQueue(type: SyncQueueType, entityId: String) {
        val queue = database.syncQueueDao().getByTypeEntity(type, entityId) ?: return
        database.syncQueueDao().deleteById(queue.queueId)
    }

    private suspend fun markGroupSyncing(group: ActivityGroupEntity) {
        database.activityGroupDao().update(group.copy(syncStatus = SyncStatus.SYNCING))
    }

    private suspend fun markGroupSynced(group: ActivityGroupEntity) {
        database.activityGroupDao().update(group.copy(syncStatus = SyncStatus.SYNCED, retryCount = 0, lastError = null))
    }

    private suspend fun markGroupError(group: ActivityGroupEntity, ex: Exception) {
        database.activityGroupDao().update(
            group.copy(
                syncStatus = SyncStatus.ERROR,
                retryCount = group.retryCount + 1,
                lastError = ex.message
            )
        )
    }

    private suspend fun markWorkLogSyncing(log: WorkLogEntity) {
        database.workLogDao().update(log.copy(syncStatus = SyncStatus.SYNCING))
    }

    private suspend fun markWorkLogSynced(log: WorkLogEntity) {
        database.workLogDao().update(log.copy(syncStatus = SyncStatus.SYNCED, retryCount = 0, lastError = null))
    }

    private suspend fun markWorkLogError(log: WorkLogEntity, ex: Exception) {
        database.workLogDao().update(
            log.copy(
                syncStatus = SyncStatus.ERROR,
                retryCount = log.retryCount + 1,
                lastError = ex.message
            )
        )
    }

    private suspend fun markPlanillaSyncing(planilla: PlanillaEntity) {
        database.planillaDao().update(planilla.copy(syncStatus = SyncStatus.SYNCING))
    }

    private suspend fun markPlanillaSynced(planilla: PlanillaEntity) {
        database.planillaDao().update(planilla.copy(syncStatus = SyncStatus.SYNCED, retryCount = 0, lastError = null))
    }

    private suspend fun markPlanillaError(planilla: PlanillaEntity, ex: Exception) {
        database.planillaDao().update(
            planilla.copy(
                syncStatus = SyncStatus.ERROR,
                retryCount = planilla.retryCount + 1,
                lastError = ex.message
            )
        )
    }

    private suspend fun shouldPushGroup(regionKey: String, subregionKey: String, group: ActivityGroupEntity): Boolean {
        val path = groupPath(regionKey, subregionKey, group.groupId)
        val remoteUpdatedAt = planillasDb.child(path).child("updatedAt").get().await()
            .getValue(Long::class.java)
        return remoteUpdatedAt == null || group.updatedAt >= remoteUpdatedAt
    }

    private suspend fun shouldPushWorkLog(regionKey: String, subregionKey: String, log: WorkLogEntity): Boolean {
        val path = workLogPath(regionKey, subregionKey, log.logId)
        val remoteUpdatedAt = planillasDb.child(path).child("updatedAt").get().await()
            .getValue(Long::class.java)
        return remoteUpdatedAt == null || log.updatedAt >= remoteUpdatedAt
    }

    private suspend fun shouldPushPlanilla(regionKey: String, subregionKey: String, planilla: PlanillaEntity): Boolean {
        val path = planillaPath(regionKey, subregionKey, planilla.fechaDia, planilla.mode, planilla.planillaId)
        val remoteUpdatedAt = planillasDb.child(path).child("updatedAt").get().await()
            .getValue(Long::class.java)
        return remoteUpdatedAt == null || planilla.updatedAt >= remoteUpdatedAt
    }

    private fun buildWorkLogFanOut(
        regionKey: String,
        subregionKey: String,
        log: WorkLogEntity,
        dto: com.Arasoftsolutions.tecniapp_ice.pm.model.dto.WorkLogDto
    ): Map<String, Any?> {
        val basePath = workLogPath(regionKey, subregionKey, log.logId)
        val indexBase = indexPath(regionKey, subregionKey)
        val day = log.fechaDia

        return mapOf(
            basePath to dto.toMap(),
            "$indexBase/workLogsPorUsuarioDia/${log.uidTecnico}/$day/${log.logId}" to true,
            "$indexBase/workLogsPorOrdenDia/${log.ordenSap}/$day/${log.logId}" to true,
            "$indexBase/workLogsPorCircuitoDia/${log.circuitoId ?: "sin_circuito"}/$day/${log.logId}" to true,
            "$indexBase/workLogsPorAgenciaDia/${log.agenciaId ?: "sin_agencia"}/$day/${log.logId}" to true,
            "$indexBase/workLogsPorModuloDia/${log.modulo ?: "sin_modulo"}/$day/${log.logId}" to true,
            "$indexBase/workLogsPorGroup/${log.groupId}/${log.logId}" to true
        )
    }

    private fun buildPlanillaFanOut(
        regionKey: String,
        subregionKey: String,
        dto: PlanillaDto
    ): Map<String, Any?> {
        val basePath = planillaPath(regionKey, subregionKey, dto.fechaDia, dto.mode, dto.planillaId)
        val byUser = planillaByUserPath(regionKey, subregionKey, dto.fechaDia, dto.uidTecnico, dto.planillaId)
        val indexBase = indexPath(regionKey, subregionKey)
        return mapOf(
            basePath to dto.toMap(),
            byUser to dto.toMap(),
            "$indexBase/planillasPorUsuarioDia/${dto.uidTecnico}/${dto.fechaDia}/${dto.planillaId}" to true,
            "$indexBase/planillasPorDia/${dto.fechaDia}/${dto.planillaId}" to true
        )
    }

    private fun groupPath(regionKey: String, subregionKey: String, groupId: String): String =
        "pm_operacion/$regionKey/$subregionKey/activityGroups/$groupId"

    private fun workLogPath(regionKey: String, subregionKey: String, logId: String): String =
        "pm_operacion/$regionKey/$subregionKey/workLogs/$logId"

    private fun planillaPath(regionKey: String, subregionKey: String, fechaDia: String, mode: String, planillaId: String): String =
        "pm_operacion/$regionKey/$subregionKey/planillasDiarias/$fechaDia/$mode/$planillaId"

    private fun planillaByUserPath(
        regionKey: String,
        subregionKey: String,
        fechaDia: String,
        uid: String,
        planillaId: String
    ): String =
        "pm_operacion/$regionKey/$subregionKey/planillasDiariasByUser/$fechaDia/$uid/$planillaId"

    private fun indexPath(regionKey: String, subregionKey: String): String =
        "pm_operacion/$regionKey/$subregionKey/index"

    companion object {
        const val PLANILLAS_DB_URL = "https://tecniapp-ice-planilla.firebaseio.com/"
    }
}
