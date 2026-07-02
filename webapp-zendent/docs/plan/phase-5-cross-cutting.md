# Fase 5 — Transversales

> Código en inglés. Depende de fases previas (consume datos de varios módulos). Ver [architecture.md](./architecture.md).

## Objetivo

Completar las superficies transversales del producto: dashboard, reportes, soporte, configuración/perfil, búsqueda global y notificaciones.

---

## PKG-5.1 — Dashboard

- **Frontend:** KPIs del día (citas, ingresos, pacientes nuevos), próximas citas, alertas de stock bajo, gráfico de ventas (**recharts**). Punto de entrada tras login.
- **Backend:** endpoints de agregados (pueden apoyarse en `reporting`).
- **DoD:** KPIs reflejan datos reales; carga performante.

## PKG-5.2 — Reporting

- **Backend (`reporting`):** read models/proyecciones para reportes financieros, de tratamientos y de ocupación de agenda.
- **Frontend:** **Report** con gráficos (recharts) y export (CSV/PDF).
- **DoD:** reportes correctos; export funcional; tests.

## PKG-5.3 — Support

- **Backend (`support`):** `SupportTicket` (CRUD, estados).
- **Frontend:** **Customer Support** (tickets, FAQ, contacto).
- **DoD:** ciclo de ticket E2E; tests.

## PKG-5.4 — Settings / Profile + Navbar

- **Frontend:** **Settings/Profile** (perfil de usuario, datos de clínica, roles/permisos, preferencias); **búsqueda global** (pacientes/citas/tratamientos) y **centro de notificaciones** del navbar.
- **Backend:** endpoints de perfil/clinic settings; search agregada; notificaciones.
- **DoD:** edición de perfil/clinic persiste; búsqueda devuelve resultados de varios módulos; tests.

## Verificación

- Recorrido E2E del producto completo; `./gradlew build` + `verify()` verdes; `bun run build`/lint verdes.
