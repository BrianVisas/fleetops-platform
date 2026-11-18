package com.fleetops.e2e.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;

public class VehiclesPage extends DashboardPage {

    public final Locator modelFilter;
    public final Locator showButton;
    public final Locator modelCells;
    public final Locator pageInfo;
    public final Locator previousButton;
    public final Locator nextButton;

    public VehiclesPage(Page page) {
        super(page);
        Locator view = page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Vehicles"));
        modelFilter = view.getByLabel("Model");
        showButton = view.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Show"));
        modelCells = view.locator("tbody tr td:nth-child(2)");
        pageInfo = view.locator("#page-info");
        previousButton = view.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Previous"));
        nextButton = view.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Next"));
    }

    public void open() {
        page.navigate("/#/vehicles");
        // Wait for the unfiltered first page, so it cannot arrive after a filtered one.
        assertThat(pageInfo).hasText(Pattern.compile("^Page 1 of \\d+$"));
    }

    public void filterByModel(String model) {
        modelFilter.fill(model);
        showButton.click();
    }
}
