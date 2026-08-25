import { defineConfig, loadEnv } from 'vite'
import { devtools } from '@tanstack/devtools-vite'

import { tanstackStart } from '@tanstack/react-start/plugin/vite'

import viteReact from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { nitro } from 'nitro/vite'

const config = defineConfig(({ mode }) => {
  // `ZENDENT_*` are server-side settings, read by the BFF through `process.env`.
  // They are deliberately NOT put on `envPrefix`: that channel inlines values
  // into the browser bundle, and this is the wrong place to grow the habit of
  // sending server configuration there. Loading them into the server process is
  // also what `bun run` does not do — it reads `.env` for its own runtime but
  // does not pass it to the script it spawns, which is why the port previously
  // had to be exported by hand on the command line.
  Object.assign(process.env, loadEnv(mode, process.cwd(), 'ZENDENT_'))

  return {
    resolve: { tsconfigPaths: true },
    plugins: [
      devtools(),
      nitro({ rollupConfig: { external: [/^@sentry\//] } }),
      tailwindcss(),
      tanstackStart(),
      viteReact(),
    ],
  }
})

export default config
