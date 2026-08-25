import { Link } from '@tanstack/react-router'
import { ScrollShadow } from '@heroui/react'
import { Logo } from './Logo'
import { ClinicSwitcher } from './ClinicSwitcher'
import { navFooterItems, navGroups } from './nav-items'
import type { NavItem } from './nav-items'
import type { SignedInMember } from '#/features/auth/session'

interface SidebarProps {
  /** Who is signed in, and the Clinic they are signed in to. */
  member: SignedInMember
  /** Called after a nav link is activated (used to close the mobile drawer). */
  onNavigate?: () => void
}

function SidebarLink({
  item,
  onNavigate,
}: {
  item: NavItem
  onNavigate?: () => void
}) {
  const Icon = item.icon
  return (
    <Link
      to={item.to}
      onClick={onNavigate}
      className="flex h-10 cursor-[var(--cursor-interactive)] items-center gap-3 rounded-lg px-3 text-sm font-medium text-muted hover:bg-surface-secondary hover:text-foreground [&.active]:bg-surface [&.active]:text-foreground [&.active]:shadow-surface"
    >
      <Icon className="size-[18px] shrink-0" />
      <span className="truncate">{item.label}</span>
    </Link>
  )
}

export function Sidebar({ member, onNavigate }: SidebarProps) {
  return (
    <aside className="flex h-full w-64 flex-col border-r border-separator bg-background">
      <div className="flex h-16 items-center px-5">
        <Logo />
      </div>

      <div className="px-3 pb-2">
        <ClinicSwitcher clinicName={member.clinicName} />
      </div>

      <ScrollShadow className="flex-1 overflow-y-auto px-3 py-2">
        <nav className="flex flex-col gap-5">
          {navGroups.map((group, index) => (
            <div
              key={group.label ?? `group-${index}`}
              className="flex flex-col gap-1"
            >
              {group.label && (
                <p className="px-3 pb-1 text-xs font-medium uppercase tracking-wide text-muted/70">
                  {group.label}
                </p>
              )}
              {group.items.map((item) => (
                <SidebarLink
                  key={item.to}
                  item={item}
                  onNavigate={onNavigate}
                />
              ))}
            </div>
          ))}
        </nav>
      </ScrollShadow>

      <div className="flex flex-col gap-1 px-3 py-3">
        {navFooterItems.map((item) => (
          <SidebarLink key={item.to} item={item} onNavigate={onNavigate} />
        ))}
      </div>
    </aside>
  )
}
