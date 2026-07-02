# Spec Delta: reservations-calendar

Capability: `reservations-calendar`
Change: `frontend-layout-reservations`

This delta defines the requirements for the Reservations screen: a
resource-style Day-view calendar (dentists x hours) built entirely against
mock data, with no backend and no create/edit persistence.

## ADDED Requirements

### Requirement: Reservations Screen Tabs
The Reservations screen SHALL present Calendar and Log History tabs, with
Log History as a non-functional placeholder in this phase.

#### Scenario: Calendar tab is selected by default
- **GIVEN** a user navigates to `/reservations`
- **WHEN** the screen renders
- **THEN** the Calendar tab MUST be active by default
- **AND** the resource calendar MUST be visible

#### Scenario: Log History tab is a placeholder
- **GIVEN** a user is on the Reservations screen
- **WHEN** the user selects the Log History tab
- **THEN** a placeholder MUST be shown
- **AND** no real log data MUST be displayed

### Requirement: Calendar Toolbar Controls
The Reservations screen SHALL present a toolbar above the calendar with an
appointment count, date navigation, a Day/Week toggle with Week inactive, an
All-Dentist filter, and a Filters button.

#### Scenario: Appointment count reflects the displayed day
- **GIVEN** the calendar is showing appointments for a given date
- **WHEN** the toolbar renders
- **THEN** it MUST display a count of total appointments for that date
- **AND** the count MUST equal the number of `Appointment` items rendered for
  that date after any active dentist filter is NOT yet applied (i.e. it
  reflects the unfiltered day total)

#### Scenario: Today control jumps to the current date
- **GIVEN** the calendar is showing a date other than today
- **WHEN** a user clicks the "Today" control
- **THEN** the calendar MUST display the current date's appointments
- **AND** the date label MUST update to reflect today's date

#### Scenario: Previous/next controls navigate by day
- **GIVEN** the calendar is showing a given date
- **WHEN** a user clicks the previous or next control
- **THEN** the calendar MUST display the prior or following day respectively
- **AND** the date label MUST update accordingly

#### Scenario: Date label reflects the active date
- **GIVEN** the calendar is showing a given date
- **WHEN** the toolbar renders
- **THEN** the date label MUST display that date in a human-readable format

#### Scenario: Day/Week toggle shows Week as inactive
- **GIVEN** the toolbar renders
- **WHEN** a user looks at the Day/Week toggle
- **THEN** "Day" MUST be shown as the active/selected option
- **AND** "Week" MUST be visibly present but disabled or otherwise
  non-selectable
- **AND** clicking "Week" MUST NOT change the displayed calendar view

#### Scenario: All-Dentist filter is present
- **GIVEN** the toolbar renders
- **WHEN** a user opens the All-Dentist filter control
- **THEN** a list of dentists (from mock data) MUST be selectable
- **AND** a "All Dentist" option MUST be available to clear the filter

#### Scenario: Filters button is present
- **GIVEN** the toolbar renders
- **WHEN** a user looks at the toolbar
- **THEN** a "Filters" button MUST be present and clickable without error
  (opening a filter panel's contents is not further specified in this phase)

### Requirement: Resource Calendar Grid
The calendar SHALL render a fixed time axis column and one column per
dentist, showing appointments, breaks, and unavailability positioned by
their time range.

#### Scenario: Time axis column is present
- **GIVEN** the calendar grid renders
- **WHEN** a user looks at the leftmost column
- **THEN** a time axis MUST be shown with hour labels covering the business
  hours range (9am through 5pm at minimum)

#### Scenario: One column per dentist with header
- **GIVEN** mock data returns N dentists for the active date
- **WHEN** the calendar grid renders
- **THEN** exactly N dentist columns MUST be rendered (before any dentist
  filter is applied)
- **AND** each column header MUST display the dentist's name and their
  today's appointment count

#### Scenario: Appointment card reflects appointment data
- **GIVEN** an `Appointment` exists for a dentist with a given `start`,
  `end`, `patientName`, `treatmentLabel`, and `status`
- **WHEN** the calendar grid renders that dentist's column
- **THEN** an appointment card MUST appear positioned and sized according to
  `start`/`end` relative to the time axis
- **AND** the card MUST display the patient name, the time range, a
  treatment chip, and a status badge
- **AND** the card's color MUST visually distinguish between the four
  statuses (`registered`, `encounter`, `finished`, `waiting_payment`)

#### Scenario: Break time block is rendered
- **GIVEN** a `TimeBlock` of kind `break` exists for a dentist
- **WHEN** the calendar grid renders that dentist's column
- **THEN** a visually distinct "BREAK TIME" block MUST appear at the
  block's time range
- **AND** the block MUST NOT be a clickable/schedulable appointment slot

#### Scenario: Not-available block is rendered
- **GIVEN** a `TimeBlock` of kind `unavailable` exists for a dentist (or
  applies to a whole column)
- **WHEN** the calendar grid renders that dentist's column
- **THEN** a visually distinct "NOT AVAILABLE" block MUST appear (e.g.
  striped/hatched pattern) covering that time range
- **AND** the block MUST NOT be a clickable/schedulable appointment slot

#### Scenario: Empty cell shows a non-functional placeholder
- **GIVEN** a time cell in a dentist column has no appointment, break, or
  unavailable block
- **WHEN** a user hovers over that empty cell
- **THEN** a "+" placeholder MUST appear
- **AND** clicking it MUST NOT create an appointment or trigger any action
  (no functional create flow exists in this phase)

### Requirement: Now Indicator
The calendar SHALL display a live indicator of the current time when the
displayed date is today.

#### Scenario: Now indicator shows on today's view
- **GIVEN** the calendar is displaying today's date
- **WHEN** the grid renders
- **THEN** a red horizontal line MUST appear positioned at the current time
  on the time axis
- **AND** the line MUST include a label showing the current time

#### Scenario: Now indicator does not show on non-today views
- **GIVEN** the calendar is displaying a date other than today
- **WHEN** the grid renders
- **THEN** the now indicator MUST NOT be shown

### Requirement: Client-Side Dentist Filter
Selecting a dentist in the All-Dentist filter SHALL narrow the displayed
columns without any server round-trip.

#### Scenario: Selecting a dentist narrows the grid
- **GIVEN** the calendar is showing all dentist columns
- **WHEN** a user selects a specific dentist in the filter
- **THEN** only that dentist's column MUST remain visible
- **AND** the appointment count in the toolbar MAY update to reflect only
  the filtered dentist's appointments
- **AND** no network request MUST be made to apply the filter

#### Scenario: Clearing the filter restores all columns
- **GIVEN** a dentist filter is active
- **WHEN** a user selects "All Dentist"
- **THEN** all dentist columns MUST be visible again

### Requirement: Hydration-Safe Time-Dependent Rendering
Components that depend on the client's current date/time (the calendar
grid and now indicator) SHALL render without SSR/client hydration
mismatches.

#### Scenario: No hydration errors from time-dependent rendering
- **GIVEN** a user performs a fresh (non-cached) load of `/reservations`
- **WHEN** the server-rendered HTML hydrates on the client
- **THEN** no hydration mismatch warnings or errors MUST appear in the
  browser console
- **AND** the now indicator and grid MUST settle to the correct client-time
  position after hydration
