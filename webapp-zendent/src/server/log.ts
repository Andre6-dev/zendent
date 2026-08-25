/**
 * Server-side logging for the BFF.
 *
 * It exists because the first real sign-in failure produced no trace anywhere:
 * the browser showed "Access denied", the API had answered correctly, and
 * nothing recorded which host the call had actually been made to — which was
 * the whole of the problem. Every line here names the clinic host, because that
 * is the one input that decides whether a request can succeed at all.
 *
 * Never log a credential, a token, or a cookie. What is worth knowing is where
 * a request went and what came back, not what it carried.
 */

export interface ApiExchange {
  method: string
  /** The API URL called, which carries the clinic host. */
  url: string
  status: number
  /** Milliseconds spent waiting on the API. */
  ms: number
}

function line(level: 'info' | 'warn', message: string): string {
  return `[zendent ${level}] ${new Date().toISOString()} ${message}`
}

export function logApiExchange(exchange: ApiExchange): void {
  const { method, url, status, ms } = exchange
  const message = `${method} ${url} -> ${status} (${ms}ms)`

  // A 4xx here is usually a caller mistake worth seeing; 5xx is ours.
  if (status >= 400) {
    console.warn(line('warn', message))
    return
  }
  console.info(line('info', message))
}

export function logRefusal(what: string, why: string): void {
  console.warn(line('warn', `${what}: ${why}`))
}
