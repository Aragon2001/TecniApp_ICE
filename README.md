# TecniApp

**TecniApp** es la aplicación Android de campo desarrollada por **Arasoft Solutions** para el **Instituto Costarricense de Electricidad (ICE)**. Permite gestionar, desde el teléfono, los seis dominios de operación de campo de las cuadrillas técnicas: averías, medidores, luminarias, inventario, vehículos y programación de trabajo (planillas).

| | |
|---|---|
| **Desarrollado por** | Arasoft Solutions |
| **Cliente** | Instituto Costarricense de Electricidad (ICE) |
| **Aplicación** | TecniApp (identificador de paquete `com.Arasoftsolutions.tecniapp_ice`) |
| **Plataforma** | Android (Kotlin) |

> 📘 **Documentación técnica completa:** este README cubre solo lo esencial para compilar y ejecutar el proyecto. Para entender el 100% de la arquitectura, qué hace cada módulo y archivo, consultar [`CLAUDE.md`](./CLAUDE.md). Para el detalle de deficiencias detectadas y el plan de mejoras priorizado, consultar [`AUDITORIA.md`](./AUDITORIA.md). Para navegación rápida entre ambos, ver [`INDEX.md`](./INDEX.md).

---

## Descripción general

TecniApp ICE está desarrollada en **Kotlin** sobre Android Views (sin Jetpack Compose), con arquitectura **MVVM + Repository** y persistencia local con **Room**. Sincroniza datos contra **doce instancias independientes de Firebase Realtime Database** (una por dominio de negocio) más Firebase Auth, Firestore y Cloud Functions. Existe un panel web complementario en el proyecto hermano `Tecniapp web/`, que consume el mismo backend de Firebase.

### Módulos disponibles

| Módulo | Descripción |
|---|---|
| **Averías** | Reporte, atención y cierre de casos de daños eléctricos, con evidencias fotográficas, generación de PDF y notificaciones push. |
| **Medidores** | Consulta y alta de medidores por subregión, pueblo y localización. |
| **Localización** | Selección de localización en mapa, con soporte de sensores de orientación. |
| **Luminarias** | Registro y reparación de alumbrado público, con control de materiales consumidos. |
| **Inventario** | Control de existencias de materiales por vehículo. |
| **Vehículos** | Bitácora de flota: kilometraje, mantenimientos y registros diarios (ETM). |
| **Programación** | Asignación y seguimiento de tareas de campo. |
| **Planillas (módulo PM)** | Generación de hojas de trabajo y planillas en PDF. **Actualmente incompleto** — ver `AUDITORIA.md` §Hallazgo A2. |
| **Reportes** | Generación de reportes exportables en Excel y PDF. |

---

## Requisitos de compilación

- **Android Studio** (Flamingo o superior)
- **Android SDK 34** (`compileSdk` / `targetSdk`), `minSdk` 26
- **JDK 17**
- **Gradle 8.7** (se provee mediante el Gradle Wrapper, no requiere instalación manual)

## Configuración del entorno

1. Clonar este repositorio.
2. Crear (o dejar que Android Studio genere automáticamente) un archivo `local.properties` en la raíz del proyecto con la ruta al Android SDK:

   ```properties
   sdk.dir=/ruta/a/tu/Android/Sdk
   ```

3. Colocar el archivo de configuración de Firebase `google-services.json` en `app/`. *(Nota: en este repositorio dicho archivo ya está versionado en git; si se rota el proyecto de Firebase, reemplazarlo aquí.)*
4. (Opcional) Configurar una clave de Google Maps propia como `MAPS_API_KEY` en `local.properties`, usada por los módulos de mapa (Averías, Localización).

## Compilación y ejecución

Abrir el proyecto con Android Studio y esperar la sincronización de Gradle. Desde la línea de comandos:

```bash
./gradlew assembleDebug          # Compila el APK de depuración
./gradlew assembleRelease        # Compila el APK de producción
./gradlew test                   # Ejecuta pruebas unitarias
./gradlew connectedAndroidTest    # Ejecuta pruebas instrumentadas (requiere dispositivo/emulador)
```

## Uso

Al abrir la aplicación se presenta una pantalla de acceso mediante Firebase Authentication. Una cuenta autenticada aquí es la misma que se usa en el panel web (`Tecniapp web/`). Tras autenticarse, la navegación principal (menú lateral + navegación inferior) da acceso a los módulos listados arriba, según el rol del usuario (técnico, supervisor o administrador).

La aplicación sincroniza datos con Firebase en segundo plano (`AveriasSyncWorker`, cada 15 minutos, más sincronización manual) y almacena información localmente en Room para permitir el uso sin conexión.

---

## Arquitectura de datos (resumen)

- **Dos bases de datos Room independientes:** `AppDatabase` (schema **v31**) para el módulo principal, y `PmDatabase` (schema v1) para el módulo de Planillas/PM.
- **Nueve instancias separadas de Firebase Realtime Database**, una por dominio de negocio (averías, usuarios, datos generales, personal, materiales, inventario, luminarias, planilla, y la base por defecto para medidores/localizaciones/pueblos).
- **Cloud Functions** (`functions/`, Node.js) implementan notificaciones push, envío de correo y sincronización periódica de averías.

Para el detalle completo de esta arquitectura —incluyendo qué archivo hace qué, y los puntos de fricción detectados con el proyecto web— ver [`CLAUDE.md`](./CLAUDE.md).

---

## Estado del proyecto y mejoras pendientes

Este proyecto cuenta con una auditoría técnica exhaustiva en [`AUDITORIA.md`](./AUDITORIA.md), que documenta (entre otros) hallazgos críticos como reglas de seguridad de Firebase incompletas, un módulo de Planillas desconectado de su propia interfaz, y deuda técnica en las clases de sincronización. Se recomienda revisar ese documento antes de iniciar trabajo nuevo sobre el proyecto.

---

*TecniApp — Desarrollado por Arasoft Solutions para el Instituto Costarricense de Electricidad (ICE)*
