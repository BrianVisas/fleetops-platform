package com.fleetops.e2e.support;

import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * One browser per test class, and a fresh context (cookies, storage, trace) per
 * test, the way Playwright Test and pytest-playwright do it. The browser is
 * picked with -Dbrowser; run.sh runs the suite once per browser.
 */
@ExtendWith(SaveArtifactsOnFailure.class)
public abstract class BrowserTest {

    /** The dashboard as the stack serves it: `make up` locally, Compose in CI. */
    protected static final String WEB_UI_URL =
            System.getenv().getOrDefault("WEB_UI_URL", "http://localhost:3000");

    private static final String BROWSER = System.getProperty("browser", "chromium");
    private static final Path RESULTS = Path.of("reports", BROWSER, "test-results");

    private static Playwright playwright;
    private static Browser browser;

    protected Page page;
    protected FleetApi api;

    private BrowserContext context;
    private APIRequestContext request;
    private boolean traceSaved;

    @BeforeAll
    protected static void launchBrowser() {
        playwright = Playwright.create();
        BrowserType type = switch (BROWSER) {
            case "chromium" -> playwright.chromium();
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> throw new IllegalArgumentException("unknown browser: " + BROWSER);
        };
        browser = type.launch();
    }

    @AfterAll
    protected static void closeBrowser() {
        // Closes the browser too.
        playwright.close();
    }

    @BeforeEach
    protected void openContext() {
        context = browser.newContext(new Browser.NewContextOptions().setBaseURL(WEB_UI_URL));
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        page = context.newPage();
        // Test data goes in through the API, on the dashboard's origin, which
        // proxies /api to fleet-api.
        request = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(WEB_UI_URL));
        api = new FleetApi(request);
        traceSaved = false;
    }

    /** Called by {@link SaveArtifactsOnFailure} after a failed test, while the page is still open. */
    void saveArtifacts(String testName) {
        Path dir = RESULTS.resolve(testName);
        page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("screenshot.png")).setFullPage(true));
        // Open with: ./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI
        //   -Dexec.classpathScope=test -Dexec.args="show-trace <path>"
        context.tracing().stop(new Tracing.StopOptions().setPath(dir.resolve("trace.zip")));
        traceSaved = true;
    }

    @AfterEach
    protected void closeContext() {
        if (!traceSaved) {
            // A passing test keeps no trace.
            context.tracing().stop();
        }
        context.close();
        request.dispose();
    }
}
