import { defineConfig, devices } from '@playwright/test'

const JAVA_HOME = process.env.JAVA_HOME ?? '/usr/lib/jvm/java-25-openjdk'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: true,
      timeout: 120_000,
    },
    {
      command: '../mvnw spring-boot:run',
      cwd: '..',
      url: 'http://localhost:8080/actuator/health',
      env: { JAVA_HOME },
      reuseExistingServer: true,
      timeout: 300_000,
    },
  ],
})
