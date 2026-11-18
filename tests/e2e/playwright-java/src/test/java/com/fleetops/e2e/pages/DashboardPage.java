package com.fleetops.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * What every view of the dashboard shares: the error banner. Page objects hold
 * locators and actions; the tests hold the assertions.
 */
public class DashboardPage {

    protected final Page page;
    public final Locator error;

    public DashboardPage(Page page) {
        this.page = page;
        this.error = page.getByRole(AriaRole.ALERT);
    }
}
