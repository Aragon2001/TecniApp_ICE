package com.Arasoftsolutions.tecniapp_ice.pm.model.mappers

import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncStatus
import com.Arasoftsolutions.tecniapp_ice.pm.model.dto.ActivityGroupDto
import com.Arasoftsolutions.tecniapp_ice.pm.model.dto.PlanillaDto
import com.Arasoftsolutions.tecniapp_ice.pm.model.dto.WorkLogDto
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.ActivityGroupEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.PlanillaEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.WorkLogEntity

fun ActivityGroupEntity.toDto(): ActivityGroupDto = ActivityGroupDto(
    groupId = groupId,
    ordenSap = ordenSap,
    ordenSapManual = ordenSapManual,
    descripcionOrden = descripcionOrden,
    regionKey = regionKey,
    subregionKey = subregionKey,
    circuitoId = circuitoId,
    agenciaId = agenciaId,
    modulo = modulo,
    uidTecnico = uidTecnico,
    fechaDia = fechaDia,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun WorkLogEntity.toDto(): WorkLogDto = WorkLogDto(
    logId = logId,
    groupId = groupId,
    ordenSap = ordenSap,
    ordenSapManual = ordenSapManual,
    regionKey = regionKey,
    subregionKey = subregionKey,
    circuitoId = circuitoId,
    agenciaId = agenciaId,
    modulo = modulo,
    uidTecnico = uidTecnico,
    fechaDia = fechaDia,
    horaEntraMin = horaEntraMin,
    horaSaleMin = horaSaleMin,
    pausaMin = pausaMin,
    horasOrdMin = horasOrdMin,
    observaciones = observaciones,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PlanillaEntity.toDto(): PlanillaDto = PlanillaDto(
    planillaId = planillaId,
    fechaDia = fechaDia,
    uidTecnico = uidTecnico,
    regionKey = regionKey,
    subregionKey = subregionKey,
    mode = mode,
    totalHorasMin = totalHorasMin,
    logIds = logIdsCsv.split(',').filter { it.isNotBlank() },
    pdfRemoteUrl = pdfRemoteUrl,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PlanillaDto.toEntity(
    pdfLocalPath: String,
    syncStatus: SyncStatus,
    retryCount: Int,
    lastError: String?
): PlanillaEntity = PlanillaEntity(
    planillaId = planillaId,
    fechaDia = fechaDia,
    uidTecnico = uidTecnico,
    regionKey = regionKey,
    subregionKey = subregionKey,
    mode = mode,
    totalHorasMin = totalHorasMin,
    logIdsCsv = logIds.joinToString(","),
    pdfLocalPath = pdfLocalPath,
    pdfRemoteUrl = pdfRemoteUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    retryCount = retryCount,
    lastError = lastError
)
