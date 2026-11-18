import { expect, Locator, Page } from "@playwright/test";
import { DashboardPage } from "./dashboard-page";

export class VehiclesPage extends DashboardPage {
  readonly modelFilter: Locator;
  readonly showButton: Locator;
  readonly modelCells: Locator;
  readonly pageInfo: Locator;
  readonly previousButton: Locator;
  readonly nextButton: Locator;

  constructor(page: Page) {
    super(page);
    const view = page.getByRole("region", { name: "Vehicles" });
    this.modelFilter = view.getByLabel("Model");
    this.showButton = view.getByRole("button", { name: "Show" });
    this.modelCells = view.locator("tbody tr td:nth-child(2)");
    this.pageInfo = view.locator("#page-info");
    this.previousButton = view.getByRole("button", { name: "Previous" });
    this.nextButton = view.getByRole("button", { name: "Next" });
  }

  async open() {
    await this.page.goto("/#/vehicles");
    // Wait for the unfiltered first page, so it cannot arrive after a filtered one.
    await expect(this.pageInfo).toHaveText(/^Page 1 of \d+$/);
  }

  async filterByModel(model: string) {
    await this.modelFilter.fill(model);
    await this.showButton.click();
  }
}
