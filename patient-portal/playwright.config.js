import { defineConfig } from '@playwright/test';

// Separate, opt-in E2E harness (run with `npm run test:e2e`): drives the real built-and-served
// frontend against a real running backend instance, with no mocks on either side. This is
// distinct from `npm test` (Vitest component tests, which mock the API-client module) and from
// the backend's own `./mvnw.cmd test` (MockMvc, in-process, transactional-rollback DB). See
// application-e2e.yml for which database the backend instance it starts here points at.
export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  fullyParallel: false,
  retries: 0,
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  webServer: [
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
    {
      command: '.\\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=e2e',
      cwd: '../QP',
      url: 'http://localhost:8081/api/v1/assessment/sessions',
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
});
