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
    // python http.server preserves query strings (serve clean-url redirects drop them)
    command: 'python3 -m http.server 4173',
    cwd: `${__dirname}/..`,
    url: 'http://127.0.0.1:4173/config-generator.html',
    reuseExistingServer: true,
  },
});
