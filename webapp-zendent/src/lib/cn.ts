import { twMerge } from 'tailwind-merge'

type ClassValue = string | false | null | undefined

/** Merge Tailwind class names, resolving conflicts with the last one winning. */
export function cn(...inputs: Array<ClassValue>): string {
  return twMerge(inputs.filter(Boolean).join(' '))
}
