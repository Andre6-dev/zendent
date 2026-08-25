import { afterEach, expect, test } from 'vitest'
import { apiUrlFor } from './api'

const API_PORT_VARIABLE = 'ZENDENT_API_PORT'

afterEach(() => {
  delete process.env[API_PORT_VARIABLE]
})

test('in production the API is the same host, under /api', () => {
  delete process.env[API_PORT_VARIABLE]

  // ADR 0017: one host per Clinic, routed by path. There is no `api.` host —
  // that label names no Clinic and would resolve to no tenant.
  expect(
    apiUrlFor(new Request('https://avicena.zendent.app/login'), '/auth/login'),
  ).toBe('https://avicena.zendent.app/api/auth/login')
})

test('in production the Clinic label survives into the API call', () => {
  delete process.env[API_PORT_VARIABLE]

  expect(
    apiUrlFor(new Request('https://other.zendent.app/login'), '/auth/login'),
  ).toBe('https://other.zendent.app/api/auth/login')
})

test('in development only the port changes, never the Clinic', () => {
  process.env[API_PORT_VARIABLE] = '8080'

  expect(
    apiUrlFor(
      new Request('http://avicena.localhost:3000/login'),
      '/auth/login',
    ),
  ).toBe('http://avicena.localhost:8080/auth/login')
})

test('an empty port setting is treated as production, not as no port', () => {
  process.env[API_PORT_VARIABLE] = ''

  expect(
    apiUrlFor(new Request('https://avicena.zendent.app/login'), '/auth/login'),
  ).toBe('https://avicena.zendent.app/api/auth/login')
})
