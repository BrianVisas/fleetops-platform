package com.fleetops.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;

public class ReleasesPage extends DashboardPage {

    public final Locator release;
    public final Locator percentage;
    public final Locator startButton;

    public ReleasesPage(Page page) {
        super(page);
        Locator view = page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Releases"));
        release = view.getByLabel("Release");
        percentage = view.getByLabel("Percentage", new Locator.GetByLabelOptions().setExact(true));
        startButton = view.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Start rollout"));
    }

    public void open() {
        page.navigate("/#/releases");
    }

    /** Picks the release by the label the operator sees: "<version> (<model>)". */
    public void startRollout(String releaseLabel, int percent) {
        release.selectOption(new SelectOption().setLabel(releaseLabel));
        percentage.fill(String.valueOf(percent));
        startButton.click();
    }
}
