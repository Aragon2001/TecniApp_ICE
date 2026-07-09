# LOG DE CORRECCIONES — TecniApp ICE

Registro cronológico de cada cambio ejecutado sobre el proyecto a partir de la
auditoría (`AUDITORIA.md`). Cada entrada indica: hallazgo atendido, archivos
afectados, qué se hizo y el riesgo/estado.

Convención de estado: ✅ hecho · 🔄 en progreso · ⏸️ pausado (requiere decisión/acceso) · ⚠️ requiere acción manual del usuario.

Sesión iniciada: 2026-07-08 · Ejecutor: Claude (Opus 4.8)

---

## Decisiones del usuario (previas a ejecutar)

- **Módulo PM (A2/A3):** terminarlo — cablear UI ↔ Repository ↔ Scheduler, uploader real de Storage, unificar tecnología de BD con la web.
- **Git/Secretos (A7/A8):** hacer todo, incluida la reescritura de historial (se hará backup bundle antes).
- **Refactor grande (B1/B2):** solo fixes seguros por ahora; división de clases Dios queda documentada como P1.
- **Password (A5):** dejar de persistir la contraseña en texto plano.
- **Firebase:** hay acceso vía CLI (`firebase`, proyecto `tecniapp-ice`). Se usará para exportar/versionar reglas; los despliegues a producción se harán con máxima cautela.

---

## Fase 1 — Fixes de código seguros

### [B6] Eliminar código muerto — ✅
- **Borrado** `app/.../Database/sync/SyncWorker.kt`: Worker sin ninguna referencia en el
  código (verificado con grep global; el sync real lo hace `AveriasSyncWorker`).
- **Borrado** `functions/mail/mailer.js` (+ carpeta `functions/mail/` vacía): módulo de correo
  huérfano con API v1 deprecada (`functions.config()`); la lógica real vive en `functions/index.js`
  con su propio transporter de nodemailer. No lo requiere ningún archivo.
- **NO borrado** `pm/sync/PmSyncScheduler.enqueue`: aunque hoy no se invoca, se **cableará** al
  completar el módulo PM (decisión del usuario), así que se conserva.
- Riesgo: nulo (código sin referencias).

### [B4] PmDatabase con patrón singleton — ✅
- `pm/room/PmDatabase.kt`: añadido `companion object` con `getInstance(context)` (doble-check
  locking `@Volatile`), idéntico al de `AppDatabase`. `DB_NAME` ahora vive aquí.
- `pm/sync/PmSyncWorker.kt`: dejó de abrir/cerrar su propia instancia de Room
  (`Room.databaseBuilder(...).build()` + `database.close()`); usa `PmDatabase.getInstance(...)`.
  Elimina el riesgo de dos handles de Room sobre el mismo `.db` cuando la UI y el worker
  corran a la vez (precondición para cablear la UI de PM).
- Riesgo: bajo. La instancia ya no se cierra al terminar el worker (correcto para un singleton
  de proceso, igual que `AppDatabase`).

### [A5] Contraseña en texto plano — ✅
Verificación previa: el login (`LoginActivity`) usa `signInWithEmailAndPassword` con el campo de
texto + Firebase Auth, **nunca** `UserEntity.password`. El registro (`Paso4Fragment`) ya no escribía
la clave al nodo de usuario. Por tanto el campo es vestigial para autenticación; el único leak venía
de `UserFragment` → `persistUserRemote`.
- `User/UserFragment.kt`: al guardar perfil ya no se copia la contraseña al `UserEntity`
  (`password = null`); el cambio real sigue por `FirebaseAuth.updatePassword()`.
- `User/UserFragment.kt` `persistUserRemote(...)`: sanitiza (`copy(password = null)`) antes de
  `setValue` — defensa en profundidad, nunca escribe texto plano al RTDB aunque quedara un valor.
- `Database/sync/FirebaseSyncManager.kt` (~1901): deja de re-hidratar `password` desde Firebase
  (`password = null`).
- `Database/entities/UserEntity.kt`: campo `password` marcado `@Deprecated`, documentado para
  eliminar en migración v32 dedicada (no se dropea ahora para no arriesgar migración sin build).
- **Firebase (producción):** verificado con `firebase database:get /usuarios --instance
  tecniapp-ice-user`: 4 usuarios, **0 contienen campo `password`**. No hubo nada que purgar; el
  histórico ya estaba limpio.
- Riesgo: bajo. Pendiente P1: migración Room v32 para dropear la columna físicamente.

### [B5] Eliminar runBlocking dentro de coroutines — ✅
Los 3 sitios reportados (más el import) eliminados; `grep runBlocking **/*.kt` → 0 coincidencias.
- `Database/room/RoomRepository.kt` (`startRealtimeSyncForScope`, ya `suspend`): `runBlocking {
  resolveVehiculoLocal(scope) }` → llamada directa `resolveVehiculoLocal(scope)`. Import removido.
- `Database/sync/FirebaseSyncManager.kt` (`startLuminariasRealtimeForAgencia`): la función pasó a
  `suspend fun` (único caller es `RoomRepository.startRealtimeSyncForScope`, ya suspend) y llama
  `luminariasRoot(...)` directo. Import removido.
- `ui/reportes/ReportesViewModel.kt`: `runBlockingUserContext()` → `suspend fun
  obtenerUserContextOrNull()`; `construirDatosBase(...)` pasó a `suspend` (se invoca dentro de
  `withContext(Dispatchers.IO)`). Dos call sites (líneas ~674 y ~809) actualizados. Import removido.
- Riesgo: bajo-medio. No se bloquea el hilo; el comportamiento es equivalente porque todos los
  contextos ya eran corrutinas. Verificar en build que no queden callers no-suspend de las
  funciones que pasaron a suspend (búsqueda global indica que no).

### [C5] Contadores de progreso hardcodeados — ✅ (mitigación de mantenibilidad)
Se verificó que `SUBREGION_SYNC_STEPS = 5` coincide con los 5 `done += 100` reales de
`syncSubregion` (agencias, pueblos, localizaciones, vehículos, medidores). El `+3` de
`SettingsFragment` corresponde a pasos propios de ese flujo (limpiar/catálogos/perfil), distinto
del `BASE_EXTRA_STEPS=2` de `Synchronizer` (técnicos+materiales) — cada constante es localmente
correcta. No se refactorizó el mecanismo a conteo dinámico (sería un cambio de diseño amplio y de
riesgo, e impacto ⚪ bajo). Mejoras aplicadas:
- `RoomRepository`: docstring en `SUBREGION_SYNC_STEPS` explicitando el acoplamiento con los pasos.
- `SettingsFragment`: el `+ 3` mágico ahora es `val cacheResyncExtraSteps = 3` con comentario.
- Riesgo: nulo (solo documentación/nombrado, sin cambio de valores).

### [B3] Gate de logging de diagnóstico — ✅
- **Nuevo** `Database/sync/SyncLog.kt`: logger `internal object` con niveles.
  `v/d/i` se emiten **solo si `BuildConfig.DEBUG`** (no-op en release → sin I/O ni construcción de
  strings en rutas calientes de sync). `w/e` se emiten siempre (útiles para diagnóstico de fallos).
  Overloads con y sin `Throwable`.
- Redirigidas **96** llamadas `Log.*` → `SyncLog.*` en `RoomRepository.kt` (46), `FirebaseSyncManager.kt`
  (37) y `Synchronizer.kt` (13) (los `[INV_DIAG]`/`[LUM_SYNC]`/`[SYNC_*]`). Import `android.util.Log`
  eliminado de los 3; `RoomRepository` importa `SyncLog` (los otros 2 comparten paquete).
- Verificado: `grep '\bLog\.'` en los 3 archivos → 0 stray; todos apuntan a `SyncLog`.
- Riesgo: bajo-medio. Cambio mecánico amplio (sed) sin compilación local; a validar en build que
  todas las llamadas multi-línea encajan en los overloads (2 o 3 args). Los mensajes de progreso
  visibles al usuario NO usan Log (usan callbacks/notificaciones), así que no se ven afectados.

### ✔️ Validación de compilación (Fase 1)
`./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL** (3m53s). Solo warnings preexistentes de
`KT-73255` (targets de anotaciones), ajenos a estos cambios. Confirma que B4/B5/B3/A5/C5 compilan.

---

## Fase 2 — Firebase (acceso directo vía CLI)

### [A4] Reglas de seguridad de Firebase — 🔴 HALLAZGO CRÍTICO + versionado — ✅ (deploy pendiente de confirmación)
Con acceso CLI se enumeraron **12 instancias RTDB** (no 9 como decía la doc): además de las
documentadas, existen `tecniapp-ice-inventario`, `-luminarias`, `-ordenes`, `-programacion` como
bases propias (no nodos del default). Se exportaron **todas** las reglas desplegadas (REST
`/.settings/rules.json` con token del CLI) a `firebase/rules/` y se respaldaron en
`firebase/rules/_backup_deployed_2026-07-08/`.
- **Hallazgo crítico (peor que A4 documentado):** **7 de 12 bases estaban ABIERTAS al público**
  (`".read": true, ".write": true`, sin auth): `default-rtdb` (¡`usuarios` con PII + `Medidores`!),
  `inventario`, `luminarias`, `materiales`, `ordenes`, `personal`, `planilla`. Cualquiera en Internet
  podía leer y **sobrescribir/borrar** esos datos.
- Bien aseguradas (sin cambios): `tecniapp-ice`, `datosgenerales` (`auth != null`), `averias`
  (cerrada + `/averias` auth), `user` (por-uid + validaciones).
- `programacion`: `false/false` (bloqueada) — no tocada (ver README, podría romper app si se usa).
- **Cambios preparados (NO desplegados aún):** las 7 abiertas endurecidas a `".read"/".write":
  "auth != null"` preservando `.indexOn`. `firebase.json` + `.firebaserc` ahora mapean las 12
  instancias a `firebase/rules/*` (antes solo 2). README documenta todo.
- **Riesgo del deploy:** medio (producción, app de campo del ICE). Mitigado: la app siempre
  autentica antes de acceder a RTDB y las Cloud Functions usan Admin SDK (ignoran reglas), así que
  `auth != null` no debería romper nada. Reversible con los backups.

#### Decisión del usuario (2026-07-08) — deploy CANCELADO / bases legacy
El usuario indica que **esas bases de datos son viejas**: las va a **eliminar y volver a subir**
todas para re-analizarlas, definir reglas por instancia y, de ser posible, **mejorar su estructura**.
En consecuencia:
- ❌ **NO se desplegaron** las reglas endurecidas (habría sido trabajo sobre bases que desaparecerán).
- 🗑️ Eliminados los artefactos locales: carpeta `firebase/rules/` (exports, versiones endurecidas y
  backups) y **`database.rules.json`** (el usuario no sabía por qué existía; el análisis confirmó que
  **no correspondía a NINGUNA base desplegada** — reglas de `vehiculos` que no coinciden con la
  estructura real; era un archivo huérfano/desconectado).
- 🔧 `firebase.json` y `.firebaserc` revertidos: se quitó el bloque `database` (queda solo
  `functions`). Se re-mapearán cuando el usuario suba las bases nuevas.
- 🚫 **NO se eliminaron las instancias RTDB en producción** (acción irreversible; la hará el usuario
  en consola, o Claude solo con confirmación explícita por instancia).

**⚠️ PENDIENTE (registro para retomar):**
1. El usuario eliminará/re-subirá las 12 bases. Cuando estén arriba: **exportar su estructura**,
   analizarla, **diseñar reglas de seguridad por instancia** (mínimo `auth != null`; idealmente
   control por rol/uid como ya tienen `user` y `averias`) y proponer **mejoras de estructura**.
2. Registro del estado de seguridad ANTERIOR (para no perder el hallazgo): **7 de 12 bases estaban
   públicas r/w sin auth** — `default-rtdb` (usuarios/Medidores), `inventario`, `luminarias`,
   `materiales`, `ordenes`, `personal`, `planilla`. Las nuevas NO deben repetir esto.
3. `tecniapp-ice-programacion` estaba `false/false` (bloqueada) — revisar si el módulo la usa.

---

## Fase 3 — Deuda técnica

### [C3] Migrar syncState string→enum — ⏸️ DIFERIDO (documentado)
Alcance real: 104 usos en 30 archivos, mezclando el `SyncStatus` del módulo principal (constantes
string) con el `SyncStatus` enum del módulo PM. Migrar el principal a enum toca entidades persistidas
(`VehiculoLogEntity`, `ProgramacionEntity`), DAOs con `@Query` que filtran por `syncState`
(`VehiculoLogDao`, `ProgramacionDao`), `RoomRepository` y varios VMs. Requiere `TypeConverter` y
riesgo de romper comparaciones de string en `@Query`. Es 🟡 Medio y toca persistencia → según la
decisión del usuario (solo fixes seguros en refactors grandes) se **difiere a una sesión dedicada con
build/test iterativo**. Mitigación inmediata: `SyncStatus` (principal) ya es la única fuente de las
constantes; no se detectaron strings libres tipo "Pendiente" fuera de esas constantes.

### [A7/A8] Limpieza de Git + secretos — ✅ parte segura · ⏸️ reescritura de historial (reevaluada)
- 🔓 Eliminado `.git/index.lock` obsoleto (0 bytes, del 2026-07-08 12:36) que bloqueaba escrituras de Git.
- 🧹 `git rm -r --cached "Tecniapp web"`: **113 archivos** del proyecto web sacados del índice de este
  repo Android (quedan 0). Esto limpia el `git status` de ~700 líneas de "deleted".
- 📝 `.gitignore`: añadido `Tecniapp web/` (carpeta completa) además de los subpaths previos.
- ⚠️ **NO commiteado todavía:** el repo está en la rama por defecto (`master`) y hay un remoto
  (`github.com/Aragon2001/TecniApp_ICE`). Los cambios están staged; falta que el usuario confirme el
  commit (y si va a master o a una rama).
- 🔎 **Reevaluación del "secreto" en historial (A7):** el `.env` en historia (commit `951c83fa`)
  contiene SOLO variables `VITE_*` (Firebase web API key, URLs de RTDB, Google Maps key). Son
  **públicas por diseño** — Vite las embebe en el bundle del navegador y todo usuario de la web ya las
  recibe. **No hay service-account ni clave privada.** Por tanto, reescribir el historial compartido
  de GitHub + `git push --force` (irreversible, rompe clones y el remoto) para purgar valores que ya
  son públicos es **desproporcionado**. Además `git-filter-repo`/BFG no están instalados.
  **Recomendación:** NO reescribir historial; en su lugar (a) restringir la Google Maps key por
  dominio/referrer en la consola, (b) el rebuild de Firebase que hará el usuario ya "rota" el entorno.
  Se deja la decisión final al usuario (cambió respecto a la elección inicial porque la premisa
  —"credenciales expuestas"— resultó ser de bajo riesgo real tras inspección).

### [A2/A3] Completar módulo PM/Planillas — ⏸️ BLOQUEADO por rebuild de Firebase
El usuario eligió "terminarlo". Sin embargo hay una **dependencia dura** que hace que cablear ahora
sea trabajo desechable:
- El destino remoto del módulo PM es `tecniapp-ice-planilla` (RTDB) — **una de las bases que el
  usuario va a eliminar y re-subir** (ver §A4). Además, el Hallazgo A3 (Android usa RTDB vs Web usa
  Firestore) es precisamente una **decisión de tecnología de datastore** que debe tomarse como parte
  del rediseño de estructura que el usuario planea tras el rebuild.
- Cablear ViewModel↔Repository↔Scheduler contra `tecniapp-ice-planilla` ahora significaría rehacerlo
  cuando esa base cambie de estructura o de tecnología (Firestore).

**Trabajo ya adelantado (no desechable):** `PmDatabase` singleton (§B4, hecho) — precondición para
conectar la UI sin doble instancia de Room.

**Plan recomendado (para retomar tras el rebuild + decisión A3):**
1. Decidir tecnología única con la web (recomendado Firestore por consultas de reporte).
2. Implementar `PlanillaStorageUploader` real contra Firebase Storage (hoy es `FakePlanillaStorageUploader`
   que devuelve `file://` local) — esto es datastore-agnóstico y se puede hacer en cualquier momento.
3. Rellenar los cuerpos `// TODO` de `PlanillasViewModel` (refresh/generatePdf/retrySync/openPlanilla)
   leyendo de `PmDatabase` (Room, local, agnóstico) y disparando `PmSyncScheduler.enqueue`.
4. Conectar los Fragments (`PlanillasHomeFragment`, `PlanillaDetailFragment`, etc.) al ViewModel.
5. Si se elige Firestore: reescribir el fan-out de `OperacionRepository`; si se mantiene RTDB:
   crear Cloud Function puente RTDB→Firestore para la web.

**Riesgo de hacerlo AHORA:** alto (throwaway) + no verificable sin correr la app. Se **difiere**.

---

## Fase 4 — Consumo excesivo de descarga de Firebase RTDB (bandwidth)

### [D1] Diagnóstico: 13.8 GB/mes descargados con solo ~6 MB de datos
Investigación read-only (sin cambios) del consumo que generó ~$50 en Blaze.
- **Causa raíz (99%):** `AveriasSyncWorker` (periódico cada 15 min, `TecniApp.kt:52`) llama a
  `AveriasRepository.pullFromFirebaseOnce()`, que **re-descarga TODA la región de averías** vía
  `orderByChild("region").equalTo(...)` **sin sync incremental** (no existe marca de agua —
  verificado). Además `AveriasViewModel` dispara `triggerNow()` (→ mismo pull completo) tras **cada**
  escritura (asignar/atender/resolver/eliminar/anular, líneas 1004/1082/1122/1149/1161/1178).
  Números: ~2 880 pulls/mes × ~5-6 MB (la región es casi toda la BD) ≈ **14-17 GB** → cuadra con los
  13.8 GB. La persistencia offline está deshabilitada para averías (`TecniApp.kt:69-70`) → cada pull
  va al servidor sin caché.
- **Secundario:** listeners realtime de inventario/luminarias (`FirebaseSyncManager.kt:1206/1258`,
  con `removeEventListener` correcto) se **re-enganchan en cada foreground** (`TecniApp.kt:83-88`) y,
  sin persistencia, re-descargan su nodo scoped completo cada vez.
- **Terciario:** `upsertUserFromFirebase(uid)` cada 15 min (pequeño). `scopedAveriasQuery()`
  (`AveriasRepository.kt:714`) descarga toda la BD sin límite pero es **código muerto** (sin callers).
- No hay `addValueEventListener` persistente ni `keepSynced(true)` en el proyecto; los
  `ChildEventListener` sí se remueven (no hay fuga clásica de listener).
- Medición en vivo no fue posible: la base `tecniapp-ice-averias` responde **HTTP 402 (downgraded)**.

### [D1-FIX] Sync incremental de averías — ✅ APLICADO + compila (NO desplegado; probar antes)
Aplicado a `AveriasRepository.kt` (3 hunks) y validado: `./gradlew compileDebugKotlin` → **BUILD
SUCCESSFUL**. **No** se despliega a producción hasta que el usuario lo pruebe (cuota Spark ajustada).
- Nuevo `fetchDeltaChildren(regionVariants, watermark)` en `AveriasRepository`: query
  `orderByChild("lastUpdated").startAt((watermark - 5min))` (índice `lastUpdated` ya existe) +
  filtro de región en cliente. Reduce cada pull de ~MB a ~KB.
- `pullFromFirebaseOnce`: watermark = `max(lastUpdated)` local (sin Context/storage/migración). Si
  hay datos locales → delta; si Room vacío → **seed original intacto** (descarga región completa).
- Constante `DELTA_OVERLAP_MS = 5 min` (ventana de solape para robustez ante desfase de reloj).
- Compatible: el pull actual NO borra locales ausentes en remoto (líneas 1518-1520), así que delta
  no regresiona borrados. Reducción esperada del bandwidth **>95-99%**.
- Complementos recomendados (fases siguientes, no en este fix): subir el intervalo del worker
  apoyándose en el FCM ya existente (`syncAveriasYNotificar`); persistencia/debounce de los listeners
  realtime; eliminar `scopedAveriasQuery()` muerto.

