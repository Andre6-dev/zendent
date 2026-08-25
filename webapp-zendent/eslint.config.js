//  @ts-check

import { tanstackConfig } from '@tanstack/eslint-config'

export default [
  ...tanstackConfig,
  {
    rules: {
      'import/no-cycle': 'off',
      'import/order': 'off',
      'sort-imports': 'off',
      '@typescript-eslint/array-type': 'off',
      '@typescript-eslint/require-await': 'off',
      'pnpm/json-enforce-catalog': 'off',
    },
  },
  {
    // `.output` is build product, not source: linting it fails on files no one
    // wrote, so `bun run build && bun run lint` breaks without this.
    ignores: ['eslint.config.js', 'prettier.config.js', '.output/**'],
  },
]
