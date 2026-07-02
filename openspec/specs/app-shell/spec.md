# Spec: app-shell

Capability: `app-shell`

This spec defines the requirements for the app-shell capability: the navigable application frame (providers, sidebar, navbar, route tree) that every business screen renders inside.

## REQUIREMENTS

### Requirement: Brand Identity
The application SHALL present the brand name "Zendent" in the document title and in the sidebar logo/header area.

#### Scenario: Document title shows Zendent
- **GIVEN** any route in the app is loaded
- **WHEN** the page finishes rendering
- **THEN** the browser tab title MUST contain "Zendent"
- **AND** the root HTML document MUST declare a `lang` attribute

#### Scenario: Sidebar logo shows Zendent
- **GIVEN** the sidebar is rendered
- **WHEN** a user looks at the sidebar header
- **THEN** the sidebar MUST display the "Zendent" brand name/logo

### Requirement: Themed Interactive Component Library
HeroUI-based components used across the shell MUST render with the Zendent theme applied and MUST be interactive (clickable, focusable, keyboard operable) without runtime errors. The specific provider/wiring mechanism is a design decision, not a spec constraint.

#### Scenario: HeroUI components render themed and interactive
- **GIVEN** the app shell has loaded in the browser
- **WHEN** a user interacts with any HeroUI-based control (e.g. dropdown, button, toggle) in the sidebar, navbar, or ClinicSwitcher
- **THEN** the control MUST render with the Zendent visual theme (brand colors, radii, shadows as defined by the theme tokens)
- **AND** the control MUST respond to the interaction (open, toggle, focus) without throwing a runtime or console error

### Requirement: Root Redirect to Reservations
Navigating to the root path SHALL redirect the user to the Reservations screen, since Reservations is the only fully functional business screen in this phase.

#### Scenario: Root path redirects
- **GIVEN** a user opens the app at `/`
- **WHEN** the app finishes loading
- **THEN** the browser URL MUST become `/reservations`
- **AND** the Reservations screen MUST be rendered

### Requirement: Grouped Sidebar Navigation
The sidebar SHALL present navigation items grouped under labeled sections matching the product's information architecture, plus two ungrouped items pinned at the bottom.

#### Scenario: Sidebar groups and items are present
- **GIVEN** the sidebar is rendered
- **WHEN** a user reads the sidebar structure
- **THEN** a group labeled "CLINIC" MUST contain items: Dashboard, Reservations, Patients, Treatments, Staff List
- **AND** a group labeled "FINANCE" MUST contain items: Accounts, Sales, Purchases, Payment Method
- **AND** a group labeled "PHYSICAL ASSET" MUST contain items: Stocks, Peripherals
- **AND** the sidebar MUST also present "Report" and "Customer Support" items outside the three groups

#### Scenario: Active sidebar item is highlighted
- **GIVEN** a user is on any route reachable from the sidebar
- **WHEN** the sidebar renders
- **THEN** the sidebar item corresponding to the current route MUST be visually marked as active (distinct from inactive items)

#### Scenario: Sidebar adapts to mobile viewport
- **GIVEN** the app is viewed on a mobile-sized viewport
- **WHEN** the layout renders
- **THEN** the sidebar MUST collapse or hide behind a drawer/toggle control
- **AND** a user MUST be able to reveal and dismiss it without navigating away from the current route

### Requirement: Navbar Controls
The navbar SHALL present a global search field, a placeholder create ("+") action, a notifications indicator, and a profile control.

#### Scenario: Global search field is present
- **GIVEN** the navbar is rendered
- **WHEN** a user looks at the navbar
- **THEN** a search input MUST be present and focusable
- **AND** typing into it MUST NOT navigate away or throw an error (search execution itself is out of scope for this phase)

#### Scenario: Placeholder create action is present
- **GIVEN** the navbar is rendered
- **WHEN** a user clicks the "+" control
- **THEN** the control MUST respond visually (e.g. focus/press state)
- **AND** it MUST NOT perform any create/persist action (no backend exists)

#### Scenario: Notifications indicator shows a badge
- **GIVEN** the navbar is rendered
- **WHEN** a user looks at the notifications icon
- **THEN** a badge MUST be visible indicating a non-zero notification count

#### Scenario: Profile control is present
- **GIVEN** the navbar is rendered
- **WHEN** a user looks at the navbar
- **THEN** a profile control (avatar and/or name) MUST be present

### Requirement: Static Clinic Switcher
The sidebar SHALL present a `ClinicSwitcher` control displaying a fixed clinic name, with no functional switching behavior in this phase.

#### Scenario: Clinic switcher displays fixed clinic
- **GIVEN** the sidebar is rendered
- **WHEN** a user looks at the clinic switcher control
- **THEN** it MUST display "Avicena Clinic"
- **AND** interacting with it MAY show a visual affordance (e.g. dropdown chevron) but MUST NOT change the displayed clinic or navigate

### Requirement: Complete Typed Route Tree
Every sidebar destination SHALL resolve to a real, typed route. Routes other than Reservations SHALL render a "Coming soon" placeholder screen.

#### Scenario: Every sidebar item routes somewhere real
- **GIVEN** the full sidebar item list (CLINIC, FINANCE, PHYSICAL ASSET groups plus Report and Customer Support)
- **WHEN** a user clicks any sidebar item
- **THEN** the app MUST navigate to a distinct, defined route for that item
- **AND** the route MUST NOT be a 404 or unmatched-route fallback

#### Scenario: Non-Reservations routes show a placeholder
- **GIVEN** a user navigates to any sidebar destination other than Reservations
- **WHEN** the route renders
- **THEN** the screen MUST display a "Coming soon" placeholder message
- **AND** the screen MUST NOT display or imply real business data

#### Scenario: Reservations route is fully functional
- **GIVEN** a user navigates to the Reservations item
- **WHEN** the route renders
- **THEN** the screen MUST render the Reservations calendar experience defined in the `reservations-calendar` capability, not a placeholder

### Requirement: Hydration-Safe Shell Rendering
The app shell SHALL render without SSR/client hydration mismatches.

#### Scenario: No hydration errors on initial load
- **GIVEN** a user performs a fresh (non-cached) load of any route
- **WHEN** the server-rendered HTML hydrates on the client
- **THEN** no hydration mismatch warnings or errors MUST appear in the browser console
