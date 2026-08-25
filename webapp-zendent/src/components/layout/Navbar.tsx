import { Avatar, Button, Dropdown, SearchField, Tooltip } from '@heroui/react'
import { primaryRoleOf } from '#/features/auth/session'
import { signOut } from '#/features/auth/sign-out'
import type { SignedInMember } from '#/features/auth/session'
import {
  Activity,
  Bell,
  ChevronDown,
  CircleHelp,
  Flag,
  Menu as MenuIcon,
  Plus,
} from 'lucide-react'

interface NavbarProps {
  /** Who is signed in, as their Clinic records them. */
  member: SignedInMember
  /** Opens the sidebar drawer on small viewports. */
  onMenuClick: () => void
}

/**
 * The initials a Clinic would write on a chart: the first letter of the first
 * name and of the last. A single-word name gives one letter rather than a
 * doubled one, and a name written in a script without spaces gives its first
 * character — never an empty circle.
 */
function initialsOf(memberName: string): string {
  const parts = memberName.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) {
    return '?'
  }
  const first = parts[0][0]
  const last = parts.length > 1 ? parts[parts.length - 1][0] : ''
  return (first + last).toUpperCase()
}

function IconAction({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <Tooltip>
      <Button isIconOnly variant="ghost" aria-label={label}>
        {children}
      </Button>
      <Tooltip.Content>{label}</Tooltip.Content>
    </Tooltip>
  )
}

export function Navbar({ member, onMenuClick }: NavbarProps) {
  return (
    <header className="flex h-16 shrink-0 items-center gap-3 border-b border-separator bg-background px-4 lg:px-6">
      <Button
        isIconOnly
        variant="ghost"
        aria-label="Open menu"
        className="lg:hidden"
        onPress={onMenuClick}
      >
        <MenuIcon className="size-5" />
      </Button>

      <SearchField className="max-w-md flex-1" aria-label="Search">
        <SearchField.Group>
          <SearchField.SearchIcon />
          <SearchField.Input placeholder="Search for anything here..." />
          <SearchField.ClearButton />
        </SearchField.Group>
      </SearchField>

      <div className="ml-auto flex items-center gap-1.5">
        <Tooltip>
          <Button
            isIconOnly
            variant="primary"
            aria-label="Create"
            className="rounded-full"
          >
            <Plus className="size-5" />
          </Button>
          <Tooltip.Content>Create</Tooltip.Content>
        </Tooltip>

        <IconAction label="Help">
          <CircleHelp className="size-5" />
        </IconAction>

        <IconAction label="Activity">
          <Activity className="size-5" />
        </IconAction>

        <Tooltip>
          <Button
            variant="ghost"
            size="sm"
            aria-label="Flagged items"
            className="gap-1.5"
          >
            <Flag className="size-4 text-success" />
            <span className="text-sm tabular-nums text-muted">1/4</span>
          </Button>
          <Tooltip.Content>Flagged items</Tooltip.Content>
        </Tooltip>

        <Tooltip>
          <Button
            isIconOnly
            variant="ghost"
            aria-label="Notifications"
            className="relative"
          >
            <Bell className="size-5" />
            <span className="absolute right-2 top-2 size-2 rounded-full bg-danger ring-2 ring-background" />
          </Button>
          <Tooltip.Content>Notifications</Tooltip.Content>
        </Tooltip>

        <UserMenu member={member} />
      </div>
    </header>
  )
}

function UserMenu({ member }: { member: SignedInMember }) {
  const role = primaryRoleOf(member)

  return (
    <Dropdown>
      <Dropdown.Trigger>
        <Button
          variant="ghost"
          className="h-auto gap-2 px-2 py-1.5"
          aria-label="Account menu"
        >
          <Avatar size="sm">
            <Avatar.Fallback>{initialsOf(member.memberName)}</Avatar.Fallback>
          </Avatar>
          <span className="hidden flex-col items-start leading-tight sm:flex">
            <span className="text-sm font-semibold text-foreground">
              {member.memberName}
            </span>
            {role !== undefined && (
              <span className="text-xs text-muted">{role}</span>
            )}
          </span>
          <ChevronDown className="hidden size-4 text-muted sm:block" />
        </Button>
      </Dropdown.Trigger>
      <Dropdown.Popover placement="bottom end" className="min-w-48">
        <Dropdown.Menu>
          <Dropdown.Item key="profile" id="profile">
            Profile
          </Dropdown.Item>
          <Dropdown.Item key="settings" id="settings">
            Settings
          </Dropdown.Item>
          <Dropdown.Item
            key="logout"
            id="logout"
            onAction={() => void signOut()}
          >
            Log out
          </Dropdown.Item>
        </Dropdown.Menu>
      </Dropdown.Popover>
    </Dropdown>
  )
}
