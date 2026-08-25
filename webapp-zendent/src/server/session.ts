import { z } from 'zod'

/**
 * The session lives here and nowhere else. Both tokens stay on the server side
 * of the application, carried between it and the browser as cookies the
 * browser's own scripts cannot read (ADR 0017).
 */

export const ACCESS_COOKIE = 'zendent_access'
export const REFRESH_COOKIE = 'zendent_refresh'

/**
 * Mirrors the API's own `zendent.jwt.refresh-token-ttl` (`P30D` in
 * `api/src/main/resources/application.yaml`). The sign-in response reports how
 * long the *access* token lasts but says nothing about the refresh token, so
 * this one is copied rather than read. Change it there, change it here.
 */
const REFRESH_MAX_AGE_SECONDS = 30 * 24 * 60 * 60

/**
 * What the API answers with when it issues a session — on sign-in and on
 * renewal alike, which is why the shape is described here rather than beside
 * either one of them. Parsed rather than trusted: a session is the one thing
 * worth refusing to build out of a body we did not recognise.
 */
export const apiSessionSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.string(),
  expiresIn: z.number().int().positive(),
  refreshToken: z.string().min(1),
})

/**
 * The part of that answer the cookies are built from.
 *
 * Narrower than the schema on purpose: `tokenType` is the API telling us how to
 * present the token, not part of the session itself, and asking for it here
 * would make every caller carry a field this file never reads.
 */
export interface ApiSession {
  accessToken: string
  refreshToken: string
  /** Seconds the access token remains valid, as the API reports it. */
  expiresIn: number
}

function cookie(
  name: string,
  value: string,
  maxAgeSeconds: number,
  secure: boolean,
): string {
  const attributes = [
    `${name}=${value}`,
    'Path=/',
    'HttpOnly',
    // Lax rather than Strict: a person following the link in a password-reset
    // email arrives cross-site, and Strict would hand them a signed-out app.
    'SameSite=Lax',
    `Max-Age=${maxAgeSeconds}`,
  ]
  // Marked Secure over https only. Setting it unconditionally would stop the
  // browser storing the cookie at all on a developer's plain-http machine.
  if (secure) {
    attributes.push('Secure')
  }
  return attributes.join('; ')
}

/**
 * Whether the browser reached us over https — which decides whether the session
 * cookies may be marked `Secure`.
 *
 * The request's own URL is not enough. In production this application sits
 * behind a proxy that terminates TLS (ADR 0017: one host, routed by path), so
 * the request arriving here reads as plain http even though the browser used
 * https. Trusting only the URL would ship every production session cookie
 * without `Secure` — the exact failure this function exists to prevent — so
 * the proxy's own `X-Forwarded-Proto` is what settles it when present.
 */
export function isSecureRequest(request: Request): boolean {
  const forwarded = request.headers.get('x-forwarded-proto')
  if (forwarded !== null && forwarded.length > 0) {
    // A proxy may forward a chain: the client's own protocol is the first.
    return forwarded.split(',')[0].trim().toLowerCase() === 'https'
  }
  return new URL(request.url).protocol === 'https:'
}

/** The `Set-Cookie` values that put a freshly issued session in the browser. */
export function sessionCookies(
  session: ApiSession,
  secure: boolean,
): Array<string> {
  return [
    cookie(ACCESS_COOKIE, session.accessToken, session.expiresIn, secure),
    cookie(
      REFRESH_COOKIE,
      session.refreshToken,
      REFRESH_MAX_AGE_SECONDS,
      secure,
    ),
  ]
}

/**
 * The `Set-Cookie` values that take the session back out of the browser.
 *
 * Same name, same path, same attributes — a cookie is only replaced by one that
 * matches how it was set, so these have to mirror `sessionCookies` rather than
 * merely mention the same two names.
 */
export function clearedSessionCookies(secure: boolean): Array<string> {
  return [
    cookie(ACCESS_COOKIE, '', 0, secure),
    cookie(REFRESH_COOKIE, '', 0, secure),
  ]
}

export function readCookie(request: Request, name: string): string | undefined {
  const header = request.headers.get('cookie')
  if (header === null) {
    return undefined
  }

  for (const part of header.split(';')) {
    const separator = part.indexOf('=')
    if (separator !== -1 && part.slice(0, separator).trim() === name) {
      return part.slice(separator + 1).trim()
    }
  }
  return undefined
}

/**
 * Whether a request arrives with a session.
 *
 * The access cookie is what decides it. The refresh cookie outlives it and is
 * deliberately not consulted here: a request holding only that one has no
 * usable credential yet, and turning it into one is renewal's job — see
 * `renewOnce` in `session-calls.ts`, which is what such a request goes to.
 *
 * What this answers is presence, not validity. Whether the token is still good
 * is the API's to say, and it says so on every call the screens make; asking it
 * here would put a network round trip in front of every document. So an expired
 * or forged cookie gets through this door and is refused at the next one, where
 * renewal takes over.
 *
 * A `false` here does not mean signed out. The access cookie expires with the
 * token it carries, so it is gone from a browser that still holds a refresh
 * cookie good for a month — which is a session to be renewed, not a person to
 * turn away. Callers ask this to find out whether a request can be made as it
 * stands, never to decide whether someone may stay.
 */
export function hasSession(request: Request): boolean {
  const token = readCookie(request, ACCESS_COOKIE)
  return token !== undefined && token.length > 0
}
