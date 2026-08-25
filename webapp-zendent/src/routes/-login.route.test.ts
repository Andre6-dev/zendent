import { expect, test, vi } from 'vitest'

/**
 * The handler itself is covered in `src/server/auth-handlers.test.ts`. What is
 * covered here is the wiring: that `POST /login` actually reaches it. Without
 * this, a typo in the route's server block passes every other test green and
 * fails only in a browser.
 */

const handleSignIn = vi.fn()
vi.mock('#/server/auth-handlers', () => ({ handleSignIn }))

type MethodHandlers = Record<
  string,
  (ctx: { request: Request }) => Promise<Response>
>

/**
 * `handlers` is declared as either a record of methods or a factory returning
 * one, so reaching a method needs narrowing the framework's own generics away.
 */
async function methodHandlers(): Promise<MethodHandlers> {
  const { Route } = await import('./login')
  const handlers = Route.options.server?.handlers

  if (typeof handlers !== 'object') {
    throw new Error('the login route declares no method handlers')
  }
  return handlers as MethodHandlers
}

test('the login route answers POST by delegating to the sign-in handler', async () => {
  const handlers = await methodHandlers()
  expect(handlers.POST).toBeTypeOf('function')

  const request = new Request('http://avicena.localhost:3000/login', {
    method: 'POST',
  })
  const answered = new Response(null, { status: 204 })
  handleSignIn.mockResolvedValue(answered)

  const result = await handlers.POST({ request })

  expect(handleSignIn).toHaveBeenCalledWith(request)
  expect(result).toBe(answered)
})

test('the login route defines no other method', async () => {
  // GET must fall through to the page component; defining it here would stop
  // the sign-in screen ever rendering.
  expect(Object.keys(await methodHandlers())).toEqual(['POST'])
})
