import { expect, test } from 'vitest'
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  hasSession,
  isSecureRequest,
  readCookie,
  sessionCookies,
} from './session'

const session = {
  accessToken: 'access-token-value',
  refreshToken: 'refresh-token-value',
  expiresIn: 900,
}

test('over https the session cookies are marked secure', () => {
  for (const cookie of sessionCookies(session, true)) {
    expect(cookie).toContain('Secure')
  }
})

test('the access cookie expires with the token the API issued', () => {
  const [access] = sessionCookies(session, true)

  expect(access).toContain(`${ACCESS_COOKIE}=access-token-value`)
  expect(access).toContain('Max-Age=900')
})

test('the refresh cookie outlives the access cookie', () => {
  const [, refresh] = sessionCookies(session, true)

  expect(refresh).toContain(`${REFRESH_COOKIE}=refresh-token-value`)
  expect(refresh).toContain(`Max-Age=${30 * 24 * 60 * 60}`)
})

test('a cookie is read back from a request that carries several', () => {
  const request = new Request('http://avicena.localhost:3000/', {
    headers: {
      cookie: `theme=dark; ${ACCESS_COOKIE}=access-token-value; ${REFRESH_COOKIE}=refresh-token-value`,
    },
  })

  expect(readCookie(request, ACCESS_COOKIE)).toBe('access-token-value')
  expect(readCookie(request, REFRESH_COOKIE)).toBe('refresh-token-value')
})

test('a request with no cookies at all reads as no session', () => {
  const request = new Request('http://avicena.localhost:3000/')

  expect(readCookie(request, ACCESS_COOKIE)).toBeUndefined()
})

test('a cookie whose name merely ends the same is not mistaken for it', () => {
  const request = new Request('http://avicena.localhost:3000/', {
    headers: { cookie: `not_${ACCESS_COOKIE}=impostor` },
  })

  expect(readCookie(request, ACCESS_COOKIE)).toBeUndefined()
})

test('a proxy that terminated TLS still counts as secure', () => {
  // Production sits behind a proxy that terminates TLS, so the request arriving
  // here reads as plain http. Trusting only the URL would ship every production
  // session cookie without Secure.
  const behindProxy = new Request('http://avicena.zendent.app/login', {
    headers: { 'x-forwarded-proto': 'https' },
  })

  expect(isSecureRequest(behindProxy)).toBe(true)
  for (const cookie of sessionCookies(session, isSecureRequest(behindProxy))) {
    expect(cookie).toContain('Secure')
  }
})

test('a forwarded chain is judged by the protocol the browser used', () => {
  const chained = new Request('http://avicena.zendent.app/login', {
    headers: { 'x-forwarded-proto': 'https, http' },
  })

  expect(isSecureRequest(chained)).toBe(true)
})

test('a proxy reporting plain http is taken at its word', () => {
  const plain = new Request('http://avicena.localhost:3000/login', {
    headers: { 'x-forwarded-proto': 'http' },
  })

  expect(isSecureRequest(plain)).toBe(false)
})

test('the scheme decides whether a request counts as secure', () => {
  expect(
    isSecureRequest(new Request('https://avicena.zendent.app/login')),
  ).toBe(true)
  expect(
    isSecureRequest(new Request('http://avicena.localhost:3000/login')),
  ).toBe(false)
})

test('the access cookie is what makes a request a session', () => {
  const request = new Request('http://avicena.localhost:3000/reservations', {
    headers: { cookie: `${ACCESS_COOKIE}=access-token-value` },
  })

  expect(hasSession(request)).toBe(true)
})

test('a request holding only the refresh cookie is not a session', () => {
  // It cannot be used as a credential until renewal exists to trade it in.
  const request = new Request('http://avicena.localhost:3000/reservations', {
    headers: { cookie: `${REFRESH_COOKIE}=refresh-token-value` },
  })

  expect(hasSession(request)).toBe(false)
})

test('an access cookie emptied by a sign-out is not a session', () => {
  const request = new Request('http://avicena.localhost:3000/reservations', {
    headers: { cookie: `${ACCESS_COOKIE}=` },
  })

  expect(hasSession(request)).toBe(false)
})
