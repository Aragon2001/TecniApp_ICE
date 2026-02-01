# Módulo "Mi Vehículo" / Control de Flotilla – Diseño Funcional

## 1. Objetivo general

Diseñar el módulo **Mi Vehículo** para TecniApp ICE que permita:

- Controlar el uso diario del vehículo o maquinaria asignada
- Registrar kilometraje u orímetro según el tipo de vehículo
- Gestionar mantenimientos preventivos y correctivos
- Generar informes ETM (Equipo / Transporte / Maquinaria)
- Enviar alertas por mantenimiento próximo
- Servir como base para el control de flotilla institucional

---

## 2. Tipos de vehículo (regla clave)

### Mapeo desde la BD actual

La BD `tecniapp-ice-datosgenerales.firebaseio.com/vehiculos` usa el campo `tipo` con valores como:

- "Grua Pequeña"
- "Doble Canasta"
- (otros)

El sistema clasificará cada vehículo en uno de tres tipos:

| Tipo sistema | Ejemplos de `tipo` en BD | Control por |
|--------------|--------------------------|-------------|
| **Liviano** | (vehículos ligeros, pickups) | Kilometraje (km) |
| **Camión / Grúa** | "Grua Pequeña", "Doble Canasta", grúas | Kilometraje (km) |
| **Maquinaria pesada** | retroexcavadoras, equipos pesados | Orímetro (horas) |

**Mapeo sugerido:** tabla en código que asocia patrones de `tipo` (ej. `contains("grua")`, `contains("canasta")`) a `LIVIANO`, `CAMION_GRUA`, `MAQUINARIA_PESADA`. Si no hay coincidencia, se usa `LIVIANO` por defecto.

---

## 3. Registro diario obligatorio

### Inicio del día

Cuando el usuario:

- Inicia sesión por primera vez en un día nuevo, **o**
- Abre el módulo "Mi Vehículo" en un día nuevo

La app **obliga** a registrar:

- **Kilometraje inicial** (si es vehículo liviano o camión)
- **Orímetro inicial** (si es maquinaria pesada)

**Bloqueo:** El usuario NO puede usar la app con normalidad hasta completar este registro (pantalla modal o bloqueante).

### Fin del día (automático)

- El kilometraje/orímetro **final** del día N = kilometraje/orímetro **inicial** del día N+1
- Al registrar el inicial del día siguiente, el sistema cierra el registro del día anterior
- Los datos se usan para el informe ETM

### Modelo de datos diario

```
RegistroDiarioVehiculo:
  - id
  - placaVehiculo / vehiculoId
  - fecha (YYYY-MM-DD)
  - valorInicial (km u horas)
  - valorFinal (km u horas) — opcional hasta cierre
  - tecnicoUid
  - observaciones (opcional)
  - cerrado (boolean)
```

---

## 4. Informe ETM (Equipo / Transporte / Maquinaria)

### Justificación

- Control institucional del uso del vehículo
- Auditoría y trazabilidad
- Cumplimiento normativo
- Validación de horas/kilómetros trabajados
- Soporte para mantenimiento y costos operativos

### Contenido del ETM

| Campo | Descripción |
|-------|-------------|
| Vehículo asignado | Placa / identificador |
| Tipo de vehículo | Liviano / Camión / Maquinaria |
| Fecha | Día del registro |
| Valor inicial | Km u horas |
| Valor final | Km u horas |
| Diferencia diaria | valorFinal - valorInicial |
| Técnico responsable | Nombre / UID |
| Observaciones | Opcional |
| Firma / confirmación | Timestamp de confirmación |

### Generación

- El ETM se genera **automáticamente** a partir de los registros diarios cerrados
- Exportable a PDF/Excel para respaldo institucional

---

## 5. Gestión de mantenimiento

### Tipos de mantenimiento

- Cambio de aceite
- Cambio de filtros
- Revisión general
- Reparaciones
- Mantenimiento correctivo

### Datos por mantenimiento

| Campo | Tipo |
|-------|------|
| Tipo | Enum / texto |
| Fecha | Fecha |
| Valor al momento | Km u horas (orímetro) |
| Observaciones | Texto |
| Próximo mantenimiento estimado | Km u horas o fecha |

---

## 6. Predicción y alertas

### Reglas de predicción

- Cada X km (ej. 5 000 km para cambio de aceite)
- Cada X horas de orímetro
- Cada X días (ej. 6 meses)

### Tipos de alerta

| Tipo | Descripción |
|------|-------------|
| Aviso | Faltan X km/horas para próximo mantenimiento |
| Crítica | Se excedió el límite |
| Recordatorio visual | Banner en el módulo "Mi Vehículo" |

---

## 7. Enfoque técnico vs supervisor

| Rol | Capacidades |
|-----|-------------|
| **Técnico** | Ve su vehículo asignado, registra uso diario, recibe alertas, consulta historial |
| **Supervisor / Admin** | Ve toda la flotilla, reportes ETM, filtros por vehículo/tipo/agencia/técnico/fechas, análisis de uso |

---

## 8. Reglas del sistema (NO negociables)

1. No se puede omitir el registro diario
2. No se pueden editar valores pasados sin permisos (solo admin/supervisor)
3. El tipo de vehículo define toda la lógica (km vs orímetro)
4. El ETM se genera automáticamente
5. Offline-first: registro local, sincronización posterior

---

## 9. Estructura de base de datos (propuesta)

### Firebase Realtime Database

**Registros diarios (ETM):**

```
/etm_registros/{placa}_{fecha}:
  placa, vehiculoId, fecha, valorInicial, valorFinal, tecnicoUid, tecnicoNombre, observaciones, cerrado, createdAt
```

**Mantenimientos:**

```
/vehiculos_mantenimientos/{placa}_{timestamp}:
  placa, tipo, fecha, valorAlMomento, observaciones, proximoKm, proximoHoras, proximoFecha
```

### Room (local, offline-first)

- `EtmRegistroEntity` — registros diarios
- `VehiculoMantenimientoEntity` — mantenimientos
- Ampliar `VehiculosEntity` con `tipoControl` (km / orímetro)

---

## 10. Flujo técnico (resumen)

1. Usuario inicia sesión o abre "Mi Vehículo"
2. Sistema comprueba si existe registro del día para su vehículo
3. Si no existe → pantalla bloqueante para registrar valor inicial
4. Usuario registra valor inicial
5. Durante el día: el usuario puede actualizar valor final (opcional hasta el cierre)
6. Al día siguiente: el inicial del nuevo día cierra el registro del día anterior
7. Alertas de mantenimiento según reglas configuradas

---

## 11. Flujo supervisor (resumen)

1. Acceso a vista de flotilla
2. Filtros: vehículo, tipo, agencia, técnico, rango de fechas
3. Listado de registros ETM y mantenimientos
4. Exportar reportes (PDF/Excel)
5. Detección de uso irregular o excesivo

---

## 12. Integración con BD actual

- **vehiculos** (datosgenerales): `placa`, `agencia`, `tipo`
- Mapear `tipo` a `LIVIANO` / `CAMION_GRUA` / `MAQUINARIA_PESADA`
- Usuario: `placaVehiculo` en perfil para saber el vehículo asignado

---

*Documento de diseño – TecniApp ICE © 2025*
