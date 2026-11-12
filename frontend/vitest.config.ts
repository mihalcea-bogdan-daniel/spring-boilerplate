import { defineConfig } from 'vitest/config'

// Plain unit tests for framework-free code (utils). Use @nuxt/test-utils for component/store tests.
export default defineConfig({
  test: { include: ['tests/**/*.test.ts'], environment: 'node' },
})
