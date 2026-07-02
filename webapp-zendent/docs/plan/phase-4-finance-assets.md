# Fase 4 — Finanzas y Activos Físicos

> Código en inglés. Depende de Fase 3. Paralelizable (un agente por módulo). Ver [architecture.md](./architecture.md).

## Objetivo

Cerrar el ciclo económico (ventas, cobros, métodos de pago), el inventario (stocks/periféricos) y las compras, integrados por eventos con reservations y treatments.

---

## PKG-4.1 — Billing

- **Backend (`billing`):** `Invoice`, `Payment`, `PaymentMethod`. Escucha `AppointmentFinishedEvent` → crea `Invoice`. Publica `InvoicePaidEvent`. **Pay Bill flow**.
- **Frontend:**
  - **Sales:** tabla de facturación por paciente con **color-coding** (pagado/parcial/impago), filtros, detalle.
  - **Pay Bill:** _Select Payment Method_ → desglose de cargos → método (efectivo con **botones de denominación**, tarjeta, etc.) → recibo (imprimir / volver al calendario).
  - **Payment Method:** configuración de métodos aceptados.
- **DoD:** cita finalizada genera factura; cobro marca pagada y libera "Waiting Payment" en reservations; tests.

## PKG-4.2 — Inventory

- **Backend (`inventory`):** `StockItem`, `Peripheral`, `StockMovement`. Escucha `TreatmentPlannedEvent` → reserva/descuenta stock; escucha `PurchaseReceivedEvent` → incrementa stock. Alertas de stock bajo.
- **Frontend:** **Stocks** (niveles, alertas, vínculo con componentes de tratamiento) y **Peripherals** (equipos, mantenimiento, asignación).
- **DoD:** movimientos de stock por eventos funcionan; alertas visibles; tests.

## PKG-4.3 — Purchases

- **Backend (`purchases`):** `PurchaseOrder`, `Supplier`; órdenes, recepción de mercancía (publica `PurchaseReceivedEvent`).
- **Frontend:** órdenes de compra, proveedores, recepción.
- **DoD:** recepción incrementa stock vía evento; tests.

## PKG-4.4 — Accounts

- **Backend:** consultas/agregados financieros por clínica y por odontólogo (apoyado en `billing`).
- **Frontend:** **Accounts** (estado financiero clínica / odontólogo).
- **DoD:** cifras consistentes con billing; tests.

## Dependencias

```
billing(4.1) ──► accounts(4.4)
purchases(4.3) ──► inventory(4.2)   (vía PurchaseReceivedEvent)
treatments(F3) ──► inventory(4.2)   (vía TreatmentPlannedEvent)
reservations(F3) ──► billing(4.1)   (vía AppointmentFinishedEvent)
```

## Verificación

- Flujo E2E: planificar tratamiento → reservar stock; finalizar cita → factura → cobro → liberar estado; comprar → recibir → stock sube.
- `./gradlew build` + `verify()` verdes; `bun run build`/lint verdes.
