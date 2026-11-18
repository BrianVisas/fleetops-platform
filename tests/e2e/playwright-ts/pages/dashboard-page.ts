import { Locator, Page } from "@playwright/test";

// Page objects hold locators and actions; the tests hold the assertions.
//
// Fields are assigned in the constructor, not in initializers: since Playwright
// 1.65 a field initializer that reads a constructor parameter (`this.page`)
// throws, because class fields follow `useDefineForClassFields`.

/** What every view of the dashboard shares: the error banner. */
export class DashboardPage {
  readonly page: Page;
  readonly error: Locator;

  constructor(page: Page) {
    this.page = page;
    this.error = page.getByRole("alert");
  }
}
