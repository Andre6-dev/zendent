import { createFileRoute, useNavigate } from '@tanstack/react-router'
// Type-only, and erased at build: it is what teaches the route options about
// `server`, which TanStack Start adds by module augmentation. Without it the
// handler below is a type error rather than a route.
import type {} from '@tanstack/react-start'
import { Logo } from '#/components/layout/Logo'
import { SignInForm } from '#/components/auth/SignInForm'

export const Route = createFileRoute('/login')({
  server: {
    handlers: {
      // Imported inside the handler so the module never enters the client
      // graph: it holds the only code that touches the API's tokens.
      POST: async ({ request }) => {
        const { handleSignIn } = await import('#/server/auth-handlers')
        return handleSignIn(request)
      },
    },
  },
  component: SignInPage,
})

/**
 * The screen carries no Clinic name or branding. It cannot: naming the Clinic
 * would need a public endpoint turning a subdomain into a display name, and
 * that endpoint would confirm to anyone which Clinics exist. The address bar is
 * what tells a person where they are.
 */
function SignInPage() {
  const navigate = useNavigate()

  return (
    <main className="flex min-h-dvh items-center justify-center bg-default-50 px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex justify-center">
          <Logo />
        </div>

        <h1 className="text-center text-2xl font-semibold text-default-900">
          Sign in
        </h1>
        <p className="mt-2 text-center text-sm text-default-500">
          Use the email address your clinic registered.
        </p>

        <SignInForm onSignedIn={() => navigate({ to: '/reservations' })} />
      </div>
    </main>
  )
}
