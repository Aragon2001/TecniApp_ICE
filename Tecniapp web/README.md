# TecniApp ICE — Dashboard Web

Panel de administración web para el **Instituto Costarricense de Electricidad (ICE)**.  
Acceso completo en tiempo real a todos los módulos del sistema de gestión de operaciones de campo.

---

## Características

- **Averías** — Gestión completa: consulta, asignación, seguimiento y resolución
- **Medidores** — Búsqueda por número, localización y cliente
- **Localizaciones** — Consulta de direcciones con integración Google Maps
- **Luminarias** — Control de reparaciones y estado
- **Vehículos** — Flota completa, mantenimiento y bitácora diaria
- **Inventario** — Control de materiales por vehículo
- **Programación** — Planificación y asignación de tareas
- **Planillas** — Gestión de hojas de trabajo y órdenes SAP (módulo PM)
- **Reportes** — Generación y descarga en Excel y PDF
- **Usuarios** — Administración de cuentas y roles
- **Dashboard** — KPIs en tiempo real con gráficos interactivos

---

## Tecnologías

| Capa | Tecnología |
|------|-----------|
| Frontend | React 18 + TypeScript |
| Build | Vite 5 |
| Estilos | Tailwind CSS 3 |
| Backend | Firebase (Auth + Realtime DB × 9 + Firestore) |
| Íconos | Lucide React |
| Gráficos | Recharts |
| Exportación | xlsx + jsPDF |
| Notificaciones | React Hot Toast |
| Fechas | date-fns |

---

## Configuración rápida

### 1. Instalar dependencias

```bash
cd "Tecniapp web"
npm install
```

### 2. Variables de entorno

Copia el archivo de ejemplo y completa con las credenciales de Firebase:

```bash
cp .env.example .env
```

Edita `.env` con los valores del proyecto Firebase `tecniapp-ice`:

```env
VITE_FIREBASE_API_KEY=AIza...
VITE_FIREBASE_AUTH_DOMAIN=tecniapp-ice.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=tecniapp-ice
VITE_FIREBASE_STORAGE_BUCKET=tecniapp-ice.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=...
VITE_FIREBASE_APP_ID=...

# Bases de datos Realtime (una por módulo)
VITE_DB_AVERIAS=https://tecniapp-ice-averias-default-rtdb.firebaseio.com
VITE_DB_USERS=https://tecniapp-ice-user-default-rtdb.firebaseio.com
VITE_DB_GENERAL=https://tecniapp-ice-datosgenerales-default-rtdb.firebaseio.com
VITE_DB_MAIN=https://tecniapp-ice-default-rtdb.firebaseio.com
VITE_DB_PERSONAL=https://tecniapp-ice-personal-default-rtdb.firebaseio.com
VITE_DB_MATERIALES=https://tecniapp-ice-materiales-default-rtdb.firebaseio.com
VITE_DB_INVENTARIO=https://tecniapp-ice-inventario-default-rtdb.firebaseio.com
VITE_DB_LUMINARIAS=https://tecniapp-ice-luminarias-default-rtdb.firebaseio.com
VITE_DB_PROGRAMACION=https://tecniapp-ice-programacion-default-rtdb.firebaseio.com

# Google Maps (opcional para vistas de mapa)
VITE_GOOGLE_MAPS_KEY=AIza...
```

### 3. Ejecutar en desarrollo

```bash
npm run dev
```

Abre `http://localhost:3000` en el navegador.

### 4. Build para producción

```bash
npm run build
```

Los archivos estáticos quedan en `dist/`. Se puede desplegar en:
- Firebase Hosting
- Azure Static Web Apps (Microsoft 365 / ICE)
- Nginx / Apache

---

## Roles y permisos

| Rol | Acceso |
|-----|--------|
| `tecnico` | Dashboard, Averías (lectura), Medidores, Localizaciones, Luminarias, Vehículos (propio), Inventario, Planillas |
| `supervisor` | Todo lo anterior + asignar averías, gestionar programación |
| `admin` | Acceso total + gestión de usuarios + panel de administración |

---

## Estructura del proyecto

```
src/
├── firebase/        # Configuración Firebase (9 instancias RTDB + Firestore)
├── context/         # AuthContext (Firebase Auth + perfil de usuario)
├── types/           # Interfaces TypeScript para todas las entidades
├── hooks/           # Hooks de datos en tiempo real (Firebase listeners)
│   ├── useAverias.ts
│   ├── useVehiculos.ts
│   ├── useLuminarias.ts
│   ├── useInventario.ts
│   ├── useMedidores.ts
│   ├── useProgramacion.ts
│   ├── useUsuarios.ts
│   ├── useStats.ts
│   ├── useLocalizaciones.ts
│   └── useMateriales.ts
├── components/
│   ├── layout/      # Sidebar, Header, Layout
│   └── ui/          # Badge, Button, Card, Modal, Table, Input, StatCard...
└── pages/           # Una página por módulo
    ├── Login.tsx
    ├── Dashboard.tsx
    ├── Averias.tsx
    ├── AveriaDetail.tsx
    ├── Medidores.tsx
    ├── Localizaciones.tsx
    ├── Luminarias.tsx
    ├── Inventario.tsx
    ├── Vehiculos.tsx
    ├── VehiculoDetail.tsx
    ├── Programacion.tsx
    ├── Planillas.tsx
    ├── Reportes.tsx
    ├── Usuarios.tsx
    └── Admin.tsx
```

---

## Reglas de Firebase

Para que el dashboard funcione correctamente desde web, asegúrate de que las reglas de cada base de datos Realtime Database permitan lectura/escritura autenticada:

```json
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null"
  }
}
```

Para mayor seguridad, considera reglas por rol usando Custom Claims en Firebase Auth.

---

## Despliegue en Azure Static Web Apps (Microsoft / ICE)

1. Crea un recurso **Static Web App** en Azure Portal
2. Conecta el repositorio GitHub
3. Configura:
   - **App location**: `Tecniapp web`
   - **Output location**: `dist`
   - **Build command**: `npm run build`
4. Agrega las variables de entorno en la configuración de la app

---

*TecniApp ICE — v1.0.0 — Instituto Costarricense de Electricidad*
