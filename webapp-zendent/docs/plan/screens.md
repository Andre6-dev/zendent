# Inventario de Pantallas (mejorado y completado)

> Se mantiene el **mismo layout** del mockup: sidebar agrupado (CLINIC / FINANCE / PHYSICAL ASSET) + navbar (buscador global, botón "+", ayuda, actividad, notificaciones con badge, avatar/perfil, selector de clínica). Se mejora la interacción y se completa lo que faltaba (marcado _(añadido)_).

---

## CLINIC

### Dashboard _(añadido)_

KPIs del día (citas, ingresos, pacientes nuevos), próximas citas, alertas de stock bajo, gráfico de ventas (recharts). Punto de entrada tras login.

### Reservations

- Tabs **Calendar / Log History**.
- Calendario **resource** (odontólogos en columnas × horas en filas), header por dentista con avatar y "Today's appointment: N".
- Filtros: **Today** + flechas de navegación, fecha, toggle **Day/Week**, filtro **All Dentist**, **Filters**.
- Tarjetas de cita con **color por estado**: Finished (verde), Encounter (azul/amarillo), Registered (azul), Waiting Payment (amarillo punteado). Bloques **BREAK TIME** y **NOT AVAILABLE**. Línea roja de **hora actual**.
- Acciones: botón **"+"** nueva cita (modal), **Waitlist** (wizard 3 pasos): (1) seleccionar tratamiento + odontólogo, (2) datos básicos del paciente, (3) hábitos de higiene oral.

### Patients

- **Lista** de pacientes (tabla, búsqueda, filtros).
- **Detalle** con tabs: _Patient Information / Appointment History / Next Treatment / Medical Record_.
- **Medical Record** con **Odontograma interactivo**: selección de dientes (numeración FDI/Universal), registro de condiciones, timeline de tratamientos por diente (condición, tratamiento, dentista, estado Done/Pending), toggle **Medical / Cosmetic**.
- Crear/editar paciente; botón **Create Appointment** desde la ficha.

### Treatments

Catálogo + flujo **"Add a treatment"** (wizard):

1. **Basic Information** — nombre, categoría, descripción.
2. **Multiple Visits** — añadir visitas, duración y agenda.
3. **Components** — materiales usados, cantidad y costo.
4. **Treatment Plan** — selección de dientes/condiciones (apoyo visual de odontograma).
5. **Summary** — desglose de servicios y componentes.
6. **Pay Bill** — ver flujo en Sales.

### Staff List

Odontólogos y personal: alta/edición, horarios (working hours), especialidades, agenda individual. Distingue rol clínica vs consultorio (1+ dentistas).

---

## FINANCE

### Accounts

Estado financiero de la clínica y por odontólogo.

### Sales

- Tabla de facturación por paciente con **color-coding** (pagado / parcial / impago), filtros y detalle.
- **Pay Bill flow:** _Select Payment Method_ → desglose de cargos → método de pago (efectivo con **botones de denominación** comunes, tarjeta, etc.) → confirmación → **recibo** (imprimir o volver al calendario).

### Purchases

Órdenes de compra, proveedores, recepción de mercancía (incrementa stock vía evento).

### Payment Method

Configuración de métodos de pago aceptados por la clínica.

---

## PHYSICAL ASSET

### Stocks

Inventario de insumos/materiales, niveles, alertas de stock bajo, vínculo con componentes de tratamiento (descuento automático).

### Peripherals

Equipos/periféricos, mantenimiento, asignación a salas/odontólogos.

---

## OTROS

### Report

Reportes financieros, de tratamientos y de ocupación de agenda (recharts + export).

### Customer Support

Tickets, FAQ, contacto.

### Auth / Onboarding _(añadido)_

Login, registro de clínica (crea el tenant), invitación de staff, recuperación de contraseña.

### Settings / Profile _(añadido)_

Perfil de usuario, datos de la clínica, roles/permisos, preferencias.

### Global search & notifications _(navbar)_

Búsqueda global (pacientes, citas, tratamientos) y centro de notificaciones.
