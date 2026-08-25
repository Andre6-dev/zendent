import { useEffect, useState } from 'react'
import { Sidebar } from './Sidebar'
import { Navbar } from './Navbar'
import type { SignedInMember } from '#/features/auth/session'

interface AppLayoutProps {
  /** Who is signed in, and the Clinic they are signed in to. */
  member: SignedInMember
  children: React.ReactNode
}

/** Shell with a persistent sidebar (desktop) / drawer (mobile) and a top navbar. */
export function AppLayout({ member, children }: AppLayoutProps) {
  const [mobileOpen, setMobileOpen] = useState(false)

  useEffect(() => {
    if (!mobileOpen) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMobileOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [mobileOpen])

  return (
    <div className="flex h-screen overflow-hidden bg-background text-foreground">
      {/* Desktop sidebar */}
      <div className="hidden lg:block">
        <Sidebar member={member} />
      </div>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            aria-label="Close menu"
            className="absolute inset-0 bg-overlay/40"
            onClick={() => setMobileOpen(false)}
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-label="Navigation"
            className="absolute inset-y-0 left-0 animate-in slide-in-from-left-4 duration-200"
          >
            <Sidebar member={member} onNavigate={() => setMobileOpen(false)} />
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <Navbar member={member} onMenuClick={() => setMobileOpen(true)} />
        <main className="flex-1 overflow-y-auto">{children}</main>
      </div>
    </div>
  )
}
