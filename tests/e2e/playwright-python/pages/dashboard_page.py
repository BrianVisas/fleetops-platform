"""Page objects hold locators and actions; the tests hold the assertions."""
from playwright.sync_api import Page


class DashboardPage:
    """What every view of the dashboard shares: the error banner."""

    def __init__(self, page: Page):
        self.page = page
        self.error = page.get_by_role("alert")
