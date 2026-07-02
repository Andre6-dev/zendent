# Arquitectura

> Código en inglés. Documento en español.

---

## Backend

### Multi-tenancy (shared schema)

- Modelo: una sola base de datos, un solo schema; cada entidad de negocio lleva la columna `clinic_id`.
- La "organización" (`Clinic`) es el tenant. Un consultorio individual = una `Clinic` con un solo `Dentist`; una clínica = una `Clinic` con varios.
- **Aislamiento:** filtro Hibernate (`@FilterDef`/`@Filter` por `tenant`) activado por un interceptor de Spring Security que lee el `clinic_id` desde el JWT del usuario autenticado. Todas las queries quedan acotadas al tenant.
- **Tests de aislamiento obligatorios** desde la Fase 2: un usuario de la clínica A nunca debe ver datos de la clínica B.
- Migraciones con **Flyway** (`V1__init.sql`, `V2__...`), versionadas y revisadas.

### Spring Modulith — módulos

Cada módulo es un paquete top-level bajo `com.zendenta`. Lo interno del módulo no se expone; solo se comparten **eventos** y un paquete público `…/api` (o `…/spi`).

| Módulo         | Responsabilidad                                                         | Entidades núcleo                                    |
| -------------- | ----------------------------------------------------------------------- | --------------------------------------------------- |
| `iam`          | Auth, usuarios, roles, clínicas (tenant), onboarding, membresías        | `Clinic`, `User`, `Role`, `Membership`              |
| `staff`        | Odontólogos y personal, horarios, especialidades                        | `Dentist`, `StaffMember`, `WorkingHours`            |
| `patients`     | Pacientes, ficha, hábitos de higiene, **odontograma**, historial médico | `Patient`, `MedicalRecord`, `ToothCondition`        |
| `treatments`   | Catálogo de tratamientos, visitas, componentes/materiales               | `Treatment`, `TreatmentVisit`, `TreatmentComponent` |
| `reservations` | Citas, calendario, waitlist, estados                                    | `Appointment`, `WaitlistEntry`                      |
| `billing`      | Ventas, facturas, cobros, métodos de pago, Pay Bill                     | `Invoice`, `Payment`, `PaymentMethod`               |
| `inventory`    | Stocks y periféricos, descuento por componentes de tratamiento          | `StockItem`, `Peripheral`, `StockMovement`          |
| `purchases`    | Órdenes de compra a proveedores                                         | `PurchaseOrder`, `Supplier`                         |
| `reporting`    | Read models / proyecciones para reportes                                | vistas/proyecciones                                 |
| `support`      | Tickets de customer support                                             | `SupportTicket`                                     |
| `shared`       | Tipos comunes, eventos publicados, `Money`, infraestructura de tenancy  | —                                                   |

### Estados de cita (`reservations`)

`REGISTERED` → `ENCOUNTER` → `FINISHED`; estado transversal `WAITING_PAYMENT` (cita finalizada con factura pendiente). El frontend mapea cada estado a un color (ver phase-1).

### Eventos de dominio (acoplamiento entre módulos)

| Evento (publicado por)                      | Consumidores   | Efecto                                                     |
| ------------------------------------------- | -------------- | ---------------------------------------------------------- |
| `AppointmentFinishedEvent` (`reservations`) | `billing`      | Genera `Invoice` (estado WAITING_PAYMENT en reservations). |
| `TreatmentPlannedEvent` (`treatments`)      | `inventory`    | Reserva/descuenta `StockItem` según componentes.           |
| `InvoicePaidEvent` (`billing`)              | `reservations` | Libera el estado "Waiting Payment" de la cita.             |
| `ClinicCreatedEvent` (`iam`)                | varios         | Provisiona datos base del tenant.                          |
| `PurchaseReceivedEvent` (`purchases`)       | `inventory`    | Incrementa stock.                                          |

Los eventos se documentan en `shared` y se publican/consumen con `ApplicationEventPublisher` + `@ApplicationModuleListener`.

### Estructura por módulo (patrón repetido)

```
com.zendenta.<module>/
  api/            # tipos/puertos públicos al resto de módulos (opcional)
  domain/         # entidades JPA, value objects
  <Module>Service.java
  <Module>Repository.java
  web/            # @RestController + DTOs request/response
  mapper/         # MapStruct
  events/         # eventos publicados/escuchados
  internal/       # detalles no expuestos
```

- **DTOs + MapStruct** para no exponer entidades JPA en la API.
- **Bean Validation** en los DTOs de request.
- **Springdoc OpenAPI** documenta cada controller (Swagger UI).
- **Errores** con formato RFC 7807 (`ProblemDetail`).
- **Tests:** `@ApplicationModuleTest` por módulo + Testcontainers (PostgreSQL real). `ApplicationModules.verify()` en el build.

---

## Frontend

### Estructura de carpetas (`src/`, alias `#/*` y `@/*` → `./src/*`)

```
src/
  routes/                      # file-based routing (TanStack Router)
    __root.tsx                 # añadir HeroUIProvider aquí
    _app/                      # layout group con sidebar+navbar (pathless route)
      route.tsx                # AppLayout (sidebar + navbar + <Outlet/>)
      index.tsx                # redirect → /reservations
      reservations/index.tsx
      patients/...             # fases siguientes
      treatments/...
      ...
  components/
    layout/                    # Sidebar, Navbar, AppLayout, ClinicSwitcher
    ui/                        # wrappers/composición sobre HeroUI
    reservations/              # CalendarGrid, DentistColumn, AppointmentCard, CalendarToolbar...
  features/<feature>/          # queries, types, schemas (Zod), hooks por feature
  lib/                         # tenancy, formatters, api client (fase backend), theme
  mocks/                       # datos mock con faker (fase 1)
```

### Convenciones

- **Componentes:** preferir bloques de **HeroUI Pro** (verificar disponibles vía skill `heroui-react-pro` / MCP) sobre HeroUI base; estilos con Tailwind v4 + `tailwind-variants`.
- **Datos servidor:** TanStack Query (en Fase 1 el `queryFn` devuelve mock; en Fase 3 se sustituye por cliente HTTP sin cambiar componentes).
- **Formularios:** TanStack Form + Zod (schemas en `features/<feature>/schemas.ts`).
- **Tablas:** TanStack Table.
- **Routing:** file-based; `routeTree.gen.ts` es autogenerado (`bun run generate-routes`), no editar a mano.
- **Buenas prácticas:** seguir skill `vercel-react-best-practices` (en `.agents/skills/`) y `heroui-pro-design-taste`.
- **SSR (TanStack Start):** aislar lo que toca `window`/DOM (calendario, tiptap, recharts) como client-only.

### Contrato mock → API

`features/<feature>/queries.ts` define los hooks de query/mutation. En Fase 1 el `queryFn` lee de `src/mocks/*`. La forma de los datos mock se deriva de los **schemas Zod**, que son los mismos que validarán las respuestas reales del backend. Migrar a API = cambiar el `queryFn`, no los componentes.
