# TecniApp ICE - Auditoría y Refactor Material 3

## 1) Resultado aplicado en esta refactorización

- Tema base unificado a `Theme.Material3.DayNight.NoActionBar` en día y noche.
- Tokenización de color semántica M3 (`primary`, `on_primary`, `surface_variant`, etc.).
- Creación de `values-night/colors.xml` para separar correctamente palette de modo oscuro.
- Estandarización de shapes globales:
  - `Small = 8dp`
  - `Medium = 16dp`
  - `Large = 28dp`
- Tipografía unificada con `TextAppearance.Material3.*` como base.
- Overlays corregidos:
  - Toolbar -> `ThemeOverlay.Material3.Toolbar`
  - BottomSheet -> `ThemeOverlay.Material3.BottomSheetDialog`
- Eliminadas referencias a Material 2 detectadas en layouts/estilos.

## 2) Archivos nuevos/actualizados

- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values-night/colors.xml` (nuevo)
- `app/src/main/res/values/styles_overlays.xml`
- `app/src/main/res/layout/fragment_home.xml`

## 3) Tabla de migración Material 2 -> Material 3

| Antes (M2) | Después (M3) |
|---|---|
| `Theme.Material3.Light.NoActionBar` (solo light) | `Theme.Material3.DayNight.NoActionBar` |
| `Widget.MaterialComponents.LinearProgressIndicator` | `Widget.Material3.LinearProgressIndicator` |
| `Widget.MaterialComponents.Button` | `Widget.Material3.Button` |
| `ThemeOverlay.Material3.ActionBar` para toolbar | `ThemeOverlay.Material3.Toolbar` |
| Alias de FAB extendido anclado a M2 | Uso directo de `Widget.Material3.ExtendedFloatingActionButton` |

## 4) Reemplazos sugeridos adicionales

1. Sustituir colores directos en layouts por atributos de tema (`?attr/colorSurface`, `?attr/colorOnSurface*`).
2. Migrar corners de tarjetas con radios arbitrarios a shapes semánticos (`Small/Medium/Large`).
3. Migrar `TextView` con `textStyle="bold"` y `textSize` hardcodeado a `TextAppearance.Tecni.*`.

## 5) Recursos eliminables (candidatos, heurístico)

> Fuente: escaneo automático por referencia textual `@.../id` y `R....id`. Validar con `lint` antes de borrar en productivo.

### Strings (muestra)
- `navigation_drawer_open`
- `navigation_drawer_close`
- `nav_header_title`
- `welcome_message`
- `mi_vehiculo_titulo`

### Styles (candidatos)
- `Tecni.Chip.Compact`
- `ThemeOverlay.Tecni.Search`
- `ThemeOverlay.Tecni.Search.Dark`
- `Tecni.Glass.EmptyState`
- `Tecni.Glass.Fab`

### Dimens (candidatos)
- `activity_horizontal_margin`
- `activity_vertical_margin`
- `fab_margin`
- `localizacion_card_radius`
- `glass_icon_main`

### Drawables (candidatos)
- `directions_car_48px`
- `ic_round_filter_list_24`
- `placeholder_mapa`
- `bg_report_spinner_popup`
- `done_icon`

## 6) Riesgo y compatibilidad

- Se conservaron aliases legacy de color para no romper referencias existentes.
- La app mantiene compatibilidad con `minSdk` actual (no se introducen APIs nuevas).
- El build local no pudo validarse por resolución de plugin AGP en entorno actual (ver salida Gradle).

## 7) Próximo sprint recomendado

1. Ejecutar `lint` y borrar candidatos de recursos no usados por lotes pequeños.
2. Introducir catálogo de componentes (`Tecni.Button.*`, `Tecni.Card.*`, `Tecni.Text.*`) en una guía interna.
3. Terminar migración de layouts con hardcoded colors/radius a tokens + shapes globales.
