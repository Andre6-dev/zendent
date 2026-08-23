import { z } from 'zod'

/**
 * Shared by the sign-in form and the handler behind it, so the browser and the
 * server never disagree about what a complete submission looks like. The form
 * catching a mistake early is a courtesy; the handler checking again is the
 * rule, because nothing stops a caller skipping the form.
 */
export const credentialsSchema = z.object({
  email: z.email('Enter a valid email address.'),
  password: z.string().min(1, 'Enter your password.'),
})

export type Credentials = z.infer<typeof credentialsSchema>
