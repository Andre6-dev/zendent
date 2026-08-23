# Zendenta — Plan de Implementación

> **Idioma:** documentación en español. **Todo el código, nombres de archivos, identificadores, ramas, commits y comentarios van en inglés.**

Zendenta es un **SaaS de gestión para clínicas dentales y consultorios individuales**. Cubre reservaciones (calendario por odontólogo), pacientes con historial médico y **odontograma**, tratamientos (visitas, componentes/materiales y plan de tratamiento), gestión de staff, finanzas (cuentas, ventas, compras, métodos de pago), activos físicos (stocks, periféricos), reportes y soporte al cliente.

Este directorio (`docs/plan/`) es la fuente de verdad del plan. **No se escribe código de producto hasta que estos documentos estén aprobados.**

---

## Índice de documentos

| Documento                                                                            | Contenido                                                                                        |
| ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ |
| [README.md](./README.md)                                                             | Este archivo: overview, stack, decisiones, evaluación Modulith, roadmap.                         |
| [architecture.md](./architecture.md)                                                 | Arquitectura backend (Modulith + multi-tenancy + eventos) y frontend (carpetas, convenciones).   |
| [screens.md](./screens.md)                                                           | Inventario completo de pantallas (mejorado y completado).                                        |
| [phase-1-frontend-layout-reservations.md](./phase-1-frontend-layout-reservations.md) | **Primer entregable de código** (detallado): layout + calendario de Reservaciones con mock data. |
| [phase-2-backend-foundations.md](./phase-2-backend-foundations.md)                   | Bootstrap Spring Boot 4, Modulith, tenancy, IAM.                                                 |
| [phase-3-core-modules.md](./phase-3-core-modules.md)                                 | Prerrequisitos de auth + módulos núcleo: patients, treatments, reservations.                                       |
| [phase-4-finance-assets.md](./phase-4-finance-assets.md)                             | Billing, inventory, purchases, accounts.                                                         |
| [phase-5-cross-cutting.md](./phase-5-cross-cutting.md)                               | Dashboard, reporting, support, settings.                                                         |
| [multiagent-workflow.md](./multiagent-workflow.md)                                   | Contrato entre agentes y asignación de paquetes (PKG).                                           |

---

## Stack técnico (confirmado)

**Frontend** (ya scaffoldeado en `webapp-zendent`):

- React 19, **TanStack Start** (Vite 8 + Nitro, **SSR**), TypeScript 6, TailwindCSS v4.
- **HeroUI v3 + HeroUI Pro** (beta) — ya cableados en `src/styles.css`.
- TanStack Router (file-based) / Query / Form / Table, Zod 4.
- `lucide-react` (íconos), `recharts` (reportes), `@faker-js/faker` (mock data), `tiptap` (texto rico), `motion`.
- Gestor de paquetes: **Bun**.

**Backend** (proyecto/carpeta aparte, a crear):

- Java 25, **Spring Boot 4**, Spring Web, Spring Security, Spring Data JPA, **Spring Modulith**.
- PostgreSQL, Flyway, MapStruct, Bean Validation, Springdoc OpenAPI, Testcontainers, Docker.

> **Nota sobre el scaffold:** el repo actual es TanStack Start (SSR vía Nitro), no un Vite SPA puro. Falta envolver la app con `HeroUIProvider`. **FullCalendar NO está instalado** y no se añadirá (ver decisión 2).

---

## Decisiones confirmadas

1. **Tenancy → SaaS multi-clínica**, _shared schema_ con discriminador `clinic_id`. Tanto la clínica grande como el consultorio individual son una "organización" (clinic) con 1+ odontólogos.
2. **Calendario de reservaciones → grid custom** (CSS grid + HeroUI). Se evalúa primero el Calendar de HeroUI Pro; si no soporta la vista _resource-timeline_ (dentistas en columnas × horas en filas), se construye a medida. **No** se usa FullCalendar Scheduler (plugin de pago).
3. **Backend → adoptar Spring Modulith** (monolito modular).
4. **Primer entregable (v1) → solo visualización con mock data**: sidebar + navbar + pantalla de Reservaciones mostrando el calendario. Sin crear/editar citas, sin backend.

---

## Evaluación: ¿Spring Modulith? → **SÍ**

El dominio tiene _bounded contexts_ nítidos (pacientes, tratamientos, reservaciones, facturación, inventario, staff) que se comunican por eventos naturales ("cita finalizada → generar cargo", "tratamiento planificado → reservar componentes de stock"). Spring Modulith aporta:

- **Límites verificados:** `ApplicationModules.verify()` falla el build si un módulo accede a internos de otro.
- **Comunicación desacoplada:** `ApplicationEventPublisher` + `@ApplicationModuleListener` (eventos transaccionales, con event-publication-registry/outbox para fiabilidad).
- **Documentación viva** (PlantUML/C4) y tests por módulo (`@ApplicationModuleTest`).
- **Migración futura** a microservicios de bajo costo.

**Coste:** disciplina en el diseño de paquetes y una curva de aprendizaje menor. Para un monolito SaaS que crecerá en superficie funcional, el beneficio supera el coste. **Adoptar.**

---

## Roadmap por fases

| Fase       | Foco                                                                                                    | Estado    |
| ---------- | ------------------------------------------------------------------------------------------------------- | --------- |
| **Fase 1** | Frontend: layout (sidebar + navbar) + calendario de Reservaciones con mock data. **Primer entregable.** | Pendiente |
| **Fase 2** | Backend: bootstrap Spring Boot 4 + Modulith + tenancy + IAM.                                            | Pendiente |
| **Fase 3** | Auth en el frontend (prerrequisito) + módulos núcleo: staff list, patients (odontograma), treatments, reservations (API real).                     | Pendiente |
| **Fase 4** | Finanzas y activos: billing (Pay Bill), inventory, purchases, accounts.                                 | Pendiente |
| **Fase 5** | Transversales: dashboard, reporting, support, settings/perfil, búsqueda y notificaciones.               | Pendiente |

Cada fase se divide en **paquetes (PKG)** asignables a agentes; ver el documento de cada fase y [multiagent-workflow.md](./multiagent-workflow.md).

---

## Notas y riesgos

- **TanStack Start es SSR:** componentes que tocan `window`/DOM (calendario, tiptap, recharts) deben ser client-only o protegidos con checks de entorno.
- **HeroUI Pro está en beta** (`1.0.0-beta.6`): posibles cambios de API; fijar versiones.
- **FullCalendar Scheduler** (vista resource) es de pago → se descarta a favor del grid custom.
- **Multi-tenancy:** validar el filtro `clinic_id` con tests de aislamiento desde el inicio (Fase 2) para evitar fugas de datos entre clínicas.
- El **backend vive en un proyecto/carpeta aparte**; decidir monorepo vs repos separados antes de Fase 2 (no bloquea la Fase 1).
