/**
 * The one place the application talks to the API.
 *
 * The Clinic a request belongs to is carried by the host it arrives on, and by
 * nothing else (ADR 0008). So the call to the API is built **from the caller's
 * own hostname**: in production that lands on the same host the application is
 * served from, under the `/api` path, which is exactly the arrangement ADR 0017
 * describes — there is no `api.` host, because that label names no Clinic and
 * resolves to no tenant.
 *
 * Locally the backend listens on its own port, so only the port is overridden;
 * the Clinic label in the hostname survives untouched. Forwarding a `Host`
 * header instead does not work: it is a forbidden header, and the HTTP client
 * overwrites it with the destination's own.
 */

/** Set in development, where the API listens on a port of its own. */
const API_PORT = 'ZENDENT_API_PORT'

export function apiUrlFor(from: Request, path: string): string {
  const caller = new URL(from.url)
  const port = process.env[API_PORT]

  return port === undefined || port === ''
    ? `${caller.origin}/api${path}`
    : `${caller.protocol}//${caller.hostname}:${port}${path}`
}

export interface ApiCall {
  path: string
  method: string
  /** Already-serialised JSON, or undefined for a bodiless call. */
  body?: string
  /** The browser's request, whose host names the Clinic. */
  from: Request
  /** Sent as a bearer token when the call needs a session. */
  accessToken?: string
}

export function callApi(call: ApiCall): Promise<Response> {
  const headers = new Headers()
  if (call.body !== undefined) {
    headers.set('content-type', 'application/json')
  }
  if (call.accessToken !== undefined) {
    headers.set('authorization', `Bearer ${call.accessToken}`)
  }

  return fetch(apiUrlFor(call.from, call.path), {
    method: call.method,
    headers,
    body: call.body,
    // The API answers 401 and 404 as part of its contract; a redirect would be
    // the infrastructure talking, and following one silently would hide it.
    redirect: 'manual',
  })
}

/**
 * The `detail` of an RFC 7807 problem, which is the message the API means for a
 * person to read. Falls back to a generic line rather than inventing one, so a
 * failure never gets described two ways.
 */
export async function problemMessage(
  response: Response,
  fallback: string,
): Promise<string> {
  try {
    const problem = (await response.json()) as { detail?: unknown }
    return typeof problem.detail === 'string' && problem.detail.length > 0
      ? problem.detail
      : fallback
  } catch {
    return fallback
  }
}
