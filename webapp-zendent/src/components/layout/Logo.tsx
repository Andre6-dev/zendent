import { cn } from '#/lib/cn'

interface LogoProps {
  className?: string
  showWordmark?: boolean
}

/** Zendenta brand mark: an accent tile with a tooth glyph, optionally with wordmark. */
export function Logo({ className, showWordmark = true }: LogoProps) {
  return (
    <div className={cn('flex items-center gap-2', className)}>
      <span className="flex size-8 items-center justify-center rounded-lg bg-accent text-accent-foreground">
        <svg
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
          className="size-5"
        >
          <path d="M17.5 2.5c-1.6 0-2.7.7-4 1.3-.6.3-1 .4-1.5.4s-.9-.1-1.5-.4c-1.3-.6-2.4-1.3-4-1.3C3.4 2.5 2 4.3 2 7.4c0 2.3.6 4.1 1.2 6.3.3 1.1.5 2.2.7 3.6.3 2 .6 4.2 2.1 4.2 1.4 0 1.7-1.8 2-3.6.3-1.6.6-3.4 2-3.4s1.7 1.8 2 3.4c.3 1.8.6 3.6 2 3.6 1.5 0 1.8-2.2 2.1-4.2.2-1.4.4-2.5.7-3.6.6-2.2 1.2-4 1.2-6.3 0-3.1-1.4-4.9-3-4.9Z" />
        </svg>
      </span>
      {showWordmark && (
        <span className="text-lg font-semibold tracking-tight text-foreground">
          Zendenta
        </span>
      )}
    </div>
  )
}
