import { Locator, Page } from "@playwright/test";
import { DashboardPage } from "./dashboard-page";

export class RolloutPage extends DashboardPage {
  readonly status: Locator;
  readonly pauseReason: Locator;
  readonly percentage: Locator;
  readonly pending: Locator;
  readonly installing: Locator;
  readonly succeeded: Locator;
  readonly failed: Locator;
  readonly pauseButton: Locator;
  readonly resumeButton: Locator;

  constructor(page: Page) {
    super(page);
    // The heading, and so the region's name, is "Rollout <id>".
    const view = page.getByRole("region", { name: /^Rollout/ });
    this.status = view.locator("#rollout-status");
    this.pauseReason = view.locator("#rollout-pause-reason");
    this.percentage = view.locator("#rollout-percentage-value");
    this.pending = view.locator("#count-pending");
    this.installing = view.locator("#count-installing");
    this.succeeded = view.locator("#count-succeeded");
    this.failed = view.locator("#count-failed");
    this.pauseButton = view.getByRole("button", { name: "Pause" });
    this.resumeButton = view.getByRole("button", { name: "Resume" });
  }

  async open(rolloutId: string) {
    await this.page.goto(`/#/rollouts/${rolloutId}`);
  }
}
