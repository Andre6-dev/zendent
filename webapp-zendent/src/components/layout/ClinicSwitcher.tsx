import { Button, Dropdown } from '@heroui/react'
import { Building2, Check, ChevronsUpDown } from 'lucide-react'

interface Clinic {
  id: string
  name: string
  address: string
}

// Placeholder clinics until the IAM module exists (multi-tenant SaaS).
const clinics: Array<Clinic> = [
  { id: 'avicena', name: 'Avicena Clinic', address: '845 Euclid Avenue, CA' },
  { id: 'northside', name: 'Northside Dental', address: '120 Market St, CA' },
]

export function ClinicSwitcher() {
  const active = clinics[0]

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
              {active.name}
            </span>
            <span className="w-full truncate text-xs text-muted">
              {active.address}
            </span>
          </span>
          <ChevronsUpDown className="size-4 shrink-0 text-muted" />
        </Button>
      </Dropdown.Trigger>
      <Dropdown.Popover placement="bottom start" className="min-w-64">
        <Dropdown.Menu>
          {clinics.map((clinic) => (
            <Dropdown.Item
              key={clinic.id}
              id={clinic.id}
              textValue={clinic.name}
            >
              <span className="flex w-full items-center gap-2">
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="truncate text-sm font-medium">
                    {clinic.name}
                  </span>
                  <span className="truncate text-xs text-muted">
                    {clinic.address}
                  </span>
                </span>
                {clinic.id === active.id && (
                  <Check className="size-4 shrink-0 text-accent" />
                )}
              </span>
            </Dropdown.Item>
          ))}
        </Dropdown.Menu>
      </Dropdown.Popover>
    </Dropdown>
  )
}
