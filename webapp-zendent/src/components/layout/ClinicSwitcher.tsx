import { Button, Dropdown } from '@heroui/react'
import { Building2, Check, ChevronsUpDown } from 'lucide-react'

interface ClinicSwitcherProps {
  /** The Clinic this session belongs to, as the Clinic names itself. */
  clinicName: string
}

/**
 * Names the Clinic whose data is on screen.
 *
 * It lists one Clinic and only ever will: a session belongs to the Clinic whose
 * address it was opened on, and the way to another is to sign in there. The
 * placeholder list this shipped with implied otherwise, which the isolation the
 * backend enforces makes untrue.
 */
export function ClinicSwitcher({ clinicName }: ClinicSwitcherProps) {
  return (
    <Dropdown>
      <Dropdown.Trigger>
        <Button
          variant="ghost"
          fullWidth
          className="h-auto justify-start gap-3 rounded-xl border border-separator bg-surface px-3 py-2.5 shadow-surface"
        >
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface-secondary text-muted">
            <Building2 className="size-5" />
          </span>
          <span className="flex min-w-0 flex-1 flex-col items-start text-left">
            <span className="w-full truncate text-sm font-semibold text-foreground">
              {clinicName}
            </span>
          </span>
          <ChevronsUpDown className="size-4 shrink-0 text-muted" />
        </Button>
      </Dropdown.Trigger>
      <Dropdown.Popover placement="bottom start" className="min-w-64">
        <Dropdown.Menu>
          <Dropdown.Item key="active" id="active" textValue={clinicName}>
            <span className="flex w-full items-center gap-2">
              <span className="min-w-0 flex-1 truncate text-sm font-medium">
                {clinicName}
              </span>
              <Check className="size-4 shrink-0 text-accent" />
            </span>
          </Dropdown.Item>
        </Dropdown.Menu>
      </Dropdown.Popover>
    </Dropdown>
  )
}
