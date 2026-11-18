import { Locator, Page } from "@playwright/test";
import { DashboardPage } from "./dashboard-page";

export class ReleasesPage extends DashboardPage {
  readonly release: Locator;
  readonly percentage: Locator;
  readonly startButton: Locator;

  constructor(page: Page) {
    super(page);
    const view = page.getByRole("region", { name: "Releases" });
    this.release = view.getByLabel("Release");
    this.percentage = view.getByLabel("Percentage", { exact: true });
    this.startButton = view.getByRole("button", { name: "Start rollout" });
  }

  async open() {
    await this.page.goto("/#/releases");
  }

  /** Picks the release by the label the operator sees: "<version> (<model>)". */
  async startRollout(releaseLabel: string, percentage: number) {
    await this.release.selectOption({ label: releaseLabel });
    await this.percentage.fill(String(percentage));
    await this.startButton.click();
  }
}
