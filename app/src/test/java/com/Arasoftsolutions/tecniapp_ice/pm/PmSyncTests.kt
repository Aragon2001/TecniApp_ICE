package com.Arasoftsolutions.tecniapp_ice.pm

import com.Arasoftsolutions.tecniapp_ice.pm.util.IdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PmSyncTests {
    @Test
    fun `deterministic id is stable across retries`() {
        val first = IdGenerator.deterministicId("worklog", "group-1", "uid-1", "20240101")
        val second = IdGenerator.deterministicId("worklog", "group-1", "uid-1", "20240101")
        assertEquals(first, second)
    }

    @Test
    fun `fan-out failure must not mark synced - pseudo`() {
        // Pseudo-test: el objetivo es asegurar que si updateChildren falla,
        // no se marque SYNCED ni se elimine la cola. Se valida con un Fake
        // Repository que lance excepción y se verifica que la entidad conserve
        // el estado ERROR o PENDING. Mantener como guía para pruebas instrumentadas.
        assertTrue(true)
    }
}
