import { createServer } from 'node:http'
import type { IncomingMessage, Server, ServerResponse } from 'node:http'

/**
 * The API, stood up locally so the BFF can be driven against something that
 * answers the way the real one does.
 *
 * A stub cannot prove that the frontend and the backend agree — that is this
 * seam's known blind spot. What it proves is what the BFF does with a given
 * answer, which is the part that lives in this repository.
 *
 * It reaches the BFF through `ZENDENT_API_PORT`, the same variable a developer's
 * machine uses, so the path under test is the one that runs locally rather than
 * one invented for tests.
 */

/** One call the BFF made, as the API saw it. */
export interface Recorded {
  method: string
  /** The path called, which is what usually distinguishes one call from another. */
  path: string
  /** The host it was called on, which is what decides the Clinic. */
  host: string | undefined
  authorization: string | undefined
  body: string
}

export interface Answer {
  status: number
  body: string
}

export interface ApiStub {
  /** The port it bound to, which callers see in the host they reached it on. */
  readonly port: number
  /** Every call made so far, in order. */
  readonly received: Array<Recorded>
  /** Decides what the API answers next. Set per test. */
  answerWith: (answer: (call: Recorded) => Answer) => void
  /** Forgets what was received. */
  reset: () => void
  close: () => Promise<void>
}

function readBody(request: IncomingMessage): Promise<string> {
  return new Promise((resolve) => {
    let raw = ''
    request.on('data', (chunk) => (raw += chunk))
    request.on('end', () => resolve(raw))
  })
}

export async function startApiStub(): Promise<ApiStub> {
  const received: Array<Recorded> = []
  let answer: (call: Recorded) => Answer = () => ({ status: 200, body: '{}' })

  const server: Server = createServer(
    (request: IncomingMessage, response: ServerResponse) => {
      void readBody(request).then((body) => {
        const call: Recorded = {
          method: request.method ?? '',
          path: request.url ?? '',
          host: request.headers.host,
          authorization: request.headers.authorization,
          body,
        }
        received.push(call)

        const answered = answer(call)
        response.writeHead(answered.status, {
          'content-type': 'application/json',
        })
        response.end(answered.body)
      })
    },
  )

  // Bound on every interface: the BFF reaches the stub through the Clinic
  // hostname it was called on, and `avicena.localhost` resolves to ::1.
  await new Promise<void>((resolve) => server.listen(0, resolve))
  const address = server.address()
  if (address === null || typeof address === 'string') {
    throw new Error('the stub API did not bind to a port')
  }
  process.env.ZENDENT_API_PORT = String(address.port)

  return {
    port: address.port,
    received,
    answerWith(next) {
      answer = next
    },
    reset() {
      received.length = 0
    },
    close() {
      return new Promise<void>((resolve) => server.close(() => resolve()))
    },
  }
}

/**
 * Answers from a plan of what each path says, in order. The last answer for a
 * path repeats, so a test only has to describe the API up to the behaviour it
 * is about.
 */
export function answeringInOrder(
  plan: Record<string, Array<Answer>>,
): (call: Recorded) => Answer {
  return (call) => {
    const answers = plan[call.path] as Array<Answer> | undefined
    if (answers === undefined || answers.length === 0) {
      return { status: 404, body: '{}' }
    }
    return answers.length > 1 ? answers.shift()! : answers[0]
  }
}
