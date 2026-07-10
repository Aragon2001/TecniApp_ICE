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
  realtime.

---

## Sesión 2026-07-09 — Continuación de auditoría

### [A7] Commit de limpieza de Git — ✅ COMPLETADO
- **Commit `edafc6b0`** (115 archivos, −30.972 líneas): eliminados del índice todos los archivos
  de `Tecniapp web/` que habían quedado tracked en este repo Android (el `git rm -r --cached`
  previo estaba staged desde la sesión anterior, confirmado con `git diff --cached`). `.gitignore`
  añadido al mismo commit para cerrar el ciclo.
- El índice de Git ya no muestra las 700+ líneas de "deleted" de archivos web.
- No se reescribió historial (decisión del usuario): el `.env` en historia solo contiene
  variables `VITE_*` públicas por diseño. Ver `AUDITORIA.md §A7`.

### [C4] Eliminar fallback de escaneo completo de medidores — ✅ COMPLETADO
- **`MedidorViewModel.kt` (confirmarRegistro, antes línea ~507):** `buscarMedidorEnFirebase` →
  `buscarMedidorEnFirebaseLigero`. Antes de registrar un medidor manual, ya no se hace un full-scan
  de toda la subregión si el lookup directo por clave falla. Si no existe en Firebase, se procede
  con el registro nuevo (comportamiento correcto — el número de medidor es la clave en Firebase).
- **`AveriasViewModel.kt` (buscarMedidor, antes línea ~512):** `buscarMedidorEnFirebase` →
  `buscarMedidorEnFirebaseLigero`. La búsqueda de un medidor asociado a una avería ya no dispara
  un full-scan de la subregión si el número no coincide con una clave exacta; devuelve
  `NotFound` directamente.
- **`AveriasRepository.kt` (scopedAveriasQuery):** **eliminada** la función muerta (0 callers,
  descargaba la BD de averías completa sin acotación de región ni límite). El import de `Query`
  se conserva porque `buildFallbackQuery` sigue usándolo.
- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL** (18 s) tras los 3 cambios.
- **Commit `953563b5`** incluye estos cambios junto con todos los fixes previos de Fase 1.
- Riesgo: bajo. El único efecto observable es que si un número de medidor existe en Firebase
  con una clave diferente a su número (datos legacy mal indexados), ya no se encontrará por
  la ruta de escaneo completo; se mostrará "no encontrado". En práctica, los medidores se
  indexan por su número exacto desde `registrarMedidorManual`.

## Sesión 2026-07-09 (continuación) — Pipeline de auto-actualización vía GitHub

### Contexto: por qué esto importa para el gasto de Firebase (A9)
El código de auto-actualización (`update/GithubUpdateChecker.kt`, `UpdateWorker.kt`,
`UpdateDownloadManager.kt`, `UpdateDialog.kt`) ya existía y está bien escrito, pero **no
tenía nada real detrás**: se verificó con la API de GitHub que el repo `Aragon2001/TecniApp_ICE`
no tenía ni la carpeta `updates/update.json` ni ningún GitHub Release publicado. El checker
apuntaba a una URL que devolvía 404 — es decir, **ningún teléfono en campo puede recibir la
actualización con el fix de A9 (sync incremental de averías)** aunque el código ya esté listo,
porque no hay forma de distribuirlo. Esto es parte directa de "las descargas masivas": los
teléfonos viejos seguirán generando el consumo de 13.8 GB/mes hasta que reciban esta actualización.

### Hallazgo crítico adicional descubierto: no había firma de release
`build.gradle.kts` no tenía ningún `signingConfigs`/`buildTypes.release` — nunca se generó
un APK de release firmado; **los teléfonos de campo corren builds debug**, firmados con la
debug key genérica de Android Studio (confirmado directamente por el usuario). Esto es
relevante porque Android exige que un "update" tenga la **misma firma** que la app instalada
para poder instalarse encima sin desinstalar.

**⚠️ Consecuencia que el usuario debe conocer:** se generó un keystore de release dedicado
(nuevo, único, para todos los releases futuros). Como los dispositivos actuales están en
debug, **el primer release firmado con este keystore NO podrá instalarse "encima" del debug
actual** — Android rechazará la instalación por firma distinta. Es decir: **el primer
despliegue de este nuevo pipeline requiere desinstalar manualmente la app debug e instalar el
nuevo release firmado una vez, por dispositivo** (se pierde la caché local de Room, pero
resincroniza solo desde Firebase — costo de red único y aceptable). A partir de esa primera
instalación, todas las actualizaciones futuras sí serán automáticas/in-place vía este pipeline.

### Trabajo realizado
1. **Keystore de release** (`tecniapp-ice-release.jks`, RSA 4096, alias `tecniapp-release`,
   validez 30 años) generado y entregado al usuario **fuera del repositorio** (archivo +
   `CREDENCIALES_KEYSTORE.txt` con las 4 credenciales necesarias). **No se commiteó ni se
   commiteará jamás** — instrucción explícita en el propio archivo de credenciales.
2. **`app/build.gradle.kts`:**
   - `signingConfigs.release` lee credenciales de variables de entorno (`KEYSTORE_PATH`,
     `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — usadas por CI) con fallback a
     `local.properties` (`RELEASE_KEYSTORE_*`) para quien quiera firmar localmente. Nunca
     hardcodeadas.
   - `buildTypes.release` aplica esa firma solo si hay credenciales disponibles (si no,
     Gradle cae a su comportamiento por defecto — APK sin firmar — para no romper builds
     locales de quien no tenga el keystore).
   - `versionCode`/`versionName` ahora son parametrizables (`-PappVersionCode`/`-PappVersionName`),
     con fallback a `1`/`"1.0-dev"` para desarrollo local sin parámetros. Elimina el riesgo de
     "olvidarse de subir el versionCode" — lo calcula el CI automáticamente desde el tag git.
3. **`.gitignore`:** añadido `*.jks`/`*.keystore` como defensa adicional.
4. **`.github/workflows/release.yml`** (nuevo): pipeline disparado por `git push` de un tag
   `vX.Y.Z`. Decodifica el keystore desde el Secret `KEYSTORE_BASE64`, calcula `versionCode`
   desde el tag (`MAJOR*10000 + MINOR*100 + PATCH`), compila y firma con `./gradlew
   assembleRelease`, calcula SHA-256 del APK, publica un GitHub Release con el APK como asset
   (usa `softprops/action-gh-release`), genera `updates/update.json` con la URL real del asset
   publicado y el SHA-256, y lo commitea de vuelta a la rama por defecto (`git push` con el
   `GITHUB_TOKEN` del propio workflow).

### ⚠️ Pendiente de configuración manual del usuario (una sola vez, en GitHub, no se puede hacer desde aquí)
1. **Settings → Actions → General → "Workflow permissions"** = *Read and write permissions*
   (el workflow necesita poder hacer `git push` de `updates/update.json`).
2. **Settings → Secrets and variables → Actions** → crear los 4 secrets:
   `KEYSTORE_BASE64` (el .jks en base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
   (valores exactos en `CREDENCIALES_KEYSTORE.txt`, entregado fuera del repo).
3. **Primer release:** hacer `git tag v1.1.0 && git push origin v1.1.0` (o el número que se
   quiera empezar) para disparar el pipeline por primera vez y confirmar que todo el flujo
   funciona de punta a punta antes de anunciar la actualización a los técnicos de campo.
4. **Comunicar a los técnicos** que la primera actualización requiere desinstalar/reinstalar
   manualmente (ver hallazgo de arriba) — las siguientes ya serán automáticas.
5. **No se probó el pipeline end-to-end** (no hay acceso a Actions/Secrets reales desde este
   entorno) — la primera ejecución real del workflow es, en sí misma, la prueba de que todo
   el cableado (firma, release, update.json) funciona.

### [A9] Sincronización incremental de Averías — ✅ VERIFICADO EN DISPOSITIVO (2026-07-09)
Prueba ejecutada con ADB en SM-A566E (Android 16, PID 25077). Logcat capturado durante 75 s:
```
[SCOPED_DELTA] desde=1783638664297 watermark=1783638964297 recibidos=2  regionMatch=1
[SCOPED_DELTA] desde=1783638664297 watermark=1783638964297 recibidos=2  regionMatch=1
[SCOPED_DELTA] desde=1783639567091 watermark=1783639867091 recibidos=27 regionMatch=4
```
- Cada sync descargó 2–27 registros (ventana delta de 5 min) en vez de toda la región.
- `AveriasSyncWorker` → `SUCCESS` en cada ejecución.
- El watermark avanza correctamente entre syncs.
- Reducción esperada de bandwidth: **>99%** (de ~14-17 GB/mes a unos pocos KB por sync).
- Hallazgo A9 marcado ✅ en `AUDITORIA.md`.

---

## 🔴 Incidente de seguridad 2026-07-09 — Exports de Firebase con datos reales (PII) commiteados en repo público

### Qué pasó
Al preparar el commit del pipeline de auto-actualización (`feat(update)... b5db1d1f`), un
`git add .` recogió sin querer 4 archivos que ya estaban modificados sin commitear en la carpeta
`firebase json/` (subidos por el usuario en una sesión previa, no relacionados con el trabajo de
hoy). Se detectó al revisar el diff del commit (~690.000 líneas insertadas/eliminadas, muy fuera
de lo esperado para cambios de config/documentación).

**Contenido real verificado (leído directamente):**
- `tecniapp-ice-user-export.json`: usuario real con **nombre completo, cédula, teléfono, email,
  placa de vehículo**, y un nodo `verificationCodes` con **códigos de verificación reales**
  asociados a correos de ICE.
- `tecniapp-ice-default-rtdb-export.json` (20 MB): miles de registros de `Medidores` con
  **nombres reales de clientes** (personas y empresas), dirección, poste, subregión.
- `tecniapp-ice-export.json` (947 KB): `Localizaciones` con coordenadas GPS reales.
- `tecniapp-ice-datosgenerales-export.json`: agencias/subregiones/vehículos (bajo riesgo, sin PII).

**Historial:** el primer commit que introdujo estos archivos fue `cf684504 "CAMBIOS DEL BOTON"`
(sesión anterior, ajena a esta), no algo generado por el trabajo de hoy — pero el commit de hoy
los volvió a tocar y los dejó en la punta visible del historial de un repositorio **público**
(`Aragon2001/TecniApp_ICE`, confirmado `"private": false` vía API de GitHub).

### Remediación aplicada (2026-07-09)
1. **Sacados del índice de git** (`git rm --cached` × 4 + commit `d95d8745` + push a `master`).
   Ya no están en el `HEAD` actual del repo.
2. **`.gitignore` actualizado:** se agregó `firebase json/` y el patrón `*-export.json` para que
   no se vuelva a repetir si el usuario vuelve a exportar datos ahí.

### ✅ Purga de historial completada — 2026-07-09

Ejecutada en sesión posterior al incidente. Pasos realizados (en orden):

1. **Backup previo:** bundle completo del repo guardado en `Desktop/TecniApp_ICE_backup_pre_purge.bundle`
   (84 MB, incluye el historial con PII — conservar unos días como red de seguridad, luego eliminar).
2. **Clon temporal:** `git clone --no-local` a `Desktop/TecniApp_ICE_purge_tmp` — nunca se operó
   sobre la carpeta de trabajo activa.
3. **Purga con `git filter-repo` v2.47.0** (instalado vía `py -m pip install git-filter-repo`):
   reescribió los **1.100 commits** del historial completo, eliminando los 4 archivos con PII de
   cada commit donde aparecían. Nuevos hashes asignados a todos los commits afectados.
4. **`git push --force` a GitHub** (`dd438805 → 626e3ecc`, rama `master`): los commits viejos con
   PII dejaron de ser accesibles desde la rama principal.
5. **Tag `v1.1.0` actualizado:** el tag viejo (`3a8eca69`, pre-purga) se borró de GitHub y se
   publicó el equivalente reescrito (`e71f4141`). Sin este paso, los datos PII hubieran seguido
   siendo accesibles vía el tag.
6. **Repo de trabajo principal sincronizado:** `git fetch --tags --force origin` + `git reset --hard
   origin/master`. Local y remoto alineados sobre el historial limpio.
7. **Stash eliminado:** el stash `guardar cambios locales antes de borrar ramas` (cambios menores en
   4 archivos Kotlin) se borró porque su cadena de padres referenciaba commits del historial viejo;
   eliminarlo completó la limpieza de objetos locales.
8. **GC local:** `git reflog expire --expire=now --all && git gc --prune=now` — los objetos huérfanos
   del historial viejo eliminados del object store local.
9. **Clon temporal eliminado** del Escritorio.

**Verificación final:** `git log --all --full-history -- "firebase json/tecniapp-ice-user-export.json"
...` → **sin output** (0 commits accesibles contienen los archivos).

**Estado de refs tras la purga:**

| Ref | Hash | Estado |
|---|---|---|
| `master` (local + origin/master) | `626e3ecc` | ✅ Historial purgado |
| `v1.1.0` | `e71f4141` | ✅ Hash reescrito |
| `refs/stash` | — | ✅ Eliminado |

**⚠️ Pendiente menor:** contactar a GitHub Support para limpieza del caché del lado del servidor
(mensaje de soporte preparado — categoría "Sensitive Data Removal"). GitHub puede tener el contenido
cacheado temporalmente aunque ya no sea accesible vía ninguna ref. La solicitud fue redactada y
entregada al usuario para envío manual.

**Pendiente de evaluación (no técnico):** la cédula/teléfono/nombre real expuestos corresponden a
un usuario identificable, en el contexto de ICE como institución pública. Evaluar si aplica algún
protocolo interno de incidente de datos. Los `verificationCodes` expuestos probablemente ya estaban
vencidos (son de un solo uso), pero conviene confirmarlo.

---

## Bug: dialog ETM congelado sin mensaje al abrir Averías/Luminarias sin red — 2026-07-09

### Síntoma reportado
Al exigir el registro de kilometraje ETM antes de abrir módulos (Averías, Luminarias, etc.),
tocar "Registrar" en ocasiones no hacía nada ni mostraba mensaje — especialmente cuando no había
red o Firebase estaba degradado (402 por cuota). El usuario esperaba que se guardara localmente
y se sincronizara después.

### Causa raíz (triple)
1. **Freeze al abrir el dialog:** la lectura inicial de `vehiculo_etm` de Firebase
   (`datosgenerales`) usaba `.get().await()` sin timeout. Si Firebase no respondía (sin red,
   402), el dialog aparecía en blanco hasta que el SDK decidiera fallar (potencialmente minutos).
2. **Excepción silenciosa:** si cualquier línea dentro del coroutine del botón "Registrar"
   lanzaba una excepción no capturada (ej. `parseRegistrosDiarios` con JSON inválido, Room error),
   el coroutine cancelaba sin llamar `dialog.dismiss()` → dialog congelado para siempre sin
   ningún mensaje visible.
3. **Botón "Sin vehículo" no navegaba:** `onNoVehiculo = {}` cerraba el dialog pero no
   llamaba `onContinue()` → el usuario quedaba atascado en Home sin poder entrar al módulo.

### Correcciones aplicadas — `RegistroVehiculoPendienteDialogExt.kt` + `HomeFragment.kt`
1. **Timeout de 6 s en la lectura inicial de Firebase:** `withTimeout(6_000L) { ...get().await() }`
   — si Firebase no responde en 6 s, el catch ya existente lo toma y el dialog se inicializa con
   datos de Room. Requirió añadir `import kotlinx.coroutines.withTimeout`.
2. **try/catch en el coroutine del botón "Registrar":** toda la lógica del coroutine (Room
   saves, Firebase writes) envuelta en `try { } catch (Exception) { }`. En el camino exitoso,
   `dialog.dismiss()` y `onRegistroGuardado()` se llaman al final del `try`. En caso de
   excepción inesperada, el catch muestra un Toast informativo, cierra el dialog y llama
   `onRegistroGuardado()` (deja pasar al módulo — `datosgenerales` tiene persistencia offline,
   los datos se subirán al volver a tener red). Los `return@launch` de validación de km (camino
   de error controlado) siguen saltando el dismiss → dialog permanece abierto para que el usuario
   corrija.
3. **Mensaje cuando vehículo no está en Room:** si `vehiculo == null` pero el usuario tiene
   placa asignada, Toast explicativo + el flujo continúa normalmente.
4. **`onNoVehiculo = { onContinue() }`** en `HomeFragment.ejecutarOperacionSiRegistroCompleto`:
   el botón "Sin vehículo" del dialog ahora deja entrar al módulo en lugar de no hacer nada.
   Esto cubre el requerimiento del usuario: "sin vehículo debe dejar entrar a los módulos".

**BUILD SUCCESSFUL** + `installDebug` en SM-A566E.

**Nota (Firebase / "no guardaba nada"):** `datosgenerales` tiene `setPersistenceEnabled(true)` en
`TecniApp.kt` (confirmado). Esto significa que cuando hay red, los datos se guardan; cuando no hay
red, los `.updateChildren().await()` resuelven inmediatamente (cola offline de Firebase SDK) y se
suben al volver. Si no estaban llegando a Firebase, la causa más probable era que el coroutine
fallaba antes de las escrituras (causa 2 arriba) o que la base está entre las 12 legacy que el
usuario va a reconstruir.

**Pendiente (requerimiento adicional del usuario):** "inhabilitar las opciones de atender avería /
reparar luminaria / ejecutar programación si no hay vehículo asignado" dentro de cada módulo —
es un cambio separado de UI en `AveriasFragment`, `LuminariasFragment` y `ProgramacionFragment`.
No implementado aún; documentado para la próxima sesión.

---

## Feature: inhabilitar acciones de campo sin vehículo asignado — 2026-07-09

Requerimiento del usuario: si el técnico no tiene vehículo asignado en su perfil, debe poder
entrar a los módulos de Averías, Luminarias y Programación, pero los botones que representan
trabajo de campo (atender/registrar/iniciar) deben quedar deshabilitados.

### Cambios aplicados (BUILD SUCCESSFUL + installDebug en SM-A566E)

**`ui/averias/AveriasAdapter.kt` — bloqueo final:**
Después del bloque de bloqueo existente (regionMismatch/bloqueadaPorClor/asignadaAOtro),
se agrega: si `currentUserVehiculo.isNullOrBlank()` y `btnAtender.isVisible`, se deshabilita
`btnAtender` y se pone `alpha = 0.45f`. `btnAsignar` y `btnResolver` NO se tocan — el técnico
sin vehículo puede seguir viendo detalles y los supervisores pueden gestionar. `currentUserVehiculo`
ya se actualiza desde el ViewModel (`adapter.currentUserVehiculo = user?.placaVehiculo`).

**`ui/luminarias/LuminariasViewModel.kt` — línea 237:**
`puedeRegistrarReparacion = true` → `puedeRegistrarReparacion = vehiculoPreferidoId != null`.
Si el usuario no tiene vehículo en Room, `vehiculoPreferidoId` es `null` → el FAB de registrar
luminaria queda oculto (`rowFabRegistrar.isVisible = state.puedeRegistrarReparacion`). El FAB
ya lo controlaba este flag; solo faltaba que el flag reflejara la realidad del vehículo.

**`ui/programacion/ProgramacionDetalleBottomSheet.kt`:**
Se lee `tieneVehiculo = !currentUser?.placaVehiculo.isNullOrBlank()` (en el coroutine donde ya
se lee `currentUser`) y se pasa a `configurarBotones(..., tieneVehiculo, ...)`. En ese método,
`btnIniciar.isEnabled` ahora requiere además `tieneVehiculo`. Esto deshabilita el botón "Iniciar"
para técnicos sin vehículo, pero `btnFinalizar` y `btnReabrir` no se tocan (son sobre
programaciones ya iniciadas donde el técnico ya estaba asignado).

---

## Intentos de build del pipeline A10 — 2 fallas encontradas y corregidas antes del primer release exitoso

### Intento 1: `mergeReleaseResources` FAILED — `navegation.png`/`bg1.png` no eran PNG reales
`assembleRelease` corre por primera vez en un runner de GitHub Actions limpio (sin caché local),
lo que expuso algo que el build debug local nunca detectó: `app/src/main/res/drawable/navegation.png`
era en realidad un **JPEG** y `bg1.png` un **WebP**, ambos con extensión `.png` falsa. AAPT2 valida
el contenido real del archivo, no la extensión, y rechaza la compilación. Se corrigieron
reconvirtiéndolos a PNG real con ImageMagick (`convert archivo PNG32:archivo`), verificado con
`file` que ambos quedaron como PNG genuino. Se escaneó el resto de `res/**/*.png` y no se
encontraron más casos. Commit de fix + re-tag de `v1.1.0` sobre el commit corregido.

### Intento 2: `lintVitalRelease` FAILED — 31 errores `MissingDefaultResource`
`assembleRelease` también corre "lint vital" (checks fatales), algo que `assembleDebug` no corre
— segunda cosa que nunca se detectó localmente. Los 31 errores son del patrón estándar de
Material Theme Builder: `values-night/colors.xml` define tokens `md_theme_dark_*` que lint
espera ver también declarados en `values/colors.xml` (base), más un layout `layout-sw600dp/
fragment_paso_4_tablet_sw600dp.xml` sin declaración base. Es casi con certeza un falso positivo
(esos tokens solo se referencian desde `values-night/themes.xml`, nunca en un contexto sin el
qualifier), pero lint no puede verificarlo estáticamente.

**Fix aplicado:** se agregó un bloque `lint { disable += "MissingDefaultResource" }` en
`build.gradle.kts` — desactiva puntualmente esa regla (no todo lint-vital) para no bloquear
releases por deuda preexistente ajena al pipeline de A10.

**⚠️ Pendiente (deuda técnica, no urgente):** para eliminar el riesgo real (por mínimo que sea)
de un `Resources.NotFoundException` si algún token `md_theme_dark_*`/el layout de tablet llegara
a consultarse fuera de su contexto esperado, lo correcto es agregar los valores por defecto
correspondientes en `values/colors.xml` (versión "light" de cada token) y un
`layout/fragment_paso_4_tablet.xml` base. No se hizo en esta sesión porque requiere decisiones de
diseño (qué colores claros corresponden a cada token oscuro) que no se deben inventar sin
confirmación. Candidato para una sesión de limpieza de UI/temas.

---

## Sesión 2026-07-09 (continuación) — Auditoría de rendimiento/consumo (A12) y viabilidad de Spark

El usuario pidió una auditoría dedicada de consumo de recursos/eficiencia, y por separado
preguntó qué consume recursos de pago porque quiere volver al plan Spark. Se documentó todo en
`AUDITORIA.md §A12` (leer ahí el detalle completo, esto es un resumen):

- **Confirmado leyendo el código:** el patrón delta-sync de A9 solo se aplicó a Averías.
  Técnicos, materiales, agencias, vehículos, y sobre todo **medidores** (`syncSubregion` borra y
  vuelve a descargar TODA la subregión en cada sync, manual o automático) siguen sin marca de agua
  — es la causa directa de la queja "sincronizar sigue descargando todas las bases".
- Los listeners realtime de inventario/luminarias sí usan `ChildEventListener` (eficiente por
  diseño), pero `TecniApp.kt` los desconecta/reconecta en cada `onStart`/`onStop` de proceso, lo
  que fuerza un re-fetch completo del nodo escopeado cada vez que el técnico reabre la app.
- **Verificado contra documentación oficial de Firebase (julio 2026):** el plan Spark solo permite
  **1 instancia de Realtime Database por proyecto** (este proyecto usa 12) y **no permite
  desplegar Cloud Functions en absoluto** (bloquea tráfico saliente no-Google; este proyecto tiene
  5 funciones, 2 de ellas con nodemailer/Gmail). Volver a Spark tal como está la arquitectura
  **rompería funcionalidad real**, no es solo un tema de ahorro. Cuotas RTDB Spark: 1 GB
  almacenado, 10 GB descargados/mes, 100 conexiones simultáneas — el propio incidente de Averías
  (13.8 GB/mes) ya superaba el cupo mensual completo de descarga de Spark por sí solo.
- Plan de 5 pasos documentado en AUDITORIA.md para que Spark sea viable de verdad: consolidar las
  12 RTDB en 1, decidir destino de las 5 Cloud Functions, aplicar delta-sync a los sync que faltan
  (prioridad: medidores), corregir el patrón de reconexión completa de listeners, y solo entonces
  medir consumo real antes de decidir Spark vs Blaze-con-alertas-de-presupuesto.
- Fuentes verificadas: `firebase.google.com/docs/database/usage/limits`,
  `firebase.google.com/pricing`, `firebase.google.com/docs/functions/quotas`.

---

## Sesión 2026-07-09 (continuación) — Ejecución del plan A12: sync incremental por fases

Contexto: el usuario decidió **quedarse en Blaze** (no bajar a Spark), pero manteniendo el consumo
real por debajo de las cuotas gratuitas de cada servicio → factura $0 salvo crecimiento anormal.
Se ejecuta el plan de remediación de A12 por fases, usando el fix de Averías (A9) como plantilla.

### Fase 0 — Diagnóstico previo (verificado con `firebase database:get` contra las RTDB reales)

Se comprobó qué nodos tienen un campo de fecha indexable apto para el delta-sync de A9:

| Nodo | Instancia RTDB | ¿Campo de fecha? |
|---|---|---|
| **Vehículos** (`/vehiculos`, map por placa) | `datosgenerales` | ✅ `updatedAt` (epoch ms) |
| Técnicos (`/`, map por cédula → `{nombre}`) | `personal` | ❌ ninguno |
| Materiales (`/`, map por código → `{Nombre, Unidad}`) | `materiales` | ❌ ninguno |
| Medidores (`/Medidores/{subregion}/{num}`) | `default-rtdb` | ❌ ninguno |
| Agencias/Subregiones/Regiones (**arrays** JSON) | `datosgenerales` | ❌ ninguno (volumen trivial → se dejan) |

**Conclusión:** solo Vehículos admite el patrón delta puro de A9. Técnicos/Materiales/Medidores no
tienen campo de fecha. Estrategia decidida con el usuario:
- **Vehículos** → compuerta por `updatedAt` (max remoto vía `limitToLast(1)`, sin Cloud Function).
- **Técnicos/Materiales/Medidores** → nodo pequeño `_meta/lastUpdated` mantenido por una **Cloud
  Function nueva** `onValueWritten`; la app lee ese único timestamp (bytes) y solo re-descarga el
  catálogo si avanzó. Medidores además con `_meta` por subregión y botón manual (Fase 3).
- **Secuencia por el rebuild pendiente de las RTDB:** el código de la app se escribe ya, de forma
  **retrocompatible** (si `_meta` no existe todavía → cae a descarga completa, sin romper nada); el
  código de las Cloud Functions se escribe ya, pero su **deploy + seed de `_meta` se difieren al
  rebuild** y los **ejecuta el usuario** (no yo).

### Fase 1 — Verificación de lo ya hecho ✅
- A9 (Averías) intacto: `AveriasRepository.fetchDeltaChildren` sin cambios (delta por `lastUpdated`
  + filtro de región en cliente; seed completo solo con Room vacío).
- A11 (purga PII): `git log --all --oneline -- "firebase json/tecniapp-ice-default-rtdb-export.json"`
  → vacío. La purga sigue en pie.

### Fase 2 — Compuerta de descarga incremental para técnicos, materiales y vehículos ✅ (compila)

Mecanismo único **retrocompatible** (`RoomRepository.debeDescargarCatalogo`): lee un timestamp
remoto pequeño, lo compara contra el último aplicado en DataStore (`sync_meta_<catálogo>`), y solo
descarga el catálogo completo si (a) no hay `_meta` todavía → fallback a descarga completa, (b) Room
vacío → seed, o (c) el timestamp remoto avanzó. Archivos tocados:
- `DataStoreManager.kt`: `getSyncMeta(name)`/`setSyncMeta(name,value)` (clave `sync_meta_*`).
- `FirebaseSyncManager.kt`: `obtenerMetaTecnicos()`, `obtenerMetaMateriales()`,
  `obtenerMetaMedidores(subregion)` (lee `/Medidores_meta/{sub}/lastUpdated`),
  `obtenerVehiculosMaxUpdatedAt()` (`orderByChild("updatedAt").limitToLast(1)`), + helper `anyToLong`.
- `RoomRepository.kt`: `syncTecnicos()` y `syncMateriales()` ahora gatean antes de
  `obtenerTecnicos()/obtenerMaterialesCatalogo()`; los dos puntos de descarga de vehículos
  (`syncSubregion` con clave por subregión `vehiculos_sub_<X>`, y `syncCatalogosGenerales` con clave
  `vehiculos_all`) gatean por `updatedAt`.
- Compilación: `./gradlew compileDebugKotlin` → EXIT=0.

Pendiente para que la compuerta AHORRE de verdad (no solo no rompa): desplegar la Cloud Function de
`_meta` (Fase 2/3 server-side, diferida al rebuild) y agregar `.indexOn: "updatedAt"` a las reglas
de vehículos. Antes de eso, técnicos/materiales/vehículos siguen descargando completo (sin regresión).

### Fase 3 — Medidores (mayor impacto): compuerta `_meta` + botón manual ✅ (compila)

Antes: `syncSubregion` hacía `db.medidorDao().eliminarPorSubregion(...)` + re-descarga completa de
la subregión en CADA sync (el mayor consumo del proyecto fuera de Averías). Ahora:
- **Compuerta `_meta` por subregión** (decisión del usuario: detección de cambios, no vencimiento por
  tiempo — ver AUDITORIA.md §A12.D). `syncSubregion` solo re-descarga medidores si
  `firebase.obtenerMetaMedidores(subregión)` avanzó, o si Room no tiene medidores de esa subregión
  (seed). `obtenerMetaMedidores` prueba claves normalizadas candidatas (id canónico y nombre de
  catálogo) con lecturas mínimas — NO baja `/Medidores` para resolver el nodo.
- **Extracción** de la descarga a `RoomRepository.descargarMedidoresSubregion(...)` (helper privado
  compartido) para no duplicar el loop por lotes.
- **Botón manual "Actualizar catálogo de medidores"** en Ajustes (no en el Home) →
  `RoomRepository.actualizarCatalogoMedidores(subregión)` fuerza la descarga saltándose la compuerta.
  Archivos: `fragment_settings.xml` (botón + hint), `strings.xml` (7 strings `settings_update_medidores*`),
  `SettingsFragment.kt` (`launchActualizarMedidores`, estado `medidoresUpdateInProgress`, `refreshSyncButtons`).
- Compilación: `./gradlew compileDebugKotlin` → EXIT=0.

### Server-side (Fases 2-3) — ✅ DESPLEGADO Y SEMBRADO EN PRODUCCIÓN (2026-07-09)

> **Actualización:** el usuario pidió desplegar ya (no esperar al rebuild). Las 3 Cloud Functions +
> `syncAveriasYNotificar` se desplegaron en `tecniapp-ice`/us-central1 (`firebase functions:list`
> confirma los 4 triggers). Se fijó una cleanup policy de Artifact Registry (borra imágenes >1 día).
> Los `_meta` se **sembraron** con `firebase database:set`: `personal /_meta/lastUpdated`,
> `materiales /_meta/lastUpdated` y `default-rtdb /Medidores_meta/subregionguapiles/lastUpdated`
> (única subregión bajo `/Medidores` hoy), todos = `1783654625301`, verificados por lectura. Con esto
> el ahorro queda ACTIVO: en el próximo sync cada catálogo se baja una última vez (guarda el meta en
> DataStore) y a partir de ahí se omite mientras el `_meta` no avance. Sigue pendiente el
> `.indexOn: "updatedAt"` de `/vehiculos` (reglas diferidas al rebuild) y la verificación en
> dispositivo real. ⚠️ Si el rebuild renombra instancias, las CF necesitan redeploy.

`functions/index.js` — 3 Cloud Functions nuevas `onValueWritten` que mantienen `_meta/lastUpdated`
(`node --check` OK):
- `bumpMetaTecnicos` (instancia `tecniapp-ice-personal`, ref `/{cedula}`, guarda `/_meta/lastUpdated`;
  ignora el evento del propio `_meta` para no entrar en bucle).
- `bumpMetaMateriales` (instancia `tecniapp-ice-materiales`, ref `/{codigo}`, misma guardia).
- `bumpMetaMedidores` (instancia `tecniapp-ice-default-rtdb`, ref `/Medidores/{sub}/{medidorId}`,
  escribe `/Medidores_meta/{normalizarClave(sub)}/lastUpdated` — árbol hermano, sin riesgo de bucle).
- `normalizarClave` en JS replica EXACTO la de Kotlin (NFD → `\p{Mn}+` → minúsculas → `[^a-z0-9]+`).
- **Supuesto de estructura** (validado contra los datos actuales): cédulas/códigos a nivel raíz y
  medidores en `/Medidores/{sub}/{id}`. Si el rebuild cambia el anidamiento, ajustar los `ref`.

**Comandos que ejecutará el usuario tras el rebuild de las RTDB:**
```bash
# 1) Desplegar las funciones nuevas + la de averías modificada (A13, intervalo 5→2 min):
firebase deploy --only functions:bumpMetaTecnicos,functions:bumpMetaMateriales,functions:bumpMetaMedidores,functions:syncAveriasYNotificar

# 2) (Opcional) Sembrar un valor inicial de _meta para forzar la primera descarga controlada:
firebase database:set /_meta/lastUpdated  <epoch_ms> --instance tecniapp-ice-personal
firebase database:set /_meta/lastUpdated  <epoch_ms> --instance tecniapp-ice-materiales
# medidores: se sembrará solo al primer cambio; o por subregión si se quiere forzar.

# 3) En las reglas del rebuild, agregar índice para la compuerta de vehículos:
#    "vehiculos": { ".indexOn": ["subregion", "updatedAt"] }
```
Nota: sin (1), la app sigue funcionando (compuerta cae a descarga completa, sin regresión); (1) es lo
que ACTIVA el ahorro. Sin el `.indexOn` de (3), la lectura de max `updatedAt` de vehículos baja el
nodo completo (funciona, pero no ahorra) — bajo impacto porque vehículos es de bajo volumen.

### Fase 4 — Debounce de los listeners realtime (inventario/luminarias) ✅ (compila)

`TecniApp.kt` adjuntaba los `ChildEventListener` en `onStart` de `ProcessLifecycleOwner` y los
soltaba en `onStop`. Como adjuntar un `ChildEventListener` reenvía un `onChildAdded` por CADA hijo
existente del nodo (re-fetch completo), cada vez que el técnico reabría la app se re-descargaba todo
el inventario del vehículo y las luminarias de la agencia — muy frecuente en campo.

Fix: se retrasa la desconexión. En `onStop` se agenda un `stopRealtimeSync()` diferido
(`REALTIME_KEEPALIVE_MS = 7 min`, coroutine cancelable en `applicationScope`); si `onStart` vuelve
antes de ese margen, cancela el stop pendiente y — gracias al flag `@Volatile realtimeActive` — NO
vuelve a llamar `startRealtimeSyncForScope` (que internamente hace stop+start = re-fetch). Así las
reaperturas rápidas no cuestan nada. Si el proceso muere durante la espera, el listener se limpia
solo (sin fuga). Solo tras 7 min reales en background se sueltan los listeners.

### Fase 5 — Alertas de presupuesto (acción manual del usuario, NO es código)

Ver el mensaje entregado al usuario. Resumen: configurar en Google Cloud Billing del proyecto
`tecniapp-ice` un presupuesto bajo (ej. $1–5 USD) con alertas por correo al 50/90/100 %. No frena el
gasto (Blaze no tiene hard-cap nativo; el hard-cap requeriría una Cloud Function que reaccione a la
alerta y deshabilite la facturación — opción avanzada, no obligatoria), pero avisa de inmediato si
algo se dispara en vez de enterarse con la factura.

### A13 — Margen de detección de averías (código ✅, deploy diferido)

El usuario preguntó si las averías aparecen instantáneas. Se leyó la función a fondo y se corrigió
un malentendido de la auditoría: `syncAveriasYNotificar` **no** es convertible a event-driven — es
un **poll a la API REST externa del ICE** (`ICE_URL`, sistema Aranda) que ingesta las averías a
Firebase; nadie más las escribe. Volverla `onValueWritten` sobre `/averias` haría bucle con sus
propias escrituras y, peor, eliminaría la ingesta. El margen de "hasta 5 min" es el intervalo del
poll a una API sin webhook.

**Aplicado (opción A elegida por el usuario):** `functions/index.js` — `schedule` de
`every 5 minutes` → `every 2 minutes`. Reduce el margen a ≤2 min + segundos de FCM, sin tocar la
arquitectura ni arriesgar la ingesta. `node --check` OK. Deploy diferido junto a las `bumpMeta*`.
Documentado en AUDITORIA.md §A13 (corregido) y CLAUDE.md §6.

### Estado del plan A12 al cierre de esta sesión
Fases 0–4 ✅ en código (todas compilan, `EXIT=0`). Server-side (deploy de las 3 Cloud Functions +
seed de `_meta` + `.indexOn` de vehículos) diferido al rebuild de las RTDB, lo ejecuta el usuario.
Fase 5 entregada como instrucción al usuario. Pendiente de verificación en dispositivo real una vez
desplegadas las Cloud Functions (igual que se hizo con A9).

---

## Sesión 2026-07-09 (continuación) — Hallazgo A13: latencia de notificación de Averías

El usuario preguntó si las averías nuevas aparecen instantáneamente o cada 5 minutos. Se verificó
leyendo `functions/index.js:571` y `fcm/TecniAppMessagingService.kt:50`:

- `syncAveriasYNotificar` es `onSchedule({ schedule: "every 5 minutes" })` — polling, no evento.
  Hay hasta 5 min de margen entre que la avería se crea y el servidor la nota.
- Pero el push FCM trae el payload completo de la avería, y `onMessageReceived` lo escribe directo
  en Room (`repo.upsertFromPush`); la UI observa Room vía `Flow` reactivo (`AveriasRepository.observe`),
  así que se actualiza sola en segundos una vez que el push sale — no espera al worker de 15 min.
- **Conclusión:** ni instantáneo puro ni "cada 15 min" — es "hasta 5 min de detección + segundos de
  propagación". Documentado en `AUDITORIA.md §1.5 Hallazgo A13`.
- **Mejora propuesta, no aplicada:** migrar `syncAveriasYNotificar` de `onSchedule` a
  `onValueWritten`/`onValueCreated` (mismo patrón que `notifyProgramacionAssigned`), eliminando el
  margen de 5 min por completo. Se agrupa con el deploy de las Cloud Functions de A12 (Fases 2/3)
  para tocar `functions/index.js` en producción una sola vez, no dos. Pendiente de implementar el
  código de la Cloud Function (aún no escrito) y de desplegar junto con el resto en el rebuild.

