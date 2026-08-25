import { createFileRoute } from '@tanstack/react-router'
// Type-only, and erased at build: it is what teaches the route options about
// `server`, which TanStack Start adds by module augmentation.
import type {} from '@tanstack/react-start'

/**
 * Signing out is a POST and not a link, deliberately. A GET that ends a session
 * is one prefetch, one crawler, or one over-eager link preview away from
 * signing someone out who only pointed at it.
 */
export const Route = createFileRoute('/logout')({
  server: {
    handlers: {
      // Imported inside the handler so the module never enters the client
      // graph: it holds the only code that touches the API's tokens.
      POST: async ({ request }) => {
        const { handleSignOut } = await import('#/server/session-handlers')
        return handleSignOut(request)
      },
    },
  },
})
