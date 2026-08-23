import { useState } from 'react'
import {
  Button,
  FieldError,
  Form,
  Input,
  Label,
  TextField,
} from '@heroui/react'
import { credentialsSchema } from '#/features/auth/schemas'

interface SignInFormProps {
  /** Called once the session cookies are in place. */
  onSignedIn: () => void | Promise<void>
}

/** Field validation, driven by the same schema the server checks against. */
function checkField(field: 'email' | 'password') {
  return (value: string) => {
    const result = credentialsSchema.shape[field].safeParse(value)
    return result.success ? null : result.error.issues[0].message
  }
}

/**
 * The form knows nothing about tokens. It posts credentials to the
 * application's own server and, when that answers success, the session is
 * already in cookies it cannot read — so there is nothing for it to store.
 */
export function SignInForm({ onSignedIn }: SignInFormProps) {
  const [refusal, setRefusal] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function signIn(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setRefusal(null)
    setSubmitting(true)

    const submitted = Object.fromEntries(
      new FormData(event.currentTarget).entries(),
    )

    try {
      const response = await fetch('/login', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(submitted),
      })

      if (response.ok) {
        await onSignedIn()
        return
      }

      const problem = (await response.json()) as { message?: string }
      setRefusal(problem.message ?? 'We could not sign you in.')
    } catch {
      setRefusal('We could not reach the server. Check your connection.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Form onSubmit={signIn} className="mt-8 flex flex-col gap-4">
      <TextField
        name="email"
        type="email"
        isRequired
        validate={checkField('email')}
        className="w-full"
      >
        <Label>Email</Label>
        <Input autoComplete="username" autoFocus placeholder="you@clinic.com" />
        {/* Without this the field is only marked invalid to assistive
            technology; a sighted person gets a red border and no reason. */}
        <FieldError />
      </TextField>

      <TextField
        name="password"
        type="password"
        isRequired
        validate={checkField('password')}
        className="w-full"
      >
        <Label>Password</Label>
        <Input autoComplete="current-password" placeholder="Your password" />
        <FieldError />
      </TextField>

      {refusal !== null && (
        // Announced rather than merely rendered: someone using a screen reader
        // must hear the refusal without going looking for it.
        <p
          role="alert"
          className="rounded-medium bg-danger-50 px-3 py-2 text-sm text-danger-700"
        >
          {refusal}
        </p>
      )}

      <Button
        type="submit"
        variant="primary"
        isDisabled={submitting}
        className="mt-2 w-full"
      >
        {submitting ? 'Signing in…' : 'Sign in'}
      </Button>
    </Form>
  )
}
