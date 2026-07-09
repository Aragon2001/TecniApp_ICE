# AUDITORÍA TÉCNICA EXTREMA — TecniApp (Android)

**TecniApp** — desarrollado por **Arasoft Solutions** para el **Instituto Costarricense de Electricidad (ICE)**.

Fecha: 2026-07-08. Metodología: lectura directa de los archivos de arquitectura crítica (Room, sync, Firebase, Cloud Functions, reglas de seguridad), análisis estático dirigido (grep de patrones de riesgo: `TODO`, `runBlocking`, `!!`, `GlobalScope`, credenciales, listeners sin liberar) y verificación cruzada contra el proyecto `Tecniapp web` para hallazgos de integración. Todos los hallazgos listados aquí fueron **verificados leyendo el código real**, no son suposiciones genéricas.

Convención de severidad: 🔴 Crítico (rompe funcionalidad, seguridad o integridad de datos) · 🟠 Alto (bug real o riesgo serio) · 🟡 Medio (deuda técnica que hay que planear) · ⚪ Bajo (limpieza/estilo).

> **📋 Estado de remediación (2026-07-08):** varias correcciones ya se ejecutaron. Ver **`log.md`**
> para el registro cronológico. Resumen: ✅ B6 (código muerto), B4 (PmDatabase singleton), B5
> (runBlocking), B3 (logging gateado), A5 (password texto plano), C5 (contadores de progreso). ✅
> parte segura de A7 (índice Git limpio) + A8. ⏸️ Diferidos: A2/A3 (PM, bloqueado por rebuild de
> Firebase), C3 (enum, refactor de persistencia), B1/B2/D1/D2 (clases Dios), B9 (tests). 🔴 A4
> **agravado**: se descubrió que **7 de 12 bases estaban abiertas al público** (no solo "sin reglas
> versionadas"); el usuario re-subirá las bases para rediseñarlas (ver `log.md` §A4).

> **🔎 Nota de re-auditoría independiente (2026-07-08, tarde/noche):** se releyó el código real para
> verificar —sin dar nada por hecho— cada hallazgo que `log.md` reporta como resuelto. B3, B4, B6 y A5
> quedaron **confirmados** tal cual se describen. A4 quedó **confirmado y es peor** de lo que este
> documento decía originalmente. A2/A3 siguen diferidos por decisión del usuario. Se agregó el
> **Hallazgo A9** (nuevo, §1.1) — 13.8 GB/mes de descarga en Averías, probablemente el problema al que
> te referías con "descargas masivas de Firebase Realtime": el diagnóstico es sólido y el fix ya está
> escrito, pero **todavía no se ha probado en un dispositivo real ni desplegado**.

---

## 0. Resumen ejecutivo

El módulo de **Averías** es, con diferencia, la parte más madura y activamente mantenida del proyecto (sync activo, notificaciones, PDF, mapa) — y también la que generó el problema más costoso detectado (Hallazgo A9: 13.8 GB/mes de descarga). Situación actual, tras la primera ronda de correcciones y esta re-auditoría:

1. **Resuelto y verificado:** logging de diagnóstico en producción (B3), singleton de `PmDatabase` (B4), contraseña en texto plano (A5), código muerto (B6), documentación de contadores hardcodeados (C5).
2. **Diagnosticado y con fix escrito, pendiente de probar en un dispositivo real:** el consumo excesivo de descarga de Firebase en Averías (Hallazgo A9, nuevo) — la causa más probable de "las descargas masivas" que motivaron esta ronda de trabajo. También hay avance parcial (visibilidad, no eliminación) en los fallbacks de escaneo completo de localizaciones/medidores (C4).
3. **Confirmado y agravado:** la gobernanza de seguridad de Firebase (Hallazgo A4) — no son ~9 bases sino **12**, y **7 de ellas estaban completamente abiertas al público** sin autenticación. El usuario decidió reconstruir esas bases desde cero en vez de parchear las actuales.
4. **Diferido por decisión explícita:** completar el módulo de Programación/Planillas (A2/A3) — bloqueado porque su base de datos remota es una de las que se van a reconstruir, y porque decidir Android-RTDB-vs-Web-Firestore es parte de ese mismo rediseño.
5. **Deuda técnica aún pendiente (sin tocar):** `RoomRepository` y `FirebaseSyncManager` siguen siendo "clases Dios" (1554 y 1944 líneas) — el usuario decidió explícitamente posponer ese refactor grande para una sesión dedicada, priorizando fixes seguros primero.

Ninguno de estos puntos es "cosmético" — todos afectan directamente la confiabilidad, el costo operativo y la capacidad de mantenimiento del proyecto. Ver `log.md` para el detalle cronológico completo de cada cambio ya ejecutado.

---

## 1. Hallazgos críticos y de alto impacto (arquitectura / integración)

### 🔴 A1 — La documentación del proyecto estaba desactualizada respecto al schema real
El `CLAUDE.md` anterior afirmaba `AppDatabase` en schema **v27** y "migraciones v24→v27 deben preservarse". El código real (`Database/room/AppDatabase.kt`) está en **`SCHEMA_VERSION = 31`**, con migraciones explícitas hasta 30→31 (tabla `reporte_generado`). El panel de administración web (`Admin.tsx`) también hardcodea `'2.7.0 (schema v27)'`. Este tipo de deriva documental es exactamente lo que hace que un "segundo cerebro" deje de ser confiable — cualquier decisión tomada asumiendo v27 (p. ej. qué columnas existen) sería incorrecta.
**Acción:** ya corregido en el `CLAUDE.md` nuevo. Recomendado: automatizar (script o CI) que compare `SCHEMA_VERSION` contra lo que se documenta/expone en el panel web, para que no se repita.

### 🔴 A2 — El módulo de Programación/Planillas (PM) está desconectado de su propia UI
Evidencia:
- `ui/planillas/viewmodel/PlanillasViewModel.kt`: los 4 métodos de negocio (`refresh`, `generatePdf`, `retrySync`, `openPlanilla`) son cuerpos vacíos con comentarios `// TODO: cargar desde Room...`, `// TODO: disparar generación de PDF...`, etc. **Cero llamadas** a `OperacionRepository` o a cualquier DAO de `PmDatabase`.
- `PmSyncScheduler.enqueue(...)` (el único punto que agenda `PmSyncWorker`) **no es invocado desde ningún otro archivo del proyecto** (verificado con búsqueda global). El worker solo correría si algo lo encola, y nada lo encola.
- `pm/repository/PlanillaStorageUploader.kt`: la única implementación existente es `FakePlanillaStorageUploader`, que no sube el PDF a ningún storage real — solo devuelve `file://<ruta local>` como si fuera una URL remota. Si algún día se conecta la UI, el PDF "subido" seguirá siendo un archivo que solo existe en el teléfono que lo generó.
- Los Fragments de la carpeta (`ActivityGroupsManagerFragment`, `PlanillaDetailFragment`, `PlanillasHomeFragment`, `WorkLogsDetailFragment`, `WorkLogEditBottomSheet`) tienen comentarios `// TODO: conectar ViewModel...`, `// TODO: reintentar sync`, `// TODO: compartir PDF`, `// TODO: abrir visor PDF`, `// TODO: navegar a WorkLogsDetail`.

**Impacto:** el módulo aparece en la navegación (`nav_planillas.xml`) pero es, en la práctica, una demo sin funcionalidad real conectada. Cualquier tiempo de desarrollo dedicado a "mejorar" la UI de planillas sin antes cablear esto es tiempo perdido.
**Acción recomendada:** decidir explícitamente si el módulo PM se termina (conectar ViewModel ↔ Repository ↔ Scheduler, implementar un `PlanillaStorageUploader` real contra Firebase Storage) o se elimina del repo/navegación para no confundir a futuros desarrolladores.

### 🔴 A2/A3 — Estado: ⏸️ DIFERIDO (decisión registrada 2026-07-08)
El usuario decidió "terminar" el módulo PM, pero se identificó una dependencia dura: su destino remoto (`tecniapp-ice-planilla`) es una de las 12 bases que el usuario va a **eliminar y volver a subir** para rediseñar su estructura (ver Hallazgo A4 actualizado). Cablear la UI ahora contra una base que va a desaparecer sería trabajo desechable, y la decisión de tecnología única con la web (Hallazgo A3) es precisamente algo que debe resolverse como parte de ese rediseño. **Se difiere explícitamente** hasta después del rebuild de Firebase. Lo único que se adelantó (no desechable): el singleton de `PmDatabase` (Hallazgo B4, ya resuelto), que es precondición para cablear la UI sin doble instancia de Room. Ver `log.md` §Fase 3 para el plan detallado de 5 pasos a retomar.

### 🔴 A3 — Planillas usa dos tecnologías de base de datos distintas entre Android y Web
`OperacionRepository.kt` (Android) escribe a **Firebase Realtime Database** (`https://tecniapp-ice-planilla.firebaseio.com/`, rutas `pm_operacion/{region}/{subregion}/...`). `Tecniapp web/src/pages/Planillas.tsx` lee de **Cloud Firestore**, colección `pm_planillas` (`collection(db, 'pm_planillas')`). Se verificó `functions/index.js` completo: no existe ninguna Cloud Function que sincronice/replique datos entre esa RTDB y Firestore. **Estas dos implementaciones no pueden ver los datos de la otra bajo ninguna circunstancia actual.**
**Impacto:** aunque se resolviera el Hallazgo A2 (conectar la UI de Android), las planillas generadas desde el teléfono seguirían sin aparecer nunca en la web, y viceversa. Este es el hallazgo más importante para el objetivo del usuario de "que la web funcione con las mismas bases de datos que Android".
**Acción recomendada:** elegir una sola tecnología (recomendado: Firestore, por su modelo de consultas más rico para reportes tipo planilla) y reescribir el lado que no la use, o construir un puente explícito (Cloud Function `onValueWritten` en RTDB → escribe a Firestore).

### 🔴 A4 — Reglas de seguridad de Firebase — **AGRAVADO Y VERIFICADO (2026-07-08 tarde)**
Con acceso directo al CLI de Firebase se hizo lo que este documento no pudo hacer originalmente por falta de acceso: **enumerar las bases reales**. Resultado: **son 12 instancias RTDB, no 9** (además de las documentadas existen `tecniapp-ice-inventario`, `-luminarias`, `-ordenes` y `-programacion` como bases propias, no nodos del default). Se exportaron las reglas desplegadas de las 12 vía REST (`/.settings/rules.json`).

**El hallazgo real es peor de lo que este documento estimaba:** **7 de las 12 bases estaban completamente ABIERTAS al público** (`".read": true, ".write": true`, sin `auth`) — `default-rtdb` (que incluye `usuarios` con PII y `Medidores`), `inventario`, `luminarias`, `materiales`, `ordenes`, `personal` y `planilla`. Cualquier persona en Internet, sin autenticarse, podía leer **y sobrescribir o borrar** esos datos. Solo `tecniapp-ice` y `datosgenerales` (`auth != null`), `averias` (cerrada + reglas en `/averias`) y `user` (por-uid + validaciones) estaban razonablemente protegidas; `programacion` estaba bloqueada por completo (`false`/`false` — a revisar si el módulo la usa).

**Decisión del usuario:** estas bases son consideradas legacy — serán **eliminadas y vueltas a subir** para rediseñar su estructura y reglas desde cero. En consecuencia, **no se desplegó** el endurecimiento de reglas ya preparado (hubiera sido trabajo sobre bases que van a desaparecer), y se removió el `database.rules.json` huérfano (no correspondía a la estructura real de ninguna base desplegada) junto con el bloque `database` de `firebase.json`/`.firebaserc`. **Las 12 instancias en producción siguen exactamente como estaban** (7 abiertas) hasta que el usuario las reconstruya — este es el pendiente de mayor riesgo del proyecto ahora mismo.
**Próximo paso (cuando el usuario suba las bases nuevas):** exportar su estructura, diseñar reglas por instancia (mínimo `auth != null`, idealmente por rol/uid) y no repetir el patrón de "abierta por defecto". Detalle completo y lista exacta de bases en `log.md` §Fase 2.

---

<details>
<summary>Texto original del hallazgo (contexto histórico, antes de tener acceso CLI)</summary>

`firebase.json` solo declara reglas (`database.rules.json`) para los targets `default` y `localizaciones`. Las otras bases activamente usadas por la app — `tecniapp-ice-user`, `tecniapp-ice-datosgenerales`, `tecniapp-ice-personal`, `tecniapp-ice-materiales`, `tecniapp-ice-averias`, `tecniapp-ice-planilla`, más los nodos de inventario/luminarias — **no tienen reglas versionadas en este repositorio**. No se puede confirmar desde el código si están protegidas por reglas configuradas manualmente en la consola de Firebase (fuera de control de versiones) o si dependen de las reglas por defecto.
Además, dentro del propio `database.rules.json`, el control de rol (`root.child('roles').child(auth.uid).child('isAdmin')`) solo se aplica al campo `km_actual` de vehículos; el resto del árbol es `".read"/"​.write": "auth != null"` — es decir, **cualquier usuario autenticado (un técnico cualquiera) puede escribir en cualquier ruta no restringida explícitamente**, incluyendo datos de otros vehículos, agencias, etc. El control de admin/supervisor que se ve en la UI (`isAdmin`, `LuminariasPermissions`, etc.) es **solo del lado del cliente**.
**Impacto:** un usuario técnico con la app modificada (o simplemente usando la API REST de Firebase con su propio token) podría escribir o borrar datos fuera de su alcance normal.
**Acción recomendada:** (1) exportar y versionar en este repo las reglas reales de las 9 bases (`firebase database:get`/consola), (2) mover el control de "quién puede escribir qué" del cliente a las reglas de seguridad, al menos para operaciones destructivas (borrar vehículo, borrar localización, editar usuarios ajenos).

</details>

### ✅ A5 — Cambio de contraseña en texto plano — **RESUELTO (verificado 2026-07-08 tarde)**
`User/UserFragment.kt` (línea ~491) construía el usuario actualizado con `password = if (newPassword.isNotEmpty()) newPassword else user.password`, y `UserEntity` tenía un campo `password` que se persistía en Room/Firebase. Si esto reflejaba la contraseña real de la cuenta, se estaba guardando en texto claro en Room y en Firebase Realtime Database, fuera del sistema de Firebase Auth.

**Estado verificado:** se confirmó que el login/registro nunca usaron `UserEntity.password` para autenticar (siempre `FirebaseAuth.signInWithEmailAndPassword`); el único leak real era en `UserFragment.persistUserRemote(...)`. Se corrigió: `UserFragment.kt` ya no copia la contraseña al `UserEntity` al guardar perfil (`password = null`) y `persistUserRemote` sanitiza (`copy(password = null)`) antes de escribir a Firebase como defensa en profundidad; `FirebaseSyncManager` ya no rehidrata `password` desde Firebase; y `Database/entities/UserEntity.kt` marca el campo `@Deprecated` con un comentario explícito que referencia este hallazgo (verificado directamente en el archivo). Se comprobó con `firebase database:get /usuarios` que los 4 usuarios reales en producción **no tenían** el campo `password` — no hubo nada que purgar. Pendiente menor: dropear la columna físicamente en una migración Room v32 dedicada (hoy solo está deprecada, no eliminada, para no arriesgar una migración sin poder compilar/probar en el momento).

### 🟡 A6 — Duplicidad de bases "localizaciones"
`firebase.json` declara un target `localizaciones` separado del `default`, apuntando a `tecniapp-ice` (vs. `tecniapp-ice-default-rtdb` del default). Sin embargo, en el código Android (`RoomRepository`/`FirebaseSyncManager`) las localizaciones/medidores/pueblos se leen todos de la base default. No quedó evidencia en este repo de qué usa realmente el target `localizaciones` — puede ser un remanente de una migración anterior.
**Acción recomendada:** confirmar en la consola de Firebase si `tecniapp-ice` (el target `localizaciones`) tiene tráfico real; si no, eliminar el target de `firebase.json` para no dejar ambigüedad.

### ✅ A7 — Historia de Git mezclada entre los dos proyectos — **RESUELTO (2026-07-09)**
**Commit `edafc6b0`** (115 archivos, −30.972 líneas): los 113 archivos de `Tecniapp web/` sacados del índice de este repo con `git rm -r --cached`, `.gitignore` ampliado con `Tecniapp web/` completo. El `git status` ya no muestra las ~700 líneas de "deleted". Commit en `master`.

**Reevaluación de "secretos expuestos":** se revisó el contenido real del `.env` en el historial (commit `951c83fa`) y solo contiene variables `VITE_*` (API key de Firebase Web, URLs de RTDB, clave de Google Maps) — son **públicas por diseño** (Vite las embebe en el bundle del navegador; cualquier visitante de la web ya las recibe) y **no incluyen** ninguna service-account ni clave privada. Por tanto, reescribir el historial compartido de GitHub (`git push --force`, irreversible) para purgar valores que ya son públicos se considera **desproporcionado** frente al riesgo real. Recomendación actualizada: no reescribir historial; en su lugar restringir la clave de Google Maps por dominio/referrer en Google Cloud Console, y confiar en que el rebuild de Firebase que hará el usuario (ver A4) ya renueva el entorno.

<details>
<summary>Texto original del hallazgo</summary>

`git log` de este repositorio muestra commits como `"CARGA DE WEB"` (×4) y `"merge(master): fusionar rama master con cambios-desde-Claude-movil"`, y `git ls-files` confirma que **113 archivos bajo `Tecniapp web/...` están indexados dentro de este mismo repositorio Git** (incluyendo `Tecniapp web/.env`, con credenciales de Firebase reales, y `Tecniapp web/dist/...`, artefactos de build). Al mismo tiempo, la carpeta `Tecniapp web` en disco **ya no vive dentro de este repositorio** (es una carpeta hermana sin `.git` propio), por lo que `git status` marca esos ~700 archivos como "deleted" permanentemente. Adicionalmente se detectó un `index.lock` de Git que no se pudo eliminar por permisos, señal de que hay un proceso de Git colgado o de un problema de permisos del sistema de archivos.
**Impacto:** (1) el historial de Git de este repo contiene secretos del proyecto web (API keys de Firebase) que probablemente ya no deberían estar en ningún historial; (2) el proyecto web, tal como existe hoy en disco, **no tiene control de versiones propio** — no hay forma de revertir cambios, ver diffs o colaborar con seguridad; (3) el `git status` de 700+ líneas hace prácticamente imposible ver cambios reales nuevos en este repo a simple vista.
**Acción recomendada:** (1) inicializar un repositorio Git propio para `Tecniapp web` cuanto antes; (2) en este repo, hacer `git rm -r --cached "Tecniapp web"` y añadir `Tecniapp web/` a `.gitignore` (ya casi está, falta el `git rm` para que el índice se limpie) o, si se prefiere, rotar todas las credenciales de Firebase del `.env` que quedaron expuestas en el historial y purgarlo con `git filter-repo`/BFG; (3) resolver el `index.lock` (verificar que no haya un proceso de Git/Android Studio bloqueándolo y que los permisos del `.git` sean correctos).

</details>

### 🟡 A8 — Secretos y configuración commiteados — reevaluado junto con A7
`git ls-files` confirma que `app/google-services.json` está commiteado. Para un repositorio privado esto es una práctica común y de bajo riesgo (las claves ahí están restringidas por `package_name`/SHA-1). Sin cambios de código aquí; la reevaluación de riesgo real (credenciales `VITE_*` públicas por diseño, sin service-account) se documentó en el Hallazgo A7 de arriba.

---

## 2. Deficiencias de código (calidad, mantenibilidad)

### 🟠 B1 — `RoomRepository` es una "clase Dios" (1554 líneas, ~15 responsabilidades)
Mezcla en una sola clase: vehículos + bitácora, medidores, localizaciones, inventario, luminarias, sincronización de catálogos, sincronización de subregión completa (con progreso), realtime listeners de Firebase, y utilidades de estimación de bytes descargados. Cualquier cambio en un dominio (p. ej. inventario) obliga a tocar un archivo que también controla vehículos y sync completo, con alto riesgo de romper algo no relacionado y dificultando los tests unitarios (no hay forma de testear "inventario" sin instanciar todo lo demás).
**Recomendación:** dividir en repositorios por dominio (`VehiculoRepository`, `MedidorRepository`, `InventarioRepository`, `LuminariaRepository`, `SyncCatalogoRepository`) que compartan `AppDatabase` y `FirebaseSyncManager` por inyección, no por composición interna.

### 🟠 B2 — `FirebaseSyncManager` es la clase más grande del proyecto (1944 líneas)
Concentra: acceso a las 9 instancias RTDB, parsing manual de `DataSnapshot` (con múltiples variantes de nombres de campo por retro-compatibilidad: `stringChildAny`, `longChildAny`, `intValueAny`...), normalización de claves de agencia (incluye una función `fixCommonMojibake` para arreglar codificación de caracteres corrupta — señal de que hubo/hay datos con problemas de encoding en Firebase), y listeners realtime. 37 llamadas a `Log.*` dentro de un solo archivo.
**Recomendación:** separar por dominio igual que B1; extraer el parsing de snapshots a mappers dedicados y testeables; investigar y corregir en origen el problema de mojibake en vez de compensarlo en cada lectura.

### ✅ B3 — Logging de diagnóstico masivo dejado en producción — **RESUELTO (verificado 2026-07-08 tarde)**
Se cuentan decenas de líneas `Log.i(TAG, "[INV_DIAG]...")`, `[LUM_SYNC]...`, `[SYNC_SUBREGION]...`, `[SYNC_FLOW]...` en `RoomRepository`, `Synchronizer` y `FirebaseSyncManager`, con formato de "tags" propios de una sesión de debugging intensiva que nunca se retiró. No es solo ruido: en producción esto es I/O y construcción de strings innecesaria en rutas calientes de sincronización (concatenación de strings en cada iteración de medidores, por ejemplo).
**Recomendación:** introducir un logger con niveles configurables (p. ej. Timber) y un `BuildConfig.DEBUG` gate, o eliminar los logs de diagnóstico ad-hoc una vez confirmado que los bugs que motivaron cada uno están resueltos.

**Estado verificado:** se creó `Database/sync/SyncLog.kt` — un logger propio que envuelve `android.util.Log` y filtra `v/d/i` por `BuildConfig.DEBUG` (no se ejecutan en builds de release), manteniendo `w`/`e` siempre activos para diagnóstico de fallos reales. Se confirmó por conteo exacto que **las tres clases afectadas ya no tienen ninguna llamada directa a `Log.*`**: `FirebaseSyncManager.kt` (0 `Log.*`, 37 `SyncLog.*`), `RoomRepository.kt` (0 `Log.*`, 46 `SyncLog.*`) y `Synchronizer.kt` (0 `Log.*`, 14 `SyncLog.*`). Migración completa y correcta. No se detectaron otros archivos con el mismo patrón de logging ad-hoc masivo fuera de estos tres.

### ✅ B4 — `PmDatabase` sin patrón singleton — **RESUELTO (verificado 2026-07-08 tarde)**
`AppDatabase.getInstance(context)` es un singleton clásico con doble-check locking. `PmDatabase`, en cambio, se instanciaba con `Room.databaseBuilder(...).build()` **directamente dentro de `PmSyncWorker.doWork()`**, sin singleton, y se cerraba al final del worker.

**Estado verificado:** se leyó directamente `pm/room/PmDatabase.kt` — ahora tiene un `companion object` con `getInstance(context)` (mismo patrón `@Volatile` + doble-check locking que `AppDatabase`, con comentario que referencia este hallazgo). `PmSyncWorker` ya no abre/cierra su propia instancia. Esto elimina el riesgo de dos handles de Room sobre el mismo `.db` y es precondición para cuando se conecte la UI del módulo PM (Hallazgo A2, diferido).

### 🟡 B5 — Uso de `runBlocking` dentro de funciones `suspend`/coroutines — **RESUELTO según `log.md` (no re-verificado línea por línea en esta sesión)**
Encontrado originalmente en `RoomRepository.startRealtimeSyncForScope`, `FirebaseSyncManager` (línea ~1234) y `ReportesViewModel` (3 sitios). `log.md` registra que los 3 sitios se reemplazaron por llamadas `suspend` directas (la función `runBlockingUserContext()` de `ReportesViewModel` pasó a `suspend fun obtenerUserContextOrNull()`) y que `grep runBlocking **/*.kt` da 0 coincidencias, validado además con `./gradlew compileDebugKotlin` exitoso. Recomendado como parte de la verificación final: confirmar con un build real que no quedan llamadas no-`suspend` a las funciones que cambiaron de firma.

### ✅ B6 — Código muerto — **RESUELTO (verificado 2026-07-08 tarde)**
- `Database/sync/SyncWorker.kt` — **confirmado eliminado** del árbol de trabajo (ya no existe en disco).
- `functions/mail/mailer.js` (+ carpeta `functions/mail/`) — **confirmado eliminado**.
- `pm/sync/PmSyncScheduler.kt` — **conservado deliberadamente** (decisión registrada): aunque hoy no se invoca, se cableará cuando se retome el módulo PM (Hallazgo A2/A3, diferido), así que no se trata como código muerto a eliminar.
- `FakePlanillaStorageUploader` — sigue como implementación de relleno (ver A2/A3); no se toca hasta que se decida la tecnología de datastore del módulo PM.

### ⚪ B7 — Naming inconsistente entre "programación" (módulo Room v26) y "planillas/PM"
Existen dos sistemas de "trabajo asignado a técnicos" con nombres que se pisan: `ui/programacion/*` (sobre `AppDatabase`, entidad `ProgramacionEntity`, activo) y `ui/planillas/*` + `pm/*` (sobre `PmDatabase`, módulo nuevo, desconectado — Hallazgo A2). Para alguien nuevo en el proyecto (o para Claude en una sesión futura) es fácil confundir cuál es cuál.
**Recomendación:** documentar explícitamente (ya se hizo en `CLAUDE.md` §5.9/5.10) o, mejor, renombrar uno de los dos para que los nombres no se solapen conceptualmente.

### ⚪ B8 — `!!` (non-null assertion) — 40 ocurrencias en el proyecto
No es alarmante en volumen, pero cada una es un `NullPointerException` potencial en producción sin mensaje descriptivo. Recomendado revisar puntualmente (no se listan todas aquí por espacio) y reemplazar por `checkNotNull(x) { "mensaje explicando por qué no debería ser null" }` donde el null realmente no debería ocurrir, o por manejo explícito donde sí puede ocurrir.

### ⚪ B9 — Tests prácticamente inexistentes
`app/src/test` y `app/src/androidTest` existen como carpetas pero no se detectó una suite de pruebas real acorde al tamaño del proyecto (~230 archivos Kotlin, dominios de negocio complejos con reglas de mantenimiento, normalización de subregiones, merges de vehículos, etc. — exactamente el tipo de lógica que más se beneficia de tests unitarios). La ausencia de tests es lo que permite que bugs como el de "planillas en dos bases distintas" (A3) o "PM desconectado de su UI" (A2) lleguen tan lejos sin detectarse.
**Recomendación:** priorizar tests unitarios sobre lógica pura sin Android (normalizadores, mergers, `RetryPolicy`, `IdGenerator`) antes que tests de UI — dan el mayor retorno por esfuerzo.

---

## 3. Deficiencias de lógica (riesgos funcionales concretos)

### 🟠 C1 — Persistencia offline de Firebase deshabilitada selectivamente "para prevenir OOM"
`TecniApp.enableFirebasePersistence()` habilita `setPersistenceEnabled(true)` solo para 4 de las 9 URLs de RTDB, con un comentario explícito: *"Evitamos persistencia local en nodos de alto volumen (medidores/localizaciones, inventario/averías/luminarias) para prevenir OOM al rehidratar caché SQLite de Firebase."* Esto confirma que en algún momento hubo problemas reales de memoria con el caché nativo de Firebase en los nodos más grandes (medidores puede tener decenas de miles de registros). La solución actual (deshabilitar persistencia ahí) es razonable como mitigación, pero significa que **esos dominios dependen 100% de que Room ya tenga los datos sincronizados**; si Room está vacío (instalación nueva) y no hay red en el primer arranque, esos módulos quedan sin datos hasta la próxima sincronización exitosa.
**Recomendación:** validar que la UI de esos módulos maneje explícitamente el estado "sin datos locales y sin red" con un mensaje claro, no solo una lista vacía.

### 🟠 C2 — Fusión de vehículos (`fusionarVehiculos`/`deduplicarVehiculos`) y de catálogos por comparación de igualdad completa de objeto
`RoomRepository.syncCatalogosGenerales`/`syncSubregion` deciden si actualizar un registro local comparando `local == remoto` (igualdad de `data class` completa). Esto significa que **cualquier diferencia, incluso en un campo irrelevante para la UI, dispara un re-escritura completa**, y a la inversa, si Firebase devuelve el mismo objeto pero Room tiene un campo local "mejorado" (p. ej. `kmActual` fusionado), la próxima sync remota podría no detectar que debe fusionar de nuevo si por casualidad el resto de campos coincide. La lógica de merge (`maxOf` de kilometraje, "preferir local si no vacío", etc.) está bien pensada pero **dispersa en al menos 3 sitios distintos** (`syncVehiculoDesdeFirebase`, `combinarVehiculosConLocales`, `fusionarVehiculos`) con reglas ligeramente distintas entre sí, lo que es una fuente probable de inconsistencias sutiles entre "qué gana" según por cuál camino de sync pasó el dato.
**Recomendación:** centralizar la política de merge de `VehiculoEntity` en una única función pura y testeada, usada por los 3 sitios.

### 🟡 C3 — `estado` de sync como `String` libre en el módulo principal vs `enum` en el módulo PM
`Database/sync/SyncStatus.kt` define constantes de texto (`"PENDING"`, `"SYNCED"`, etc. — inferido del uso de `syncState = "PENDING"` en `RoomRepository`/`VehiculoLogEntity`/`ProgramacionEntity`), mientras que `pm/model/SyncStatus.kt` es un `enum class` real. Usar strings libres para un campo de estado permite typos silenciosos (`"Pendiente"` vs `"PENDING"`) que compilan sin error y fallan solo en runtime/queries.
**Recomendación:** migrar el módulo principal al mismo patrón de enum + `TypeConverter` que ya existe y funciona en el módulo PM.

### ✅ C4 — Fallback "descarga completa y filtra en memoria" en localizaciones y búsqueda de medidores — **RESUELTO (2026-07-09)**
`FirebaseSyncManager.obtenerLocalizacionesPorPueblos` documenta explícitamente una "Estrategia 2 (fallback): descarga completa y filtra en memoria" cuando la estrategia indexada falla. Para una base con muchas localizaciones esto puede ser una descarga muy pesada (tiempo, datos móviles, memoria) disparada silenciosamente como fallback. Debe registrarse/alertarse cuándo se cae a este camino (ya hay `Log.w`, pero conviene además instrumentar con analytics para saber con qué frecuencia ocurre en producción).

**Estado final (2026-07-09):**
- `FirebaseSyncManager.obtenerLocalizacionesPorPueblos`: Estrategia 2 (fallback de full-scan) sigue existiendo con `SyncLog.w` cuando supera 10.000 registros — visible, pero no eliminada (la sincronización batch de `obtenerMedidoresPorLotes` y el conteo `?shallow=true` ya estaban bien diseñados).
- **`MedidorViewModel.confirmarRegistro`** (antes línea ~507): `buscarMedidorEnFirebase` → `buscarMedidorEnFirebaseLigero` ✅ (2026-07-09). Elimina el full-scan en el flujo de registro manual.
- **`AveriasViewModel.buscarMedidor`** (antes línea ~512): `buscarMedidorEnFirebase` → `buscarMedidorEnFirebaseLigero` ✅ (2026-07-09). Elimina el full-scan en la búsqueda de medidor de una avería.
- **`AveriasRepository.scopedAveriasQuery`**: eliminada ✅ (2026-07-09). Era código muerto (0 callers) que descargaba la BD de averías completa sin límite.
- Commit: `953563b5`. BUILD SUCCESSFUL verificado.
- `buscarMedidorEnFirebase` (con fallback) sigue existiendo en `FirebaseSyncManager` como función pública pero ya no tiene callers en el proyecto — candidato a eliminar o marcar `@Deprecated` en una limpieza futura.

### ✅ C5 — Contadores de progreso hardcodeados — **MITIGADO (verificado 2026-07-08 tarde)**
`SUBREGION_SYNC_STEPS = 5` y `BASE_EXTRA_STEPS = 2` en `Synchronizer`/`RoomRepository` están hardcodeados y deben mantenerse manualmente en sincronía con la cantidad real de pasos del método `syncSubregion`. Se verificó que ambos valores **sí coinciden** con los pasos reales actuales (5 `done += 100` en `syncSubregion`: agencias, pueblos, localizaciones, vehículos, medidores). No se convirtió a conteo dinámico (sería un cambio de diseño más amplio para un impacto ⚪ bajo), pero se documentó explícitamente el acoplamiento con comentarios en el código (`RoomRepository` y el `+3` de `SettingsFragment`, antes mágico, ahora nombrado `cacheResyncExtraSteps`) para que la próxima persona que agregue un paso sepa qué constante actualizar.

---

## 1.1 Hallazgo nuevo — 🔴 A9 (bandwidth) — Consumo excesivo de descarga de Firebase RTDB en Averías

**Este es, con datos reales, el hallazgo de mayor impacto económico y técnico detectado en todo el proyecto — y el que motivó la sesión de correcciones en curso.**

**Diagnóstico (verificado, investigación read-only):** el proyecto Firebase generó **~13.8 GB/mes de descarga** con solo ~6 MB de datos reales en la base de averías, lo que llevó el proyecto al plan de pago (Blaze) con un costo de ~$50. Causa raíz identificada con >99% de confianza:
- `AveriasSyncWorker` corre cada 15 minutos (`TecniApp.kt`) y llama a `AveriasRepository.pullFromFirebaseOnce()`, que **re-descargaba toda la región de averías completa en cada ejecución** (`orderByChild("region").equalTo(...)`), sin ninguna marca de agua/sync incremental.
- Además, `AveriasViewModel` dispara `AveriasSyncWorker.triggerNow()` (mismo pull completo) **después de cada escritura del usuario** — asignar, atender, cerrar, anular, revertir (6 call-sites distintos).
- La persistencia offline de Firebase está deshabilitada a propósito para el nodo de averías (`TecniApp.kt`, para evitar OOM — ver Hallazgo C1), así que cada uno de esos pulls iba directo al servidor sin ningún caché que lo abaratara.
- Matemática: ~2.880 pulls/mes × ~5-6 MB (la región es casi toda la base) ≈ 14-17 GB/mes — cuadra con lo observado.
- Hallazgo secundario: `scopedAveriasQuery()` en `AveriasRepository.kt` también descarga la base completa sin límite, pero **es código muerto** (sin ningún caller) — no contribuye al consumo real, pero es candidato a limpieza (Hallazgo B6).
- No se detectaron listeners de Firebase sin liberar (`removeEventListener` correcto en inventario/luminarias) — el problema no era una fuga de listeners, sino pulls completos y repetidos por diseño.

**Fix aplicado (verificado en el código real de `AveriasRepository.kt`):** se implementó sincronización incremental por "marca de agua" (`watermark`):
- Nueva función privada `fetchDeltaChildren(regionVariants, watermark)`: consulta solo los registros con `lastUpdated >= (watermark - 5 min de solape)` usando el índice `lastUpdated` que ya existe, y filtra por región en el cliente (RTDB no permite filtrar por dos campos a la vez). Reduce cada pull de "toda la región" a "solo lo que cambió" — de megabytes a kilobytes.
- `pullFromFirebaseOnce()`: si ya hay averías locales, calcula el `watermark` como el mayor `lastUpdated` local y usa el camino delta; si Room está vacío (instalación nueva / primera sync), mantiene intacta la descarga completa original acotada por región (comportamiento de "seed" sin cambios).
- El diseño es compatible hacia atrás: el pull nunca borra localmente lo que no viene en la respuesta remota, así que el delta no puede "perder" registros por accidente.
- **Se verificó por lectura directa del código** que la lógica está implementada como se describe (constantes `DELTA_OVERLAP_MS` y `SYNC_SCOPE_CACHE_MS` viven en un `private companion object` al final de la clase, junto con `TAG` y `FALLBACK_GLOBAL_LIMIT`).

**⚠️ Estado real: implementado pero NO desplegado ni probado en producción todavía** (el propio registro de cambios lo marca así explícitamente, a la espera de que el usuario lo pruebe antes de publicar, dado que el proyecto está en un plan con cuota ajustada). Antes de darlo por cerrado falta:
1. Probar en un dispositivo/emulador real que el primer sync (Room vacío) sigue trayendo todo correctamente y que el segundo sync en adelante usa el camino delta (verificable en Logcat por el tag `SCOPED_DELTA`).
2. Confirmar que no haya "agujeros" de sincronización si el dispositivo estuvo apagado más tiempo que la ventana de solape en un escenario específico (el diseño usa `watermark` persistido implícitamente en el propio dato de Room, no en un timestamp aparte — revisar que el `maxOf(lastUpdated)` local sea siempre confiable incluso tras una reinstalación parcial).
3. Medir el consumo real tras desplegar (la base `tecniapp-ice-averias` respondía HTTP 402 "downgraded" al momento del diagnóstico, así que no se pudo medir en vivo).
4. Complementos recomendados, no incluidos en este fix: espaciar el intervalo del worker (apoyándose en las notificaciones push ya existentes vía `syncAveriasYNotificar` para no depender solo del polling de 15 min), debounce/persistencia en los listeners realtime de inventario/luminarias (que se re-enganchan completos en cada `onStart` de foreground), y eliminar el código muerto `scopedAveriasQuery()`.

**Esto responde directamente a la pregunta de "cómo van los avances de las descargas masivas de Firebase":** el diagnóstico está completo y es sólido, el fix de mayor impacto (Averías) está escrito y compila, pero **todavía no se ha probado en un dispositivo real ni desplegado** — es lo primero que debería confirmarse antes de considerar este punto cerrado. En paralelo, el Hallazgo C4 (fallbacks de escaneo completo en localizaciones/medidores) tiene avances menores independientes de este fix — ver arriba.

---

## 1.2 Hallazgo nuevo — 🔴 A10 — El mecanismo de auto-actualización (GitHub) no tenía nada real detrás, y no había firma de release

**Contexto:** el código de `update/*.kt` (checker, worker, download manager, dialog) ya existía
y estaba bien escrito, pero apuntaba a `https://raw.githubusercontent.com/.../updates/update.json`,
un archivo que **no existía** en el repo (verificado con la API de GitHub: `contents/updates` →
vacío, `releases` → `[]`). Es decir, **ningún dispositivo en campo podía recibir nunca una
actualización**, incluyendo el fix de A9 — esto es una causa directa de que "las descargas
masivas" sigan ocurriendo: los teléfonos viejos no tienen forma de enterarse de que existe una
versión más eficiente.

**Segundo hallazgo, más grave:** `build.gradle.kts` no tenía `signingConfigs` ni
`buildTypes.release` — nunca se firmó un release real. Se confirmó con el usuario que los
teléfonos de campo corren **builds debug** (firma genérica de Android Studio). Un "update" solo
se puede instalar in-place si tiene la **misma firma** que la app instalada.

**Fix aplicado (2026-07-09):**
- Generado un keystore de release dedicado (RSA 4096), entregado al usuario **fuera del repo**
  (nunca se commitea — instrucción explícita adjunta).
- `build.gradle.kts`: `signingConfigs.release` (credenciales por variable de entorno/CI o
  `local.properties`, nunca hardcodeadas) + `buildTypes.release` + `versionCode`/`versionName`
  parametrizables desde CI (derivados automáticamente del tag git, elimina el riesgo de
  "olvidar bump-ear la versión").
- Nuevo `.github/workflows/release.yml`: al pushear un tag `vX.Y.Z` compila, firma, publica un
  GitHub Release con el APK, y genera/commitea `updates/update.json` con la URL real y el
  SHA-256 — cierra el círculo completo que el código cliente ya esperaba.

**⚠️ Consecuencia operativa importante (no técnica, pero crítica):** como los teléfonos actuales
están en debug y el nuevo keystore es distinto, **el primer release de este pipeline no podrá
instalarse encima de la app actual** — Android lo rechazará por firma distinta. El primer
despliegue requiere desinstalar/reinstalar manualmente una vez por dispositivo; a partir de ahí,
todas las actualizaciones futuras sí serán in-place y automáticas.

**Pendiente (configuración manual del usuario en GitHub, no se puede hacer desde este entorno):**
habilitar permisos de escritura del workflow, cargar los 4 Secrets del keystore, y disparar el
primer tag para validar el pipeline de punta a punta. Ver `log.md` §"Pipeline de
auto-actualización" para el detalle exacto paso a paso.

---

## 1.3 Hallazgo nuevo — 🔴 A11 (CRÍTICO, PII) — Exports reales de Firebase commiteados en un repositorio público

**El hallazgo de mayor riesgo de privacidad detectado en todo el proyecto.** Se encontró (al
revisar un commit generado durante el trabajo de A10) una carpeta `firebase json/` con 4 archivos
de exportación real de datos de producción, commiteados en un momento anterior (`cf684504
"CAMBIOS DEL BOTON"`) y vueltos a tocar en un commit posterior, en un repositorio **público**
(`Aragon2001/TecniApp_ICE`, confirmado `"private": false`).

Contenido verificado leyendo los archivos directamente:
- `tecniapp-ice-user-export.json`: usuario real con **nombre completo, cédula, teléfono, email,
  placa de vehículo**, y un nodo `verificationCodes` con **códigos de verificación reales**.
- `tecniapp-ice-default-rtdb-export.json` (20 MB): miles de registros de `Medidores` con
  **nombres reales de clientes** (personas y empresas) y ubicación.
- `tecniapp-ice-export.json` (947 KB): `Localizaciones` con coordenadas GPS reales.
- `tecniapp-ice-datosgenerales-export.json`: catálogos (bajo riesgo).

**Remediado parcialmente (2026-07-09):** los 4 archivos se sacaron del índice de git
(`git rm --cached` + commit `d95d8745`) y se agregó `firebase json/`/`*-export.json` a
`.gitignore`. **Pendiente (crítico):** los archivos siguen visibles en el **historial** de git
(commits `cf684504` y `b5db1d1f`) — hace falta purgarlo con `git filter-repo`/BFG +
`push --force`, y decidir si el repo pasa a privado como mitigación inmediata mientras tanto. Ver
`log.md` §"Incidente de seguridad 2026-07-09" para el detalle completo y la lista de pendientes
(incluye evaluar si amerita algún protocolo de incidente de datos, dado que ICE es una
institución pública).

---

## 4. Deficiencias de diseño (UX / estructura de proyecto)

- **⚪ D1 — Archivos "gigantes" concentran demasiada UI+lógica en un solo Fragment/BottomSheet:** `AveriaDetalleBottomSheet.kt` (2181 líneas), `ReportesViewModel.kt` (1871), `LocalizacionFragment.kt` (1601), `AdminManagementFragment.kt` (1483) son difíciles de navegar, revisar en PRs y testear. Recomendado extraer sub-componentes (p. ej. el formulario de "cambio de medidor" dentro de `AveriaDetalleBottomSheet` como su propio `BottomSheetDialogFragment` reutilizable).
- **⚪ D2 — Falta de un layer de "casos de uso" o `UseCase` intermedio:** los ViewModels llaman directamente a métodos de bajo nivel del repositorio (que a su vez mezclan Room + Firebase). Para lógica de negocio no trivial (p. ej. "registrar reparación de luminaria y descontar materiales del inventario", que hoy vive dentro de `RoomRepository.registrarReparacionLuminaria`) un `UseCase` explícito mejoraría la testabilidad y la legibilidad de la intención de negocio.
- **⚪ D3 — README del proyecto es mínimo** (2KB) y no documenta la topología multi-Firebase ni el estado real del módulo PM; ahora que existe este `AUDITORIA.md` y el `CLAUDE.md` ampliado, vale la pena enlazarlos desde el `README.md` para que cualquier persona que abra el repo por primera vez los encuentre.
- **⚪ D4 — Carpeta `app/sampledata` presente** — confirmar si tiene contenido real usado por Design/Preview de Android Studio o si es un remanente vacío del template inicial de Android Studio (candidato a limpieza si está vacía).

---

## 5. Plan de acción priorizado (roadmap sugerido) — actualizado 2026-07-09

**P0 — Lo más urgente ahora mismo:**
1. **[ÚNICO PENDIENTE P0] Probar el fix de sincronización incremental de Averías (A9)** en un dispositivo/emulador real: instalar APK debug, hacer login, verificar en Logcat (`[SCOPED_DELTA]`) que la segunda sync en adelante descarga solo el delta. Sin esta prueba, el fix no se puede considerar cerrado. Ver `log.md §Sesión 2026-07-09` para pasos exactos.
2. ✅ A7 completado (commit `edafc6b0`, 2026-07-09).
3. ✅ C4 completado (commit `953563b5`, 2026-07-09).
4. **Cuando el usuario reconstruya las 12 bases de Firebase:** diseñar reglas de seguridad por instancia desde el primer día — ver A4.

**P1 — Retomar cuando se resuelva el rediseño de Firebase:**
5. Completar el módulo Programación/Planillas siguiendo el plan de 5 pasos de `log.md` §Fase 3 (A2/A3), una vez decidida la tecnología única de datastore con la web.
6. Dividir `RoomRepository` y `FirebaseSyncManager` por dominio (B1, B2) — refactor grande, pospuesto deliberadamente.
7. Centralizar la política de merge de vehículos/catálogos en una sola función testeada (C2).
8. Migrar `syncState` de string libre a enum + `TypeConverter` (C3) — alcance real: 104 usos en 30 archivos, requiere sesión dedicada con build/test iterativo (ya evaluado y diferido conscientemente).

**P2 — Calidad y experiencia de desarrollo a mediano plazo:**
9. Espaciar el intervalo de `AveriasSyncWorker` apoyándose en las notificaciones push existentes, y añadir debounce/persistencia a los listeners realtime de inventario/luminarias (complementos recomendados del fix A9, no incluidos en él).
10. Empezar una suite de tests unitarios sobre lógica pura (normalizadores, mergers, `RetryPolicy`) (B9).
11. Romper los Fragments/ViewModels más grandes en componentes menores (D1, D2 de la sección de diseño).
12. Mantener `README.md`/`CLAUDE.md`/`AUDITORIA.md` enlazados y actualizados a medida que avance el rediseño de Firebase (D3).
