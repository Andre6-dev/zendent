# Fase 3 — Módulos Núcleo

> Código en inglés. Depende de Fase 2. Ver [architecture.md](./architecture.md) y [multiagent-workflow.md](./multiagent-workflow.md).
>
> **Este documento se reescribió tras una sesión de grilling sobre PKG-3.1.** Varias suposiciones del plan original resultaron falsas al contrastarlas con el código que la Fase 2 dejó en `api/`. Ver [Decisiones que reconfiguraron la fase](#decisiones-que-reconfiguraron-la-fase) al final.

## Objetivo

Implementar los módulos clínicos y conectar el frontend a la API real, sustituyendo los mocks de Fase 1.

---

## Prerrequisitos — el frontend no puede llamar a nada todavía

La Fase 2 dejó el backend con toda su superficie de autenticación funcionando, y el frontend con **cero** integración: no hay un solo `fetch` en `webapp-zendent/src/`, ni cliente HTTP, ni almacenamiento de sesión, ni ruta de login. `src/routes/_app/route.tsx` monta el layout sin guard alguno.

Como **todos** los endpoints están tras un JWT cuyo tenant se resuelve del subdominio, ninguna pantalla de esta fase puede hacer una petición real hasta que exista esa plomería. Y la comparten los cuatro paquetes por igual, así que va fuera de ellos: metida dentro de 3.1, bloquea a 3.2–3.4.

### PRE-A — `iam-password-recovery` _(backend)_

`screens.md` lista recuperación de contraseña como parte de Auth, pero `AuthController` solo tiene `register`, `login`, `refresh` y `logout`. No existe.

- `POST /auth/forgot-password` y `POST /auth/reset-password`, con el mismo scoping por subdominio que el login.
- **Entrega por correo.** El patrón de la invitación —devolver el token en claro al administrador para que lo entregue a mano— no sirve aquí: quien pide un reset está bloqueado fuera y sin autenticar. Y el ADR 0005 dice que un consultorio de un solo odontólogo también es una Clinic: ahí el administrador *es* la persona bloqueada.
- `spring-boot-starter-mail` con `spring.mail.*`; **Mailpit** (`axllent/mailpit`, 1025 SMTP / 8025 UI) como servicio en `api/compose.yaml` para local. El proveedor de producción es un relé SMTP configurado por entorno, nunca una dependencia en el código.
- El envío va **por evento** sobre el event-publication-registry de Modulith que ya está activo (`republish-outstanding-events-on-restart: true`), escuchado dentro de `iam`. Nada de módulo `notifications` todavía: sería un módulo con un listener.
- **Respuesta idéntica** para email conocido y desconocido — si no, es un enumerador de los usuarios de una clínica. El envío asíncrono también evita que el tiempo de respuesta delate la diferencia.
- Límite de frecuencia en el endpoint.
- Un reset **borra todos los `RefreshToken` del usuario**. La razón principal para restablecer una contraseña es sospechar de un robo; si el token robado sobrevive, el reset no sirvió.
- **DoD:** flujo completo contra Mailpit; test de que dos emails (uno real, uno inexistente) producen respuestas indistinguibles; test de que el reset invalida los refresh tokens.

### PRE-B — `frontend-auth-shell`

- **BFF en el servidor de TanStack Start** (nitro ya está en `vite.config.ts`): recibe el login, guarda access y refresh en cookies `httpOnly` + `Secure` + `SameSite`, y reenvía a la API. El navegador nunca ve un token. Ver [ADR 0017](../../../docs/adr/0017-api-served-under-each-clinic-subdomain.md).
- Rutas `/login`, `/forgot-password`, `/reset-password`.
- Guard sobre `_app`: sin sesión no se renderiza el layout.
- Refresh transparente al expirar el access token (15 min) y `401 → /login`.
- Todo `loader` de SSR reenvía las cookies de la petición entrante.
- **Dev por Host, igual que producción:** se trabaja en `avicena.localhost:3000`. El CORS del backend ya admite `http://*.localhost:[*]`. La cabecera `X-Clinic-Slug` queda solo para tests de integración. Si dev resuelve el tenant por un mecanismo distinto al de prod, el primer fallo por Host aparece en producción y sin forma de reproducirlo.
- **Fuera de alcance:** UI de onboarding (vive en el host apex, es registro público, otro producto) y UI de invitación (pertenece a la pantalla de personal, o sea a PKG-3.1).
- **DoD:** login → calendario con datos reales de sesión; recarga de página conserva la sesión; expiración renueva sin que el usuario lo note; `bun run build` y lint verdes.

---

## PKG-3.1 — Staff List

**Este paquete ya no es "un módulo backend + una UI".** El plan original prometía un módulo `staff` con entidades `Dentist`, `StaffMember` y `WorkingHours`. Nada de eso sobrevivió al grilling:

- Todo Dentist tiene cuenta, así que un Dentist **es** un `Membership` con rol `DENTIST` — no hace falta entidad ni tabla nueva.
- Un Staff tampoco es entidad: es `Membership` con rol `STAFF`. La asimetría no existe porque nada se agenda ni se factura contra un administrativo.
- `WorkingHours` se fue a PKG-3.4 (ver allí).
- Las especialidades se fueron a PKG-3.3 (ver allí).
- El nombre `staff` estaba además vetado por `CONTEXT.md`, que define **Staff** como el miembro administrativo *sin acceso clínico alguno* — un módulo llamado `staff` conteniendo `Dentist` contradice el glosario.

Lo que queda es completar la superficie de miembros de `iam` y dibujar la pantalla.

**Backend (`iam`)**

- Filtro por rol en `ClinicMemberService.listMembers()` → el roster de odontólogos que consume el calendario. `MemberResponse` ya trae `fullName`, `role`, `status` y `memberSince`; se consume tal cual.
- **Baja de miembro** — hueco que dejó la Fase 2: `Membership.Status` tiene hoy un único valor `ACTIVE` y no hay forma de revocarle el acceso a nadie. Un valor más en el enum y un `PATCH /members/{id}`. Se **revoca, nunca se borra**: el rastro de quién tuvo acceso y hasta cuándo se conserva (ADR 0010).
- UI de invitación sobre el `POST /auth/invitations` que ya existe.
- **Sin `title` ni `avatarUrl`.** El avatar necesita object storage y URLs firmadas (ADR 0007) — es un cambio entero, no un campo; se resuelve con iniciales sobre color derivado del nombre, que además nunca falla al cargar. `title` ("Drg") es decoración del mockup.

**Frontend**

- Pantalla **Staff List**: tabla de miembros con rol y estado, invitar, revocar.
- La "agenda individual" es un **enlace al calendario ya filtrado**, no una vista nueva: `src/features/reservations/types.ts` ya define `DentistFilter = 'all' | string` y el toolbar ya tiene el selector "All Dentist". Un segundo calendario diverge del primero.
- Sustituir `src/mocks/dentists.ts` por el roster real en `dentistsQueryOptions()`, sin tocar componentes.
- **Borrar `Dentist.available`** de `types.ts` y `schemas.ts`. Es código muerto: ningún componente lo lee — `CalendarGrid.tsx` dibuja la banda NOT AVAILABLE desde `schedule.blocks` con `kind === 'unavailable'`, o sea desde **Block**, que es lo que el glosario dice. La disponibilidad vuelve en 3.4, derivada.

**DoD:** invitar y revocar E2E; el calendario dibuja columnas desde el roster real; tests de aislamiento por tenant; `verify()` verde.

## PKG-3.2 — Patients (odontograma)

> No grillado todavía. Contenido del plan original, pendiente de la misma revisión que recibió 3.1.

- **Backend (`patients`):** `Patient`, `MedicalRecord`, `ToothCondition`; ficha, hábitos de higiene, historial; modelo de **odontograma** (diente + condición + tratamiento + dentista + estado).
- **Frontend:** lista + detalle con tabs (_Patient Information / Appointment History / Next Treatment / Medical Record_); **odontograma interactivo** (selección de dientes, condiciones, timeline, toggle Medical/Cosmetic); crear/editar.
- Toda pantalla con dato clínico queda fuera del alcance de `STAFF` y **emite el evento de auditoría de lectura** (ADR 0012 y 0010). Una pantalla que muestra historia clínica sin auditar es un fallo, no una omisión.
- **DoD:** odontograma muestra y registra condiciones desde la API; tests.

## PKG-3.3 — Treatments

> No grillado todavía, salvo la incorporación de especialidades.

- **Backend (`treatments`):** `Treatment`, `TreatmentVisit`, `TreatmentComponent`; catálogo, visitas, componentes/costos. Publica `TreatmentPlannedEvent`.
- **Especialidades** (heredadas de PKG-3.1). Aquí y no antes porque una especialidad no *hace* nada hasta que restringe algo — "solo un ortodoncista puede tomar un TreatmentType de ortodoncia" — y esa regla necesita `TreatmentType`. Si se adelantan, **tienen que ser catálogo desde el primer día, nunca texto libre**: un campo libre se llena de "Ortodoncia", "ortodoncia" y "ORTO", y convertirlo en catálogo con clínicas reales dentro es migración manual una por una.
- **Frontend:** catálogo + wizard **"Add a treatment"** (Basic info → Multiple visits → Components → Treatment plan → Summary → Pay Bill). TanStack Form + Zod.
- **DoD:** crear tratamiento con visitas y componentes E2E; evento publicado; tests.

## PKG-3.4 — Reservations (API real)

- **Backend (`reservations`):** `Appointment` (estados REGISTERED/ENCOUNTER/FINISHED/WAITING_PAYMENT), `WaitlistEntry`. Publica `AppointmentFinishedEvent`; escucha `InvoicePaidEvent`.
- **`WorkingHours` vive aquí** (heredado de PKG-3.1), junto a `Block`. Disponibilidad = Working Hours menos Blocks: partir esa resta entre dos módulos obliga a `reservations` a preguntarle a otro módulo para dibujar una columna del calendario, que es su bucle más caliente. En `iam` tampoco: meter horario de agenda en el módulo fundacional del que cuelga todo es el acoplamiento que `ApplicationModules.verify()` existe para impedir.
- **La disponibilidad se deriva, nunca se persiste.** Un booleano guardado al lado se contradice con el horario en cuanto alguien lo edita.
- **Cubrir a un colega ausente es reasignar sus Appointments**, no conceder permisos. El ADR 0012 ya da acceso clínico amplio a todo `DENTIST` — no hay permiso que conceder, y modelarlo como permiso deja el calendario mostrando al ausente atendiendo pacientes el día que estaba de vacaciones.
- **Frontend:** sustituir el `queryFn` mock de Fase 1 por el cliente HTTP (sin reescribir componentes); modal de **nueva cita**; **waitlist wizard** (3 pasos).
- **DoD:** calendario muestra citas reales; crear cita persiste; transiciones de estado funcionan; tests de aislamiento por tenant.

---

## Dependencias

```
PRE-A password recovery ──► PRE-B auth shell ──┬─► PKG-3.1 Staff List
                                               ├─► PKG-3.2 Patients
                                               ├─► PKG-3.3 Treatments
                                               └─► PKG-3.4 Reservations
```

- **PRE-A y PRE-B bloquean toda la fase.** Ningún frontend de Fase 3 puede llamar a la API antes.
- 3.1–3.4 son paralelizables entre sí una vez PRE-B está en verde.
- 3.4 consume el roster de 3.1 y publica eventos que 3.3 escucha; si se solapan, acordar los contratos OpenAPI primero.
- 3.1 es notablemente más pequeño de lo que este plan suponía antes: no abre módulo nuevo.

## Verificación

- **Backend:** `./mvnw test` con Testcontainers verde, `ApplicationModules.verify()` verde, Swagger accesible, tests de aislamiento entre clínicas por cada tabla nueva (ADR 0008).
- **Frontend:** la pantalla opera contra la API real en `avicena.localhost:3000`; `bun run build` y lint verdes.

---

## Decisiones que reconfiguraron la fase

Salidas de la sesión de grilling sobre PKG-3.1:

| Decisión | Efecto |
| --- | --- |
| Todo Dentist tiene cuenta; un suplente recibe una normal, dada de baja a mano | Desaparecen `Dentist` y `StaffMember` como entidades, y el módulo `staff` con ellas |
| El acceso clínico ya es amplio (ADR 0012) | Se descarta el "permiso temporal"; cubrir a un ausente es reasignar citas |
| `WorkingHours` con `Block`, en `reservations` | 3.1 pierde los horarios, 3.4 los gana |
| Especialidades solo cuando restringen algo | 3.1 las pierde, 3.3 las gana |
| La API se sirve bajo el subdominio de cada clínica, con BFF | [ADR 0017](../../../docs/adr/0017-api-served-under-each-clinic-subdomain.md); condiciona el despliegue entero |
| Recuperación de contraseña necesita correo real | Nace PRE-A, que antes no existía en ningún plan |
| El frontend no tiene nada de la Fase 2 integrado | Nace PRE-B, que el plan daba por hecho con un "swap del queryFn" |

Glosario afectado: **Dentist** y **Membership** en [`CONTEXT.md`](../../../CONTEXT.md).
