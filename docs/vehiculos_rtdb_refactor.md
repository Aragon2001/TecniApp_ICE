# Vehículos RTDB Multi-Instance (datosgenerales)

## A) JSON ejemplo completo en `/vehiculos/{vehiculoId}`

```json
{
  "vehiculos": {
    "vehiculo_123": {
      "meta": {
        "id": 123,
        "placa": 240123,
        "agencia": "Liberia",
        "subregion": "chorotega",
        "estado": "activo",
        "tipo": "pickup",
        "updatedAt": 1739290000000
      },
      "odometro": {
        "km_actual": 154322.7,
        "version": 48,
        "updatedAt": 1739290000000,
        "updatedBy": "uid_tecnico_01",
        "origen": "mantenimiento"
      },
      "historial": {
        "lecturasKm": {
          "evt_001": {
            "eventoId": "evt_001",
            "tipo": "etm",
            "km": 154322.7,
            "refPath": "etm/ot_8891",
            "fuera_de_rango": false,
            "odometroVersion": 48,
            "km_actual_snapshot": 154322.7,
            "createdBy": "uid_tecnico_01",
            "createdAt": 1739290000000
          }
        },
        "mantenimientos": {
          "mant_100": {
            "tipo": "preventivo",
            "valorAlMomento": 154300.1,
            "proximoKm": 159300.1,
            "refPath": "pm/planillas/mant_100",
            "updatedBy": "uid_tecnico_01",
            "updatedAt": 1739289900000
          }
        },
        "eventosETM": {
          "etm_778": {
            "etmId": "etm_778",
            "km_inicio": 154280.0,
            "km_fin": 154322.7,
            "refPath": "etm/ot_8891",
            "updatedBy": "uid_tecnico_01",
            "updatedAt": 1739290000000
          }
        },
        "eventosAverias": {
          "av_991": {
            "averiaId": "av_991",
            "km": 154310.0,
            "refPath": "averias/case_991",
            "updatedBy": "uid_tecnico_02",
            "updatedAt": 1739289950000
          }
        }
      }
    }
  }
}
```

## D) Checklist de archivos tocados

- `app/src/main/java/com/Arasoftsolutions/tecniapp_ice/Database/sync/RtdbInstances.kt`
- `app/src/main/java/com/Arasoftsolutions/tecniapp_ice/Database/sync/vehicle/FirebaseVehicleDataSource.kt`
- `app/src/main/java/com/Arasoftsolutions/tecniapp_ice/Database/sync/vehicle/VehicleRepository.kt`
- `app/src/main/java/com/Arasoftsolutions/tecniapp_ice/Database/sync/FirebaseSyncManager.kt`
- `app/src/main/java/com/Arasoftsolutions/tecniapp_ice/Database/room/RoomRepository.kt`
- `app/src/main/java/com/Arasoftsolutions/tecniapp_ice/ui/averias/AveriasRepository.kt`
- `app/src/main/java/com/Arasoftsolutions/tecniapp_ice/ui/vehiculo/worker/VehiculoSyncWorker.kt`
- `database.rules.json`

## D) Plan de migración (fuente única)

1. **Inventario de nodos legacy**
   - Leer candidatos legacy: `vehiculo_kilometrajes`, `vehiculo_mantenimientos`, nodos de ETM y averías con campos de km.
   - Resolver `vehiculoId` por placa normalizada o ID existente.

2. **Consolidación de km por vehículo**
   - Calcular `km_max = MAX(km_etm, km_averias, km_mantenimientos, km_lecturas)`.
   - Escribir en `/vehiculos/{vehiculoId}/odometro` con `km_actual = km_max`, `version = 1` si no existe.

3. **Backfill de historial**
   - Migrar eventos a:
     - `historial/lecturasKm/{eventoId}`
     - `historial/mantenimientos/{mantId}`
     - `historial/eventosETM/{etmId}`
     - `historial/eventosAverias/{averiaId}`
   - Si no existe vínculo exacto, usar `refPath = "legacy/<origen>/<id>"`.

4. **Cut-over de escritura**
   - Desactivar writers legacy (default RTDB) para vehículos.
   - Mantener solo writers en `https://tecniapp-ice-datosgenerales.firebaseio.com/vehiculos`.

5. **Verificación post-migración**
   - Muestrear vehículos y confirmar `km_actual` >= último km de eventos.
   - Repetir apertura de app y validar que no hay escrituras automáticas.

## E) Explicación de "una sola verdad" del km

- El único valor autoritativo será `odometro.km_actual`.
- Mantenimientos, ETM y averías son eventos inmutables/auditables en `historial/*`.
- Cada evento pasa por transacción sobre `odometro`, lo que elimina carreras entre técnicos concurrentes.
- Eventos con km menor no reducen el odómetro: quedan marcados `fuera_de_rango=true`.
- Solo admin, con motivo explícito, puede bajar km mediante transacción y con reglas RTDB.
