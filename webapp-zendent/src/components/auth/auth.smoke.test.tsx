// @vitest-environment jsdom
import { afterEach, expect, test, vi } from 'vitest'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { SignInForm } from './SignInForm'

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

afterEach(() => {
  // Not automatic here: vitest globals are off, so testing-library never gets
  // to register its own hook and renders would otherwise pile up across tests.
  cleanup()
  vi.restoreAllMocks()
})

function stubFetch(response: Response) {
  const fetchStub = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchStub)
  return fetchStub
}

function field(label: string): HTMLElement {
  // Scoped to the input: react-aria labels the field wrapper as well, so an
  // unscoped lookup matches two elements.
  return screen.getByLabelText(label, { selector: 'input' })
}

function fillIn(label: string, value: string) {
  fireEvent.change(field(label), { target: { value } })
}

function submit() {
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
}

test('the form renders its labelled fields', () => {
  render(<SignInForm onSignedIn={() => {}} />)

  expect(field('Email')).toBeTruthy()
  expect(field('Password')).toBeTruthy()
  expect(screen.getByRole('button', { name: 'Sign in' })).toBeTruthy()
})

test('an address that is not an address never reaches the server', async () => {
  const fetchStub = stubFetch(new Response(null, { status: 204 }))
  render(<SignInForm onSignedIn={() => {}} />)

  fillIn('Email', 'not-an-address')
  fillIn('Password', 'whatever')
  submit()

  await waitFor(() =>
    expect(field('Email').getAttribute('aria-invalid')).toBe('true'),
  )
  expect(screen.getByText('Enter a valid email address.')).toBeTruthy()
  expect(fetchStub).not.toHaveBeenCalled()
})

test('a missing password never reaches the server', async () => {
  const fetchStub = stubFetch(new Response(null, { status: 204 }))
  render(<SignInForm onSignedIn={() => {}} />)

  fillIn('Email', 'drg.soap@avicena.test')
  submit()

  // The message for an empty required field comes from the browser and reads
  // differently in each one, so only the refusal itself is asserted here.
  await waitFor(() =>
    expect(field('Password').getAttribute('aria-invalid')).toBe('true'),
  )
  expect(fetchStub).not.toHaveBeenCalled()
})

test('a complete submission is sent and signs the person in', async () => {
  const fetchStub = stubFetch(new Response(null, { status: 204 }))
  const onSignedIn = vi.fn()
  render(<SignInForm onSignedIn={onSignedIn} />)

  fillIn('Email', 'drg.soap@avicena.test')
  fillIn('Password', 'correct-horse')
  submit()

  await waitFor(() => expect(onSignedIn).toHaveBeenCalledOnce())

  const [url, init] = fetchStub.mock.calls[0] as [string, RequestInit]
  expect(url).toBe('/login')
  expect(init.method).toBe('POST')
  expect(JSON.parse(init.body as string)).toEqual({
    email: 'drg.soap@avicena.test',
    password: 'correct-horse',
  })
})

test("the server's refusal is shown as it was written, and no more", async () => {
  stubFetch(
    new Response(JSON.stringify({ message: 'Invalid credentials' }), {
      status: 401,
      headers: { 'content-type': 'application/json' },
    }),
  )
  const onSignedIn = vi.fn()
  render(<SignInForm onSignedIn={onSignedIn} />)

  fillIn('Email', 'drg.soap@avicena.test')
  fillIn('Password', 'wrong')
  submit()

  const alert = await screen.findByRole('alert')
  expect(alert.textContent).toBe('Invalid credentials')
  // The screen must not helpfully narrow what the server deliberately kept vague.
  expect(alert.textContent).not.toMatch(/password|email|user/i)
  expect(onSignedIn).not.toHaveBeenCalled()
})

test('an unreachable server is explained rather than swallowed', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
  render(<SignInForm onSignedIn={() => {}} />)

  fillIn('Email', 'drg.soap@avicena.test')
  fillIn('Password', 'correct-horse')
  submit()

  const alert = await screen.findByRole('alert')
  expect(alert.textContent).toContain('We could not reach the server.')
})
