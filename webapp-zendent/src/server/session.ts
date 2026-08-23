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
