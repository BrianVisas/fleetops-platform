package com.fleetops.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;

public class RolloutPage extends DashboardPage {

    public final Locator status;
    public final Locator pauseReason;
    public final Locator percentage;
    public final Locator pending;
    public final Locator installing;
    public final Locator succeeded;
    public final Locator failed;
    public final Locator pauseButton;
    public final Locator resumeButton;

    public RolloutPage(Page page) {
        super(page);
        // The heading, and so the region's name, is "Rollout <id>".
        Locator view = page.getByRole(AriaRole.REGION,
                new Page.GetByRoleOptions().setName(Pattern.compile("^Rollout")));
        status = view.locator("#rollout-status");
        pauseReason = view.locator("#rollout-pause-reason");
        percentage = view.locator("#rollout-percentage-value");
        pending = view.locator("#count-pending");
        installing = view.locator("#count-installing");
        succeeded = view.locator("#count-succeeded");
        failed = view.locator("#count-failed");
        pauseButton = view.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Pause"));
        resumeButton = view.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Resume"));
    }

    public void open(String rolloutId) {
        page.navigate("/#/rollouts/" + rolloutId);
    }
}
