# El cargo nace del Procedure ejecutado, no del TreatmentPlan aprobado

Se cobra lo que efectivamente se hizo. El `TreatmentPlan` queda como presupuesto
de referencia contra el que se compara el avance, pero no genera cargos por sí
mismo. Los pagos parciales contra el saldo del paciente existen desde la primera
versión, no como añadido posterior.

En odontología los planes se ejecutan a lo largo de meses y con frecuencia se
abandonan a medias, y pagar a cuenta es la norma. Facturar el plan completo por
adelantado obligaría a implementar devoluciones y notas de crédito desde el
día 1. Y retrofitear pagos a cuenta sobre un modelo binario de "factura
pagada/impagada" es una reescritura completa del módulo de finanzas.

## Consecuencias

El modelo reserva desde ya tipo de comprobante, serie y número correlativo,
aunque al inicio los comprobantes se emitan solo internamente y la integración
con SUNAT vía proveedor (Nubefact, Efact o similar) llegue en una fase
posterior. Añadir esos campos después obligaría a renumerar el histórico, y la
numeración correlativa de comprobantes no admite rehacerse.
