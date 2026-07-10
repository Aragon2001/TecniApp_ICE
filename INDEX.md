# INDEX — TecniApp (Android)

**TecniApp** — desarrollado por **Arasoft Solutions** para el **Instituto Costarricense de Electricidad (ICE)**.

Punto de entrada rápido a la documentación de este proyecto. Este proyecto es la app **Android** de campo del ICE. Su contraparte web vive en la carpeta hermana `Tecniapp web/` (documentación propia: `Tecniapp web/INDEX.md`).

## Documentos de este repositorio

| Documento | Para qué sirve |
|---|---|
| [`CLAUDE.md`](./CLAUDE.md) | Referencia completa de arquitectura: stack, las 12 bases de Firebase, las 2 bases Room, mapa de todos los módulos/paquetes y qué hace cada archivo relevante. Léelo primero si vas a tocar código. |
| [`log.md`](./log.md) | Registro cronológico de las correcciones ejecutadas sobre la auditoría (qué se cambió, riesgos y pendientes). |
| [`AUDITORIA.md`](./AUDITORIA.md) | Auditoría técnica: bugs confirmados, deuda técnica, riesgos de seguridad y un plan de acción priorizado (P0/P1/P2). Léelo antes de decidir en qué trabajar. |
| [`README.md`](./README.md) | Introducción original del proyecto (breve). |

## Mapa mental rápido del proyecto

- **6 dominios de negocio:** Averías · Medidores · Luminarias · Inventario · Vehículos · Programación/Planillas (este último incompleto, ver `AUDITORIA.md` §A2).
- **Arquitectura:** MVVM + Repository, offline-first en lectura, single-module, sin DI framework.
- **Persistencia:** 2 bases Room (`AppDatabase` v31, `PmDatabase` v1) + **12 instancias separadas de Firebase Realtime Database** + 1 Firestore (solo consumida desde la web hoy). *(Las RTDB están en proceso de rediseño — ver `log.md` §A4.)*
- **Sync:** `AveriasSyncWorker` (activo, cada 15 min) es un respaldo — las averías nuevas llegan casi al instante vía push FCM con datos embebidos (ver `AUDITORIA.md` §A13). `Synchronizer` orquesta la sync manual/al iniciar sesión; desde A12, técnicos/materiales/medidores/vehículos usan una compuerta `_meta`/`updatedAt` para no re-descargar el catálogo completo si no cambió (código listo, servidor pendiente de desplegar). La cola de sync del módulo PM (`PmSyncManager`/`PmSyncWorker`) existe pero nunca se dispara.

## Por dónde empezar según tu objetivo

- **Voy a arreglar un bug de averías** → `CLAUDE.md` §5.5, luego `ui/averias/AveriasViewModel.kt` / `AveriasRepository.kt`.
- **Voy a revisar seguridad de Firebase** → `AUDITORIA.md` §1 (Hallazgo A4) y `database.rules.json`.
- **Voy a decidir el futuro del módulo Planillas/PM** → `AUDITORIA.md` §1 (Hallazgos A2 y A3) — leer completo antes de escribir código nuevo ahí.
- **Voy a entender cómo se relaciona esto con la web** → `CLAUDE.md` §8 y `AUDITORIA.md` Hallazgos A3 y A7.
- **Voy a tocar consumo de datos/Firebase o el plan de facturación** → `AUDITORIA.md` §1.4/§1.5 (Hallazgos A12 y A13) y `log.md` §"Ejecución del plan A12" — el código ya está listo, falta desplegar el server-side (Cloud Functions + `_meta`) junto con el rebuild de las 12 RTDB.
- **Soy nuevo en el proyecto y quiero el panorama completo** → `CLAUDE.md` de arriba a abajo (~15 min de lectura), luego el resumen ejecutivo de `AUDITORIA.md` §0.
