import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://127.0.0.1:4173',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'npx serve .. -l 4173 --no-clipboard',
    cwd: __dirname,
    url: 'http://127.0.0.1:4173/config-generator.html',
    reuseExistingServer: true,
  },
});
