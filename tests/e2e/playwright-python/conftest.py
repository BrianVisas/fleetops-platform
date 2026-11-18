"""Where the dashboard is, and the fixtures every test shares.

pytest-playwright provides `page` (a fresh browser context per test) and runs
each test once per `--browser`; this file only points it at the dashboard and
adds an API client for test data."""
import os

import pytest

from support.fleet_api import FleetApi

# The dashboard as the stack serves it: `make up` locally, Compose in CI.
WEB_UI_URL = os.environ.get("WEB_UI_URL", "http://localhost:3000").rstrip("/")


@pytest.fixture(scope="session")
def browser_context_args(browser_context_args):
    return {**browser_context_args, "base_url": WEB_UI_URL}


@pytest.fixture
def api(playwright):
    """Test data goes in through the API, on the dashboard's origin, which
    proxies /api to fleet-api."""
    request = playwright.request.new_context(base_url=WEB_UI_URL)
    yield FleetApi(request)
    request.dispose()
