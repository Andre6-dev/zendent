/**
 * The session lives here and nowhere else. Both tokens stay on the server side
 * of the application, carried between it and the browser as cookies the
 * browser's own scripts cannot read (ADR 0017).
 */

export const ACCESS_COOKIE = 'zendent_access'
export const REFRESH_COOKIE = 'zendent_refresh'

/** The refresh token's own lifetime in the API, in seconds. */
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

export function isSecureRequest(request: Request): boolean {
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
