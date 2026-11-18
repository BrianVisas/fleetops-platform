import re

from playwright.sync_api import Page

from pages.dashboard_page import DashboardPage


class RolloutPage(DashboardPage):
    def __init__(self, page: Page):
        super().__init__(page)
        # The heading, and so the region's name, is "Rollout <id>".
        view = page.get_by_role("region", name=re.compile(r"^Rollout"))
        self.status = view.locator("#rollout-status")
        self.pause_reason = view.locator("#rollout-pause-reason")
        self.percentage = view.locator("#rollout-percentage-value")
        self.pending = view.locator("#count-pending")
        self.installing = view.locator("#count-installing")
        self.succeeded = view.locator("#count-succeeded")
        self.failed = view.locator("#count-failed")
        self.pause_button = view.get_by_role("button", name="Pause")
        self.resume_button = view.get_by_role("button", name="Resume")

    def open(self, rollout_id):
        self.page.goto(f"/#/rollouts/{rollout_id}")
