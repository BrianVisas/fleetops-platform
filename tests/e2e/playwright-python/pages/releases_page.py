from playwright.sync_api import Page

from pages.dashboard_page import DashboardPage


class ReleasesPage(DashboardPage):
    def __init__(self, page: Page):
        super().__init__(page)
        view = page.get_by_role("region", name="Releases")
        self.release = view.get_by_label("Release")
        self.percentage = view.get_by_label("Percentage", exact=True)
        self.start_button = view.get_by_role("button", name="Start rollout")

    def open(self):
        self.page.goto("/#/releases")

    def start_rollout(self, release_label, percentage):
        """Picks the release by the label the operator sees: "<version> (<model>)"."""
        self.release.select_option(label=release_label)
        self.percentage.fill(str(percentage))
        self.start_button.click()
