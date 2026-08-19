# Zendenta

SaaS de gestión clínica odontológica para clínicas con varios odontólogos y para
consultorios de un solo profesional. Cubre la agenda, la historia clínica con
odontograma, los tratamientos y su facturación.

Este archivo es **solo un glosario**. No contiene decisiones de implementación —
esas van en `docs/adr/`.

## Language

### Tenencia e identidad

**Clinic**:
La organización que contrata Zendenta y es dueña de todos sus datos. Un
consultorio de un solo odontólogo también es una Clinic.
_Avoid_: Tenant, Organization, Practice, Consultorio

**Membership**:
La pertenencia de un usuario a una Clinic con un rol determinado. Un mismo
usuario puede tener Membership en varias Clinics.
_Avoid_: UserRole, Employee, Account

**Dentist**:
El miembro de una Clinic que ejerce clínicamente: atiende Appointments, ejecuta
Procedures y escribe en el Odontogram.
_Avoid_: Doctor, Practitioner, Odontólogo, Provider

**Staff**:
El miembro de una Clinic con funciones administrativas — agenda, contacto y
cobros — y sin acceso a dato clínico alguno.
_Avoid_: Receptionist, Assistant, Admin, Recepción

**Subscription**:
Lo que una Clinic paga a Zendenta por usar el producto. Nunca se confunde con un
Charge, que es lo que un Patient paga a la Clinic.
_Avoid_: Plan, Billing, License, Suscripción

### Agenda

**Appointment**:
Una franja de tiempo reservada para que un odontólogo atienda a un paciente en
una fecha concreta. Es lo que el mockup rotula como "Reservation" en la UI; en
código y base de datos siempre es Appointment.
_Avoid_: Reservation, Booking, Cita, Visit, Schedule

### Historia clínica

**Patient**:
La persona que recibe atención odontológica en una Clinic. Pertenece a esa
Clinic: la misma persona atendida en dos clínicas son dos Patients distintos.
_Avoid_: Client, Customer, Cliente

**Guardian**:
El apoderado o tutor legal de un Patient menor de edad. Es quien otorga el
Consent en su nombre.
_Avoid_: Parent, Responsible, Apoderado, Contact

**Odontogram**:
La representación del estado dental completo de un Patient: el conjunto de
condiciones registradas sobre las superficies de sus dientes.
_Avoid_: Dental chart, Medical record, Teeth map

**Tooth**:
Una pieza dental identificada por su código de notación FDI de dos dígitos
(11-48 en dentición permanente, 51-85 en dentición temporal).
_Avoid_: Diente, Piece, Teeth number

**Tooth Surface**:
Una de las cinco caras de un Tooth (mesial, distal, vestibular, lingual/palatina,
oclusal/incisal). Es la unidad mínima sobre la que se registra una condición o
se ejecuta un Procedure.
_Avoid_: Face, Side, Cara, Area

**Dental Condition**:
Un hallazgo clínico registrado sobre una Tooth Surface — caries, fractura,
ausencia, restauración previa. Describe el estado, no el trabajo realizado.
_Avoid_: Diagnosis, Finding, Problem, Issue

### Tratamiento

**TreatmentType**:
Una entrada del catálogo de servicios que la Clinic ofrece, con su precio,
duración estimada y materiales asociados. Es una plantilla, no algo que le
ocurre a un paciente.
_Avoid_: Treatment, Service, Procedure, Catalog item

**TreatmentPlan**:
El conjunto de trabajos que un odontólogo propone a un Patient concreto sobre
superficies concretas, con su presupuesto. Es una propuesta: puede aceptarse,
rechazarse o quedar parcialmente ejecutada.
_Avoid_: Treatment, Quote, Budget, Presupuesto, Estimate

**Procedure**:
Un trabajo efectivamente ejecutado sobre una Tooth Surface durante un
Appointment. Es lo que actualiza el Odontogram y lo que genera el cargo.
_Avoid_: Treatment, Intervention, Operation, Service performed

### Adjuntos clínicos

**Attachment**:
Una imagen clínica tomada durante un Appointment, opcionalmente etiquetada
contra uno o más dientes. Cubre fotografías intraorales y radiografías
exportadas como imagen; no cubre DICOM.
_Avoid_: Photo, Image, File, Media

### Facturación

**Charge**:
El importe que un Procedure ejecutado genera contra un Patient. Nace del trabajo
hecho, nunca del TreatmentPlan propuesto.
_Avoid_: Fee, Item, Line item, Cargo

**Payment**:
Un abono que un Patient realiza contra su saldo. Puede ser parcial y puede no
corresponderse uno a uno con ningún Charge concreto.
_Avoid_: Transaction, Settlement, Abono

**Patient Balance**:
La diferencia entre los Charges acumulados de un Patient y sus Payments. Es lo
que el paciente debe en un momento dado.
_Avoid_: Debt, Account, Outstanding, Saldo

**TaxDocument**:
El comprobante de pago que la Clinic emite ante SUNAT por una atención, de tipo
boleta o factura, con su serie y número correlativo.
_Avoid_: Invoice, Receipt, Bill, Boleta, Factura

### Disponibilidad

**Working Hours**:
El horario semanal habitual en que un odontólogo atiende en una Clinic.
_Avoid_: Schedule, Shift, Availability, Horario

**Block**:
Una excepción puntual que retira disponibilidad a un odontólogo fuera de su
Working Hours — vacaciones, un descanso, una ausencia. Es lo que el mockup
muestra como BREAK TIME y NOT AVAILABLE.
_Avoid_: Unavailability, Timeoff, Break, Bloqueo

**No-show**:
El estado final de un Appointment al que el Patient no acudió ni canceló.
_Avoid_: Missed, Absent, Falta

### Protección de datos

**Consent**:
La aceptación por parte de un Patient de una versión concreta y fechada del
texto de consentimiento informado para el tratamiento de sus datos de salud.
_Avoid_: Agreement, Terms, Permission, Consentimiento
