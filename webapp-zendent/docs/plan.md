# Plan: Zendenta — Sistema de Gestión para Clínicas y Consultorios Odontológicos

> Documento en español. **Todo el código, nombres de archivos, identificadores, ramas, commits y comentarios van en inglés.**

---

## Contexto

Queremos construir **Zendenta**, un SaaS de gestión para clínicas dentales y consultorios individuales. El producto cubre: reservaciones (calendario por odontólogo), pacientes con historial médico y **odontograma**, tratamientos (con visitas, componentes/materiales y plan de tratamiento), gestión de staff, finanzas (cuentas, ventas, compras, métodos de pago), activos físicos (stocks, periféricos), reportes y soporte al cliente.

El repositorio actual (`webapp-zendent`) es un **scaffold limpio de TanStack Start** (no Vite SPA puro): file-based routing, SSR vía Nitro, Bun como gestor de paquetes, HeroUI v3 + HeroUI Pro (beta) ya cableados en `src/styles.css`, y TanStack Query/Router/Form/Table + Zod instalados. **Falta** envolver la app con `HeroUIProvider` y no hay layout ni rutas de negocio todavía. **FullCalendar no está instalado.**

**Decisiones confirmadas con el usuario:**
1. **Tenancy:** SaaS multi-clínica, *shared schema* con discriminador `clinic_id`. Tanto la clínica como el consultorio individual son una "organización" (clinic) con 1+ odontólogos.
2. **Calendario de reservaciones:** **grid custom** (CSS grid + HeroUI). Se evaluará primero el componente Calendar de HeroUI Pro; si no encaja la vista *resource-timeline* (dentistas en columnas × horas en filas), se construye a medida. **No** se usará FullCalendar Scheduler (plugin de pago).
3. **Backend:** **Sí adoptamos Spring Modulith** (monolito modular).
4. **Primer entregable (v1):** Solo visualización con datos *mock* — sidebar + navbar + pantalla de Reservaciones mostrando el calendario. Sin crear/editar citas, sin backend.

**Resultado esperado:** Un plan ejecutable por fases, dividido en *paquetes de trabajo* aptos para multiagentes, empezando por el layout + calendario de Reservaciones en frontend con mock data.

> **PASO 0 (a ejecutar primero, sin código):** Materializar este plan dentro del repositorio en una carpeta `docs/`, con un documento maestro y **un documento detallado por cada fase**. Solo después de eso (y con aprobación) se empieza a implementar. Ver sección **"Entregable inmediato: documentación en el repo"**.

---

## Entregable inmediato: documentación en el repo (sin implementar código)

Antes de escribir cualquier código, se crea la siguiente estructura de documentación dentro del proyecto. Cada documento de fase contiene: objetivo, alcance, paquetes (PKG) con archivos a crear, dependencias entre paquetes, criterios de aceptación (DoD) y cómo verificar.

```
docs/
  plan/
    README.md                                   # documento maestro (overview, stack, decisiones, roadmap, índice)
    architecture.md                             # backend (Modulith + multi-tenancy + eventos) y frontend (carpetas, convenciones)
    screens.md                                  # inventario completo de pantallas (mejorado)
    phase-1-frontend-layout-reservations.md     # PRIMER ENTREGABLE de código (detallado)
    phase-2-backend-foundations.md
    phase-3-core-modules.md
    phase-4-finance-assets.md
    phase-5-cross-cutting.md
    multiagent-workflow.md                       # contrato entre agentes y cómo asignar paquetes
```

Contenido de cada archivo = las secciones correspondientes de este plan, ampliadas a nivel de detalle ejecutable (especialmente `phase-1-*`, que incluye props de componentes, forma de los datos mock y layout del `CalendarGrid`). **No se toca código de `src/` en este paso.**

---

## Stack técnico (confirmado)

**Frontend** (ya scaffoldeado): React 19, TanStack Start (Vite 8 + Nitro), TypeScript 6, TailwindCSS v4, HeroUI v3 + **HeroUI Pro**, TanStack Router (file-based) / Query / Form / Table, Zod 4, `lucide-react`, `recharts` (reportes), `@faker-js/faker` (mock), `tiptap` (notas/descripciones de texto rico), `motion`. Gestor: **Bun**.

**Backend** (a crear en repo/carpeta aparte, p. ej. `../api-zendent` o monorepo): Java 25, **Spring Boot 4**, Spring Web, Spring Security, Spring Data JPA, **Spring Modulith**, PostgreSQL, Flyway, MapStruct, Bean Validation, Springdoc OpenAPI, Testcontainers, Docker.

---

## Evaluación: ¿Spring Modulith? → **Recomendado SÍ**

El dominio tiene límites (bounded contexts) muy nítidos —pacientes, tratamientos, reservaciones, facturación, inventario, staff— que se comunican por eventos naturales (p. ej. "cita finalizada → generar cargo", "tratamiento creado → reservar componentes de stock"). Spring Modulith aporta exactamente eso sin la complejidad de microservicios:

- **Límites verificados**: `ApplicationModules.verify()` falla el build si un módulo accede a internos de otro.
- **Comunicación desacoplada** vía `ApplicationEventPublisher` + `@ApplicationModuleListener` (eventos transaccionales, con outbox/event-publication-registry para fiabilidad).
- **Documentación viva** (PlantUML/C4) y tests por módulo (`@ApplicationModuleTest`).
- **Migración futura** a microservicios de bajo costo si algún módulo lo amerita.

**Coste:** curva de aprendizaje menor y disciplina en el diseño de paquetes. Para un monolito SaaS que crecerá en superficie funcional, el beneficio supera al coste. **Adoptar.**

---

## Arquitectura backend (resumen)

**Multi-tenancy:** shared schema. Toda entidad de negocio lleva `clinic_id`. Aislamiento con un filtro Hibernate (`@FilterDef`/`@Filter tenant`) activado por interceptor de Spring Security que lee el `clinic_id` del JWT. Migraciones con Flyway (`V1__init.sql`, …).

**Módulos Spring Modulith** (cada uno = paquete top-level bajo `com.zendenta`):

| Módulo | Responsabilidad | Entidades núcleo |
|---|---|---|
| `iam` | Auth, usuarios, roles, clínicas (tenant), onboarding | `Clinic`, `User`, `Role`, `Membership` |
| `staff` | Odontólogos y personal, horarios, especialidades | `Dentist`, `StaffMember`, `WorkingHours` |
| `patients` | Pacientes, ficha, hábitos de higiene, **odontograma**, historial médico | `Patient`, `MedicalRecord`, `ToothCondition` |
| `treatments` | Catálogo de tratamientos, visitas, componentes/materiales | `Treatment`, `TreatmentVisit`, `TreatmentComponent` |
| `reservations` | Citas, calendario, waitlist, estados (Registered/Encounter/Finished) | `Appointment`, `WaitlistEntry` |
| `billing` | Ventas, facturas, cobros, métodos de pago, flujo Pay Bill | `Invoice`, `Payment`, `PaymentMethod` |
| `inventory` | Stocks y periféricos, descuento por componentes de tratamiento | `StockItem`, `Peripheral`, `StockMovement` |
| `purchases` | Órdenes de compra a proveedores | `PurchaseOrder`, `Supplier` |
| `reporting` | Agregados/consultas de reporte (read models) | vistas/proyecciones |
| `support` | Tickets de customer support | `SupportTicket` |
| `shared` | Tipos comunes, eventos publicados, `Money`, tenancy | — |

**Eventos clave (ejemplos):** `AppointmentFinishedEvent` → `billing` crea `Invoice`; `TreatmentPlanned` → `inventory` reserva componentes; `InvoicePaidEvent` → `reservations` libera estado "Waiting Payment".

Cada módulo expone una **API REST** (controllers) + **service** (lógica) + **repository** (JPA) + **DTOs/MapStruct**. Solo se exponen al resto eventos y un paquete `…/spi` o `…/api` público; lo demás es interno al módulo.

---

## Inventario de pantallas (mejorado y completado)

Manteniendo el **mismo layout** del mockup (sidebar agrupado: CLINIC / FINANCE / PHYSICAL ASSET + navbar con buscador, "+", ayuda, notificaciones, perfil), mejorando interacción y añadiendo lo que falta:

**CLINIC**
- **Dashboard** *(faltaba; añadido)*: KPIs del día (citas, ingresos, pacientes nuevos), próximas citas, alertas de stock bajo, gráfico de ventas (recharts).
- **Reservations**: calendario *resource* (dentistas × horas), tabs Calendar / Log History, filtros Today, Day/Week, All Dentist, estados de cita con color (Finished/Encounter/Registered/Waiting Payment), break time, "Not available". Modal nueva cita. **Waitlist** (wizard 3 pasos: tratamiento+dentista → datos básicos → hábitos de higiene).
- **Patients**: lista + detalle con tabs *Patient Information / Appointment History / Next Treatment / Medical Record*. **Medical Record** con **Odontograma** interactivo (selección de dientes, condiciones, timeline de tratamientos por diente, toggle Medical/Cosmetic). Crear/editar paciente.
- **Treatments**: catálogo + flujo "Add a treatment": (1) Basic info (nombre, categoría, descripción), (2) Multiple visits (duración/agenda), (3) Components (materiales, cantidad, costo), (4) Treatment plan (selección de dientes/condiciones), (5) Summary, (6) Pay Bill.
- **Staff List**: odontólogos y personal, alta/edición, horarios, especialidades, agenda.

**FINANCE**
- **Accounts**: estado financiero de la clínica / por odontólogo.
- **Sales**: tabla de facturación por paciente con *color-coding* (pagado/parcial/impago), filtros, detalle. **Pay Bill flow** (Select Payment Method → desglose de cargos → método (efectivo con botones de denominación, tarjeta, etc.) → recibo, imprimir o volver al calendario).
- **Purchases**: órdenes de compra, proveedores, recepción de mercancía.
- **Payment Method**: configuración de métodos de pago aceptados.

**PHYSICAL ASSET**
- **Stocks**: inventario de insumos/materiales, niveles, alertas, vínculo con componentes de tratamiento.
- **Peripherals**: equipos/periféricos, mantenimiento, asignación a salas/odontólogos.

**OTROS**
- **Report**: reportes financieros, de tratamientos, de ocupación de agenda (recharts + export).
- **Customer Support**: tickets, FAQ, contacto.
- **Auth/Onboarding** *(faltaba; añadido)*: login, registro de clínica (tenant), invitación de staff, recuperación de contraseña.
- **Settings/Profile** *(faltaba; añadido)*: perfil de usuario, datos de la clínica, roles/permisos, preferencias.
- **Global search & notifications** *(navbar)*.

---

## Arquitectura frontend

**Estructura de carpetas** (bajo `src/`, alias `#/*` y `@/*` → `./src/*`):

```
src/
  routes/                      # file-based (TanStack Router)
    __root.tsx                 # añadir HeroUIProvider aquí
    _app/                      # layout group con sidebar+navbar (pathless)
      route.tsx                # AppLayout (sidebar + navbar + <Outlet/>)
      index.tsx                # redirect a /reservations
      reservations/index.tsx
      patients/...             # (fases siguientes)
      treatments/...
      ...
  components/
    layout/                    # Sidebar, Navbar, AppLayout
    ui/                        # wrappers/composición sobre HeroUI
    reservations/              # CalendarGrid, DentistColumn, AppointmentCard, CalendarToolbar...
  features/<feature>/          # hooks, queries, types, schemas (Zod) por feature
  lib/                         # tenancy, formatters, api client (fase backend)
  mocks/                       # datos mock con faker (fase 1)
```

**Convenciones:** componentes con HeroUI Pro (verificar bloques disponibles vía skill `heroui-react-pro` / MCP), estilos con Tailwind v4 + `tailwind-variants`; datos servidor con TanStack Query; formularios con TanStack Form + Zod; tablas con TanStack Table; seguir skill `vercel-react-best-practices` (ya en `.agents/skills/`) y `heroui-pro-design-taste`.

**Calendario:** componente `CalendarGrid` propio — CSS grid con eje de horas (filas) y odontólogos (columnas), `now-indicator` (línea roja), bloques de cita posicionados por hora inicio/fin, soporte break time y "Not available". Day/Week toggle. Primero revisar si el Calendar de HeroUI Pro cubre parte; si no, custom.

---

## Roadmap por fases (divisible en multiagentes)

> Cada **paquete (PKG)** está pensado para asignarse a un agente. Se indican dependencias para paralelizar. Definition of Done global: `bun run lint`, `bun run check` y `bun run build` pasan; (backend) `./gradlew build` con Testcontainers verde y `ApplicationModules.verify()` OK.

### FASE 1 — Frontend: Layout + Reservaciones (mock) — *PRIMER ENTREGABLE*

Sin backend. Todo con datos mock (`@faker-js/faker`). Objetivo visual: replicar el mockup de Reservations mejorando detalles.

- **PKG-1.1 — Base & Providers** *(bloquea al resto de la fase)*
  - Añadir `HeroUIProvider` (y `ToastProvider` si aplica) en `src/routes/__root.tsx`; verificar tema y que `@heroui/styles` + `@heroui-pro/react/css` cargan.
  - Crear tokens de tema (colores de marca Zendenta, radios, sombras) compatibles con HeroUI v3.
  - Configurar fuente, `lang`, título "Zendenta".
  - Archivos: `src/routes/__root.tsx`, `src/styles.css`, (opcional) `src/lib/theme.ts`.

- **PKG-1.2 — App Layout (Sidebar + Navbar)** *(depende de 1.1)*
  - `AppLayout` con sidebar colapsable agrupado (CLINIC / FINANCE / PHYSICAL ASSET) + selector de clínica (header "Avicena Clinic"), navbar (buscador global, botón "+", ayuda, actividad, notificaciones con badge "1/4", avatar/perfil).
  - Ruta pathless `_app/route.tsx` que renderiza layout + `<Outlet/>`; `_app/index.tsx` redirige a `/reservations`.
  - Estados activos de navegación, responsive (sidebar colapsa en móvil), íconos `lucide-react`.
  - Archivos: `src/routes/_app/route.tsx`, `src/routes/_app/index.tsx`, `src/components/layout/{Sidebar,Navbar,AppLayout,ClinicSwitcher}.tsx`.

- **PKG-1.3 — Datos mock & tipos** *(paralelo a 1.2, depende de 1.1)*
  - Tipos TS + esquemas Zod para `Dentist`, `Appointment`, `Patient`(mínimo), estados de cita y tratamientos.
  - Generadores con faker: ~3–5 dentistas, citas del día con horas/estados/colores, break time.
  - Archivos: `src/features/reservations/types.ts`, `src/features/reservations/schemas.ts`, `src/mocks/reservations.ts`, `src/mocks/dentists.ts`.

- **PKG-1.4 — Calendar de Reservaciones** *(depende de 1.2 y 1.3)*
  - `CalendarToolbar`: total de citas, Today + flechas, fecha, toggle Day/Week, filtro All Dentist, Filters.
  - `CalendarGrid` (resource): columnas por dentista (con header avatar + "Today's appointment: N"), filas por hora (GMT label), `now-indicator` (línea roja con hora), `AppointmentCard` (color por estado, paciente, rango horario, etiqueta de tratamiento, badge de estado), bloques "BREAK TIME" y "NOT AVAILABLE", placeholder "+ nueva cita" (solo UI, sin acción).
  - Tabs Calendar / Log History (Log History puede ser placeholder).
  - Ruta `_app/reservations/index.tsx` que consume mock vía TanStack Query (queryFn que devuelve mock).
  - Archivos: `src/routes/_app/reservations/index.tsx`, `src/components/reservations/{CalendarToolbar,CalendarGrid,DentistColumn,AppointmentCard,TimeAxis,NowIndicator}.tsx`, `src/features/reservations/queries.ts`.

- **PKG-1.5 — Pulido & verificación** *(depende de 1.2–1.4)*
  - Revisión con skills `heroui-pro-design-taste` y `vercel-react-best-practices`; responsive; accesibilidad básica; `bun run build` y lint verdes.
  - Captura/recorrido del calendario para validar contra el mockup.

> **Nota:** evaluar primero el componente **Calendar de HeroUI Pro** (skill `heroui-react-pro`/MCP) en PKG-1.4; si no soporta vista resource-timeline, construir `CalendarGrid` custom (ruta por defecto). **No instalar FullCalendar en v1.**

### FASE 2 — Fundaciones Backend

- **PKG-2.1** — Bootstrap Spring Boot 4 (Java 25, Gradle), Docker Compose (PostgreSQL), perfiles, Flyway base, Springdoc, configuración Spring Modulith + test `ApplicationModules.verify()`.
- **PKG-2.2** — Módulo `iam`: `Clinic` (tenant), `User`, `Role`, login JWT (Spring Security), onboarding de clínica, filtro de tenancy (`clinic_id`).
- **PKG-2.3** — `shared`: `Money`, tipos comunes, infraestructura de eventos (Modulith), MapStruct base, manejo de errores (RFC 7807).

### FASE 3 — Módulos núcleo (paralelizables tras los prerrequisitos)

> Reescrita. El detalle vive en [`plan/phase-3-core-modules.md`](./plan/phase-3-core-modules.md); esto es solo el resumen.

Dos prerrequisitos bloquean la fase entera, porque el frontend no tiene integrado nada de la Fase 2:

- **PRE-A `iam-password-recovery`** — forgot/reset por correo (Mailpit en local), rate limit, revocación de sesiones.
- **PRE-B `frontend-auth-shell`** — BFF con cookies `httpOnly`, `/login`, guard, refresh, `401 → /login`.

Después, paralelizables:

- **PKG-3.1 Staff List** — **sin módulo backend nuevo**: un Dentist es un `Membership` con rol `DENTIST`, así que es filtro por rol + baja de miembro en `iam`, más la pantalla.
- **PKG-3.2 Patients** (`patients`, incluye odontograma y medical record) + UI Patients (lista/detalle/odontograma).
- **PKG-3.3 Treatments** (`treatments`, visitas + componentes) + UI flujo "Add a treatment" (wizard). Hereda las **especialidades**.
- **PKG-3.4 Reservations** (`reservations`, citas + waitlist) + reemplazar mock de Fase 1 por API real; modal nueva cita; waitlist wizard. Hereda las **Working Hours**.

### FASE 4 — Finanzas & Activos

- **PKG-4.1 Billing** (`billing`): Sales, Pay Bill flow, Payment Method + UI.
- **PKG-4.2 Inventory** (`inventory`): Stocks + Peripherals + UI; integración eventos con treatments.
- **PKG-4.3 Purchases** (`purchases`): órdenes + proveedores + UI.
- **PKG-4.4 Accounts**: estados financieros (clínica/odontólogo) + UI.

### FASE 5 — Transversales

- **PKG-5.1 Dashboard** (KPIs, gráficos recharts).
- **PKG-5.2 Reporting** (`reporting`) + UI Report (export).
- **PKG-5.3 Support** (`support`) + UI Customer Support.
- **PKG-5.4 Settings/Profile**, notificaciones, búsqueda global.

---

## Contrato entre agentes (para multiagentes)

Para que los paquetes encajen sin fricción:
- **Tipos compartidos primero:** los esquemas Zod del frontend y los DTOs/OpenAPI del backend son la fuente de verdad de cada feature; definir antes de implementar consumidores.
- **Mock → API:** el frontend de Fase 1 usa `queries.ts` con `queryFn` mock; en Fase 3 se sustituye la `queryFn` por el cliente HTTP sin cambiar componentes.
- **Eventos del dominio** se documentan en `shared` y son el único acoplamiento entre módulos backend.
- **DoD por paquete:** lint + build verdes (front), `gradlew build` + `verify()` verdes (back), y descripción de cómo probarlo.

---

## Verificación

**Fase 1 (entregable inmediato):**
1. `bun install` (FullCalendar **no** se añade).
2. `bun run dev` → abrir `http://localhost:3000`; debe redirigir a `/reservations`.
3. Validar visualmente contra el mockup: sidebar agrupado + navbar; calendario con columnas por dentista, eje horario, citas con colores por estado, break time, "Not available", línea roja de hora actual, toggles Today/Day/Week/All Dentist.
4. `bun run lint` y `bun run build` deben pasar.
5. (Opcional) usar skill `run`/`verify` para recorrer la app y skill `heroui-pro-design-taste` para revisar el pulido.

**Fases backend:** `docker compose up` (PostgreSQL) + `./gradlew build` con Testcontainers; `ApplicationModules.verify()` en verde; Swagger UI (Springdoc) accesible; smoke test de cada endpoint nuevo.

---

## Notas y riesgos

- **TanStack Start es SSR**: componentes que tocan `window`/DOM (calendario, tiptap, recharts) deben ser client-only o guardados con checks de entorno. Tenerlo en cuenta en PKG-1.4.
- **HeroUI Pro está en beta** (`1.0.0-beta.6`): posibles cambios de API; fijar versiones.
- **FullCalendar Scheduler** (vista resource) es de pago — por eso se descarta a favor del grid custom.
- **Multi-tenancy**: validar el filtro `clinic_id` con tests de aislamiento desde el inicio (Fase 2) para evitar fugas de datos entre clínicas.
- El **backend vive en un proyecto/carpeta aparte**; definir si monorepo o repos separados antes de Fase 2 (no bloquea Fase 1).
