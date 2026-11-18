import { defineConfig, devices } from "@playwright/test";

// The dashboard as the stack serves it: `make up` locally, Compose in CI.
const baseURL = process.env.WEB_UI_URL ?? "http://localhost:3000";

export default defineConfig({
  testDir: "./tests",
  outputDir: "reports/test-results",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  // No retries: a test that passes on the second try is a flaky test, and that
  // should fail the build until it is fixed, not be hidden by it.
  retries: 0,
  reporter: [
    ["list"],
    ["html", { outputFolder: "reports/html", open: "never" }],
    ["junit", { outputFile: "reports/junit.xml" }],
  ],
  use: {
    baseURL,
    // Kept only for failed tests: open with `npx playwright show-trace <zip>`.
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
  ],
});
