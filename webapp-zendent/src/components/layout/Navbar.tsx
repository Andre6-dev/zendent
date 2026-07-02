import { Avatar, Button, Dropdown, SearchField, Tooltip } from '@heroui/react'
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
  /** Opens the sidebar drawer on small viewports. */
  onMenuClick: () => void
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

export function Navbar({ onMenuClick }: NavbarProps) {
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

        <UserMenu />
      </div>
    </header>
  )
}

function UserMenu() {
  return (
    <Dropdown>
      <Dropdown.Trigger>
        <Button
          variant="ghost"
          className="h-auto gap-2 px-2 py-1.5"
          aria-label="Account menu"
        >
          <Avatar size="sm">
            <Avatar.Fallback>DS</Avatar.Fallback>
          </Avatar>
          <span className="hidden flex-col items-start leading-tight sm:flex">
            <span className="text-sm font-semibold text-foreground">
              Darrell Steward
            </span>
            <span className="text-xs text-muted">Super admin</span>
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
          <Dropdown.Item key="logout" id="logout">
            Log out
          </Dropdown.Item>
        </Dropdown.Menu>
      </Dropdown.Popover>
    </Dropdown>
  )
}
