import re

from playwright.sync_api import Page, expect

from pages.dashboard_page import DashboardPage


class VehiclesPage(DashboardPage):
    def __init__(self, page: Page):
        super().__init__(page)
        view = page.get_by_role("region", name="Vehicles")
        self.model_filter = view.get_by_label("Model")
        self.show_button = view.get_by_role("button", name="Show")
        self.model_cells = view.locator("tbody tr td:nth-child(2)")
        self.page_info = view.locator("#page-info")
        self.previous_button = view.get_by_role("button", name="Previous")
        self.next_button = view.get_by_role("button", name="Next")

    def open(self):
        self.page.goto("/#/vehicles")
        # Wait for the unfiltered first page, so it cannot arrive after a filtered one.
        expect(self.page_info).to_have_text(re.compile(r"^Page 1 of \d+$"))

    def filter_by_model(self, model):
        self.model_filter.fill(model)
        self.show_button.click()
