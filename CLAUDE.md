# CLAUDE.md — TecniApp (Android)

**TecniApp** — desarrollado por **Arasoft Solutions** para el **Instituto Costarricense de Electricidad (ICE)**.

> Documento "segundo cerebro" para este repositorio. Objetivo: que cualquier persona (o Claude) pueda entender el 100% del proyecto — qué hace cada capa, cada módulo y cada archivo relevante — sin tener que leer el código fuente completo. Para hallazgos de calidad, bugs y mejoras ver `AUDITORIA.md`. Para navegación rápida ver `INDEX.md`.

Última sincronización de este documento con el código: 2026-07-09. Room `AppDatabase` en `SCHEMA_VERSION = 31` (este documento corrige la versión v27 que aparecía en la versión anterior de este archivo — ver `AUDITORIA.md` §Hallazgo A1). Trabajo activo actual: eficiencia de sync y consumo de Firebase (ver `AUDITORIA.md` §A9/A12/A13) — decisión tomada de quedarse en el plan **Blaze** manteniendo el consumo dentro de las cuotas gratuitas, no migrar a Spark (bloqueado estructuralmente por las 12 instancias RTDB y las 5 Cloud Functions).

---

## 1. Qué es este proyecto

TecniApp ICE es la app Android de campo para cuadrillas técnicas del ICE (Instituto Costarricense de Electricidad). Cubre seis dominios de negocio:

1. **Averías** — reporte, atención y cierre de casos de daños eléctricos (con evidencias, PDF, notificaciones push).
2. **Medidores** — catálogo/consulta de medidores por subregión/pueblo/localización.
3. **Luminarias** — reparación de alumbrado público (registro, materiales consumidos, machote de reporte).
4. **Inventario** — stock de materiales por vehículo, con ajustes por consumo.
5. **Vehículos** — bitácora (kilometraje, mantenimientos, registros diarios ETM).
6. **Programación / Planillas (módulo "PM")** — asignación de trabajo diario y generación de planillas en PDF. **Este módulo está incompleto/desconectado — ver `AUDITORIA.md` §Hallazgo A2.**

Es un proyecto **single-module** (`:app`), Kotlin, `compileSdk 34`, `minSdk 26`, JDK 17, sin arquitectura multi-módulo ni Hilt/Dagger (DI manual por singletons).

---

## 2. Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin (con algo de Java heredado en tests) |
| UI | Android Views + ViewBinding + DataBinding, Fragments + Jetpack Navigation (no Compose) |
| Persistencia local | Room (2 bases de datos independientes, ver §3) |
| Backend | Firebase: **12 instancias separadas de Realtime Database** (¡no 9! ver `log.md` §A4) + 1 Firestore + Cloud Functions (Node.js) + FCM + Auth. **Nota:** al 2026-07-08 el usuario planea eliminar y re-subir todas las RTDB para rediseñar reglas/estructura. |
| Concurrencia | Coroutines (`viewModelScope`, `applicationScope`, `Dispatchers.IO`), `StateFlow`/`LiveData` |
| Background work | WorkManager (`AveriasSyncWorker`, `PmSyncWorker`, `UpdateWorker`, `VehiculoReminderWorker`) |
| Mapas/ubicación | Google Play Services Maps + Location |
| Documentos | Apache POI (Excel), PdfBox-Android / `PdfDocument` nativo (PDF) |
| Networking REST | Retrofit + Moshi (KSP) — para `IceApi` (endpoint externo del ICE, ver `BuildConfig.ICE_BASE_URL`) |
| Actualizaciones OTA | Chequeo contra `update.json` en GitHub raw (`UPDATE_JSON_URL`), descarga e instalación manual del APK |

---

## 3. Arquitectura de datos — el punto más importante de todo el proyecto

### 3.1 Patrón general

```
UI (Fragment/Activity, ViewBinding)
   ↕ StateFlow / LiveData
ViewModel (viewModelScope)
   ↕ suspend fun
Repository (RoomRepository / AveriasRepository / ProgramacionRepository / OperacionRepository)
   ↕                                  ↕
Room (SQLite local)          FirebaseSyncManager (Realtime DB, por dominio)
```

Es "offline-first" **en la lectura** (Room siempre es la fuente de UI) pero la escritura es mayormente **online-first en el mismo hilo de la operación**: cada `RoomRepository.guardarX(...)` escribe primero a Firebase y luego a Room (o viceversa según el método), sin una cola de reintentos centralizada para el módulo principal (sí existe una cola para el módulo PM, ver 3.4).

### 3.2 Dos bases de datos Room independientes

- **`AppDatabase`** (`Database/room/AppDatabase.kt`) — `SCHEMA_VERSION = 31`. Contiene: usuarios, regiones, agencias, subregiones, localizaciones, medidores, pueblos, vehículos + bitácora (`vehiculo_log`), materiales, técnicos, averías, inventario, luminarias, programaciones (+ fotos), reportes generados. Migraciones explícitas 24→31; `fallbackToDestructiveMigrationFrom(true, 23)` para instalaciones muy antiguas. Singleton vía `getInstance(context)`.
- **`PmDatabase`** (`pm/room/PmDatabase.kt`) — `version = 1`, **sin migraciones** (proyecto nuevo, sin historial de esquema todavía). Entidades: `OrdenSapEntity`, `CatalogSyncMetaEntity`, `ActivityGroupEntity`, `WorkLogEntity`, `PlanillaEntity`, `SyncQueueEntity`, `SyncErrorLogEntity`. **No tiene un singleton `getInstance()` como `AppDatabase`** — se instancia con `Room.databaseBuilder(...).build()` directamente dentro de `PmSyncWorker` cada vez que corre el worker (ver `AUDITORIA.md` §Hallazgo B4).

### 3.3 Topología multi-base de datos de Firebase (CRÍTICO para el proyecto web)

La app **no usa una sola Realtime Database**: usa una instancia separada por dominio (sharding manual, probablemente para evitar límites de tamaño/rendimiento de un único RTDB). Esto es la pieza de arquitectura más importante para portar a la web, y debe replicarse 1:1:

| Firebase RTDB (URL) | Dominio | Usado por (Android) |
|---|---|---|
| `tecniapp-ice-default-rtdb` (default) | Medidores, Localizaciones, Pueblos | `RoomRepository`, `FirebaseSyncManager` |
| `tecniapp-ice` (target `localizaciones` en `firebase.json`) | Localizaciones (posible duplicado del anterior) | Ver `AUDITORIA.md` §Hallazgo A6 |
| `tecniapp-ice-user` | Usuarios, tokens FCM | `RoomRepository.obtenerUsuario`, Cloud Functions |
| `tecniapp-ice-datosgenerales` | Agencias, Subregiones, Vehículos (catálogo) | `FirebaseSyncManager.obtenerAgencias/obtenerVehiculos` |
| `tecniapp-ice-personal` | Técnicos | `FirebaseSyncManager.obtenerTecnicos` |
| `tecniapp-ice-materiales` | Catálogo de materiales | `FirebaseSyncManager.obtenerMaterialesCatalogo` |
| `tecniapp-ice-averias` | Casos de avería | `AveriasRepository`/`AveriasSyncWorker`, Cloud Functions `syncAveriasYNotificar` |
| (nodo `/luminarias` en RTDB, ruta específica por agencia) | Reparaciones de luminarias | `FirebaseSyncManager.guardarReparacionLuminaria` |
| (nodo `/inventario`) | Inventario por vehículo | `FirebaseSyncManager` realtime listeners |
| `tecniapp-ice-planilla` (`https://tecniapp-ice-planilla.firebaseio.com/`) | Módulo PM (grupos de actividad, worklogs, planillas) — **Realtime Database**, no Firestore | `OperacionRepository` |

Además existe **Cloud Firestore** (`projectId` del mismo proyecto Firebase) usado únicamente por el módulo PM según el `CLAUDE.md` anterior — pero la evidencia real en código Android (`OperacionRepository`) muestra que el módulo PM en Android escribe a **Realtime Database** (`pm_operacion/...`), no a Firestore. Firestore solo aparece consumido desde el lado **web** (`pm_planillas`). Esto es una inconsistencia real entre plataformas — ver `AUDITORIA.md` §Hallazgo A3 (el hallazgo más grave del proyecto).

**Reglas de seguridad:** solo hay UN archivo `database.rules.json` en el repo, aplicado a 2 de los ~9 targets (`default` y `localizaciones`) según `firebase.json`. Las reglas de las otras ~7 bases (`user`, `datosgenerales`, `personal`, `materiales`, `averias`, `planilla`, y los nodos de inventario/luminarias si viven en bases propias) **no están versionadas en este repositorio**. Ver `AUDITORIA.md` §Hallazgo A4 (seguridad).

### 3.4 Sincronización

- **Módulo principal:** `Synchronizer.syncSubregion(...)` orquesta: técnicos → materiales → `RoomRepository.syncSubregion` (agencias/pueblos/localizaciones/vehículos/medidores por lotes) → inventario del vehículo del usuario → luminarias de la agencia del usuario. Usa `AppSyncCoordinator.runExclusive` para evitar sincronizaciones concurrentes. Push periódico independiente vía `AveriasSyncWorker` (`PeriodicWorkRequestBuilder`, cada 15 min, más disparo manual `triggerNow`).
- **Realtime listeners:** `RoomRepository.startRealtimeSyncForScope/stopRealtimeSync` — escuchan cambios en vivo de inventario y luminarias del vehículo/agencia del usuario mientras la app está en foreground (`ProcessLifecycleOwner`, registrado en `TecniApp.onCreate`).
- **Módulo PM:** cola propia (`SyncQueueEntity` con índice único `(type, entityId)`, `SyncStatus` PENDING/SYNCING/SYNCED/ERROR) + `RetryPolicy` (backoff exponencial con jitter, base 5s, máx 10 min) + `PmSyncManager.processQueue` ejecutado por `PmSyncWorker` (encolado por `PmSyncScheduler.enqueue`, con restricción de red). **Esta cola nunca se dispara en la práctica — ver `AUDITORIA.md` §Hallazgo A2.**
- Existe también `Database/sync/SyncWorker.kt`, una clase de Worker que **no se usa en ningún lugar del código** (código muerto, reemplazado por `AveriasSyncWorker`).

### 3.5 Repositorios — mapa de responsabilidades

| Repositorio | Archivo | Responsabilidad | Observación |
|---|---|---|---|
| `RoomRepository` | `Database/room/RoomRepository.kt` (1554 líneas) | Repositorio "Dios": vehículos, bitácora, medidores, localizaciones, inventario, luminarias, sincronización de catálogos y de subregión, realtime listeners | Mezcla ~15 dominios en una sola clase. Ver `AUDITORIA.md` §Hallazgo B1 |
| `FirebaseSyncManager` | `Database/sync/FirebaseSyncManager.kt` (1944 líneas) | Toda la I/O contra Firebase Realtime Database (multi-instancia) | Clase más grande del proyecto; mezcla parsing de snapshots, normalización de claves y lógica de negocio |
| `AveriasRepository` | `ui/averias/AveriasRepository.kt` (1535 líneas) | CRUD y consultas de averías sobre Room | Independiente de `RoomRepository` |
| `ProgramacionRepository` | `ui/programacion/ProgramacionRepository.kt` | CRUD de programaciones sobre Room | |
| `OperacionRepository` | `pm/repository/OperacionRepository.kt` | CRUD + fan-out a Firebase RTDB del módulo PM (grupos de actividad, worklogs, planillas PDF) | Usado solo por `PmSyncWorker`; sin consumidor de UI real |
| `CatalogRepository` | `pm/repository/CatalogRepository.kt` | Catálogos del módulo PM (órdenes SAP, etc.) | |

---

## 4. Entry points y ciclo de vida de la app

- **`TecniApp.kt`** (Application) — habilita canal de notificaciones, `NetworkAlertManager`, `NetworkHealthMonitor`, persistencia offline de Firebase (solo para 4 de las 9 bases, ver comentario en código: se evita en medidores/localizaciones/inventario/averías/luminarias "para prevenir OOM"), tema oscuro desde `DataStoreManager`, agenda `AveriasSyncWorker` y `VehiculoReminderWorker` si hay sesión, agenda `UpdateWorker`, y registra un observer de ciclo de vida de proceso que arranca/detiene los realtime listeners de `RoomRepository`.
- **`SplashActivity.kt`** → decide ir a `LoginActivity` o `ActivityMain` según sesión (`SessionManager`).
- **`LoginActivity.kt`** (424 líneas) — login con Firebase Auth, dispara `AveriasSyncWorker.triggerNow` tras autenticar.
- **`RegistroActivity.kt`** + **`registro/Paso1..4Fragment.kt`** + `RegistroViewModel` — wizard de registro de nuevo usuario en 4 pasos.
- **`ActivityMain.kt`** (517 líneas) — host principal: `DrawerLayout` + `BottomNavigationView` + `NavController` sobre `mobile_navigation.xml`; observa el estado del `WorkManager` para reflejar progreso de sincronización manual.
- **`permissions/PermissionsActivity.kt`** + `PermissionInitializer` — pantalla/flujo de permisos runtime (ubicación, notificaciones, etc.) antes de entrar a la app.
- **`ui/onboarding/OnboardingActivity.kt`** — carrusel de bienvenida (`OnboardingAdapter`, `OnboardingPage`).

---

## 5. Inventario de módulos (package por package)

> Cada fila resume el propósito de un archivo. Para el detalle línea a línea de los archivos más grandes/críticos, ver §3 y `AUDITORIA.md`.

### 5.1 `Database/entities/` — modelos de Room (AppDatabase)
`AgenciaEntity`, `AveriaEntity`, `InventarioEntities.kt` (contiene `InventarioItemEntity` + proyección `InventarioConVehiculo`), `LocalizacionesEntity`, `LuminariaEntities.kt` (`LuminariaReparacionEntity` + enum `LuminariaEstado`), `MaterialEntity`, `MedidorEntity`, `ProgramacionEntity` (+ `ProgramacionFotoEntity`), `PueblosEntity`, `RegionEntity`, `ReporteGeneradoEntity`, `SubregionesEntity`, `TecnicoEntity`, `UserEntity`, `VehiculoEntity` (tabla `vehiculos`, clase de dominio real `VehiculosEntity`), `VehiculoLogEntity` (bitácora unificada: KM, DIARIO, MANTENIMIENTO, todo serializado como `payloadJson`).

### 5.2 `Database/room/` — DAOs + AppDatabase + RoomRepository
Un DAO por entidad (`AgenciaDao`, `AveriaDao`, `InventarioDao`, `LocalizacionDao`, `LuminariaReparacionDao`, `MaterialDao`, `MedidorDao`, `ProgramacionDao`, `PuebloDao`, `RegionDao`, `ReporteGeneradoDao`, `SubregionDao`, `TecnicoDao`, `UsuarioDao`, `VehiculoDao`, `VehiculoLogDao`) — CRUD + queries `Flow` reactivas. `AppDatabase.kt` y `RoomRepository.kt` descritos en §3.2/§3.5.

### 5.3 `Database/sync/`
- `FirebaseSyncManager.kt` — ver §3.5.
- `Synchronizer.kt` — orquestador de sync de subregión, ver §3.4.
- `AppSyncCoordinator.kt` — mutex/exclusión mutua para que solo corra un sync a la vez.
- `SubregionNormalizer.kt` — normaliza IDs/nombres de subregión (mapea variantes de escritura a un ID canónico).
- `SyncStatus.kt` — constantes de estado de sync usadas por el módulo principal (`"PENDING"`, `"SYNCED"`, etc. como strings, no enum — inconsistente con el enum `SyncStatus` del módulo `pm`).
- `SyncWorker.kt` — **código muerto** (ver §3.4).

### 5.4 `pm/` — módulo Programación (PM)
- `model/` — `ActivityGroupInput`, `WorkLogInput` (inputs de UI/casos de uso), `RoomConverters` (TypeConverters de Room para el enum `SyncStatus`/`SyncQueueType`), `SyncQueueType` (enum: GROUP, WORKLOG, PLANILLA_PDF), `SyncStatus` (enum: PENDING/SYNCING/SYNCED/ERROR).
- `model/dto/` — `ActivityGroupDto`, `PlanillaDto`, `WorkLogDto` — forma serializada para el fan-out a Firebase RTDB.
- `model/entities/` — `ActivityGroupEntity`, `CatalogSyncMetaEntity`, `OrdenSapEntity`, `PlanillaEntity`, `SyncErrorLogEntity`, `SyncQueueEntity`, `WorkLogEntity` — tablas de `PmDatabase`.
- `model/mappers/PmMappers.kt` — `toDto()` extension functions entidad → DTO.
- `repository/CatalogRepository.kt` — catálogo de órdenes SAP.
- `repository/OperacionRepository.kt` — ver §3.5; genera PDFs de planilla con `PdfDocument` nativo (layout muy básico, texto plano) y sube el archivo vía `PlanillaStorageUploader` — cuya única implementación real en el repo es `FakePlanillaStorageUploader`, que **no sube nada a ningún storage**, solo devuelve la ruta local como si fuera una URL (`file://...`). Ver `AUDITORIA.md` §Hallazgo A2.
- `repository/PlanillaStorageUploader.kt` — interfaz + fake.
- `room/PmDatabase.kt`, `room/dao/*` — ver §3.2.
- `sync/PmSyncManager.kt` — procesa la cola por tipo (GROUP, WORKLOG, PLANILLA_PDF), marca error + backoff con `RetryPolicy` y registra en `SyncErrorLogEntity`.
- `sync/PmSyncScheduler.kt` — encola `PmSyncWorker` único (`enqueueUniqueWork`, `ExistingWorkPolicy.KEEP`). **Nunca invocado** desde ningún otro archivo del proyecto.
- `sync/PmSyncWorker.kt` — `CoroutineWorker` que abre una instancia nueva de `PmDatabase` (no singleton) en cada ejecución y la cierra al final.
- `sync/RetryPolicy.kt` — backoff exponencial `5s * 2^min(retry,6)` + jitter aleatorio 0–2s, tope 10 min.
- `util/IdGenerator.kt` — IDs determinísticos (hash de campos concatenados) para evitar duplicados al reintentar.

### 5.5 `ui/averias/` — módulo de averías (el más grande y más maduro)
- `AveriasFragment.kt` (957) / `AveriasViewModel.kt` (1458) / `AveriasRepository.kt` (1535) — listado, filtros por fecha/estado, StateFlow de UI.
- `AveriaDetalleBottomSheet.kt` (2181 líneas — **el archivo más grande de todo el proyecto**) — formulario de detalle/atención de una avería (lecturas de medidor, evidencias, cambio de medidor, etc.).
- `AveriasMapFragment.kt` — mapa de averías (Google Maps).
- `AveriasAdapter.kt`, `AveriaActionData.kt`, `AveriaDraft.kt`, `AveriaUI.kt` — modelos/adaptadores de lista.
- `AveriasSyncWorker.kt` — Worker periódico (15 min) + `triggerNow` manual; único mecanismo de sync real y activo del proyecto.
- `AveriaNotificationDispatcher.kt`, `AveriaNotifications.kt`, `AveriaNotificationPreferences.kt`, `AveriaNotificationFilters.kt` — notificaciones locales de nuevas averías/cambios, con preferencias de usuario.
- `AveriaDeepLink.kt` — deep links a un caso específico (desde notificación).
- `AveriaMapLauncher.kt`, `AveriaMapPreviewRenderer.kt`, `AveriaStaticMapProvider.kt` — utilidades de mapas estáticos/preview.
- `PdfGenerator.kt` (1190) — generación del PDF de reporte de avería.
- `AveriaTextUtils.kt`, `AveriasForegroundTracker.kt` — utilidades varias y tracking de si la pantalla de averías está en foreground (para decidir si mostrar notificación).

### 5.6 `ui/luminarias/`
`LuminariasFragment.kt` (1273) / `LuminariasViewModel.kt` (719) — listado pendientes/reparadas con permisos por rol (`LuminariasPermissions.kt`). `LuminariaReparacionAdapter.kt`, `LuminariaMaterialAdapter.kt`, `LuminariaMaterialSerializer.kt` (serializa lista de materiales usados a JSON dentro de `materialesJson`). `LuminariaCsvRegistro.kt` — carga masiva desde CSV. `LuminariaMachoteExporter.kt` — exporta "machote" (plantilla) de reporte. `LuminariaDeepLink.kt`.

### 5.7 `ui/medidor/`, `ui/inventario/`, `ui/localizacion/`, `ui/materiales/`
- `MedidorFragment/ViewModel` — búsqueda/alta de medidores.
- `InventarioFragment/ViewModel/Adapter` — inventario por vehículo, `InventarioMensaje` (sealed class de eventos UI).
- `LocalizacionFragment.kt` (1601 líneas) — el segundo Fragment más grande del proyecto; combina mapa, sensores (`SensorEventListener`, probablemente brújula/orientación) y selección de localización.
- `MaterialMetadataRules.kt` — reglas de negocio sobre metadatos de materiales.

### 5.8 `ui/vehiculo/`
`MiVehiculoFragment/ViewModel` — bitácora del vehículo del usuario (kilometraje, mantenimientos, registros diarios ETM). `FirebaseVehicleDataSource.kt` — acceso directo a Firebase para pull de vehículo (usado por `RoomRepository.syncVehiculoDesdeFirebase`). `EtmRegistroRules.kt`, `RegistroDiarioVehiculo.kt`, `VehiculoRegistrosModels.kt`, `TipoVehiculo.kt` (enum con flags `usaKilometraje`/`usaOrimetro`), `VehiculoPlacaUtils.kt` (parseo de placa string↔long), `VehiculoSyncService.kt`, `RegistroVehiculoDialogFragment.kt` + `RegistroVehiculoPendienteDialogExt.kt`. `worker/VehiculoReminderWorker.kt` — recordatorio diario de registro (`scheduleDaily`).

### 5.9 `ui/programacion/`
`ProgramacionFragment/ViewModel/Repository/Adapter`, `NuevaProgramacionFragment.kt`, `ProgramacionDetalleBottomSheet.kt`, `LocalizacionUtils.kt`, `SimpleItemSelectedListener.kt`. **Nota:** este es el módulo "programación" del `AppDatabase` (v26+), **distinto** del módulo `pm/ui/planillas` (ver 5.10) — dos sistemas de programación de trabajo coexisten en el proyecto con nombres parecidos, ver `AUDITORIA.md`.

### 5.10 `ui/planillas/` — capa de UI del módulo PM
`ActivityGroupsManagerFragment.kt`, `PlanillaDetailFragment.kt`, `PlanillasHomeFragment.kt`, `WorkLogsDetailFragment.kt`, `sheets/WorkLogEditBottomSheet.kt`, `adapter/{ActivityGroupsAdapter,PlanillasAdapter,WorkLogsAdapter}.kt`, `model/PlanillasUiModels.kt`, `viewmodel/PlanillasViewModel.kt`. **Todos sus métodos de negocio son cuerpos vacíos con comentario `// TODO`** — no hay una sola llamada real a `OperacionRepository` desde esta capa. Nav graph propio: `res/navigation/nav_planillas.xml`.

### 5.11 `ui/reportes/`
`ReportesFragment.kt` (944) / `ReportesViewModel.kt` (1871 líneas — el ViewModel más grande del proyecto) — generación de reportes (KPIs, bitácora de eventos, consumo de inventario, mis averías/luminarias). `ExcelReportExporter.kt` (Apache POI), `PdfReportExporter.kt`, `ReportDownloadNotifier.kt`, `ReportHistoryAdapter.kt`, y ~6 adapters de `ListAdapter` para las distintas secciones del reporte (`BitacoraEventosAdapter`, `DescargoMaterialAdapter`, `InventarioConsumoAdapter`, `InventarioReporteAdapter`, `MisAveriasAdapter`, `MisLuminariasAdapter`, `ResumenKpiAdapter`).

### 5.12 `ui/admin/`
`AdminManagementFragment.kt` (1483) / `AdminManagementViewModel.kt` (580) — panel de administración (gestión de usuarios/catálogos). `MapCoordinatePickerBottomSheet.kt` — selector de coordenadas en mapa para altas administrativas.

### 5.13 `ui/home/`, `ui/settings/`, `ui/help/`, `ui/legal/`, `ui/policies/`, `ui/onboarding/`, `ui/modal/`, `ui/common/`
`HomeFragment/ViewModel` — dashboard inicial. `SettingsFragment.kt` — ajustes (tema oscuro vía `DataStoreManager`, notificaciones). `HelpFragment.kt`, `PoliciesFragment.kt`, `StructuredText.kt` (parser de texto estructurado para mostrar políticas/ayuda con formato). `OnboardingActivity/Adapter/Page`. `SyncDialogFragment.kt` — modal de progreso de sincronización manual. `NetworkAlertManager.kt` — banner/alerta de conectividad.

### 5.14 `User/`
`UserFragment.kt` (780) / `UserViewModel.kt` — perfil de usuario (incluye cambio de contraseña en texto claro comparando contra `user.password`, ver `AUDITORIA.md` §Hallazgo A5).

### 5.15 Infraestructura transversal
- `session/SessionManager.kt` — `object` singleton, sesión vía Firebase Auth + SharedPreferences.
- `preferences/DataStoreManager.kt` — Jetpack DataStore (tema oscuro, preferencias simples).
- `network/NetworkHealth.kt` (enum) + `NetworkHealthMonitor.kt` — monitor de conectividad real (no solo `ConnectivityManager`, valida alcance real).
- `notifications/SyncStatusNotifications.kt`, `notifications/VehiculoNotifications.kt` — notificaciones de sistema.
- `fcm/TecniAppMessagingService.kt` — `FirebaseMessagingService`, recibe push de Cloud Functions.
- `permissions/PermissionInitializer.kt`, `permissions/PermissionsActivity.kt`.
- `update/` — `GithubUpdateChecker.kt` (sealed `UpdateCheckResult`), `UpdateInfo.kt`, `UpdateDialog.kt`, `UpdateDownloadManager.kt`, `UpdateWorker.kt` — chequeo/descarga/instalación de APK vía GitHub raw JSON, sin Play Store.

---

## 6. Firebase Cloud Functions (`functions/`)

Node.js, Firebase Functions **v2** (`onSchedule`, `onCall`, `onValueCreated`, `defineSecret`). Un solo archivo `index.js` (1049 líneas) con 5 funciones exportadas:

| Función | Trigger | Propósito |
|---|---|---|
| `syncAveriasYNotificar` | `onSchedule` (cada **2 min**) | Poll a la **API REST externa del ICE** (Aranda, `ICE_URL`): ingesta las averías al nodo `tecniapp-ice-averias` y dispara notificaciones FCM. **Ver `AUDITORIA.md` §A13**: NO es convertible a event-driven (el origen es externo sin webhook; esta función es la que *escribe* las averías, nadie más). El margen de detección es el intervalo del poll; se bajó de 5 a 2 min (código listo, deploy diferido al rebuild). La propagación al teléfono vía FCM sí es casi instantánea una vez disparada. |
| `sendVerificationCode` | `onCall` | Envío de código de verificación (probablemente para registro/recuperación) vía `nodemailer` |
| `sendReport` | `onCall` | Envío de reportes generados por correo (adjuntos) |
| `notifyCambioMedidorSupervisor` | `onCall` | Notifica a supervisor cuando se registra un cambio de medidor |
| `notifyProgramacionAssigned` | `onValueCreated` | Notifica al técnico cuando se le asigna una programación |

Detalles de implementación: `admin.initializeApp()` + 2 apps secundarias (`averiasApp`, `usersApp`) apuntando a sus URLs de RTDB específicas; transporter de `nodemailer` (Gmail) cacheado con reintento (`sendMailWithRetry`, hasta 2 reintentos, descarta conexión muerta); secretos de correo vía `defineSecret` (v2 params, correcto). `functions/mail/mailer.js` es un **archivo huérfano** (implementación anterior con `functions.config()`, API v1 deprecada) que ya no se usa — la lógica real vive duplicada dentro de `index.js`.

**Cloud Functions nuevas, planeadas por `AUDITORIA.md` §A12 (código de la app ya listo, función server-side aún NO escrita/desplegada — diferida al rebuild de las 12 RTDB):**

| Función planeada | Trigger | Propósito |
|---|---|---|
| `bumpMetaTecnicos` | `onValueWritten` sobre `tecniapp-ice-personal/{cedula}` | Actualiza `/_meta/lastUpdated` para que la app sepa si el catálogo de técnicos cambió sin descargarlo completo |
| `bumpMetaMateriales` | `onValueWritten` sobre `tecniapp-ice-materiales/{codigo}` | Igual que arriba, para el catálogo de materiales |
| `bumpMetaMedidores` | `onValueWritten` sobre `default-rtdb/Medidores/{sub}/{medidorId}` | Actualiza `/Medidores_meta/{sub}/lastUpdated` por subregión |

La app (`RoomRepository`/`DataStoreManager`) ya tiene la "compuerta" que lee estos nodos `_meta` antes de decidir si re-descarga cada catálogo completo — es retrocompatible (si `_meta` no existe todavía, cae a la descarga completa de siempre). Ver `log.md` §"Ejecución del plan A12" para el detalle exacto.

---

## 7. Build, configuración y convenciones

- **Gradle:** Version Catalog (`libs.versions.toml`) + Kotlin DSL. `viewBinding`, `dataBinding` y `buildConfig` habilitados. `buildConfigField` expone `ICE_BASE_URL` (endpoint REST externo del ICE) y `UPDATE_JSON_URL` (hardcodeados en `build.gradle.kts`, no en `local.properties`).
- **Setup local no versionado:** `local.properties` (con `sdk.dir` y `MAPS_API_KEY`), `app/google-services.json` (Firebase) — **este último SÍ está commiteado en git** (`git ls-files` lo confirma), ver `AUDITORIA.md`.
- **Room:** migraciones explícitas 24→31 en `AppDatabase`; `exportSchema = true` (los JSON de esquema viven en `app/schemas/`). `PmDatabase` en v1 sin historial de migración todavía.
- **Navegación:** Jetpack Navigation, grafo principal `res/navigation/mobile_navigation.xml`, grafo propio de planillas `nav_planillas.xml`.
- **Layouts:** `layout/` (default) + `layout-sw600dp/` (tablets); `values`, `values-land`, `values-night`, `values-w600dp`, `values-w1240dp` para variantes de tamaño/tema.
- **ViewBinding:** patrón `_binding` nullable + limpieza en `onDestroyView` (estándar en todos los Fragments).
- **Serialización:** Moshi + KSP para Retrofit (`IceApi`); JSON manual (`org.json.JSONObject`) para los payloads embebidos en Room (`payloadJson` de `VehiculoLogEntity`, `materialesJson` de luminarias, etc.) y para los nodos de Firebase.
- **Exportación de documentos:** Apache POI (`.xlsx`), `PdfDocument` nativo de Android + posiblemente PdfBox-Android para lectura.
- **Testing:** carpetas `app/src/test` y `app/src/androidTest` existen pero están prácticamente vacías (ver `AUDITORIA.md`).

---

## 8. Cómo se relaciona con `Tecniapp web`

Ambos proyectos deben leer/escribir **las mismas instancias de Firebase Realtime Database** descritas en §3.3 (son **12**, no 9 — ver `log.md` §A4), más el mismo proyecto de Firebase Auth. Puntos de fricción detectados (detalle completo en `AUDITORIA.md`):

- El módulo de Planillas/PM **no comparte tecnología de base de datos** entre plataformas (Android → RTDB `pm_operacion`; Web → Firestore `pm_planillas`) y, además, en Android está desconectado de su propia UI.
- El panel de administración web (`Admin.tsx`) muestra una versión de Android hardcodeada (`2.7.0 (schema v27)`) que ya no corresponde al schema real (v31).
- El árbol de directorios `Tecniapp web/` estuvo históricamente commiteado **dentro de este mismo repositorio Git** (ver historial `git log` y `git status`), y solo después se separó a una carpeta hermana en disco — pero sin repositorio Git propio hoy. Ver `AUDITORIA.md` §Hallazgo A7.

---

## 9. Dónde mirar primero para cada tarea

| Quiero... | Empezar por |
|---|---|
| Entender el modelo de datos completo | `Database/entities/*`, `pm/model/entities/*`, `app/schemas/*.json` |
| Tocar sync de averías | `ui/averias/AveriasSyncWorker.kt` → `AveriasRepository.kt` → `Database/sync/FirebaseSyncManager.kt` |
| Tocar sync general (medidores/vehículos/etc.) | `Database/sync/Synchronizer.kt` → `Database/room/RoomRepository.kt` |
| Arreglar/completar el módulo Planillas | `pm/repository/OperacionRepository.kt` (lógica ya existe) + `ui/planillas/viewmodel/PlanillasViewModel.kt` (hay que conectarlo) |
| Cambiar reglas de seguridad de Firebase | Pendiente de rediseño tras el rebuild de RTDB (ver `log.md` §A4). `database.rules.json` fue **eliminado** (era huérfano). Las reglas se re-versionarán por instancia en `firebase.json`/`.firebaserc`. |
| Cambiar Cloud Functions | `functions/index.js` (`functions/mail/mailer.js` fue **eliminado**, era código muerto) |
| Entender el sistema de notificaciones push | `fcm/TecniAppMessagingService.kt` + `ui/averias/AveriaNotification*.kt` + `functions/index.js` |

---

Para la lista priorizada de bugs, deuda técnica y mejoras recomendadas, ver **`AUDITORIA.md`**. Para navegación general del repositorio, ver **`INDEX.md`**.
