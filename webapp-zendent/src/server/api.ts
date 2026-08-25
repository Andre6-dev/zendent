import { logApiExchange, logRefusal } from './log'

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

/**
 * Name of the variable set in development, where the API listens on a port of
 * its own. Holds the variable's name, not a port.
 */
const API_PORT_VARIABLE = 'ZENDENT_API_PORT'

export function apiUrlFor(from: Request, path: string): string {
  const caller = new URL(from.url)
  const port = process.env[API_PORT_VARIABLE]

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
}

export async function callApi(call: ApiCall): Promise<Response> {
  const headers = new Headers()
  if (call.body !== undefined) {
    headers.set('content-type', 'application/json')
  }

  const url = apiUrlFor(call.from, call.path)
  const startedAt = Date.now()

  try {
    const response = await fetch(url, {
      method: call.method,
      headers,
      body: call.body,
      // The API answers 401 and 404 as part of its contract; a redirect would
      // be the infrastructure talking, and following one silently would hide it.
      redirect: 'manual',
    })

    // Logged with the URL, because the host in it is what decides the Clinic —
    // and calling the wrong host is the failure that leaves no other trace.
    logApiExchange({
      method: call.method,
      url,
      status: response.status,
      ms: Date.now() - startedAt,
    })
    return response
  } catch (cause) {
    logRefusal(
      `${call.method} ${url} could not be reached`,
      cause instanceof Error ? cause.message : String(cause),
    )
    throw cause
  }
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
