# Spec: reservations-mock-data

Capability: `reservations-mock-data`

This spec defines the requirements for the mock data layer backing the Reservations calendar: Zod schemas as the source-of-truth data shape, plus faker-based generators. This layer MUST be reusable, unchanged in shape, when a later change swaps mock data for a real API.

## REQUIREMENTS

### Requirement: Dentist Schema
A `Dentist` Zod schema SHALL define the shape of a dentist resource shown as a calendar column.

#### Scenario: Dentist schema validates a well-formed record
- **GIVEN** a candidate object with `id`, `fullName`, `title`, `avatarUrl`, `todayAppointmentCount`, and `available`
- **WHEN** the object is parsed against the `Dentist` schema
- **THEN** parsing MUST succeed
- **AND** the parsed value's static type MUST be usable as `Dentist` throughout the codebase

#### Scenario: Dentist schema rejects malformed records
- **GIVEN** a candidate object missing a required field (e.g. `id`) or with a wrong-typed field (e.g. `todayAppointmentCount` as a string)
- **WHEN** the object is parsed against the `Dentist` schema
- **THEN** parsing MUST fail with a validation error

### Requirement: Appointment Status Enum
An `AppointmentStatus` Zod enum SHALL restrict appointment status values to exactly four states.

#### Scenario: Appointment status accepts the four defined values
- **GIVEN** each of the values `registered`, `encounter`, `finished`, and `waiting_payment`
- **WHEN** each value is parsed against the `AppointmentStatus` schema
- **THEN** parsing MUST succeed for all four

#### Scenario: Appointment status rejects values outside the enum
- **GIVEN** a value not in the defined set (e.g. `cancelled`)
- **WHEN** the value is parsed against the `AppointmentStatus` schema
- **THEN** parsing MUST fail with a validation error

### Requirement: Appointment Schema
An `Appointment` Zod schema SHALL define the shape of a scheduled appointment, including its dentist reference, patient, treatment, time range, and status.

#### Scenario: Appointment schema validates a well-formed record
- **GIVEN** a candidate object with `id`, `dentistId`, `patientName`, `treatmentLabel`, `start` (ISO datetime string), `end` (ISO datetime string), and `status` (a valid `AppointmentStatus`)
- **WHEN** the object is parsed against the `Appointment` schema
- **THEN** parsing MUST succeed

#### Scenario: Appointment schema rejects malformed records
- **GIVEN** a candidate object with an invalid `status` value or a non-ISO-formatted `start`/`end`
- **WHEN** the object is parsed against the `Appointment` schema
- **THEN** parsing MUST fail with a validation error

### Requirement: Time Block Schema
A `TimeBlock` Zod schema SHALL define the shape of a break or unavailability block, optionally scoped to a specific dentist.

#### Scenario: Time block schema validates a dentist-scoped break
- **GIVEN** a candidate object with `dentistId` set to a dentist id, `kind: 'break'`, `start`, `end`, and `label`
- **WHEN** the object is parsed against the `TimeBlock` schema
- **THEN** parsing MUST succeed

#### Scenario: Time block schema validates a column-wide unavailability
- **GIVEN** a candidate object with `dentistId: null`, `kind: 'unavailable'`, `start`, `end`, and `label`
- **WHEN** the object is parsed against the `TimeBlock` schema
- **THEN** parsing MUST succeed

#### Scenario: Time block schema rejects an invalid kind
- **GIVEN** a candidate object with `kind` set to a value other than `'break'` or `'unavailable'`
- **WHEN** the object is parsed against the `TimeBlock` schema
- **THEN** parsing MUST fail with a validation error

### Requirement: Dentist Generator
A `getDentists()` function SHALL return a mock list of 3 to 5 dentists that validate against the `Dentist` schema.

#### Scenario: Generator returns a valid, correctly sized list
- **GIVEN** `getDentists()` is called
- **WHEN** the result is inspected
- **THEN** the result MUST contain between 3 and 5 entries inclusive
- **AND** every entry MUST successfully validate against the `Dentist` schema
- **AND** every entry MUST have a unique `id`

### Requirement: Reservations Generator For A Date
A `getReservationsForDate(date)` function SHALL return mock appointments and time blocks for the given date that validate against their respective schemas, with enough variety to exercise every visual state of the calendar.

#### Scenario: Generator returns a valid, correctly sized appointment set
- **GIVEN** `getReservationsForDate(date)` is called for a given date
- **WHEN** the result's appointments are inspected
- **THEN** the result MUST contain between 12 and 16 appointments
- **AND** every appointment MUST successfully validate against the `Appointment` schema
- **AND** every appointment's `start` and `end` MUST fall within the 9am to 5pm window of the given date
- **AND** every appointment MUST reference a `dentistId` present in `getDentists()`

#### Scenario: Generator produces varied appointment statuses
- **GIVEN** `getReservationsForDate(date)` is called
- **WHEN** the returned appointments' `status` values are inspected
- **THEN** at least two distinct `AppointmentStatus` values MUST be present across the returned set

#### Scenario: Generator includes one break block
- **GIVEN** `getReservationsForDate(date)` is called
- **WHEN** the result's time blocks are inspected
- **THEN** at least one `TimeBlock` with `kind: 'break'` MUST be present at or around 1pm on the given date
- **AND** it MUST successfully validate against the `TimeBlock` schema

#### Scenario: Generator includes one not-available column
- **GIVEN** `getReservationsForDate(date)` is called
- **WHEN** the result's time blocks are inspected
- **THEN** at least one `TimeBlock` with `kind: 'unavailable'` MUST be present, scoped to a single dentist column
- **AND** it MUST successfully validate against the `TimeBlock` schema

#### Scenario: Generator output is deterministically shaped across calls
- **GIVEN** `getReservationsForDate(date)` is called twice for the same date
- **WHEN** both results are inspected
- **THEN** both results MUST independently satisfy all schema and count constraints above (exact appointment content MAY differ between calls, but structural validity and cardinality MUST NOT)
