interface PlaceholderPageProps {
  title: string
  description?: string
}

/** Temporary page body for routes not yet implemented. */
export function PlaceholderPage({ title, description }: PlaceholderPageProps) {
  return (
    <div className="px-6 pt-8">
      <h1 className="text-2xl font-semibold tracking-tight text-foreground">
        {title}
      </h1>
      <p className="mt-2 max-w-prose text-sm text-muted">
        {description ?? 'This screen is coming soon.'}
      </p>
    </div>
  )
}
