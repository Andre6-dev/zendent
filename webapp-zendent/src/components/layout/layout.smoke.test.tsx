// @vitest-environment jsdom
import { afterEach, expect, test, vi } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import { Navbar } from './Navbar'
import { ClinicSwitcher } from './ClinicSwitcher'
import type { SignedInMember } from '#/features/auth/session'

afterEach(() => {
  // Not automatic here: vitest globals are off, so testing-library never gets
  // to register its own hook and renders would otherwise pile up across tests.
  cleanup()
})

function signedIn(
  memberName: string,
  roles: Array<string> = ['ADMIN'],
): SignedInMember {
  return { memberName, clinicName: 'Avicena Clinic', roles }
}

// react-aria relies on these browser APIs that jsdom doesn't implement.
vi.stubGlobal(
  'matchMedia',
  vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
)

test('Navbar renders without throwing (validates HeroUI compound anatomy)', () => {
  const { getByText, getByPlaceholderText } = render(
    <Navbar member={signedIn('Ada Admin')} onMenuClick={() => {}} />,
  )
  // The name is the session's now, not a name compiled into the shell.
  expect(getByText('Ada Admin')).toBeTruthy()
  expect(getByPlaceholderText('Search for anything here...')).toBeTruthy()
})

test('the role is shown from the session rather than compiled in', () => {
  const { getByText, queryByText } = render(
    <Navbar
      member={signedIn('Dee Dentist', ['DENTIST'])}
      onMenuClick={() => {}}
    />,
  )

  expect(getByText('Dentist')).toBeTruthy()
  expect(queryByText('Super admin')).toBeNull()
})

test('a member with no role at all is shown without one', () => {
  const { getByText } = render(
    <Navbar member={signedIn('Dee Dentist', [])} onMenuClick={() => {}} />,
  )

  expect(getByText('Dee Dentist')).toBeTruthy()
})

test('the navigation bar shows the signed-in member, whoever they are', () => {
  const { getByText, queryByText } = render(
    <Navbar member={signedIn('Dee Dentist')} onMenuClick={() => {}} />,
  )

  expect(getByText('Dee Dentist')).toBeTruthy()
  // The placeholder this shell shipped with is gone rather than defaulted to.
  expect(queryByText('Darrell Steward')).toBeNull()
})

test('the avatar carries the member initials', () => {
  // HeroUI's Avatar renders its fallback more than once, so the question is
  // whether the initials are there at all, not how many nodes hold them.
  const { getAllByText } = render(
    <Navbar member={signedIn('Ada Admin')} onMenuClick={() => {}} />,
  )

  expect(getAllByText('AA').length).toBeGreaterThan(0)
})

test('a one-word name gives one initial rather than a doubled one', () => {
  const { getAllByText } = render(
    <Navbar member={signedIn('Prince')} onMenuClick={() => {}} />,
  )

  expect(getAllByText('P').length).toBeGreaterThan(0)
})

test('the Clinic is named from the session rather than compiled in', () => {
  const { getByText, queryByText } = render(
    <ClinicSwitcher clinicName="Avicena Clinic" />,
  )

  expect(getByText('Avicena Clinic')).toBeTruthy()
  // The second placeholder Clinic this shell shipped with implied a session
  // could reach another Clinic's data. It cannot.
  expect(queryByText('Northside Dental')).toBeNull()
})
