package com.Arasoftsolutions.tecniapp_ice.pm.repository

import java.io.File

interface PlanillaStorageUploader {
    suspend fun uploadPlanilla(planillaId: String, file: File): String?
}

class FakePlanillaStorageUploader : PlanillaStorageUploader {
    override suspend fun uploadPlanilla(planillaId: String, file: File): String? {
        return "file://${file.absolutePath}"
    }
}
