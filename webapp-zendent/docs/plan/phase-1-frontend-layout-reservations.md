# Fase 1 — Frontend: Layout + Reservaciones (mock) · **PRIMER ENTREGABLE**

> Código en inglés. Sin backend. Todos los datos vienen de mocks (`@faker-js/faker`). Objetivo visual: replicar el mockup de Reservations, mejorando detalles de UX.

## Objetivo

Tener la app navegable con su **layout general** (sidebar + navbar) y la pantalla de **Reservations** mostrando el **calendario resource** (odontólogos × horas) con datos mock. **Sin** crear/editar citas.

## Alcance (in/out)

- **Incluye:** providers (HeroUI), layout (sidebar agrupado + navbar), ruta `/reservations`, calendario con toolbar y grid, mock data, filtros funcionales en UI (Today, Day/Week, All Dentist).
- **No incluye:** backend, persistencia, modal de nueva cita funcional (solo placeholder visual "+"), otras pantallas (placeholders permitidos en el sidebar).

---

## Paquetes (PKG)

### PKG-1.1 — Base & Providers _(bloquea al resto)_

**Objetivo:** dejar la app lista para usar HeroUI/HeroUI Pro y con identidad Zendenta.

- Añadir `HeroUIProvider` (y `ToastProvider` si aplica) en `src/routes/__root.tsx`, envolviendo `{children}`. Verificar que `@heroui/styles` y `@heroui-pro/react/css` (ya importados en `src/styles.css`) renderizan correctamente.
- Definir tokens de tema (colores de marca, radios, sombras) compatibles con HeroUI v3; cambiar `title` a "Zendenta" y `lang`.
- **Archivos:** `src/routes/__root.tsx`, `src/styles.css`, opcional `src/lib/theme.ts`.
- **SSR:** si `HeroUIProvider` requiere acceso a `window`, montarlo de forma segura para SSR.
- **DoD:** la app levanta (`bun run dev`), un componente HeroUI de prueba renderiza con tema; `bun run build` verde.

### PKG-1.2 — App Layout (Sidebar + Navbar) _(depende de 1.1)_

**Objetivo:** layout general reutilizable por todas las rutas de negocio.

- Ruta **pathless** `_app/route.tsx` que renderiza `AppLayout` con `<Outlet/>`. `_app/index.tsx` redirige a `/reservations`.
- `Sidebar`:
  - Logo "Zendenta" + `ClinicSwitcher` (header "Avicena Clinic / 845 Euclid Avenue, CA").
  - Grupos con encabezado: **CLINIC** (Dashboard, Reservations, Patients, Treatments, Staff List), **FINANCE** (Accounts, Sales, Purchases, Payment Method), **PHYSICAL ASSET** (Stocks, Peripherals), y abajo Report, Customer Support.
  - Ítems con ícono `lucide-react`, estado activo, colapsable.
- `Navbar`: buscador global ("Search for anything here…"), botón **"+"** (acción placeholder), íconos ayuda / actividad / notificaciones (badge "1/4"), avatar + nombre + rol ("Darrell Steward / Super admin").
- Responsive: sidebar colapsa/oculta en móvil (drawer).
- **Archivos:** `src/routes/_app/route.tsx`, `src/routes/_app/index.tsx`, `src/components/layout/{AppLayout,Sidebar,Navbar,ClinicSwitcher}.tsx`, `src/components/layout/nav-items.ts` (definición de ítems).
- **DoD:** navegar a `/` redirige a `/reservations`; el ítem activo se resalta; layout estable en desktop y móvil.

### PKG-1.3 — Datos mock & tipos _(depende de 1.1, paralelo a 1.2)_

**Objetivo:** tipos + mocks que alimentan el calendario.

- **Schemas Zod + tipos** (fuente de verdad de la forma de datos; se reutilizarán con el backend):
  - `Dentist`: `{ id, fullName, title (e.g. "Drg"), avatarUrl, todayAppointmentCount, available }`.
  - `AppointmentStatus`: `'registered' | 'encounter' | 'finished' | 'waiting_payment'`.
  - `Appointment`: `{ id, dentistId, patientName, treatmentLabel, start (ISO), end (ISO), status }`.
  - `TimeBlock` (break / not available): `{ dentistId | null, kind: 'break' | 'unavailable', start, end, label }`.
- **Generadores faker:** ~3–5 dentistas; para "hoy", 12–16 citas distribuidas en horas (9am–5pm) con estados/colores variados; un break time (1pm) y al menos una columna "Not available".
- **Archivos:** `src/features/reservations/types.ts`, `src/features/reservations/schemas.ts`, `src/mocks/dentists.ts`, `src/mocks/reservations.ts`.
- **DoD:** `getReservationsForDate(date)` y `getDentists()` devuelven datos válidos contra los schemas Zod.

### PKG-1.4 — Calendar de Reservaciones _(depende de 1.2 y 1.3)_

**Objetivo:** la pantalla Reservations completa en visualización.

- Ruta `_app/reservations/index.tsx`: tabs **Calendar / Log History** (Log History = placeholder), consume mock vía TanStack Query (`useReservationsQuery`).
- `CalendarToolbar`: contador "N total appointments", **Today** + `‹ ›`, fecha ("Fri, 16 May 2022"), toggle **Day / Week**, filtro **All Dentist** (dropdown), botón **Filters**.
- `CalendarGrid` (resource, **custom CSS grid**):
  - Columna izquierda fija = **TimeAxis** (etiqueta GMT + horas 9am…).
  - Una columna por dentista con header (`DentistColumn` header: avatar, nombre, "Today's appointment: N").
  - `NowIndicator`: línea roja horizontal con etiqueta de hora actual, posicionada por la hora real.
  - `AppointmentCard`: color por estado, nombre del paciente, rango horario, etiqueta de tratamiento (chip), badge de estado (• Finished / • Encounter / • Registered). Posición/altura calculadas por `start`/`end`.
  - Bloques **BREAK TIME** y **NOT AVAILABLE** (patrón rayado), placeholder **"+"** al hover de celda vacía (sin acción).
- **Estado de filtros en UI:** Day/Week y dentista filtran lo mostrado (sin backend).
- **SSR:** marcar el grid/now-indicator como client-only (usan fecha/hora del cliente).
- **Archivos:** `src/routes/_app/reservations/index.tsx`, `src/components/reservations/{CalendarToolbar,CalendarGrid,TimeAxis,DentistColumn,AppointmentCard,NowIndicator,EmptyCell}.tsx`, `src/features/reservations/queries.ts`.
- **DoD:** el calendario se ve como el mockup (mejorado), filtros operan en cliente, sin errores de hidratación.

#### Decisión HeroUI Pro vs custom (resolver al inicio de PKG-1.4)

Evaluar el componente **Calendar de HeroUI Pro** (skill `heroui-react-pro` / MCP). Si **no** soporta vista _resource-timeline_ (lo más probable, suelen ser date-pickers/month views), construir `CalendarGrid` **custom** (ruta por defecto de este plan). **No instalar FullCalendar.**

### PKG-1.5 — Pulido & verificación _(depende de 1.2–1.4)_

- Revisión con skills `heroui-pro-design-taste` y `vercel-react-best-practices`; responsive; accesibilidad básica (roles, foco, contraste).
- **DoD:** `bun run lint`, `bun run check` y `bun run build` verdes; recorrido visual validado.

---

## Dependencias entre paquetes

```
1.1 ──► 1.2 ──┐
   └──► 1.3 ──┴► 1.4 ──► 1.5
```

1.2 y 1.3 pueden ir en paralelo tras 1.1.

## Verificación de la Fase 1

1. `bun install` (FullCalendar **no** se añade).
2. `bun run dev` → `http://localhost:3000` redirige a `/reservations`.
3. Validar contra el mockup: sidebar agrupado + navbar; calendario con columnas por dentista, eje horario, citas con colores por estado, break time, "Not available", línea roja de hora actual, toggles Today / Day/Week / All Dentist.
4. `bun run lint` y `bun run build` pasan.
5. (Opcional) skills `run`/`verify` para recorrer la app y `heroui-pro-design-taste` para el pulido.
