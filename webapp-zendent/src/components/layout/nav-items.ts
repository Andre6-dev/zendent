import {
  BarChart3,
  CalendarDays,
  CreditCard,
  FileBarChart,
  Headset,
  LayoutDashboard,
  MonitorSmartphone,
  Package,
  ShoppingCart,
  Stethoscope,
  UserCog,
  Users,
  Wallet,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { FileRouteTypes } from '#/routeTree.gen'

/** Any valid route path in the app (typed against the generated route tree). */
export type AppRoute = FileRouteTypes['to']

export interface NavItem {
  label: string
  to: AppRoute
  icon: LucideIcon
}

export interface NavGroup {
  /** Optional section label shown above the group. */
  label?: string
  items: Array<NavItem>
}

/** Main grouped navigation, rendered in order. Mirrors the Zendenta sidebar. */
export const navGroups: Array<NavGroup> = [
  {
    items: [{ label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard }],
  },
  {
    label: 'Clinic',
    items: [
      { label: 'Reservations', to: '/reservations', icon: CalendarDays },
      { label: 'Patients', to: '/patients', icon: Users },
      { label: 'Treatments', to: '/treatments', icon: Stethoscope },
      { label: 'Staff List', to: '/staff', icon: UserCog },
    ],
  },
  {
    label: 'Finance',
    items: [
      { label: 'Accounts', to: '/accounts', icon: Wallet },
      { label: 'Sales', to: '/sales', icon: BarChart3 },
      { label: 'Purchases', to: '/purchases', icon: ShoppingCart },
      { label: 'Payment Method', to: '/payment-methods', icon: CreditCard },
    ],
  },
  {
    label: 'Physical Asset',
    items: [
      { label: 'Stocks', to: '/stocks', icon: Package },
      { label: 'Peripherals', to: '/peripherals', icon: MonitorSmartphone },
    ],
  },
]

/** Bottom-anchored items (separated by spacing, not a divider). */
export const navFooterItems: Array<NavItem> = [
  { label: 'Report', to: '/report', icon: FileBarChart },
  { label: 'Customer Support', to: '/support', icon: Headset },
]
