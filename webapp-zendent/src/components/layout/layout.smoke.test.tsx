// @vitest-environment jsdom
import { expect, test, vi } from 'vitest'
import { render } from '@testing-library/react'
import { Navbar } from './Navbar'

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
    <Navbar onMenuClick={() => {}} />,
  )
  expect(getByText('Darrell Steward')).toBeTruthy()
  expect(getByPlaceholderText('Search for anything here...')).toBeTruthy()
})
