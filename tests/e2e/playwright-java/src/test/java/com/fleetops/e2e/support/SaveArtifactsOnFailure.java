package com.fleetops.e2e.support;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Saves a screenshot and the Playwright trace of every failed test, like
 * `trace: "retain-on-failure"` in Playwright Test. It runs right after the test
 * method and before @AfterEach, so the page and its context are still open.
 */
class SaveArtifactsOnFailure implements AfterTestExecutionCallback {

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isPresent()) {
            BrowserTest test = (BrowserTest) context.getRequiredTestInstance();
            test.saveArtifacts(context.getRequiredTestMethod().getName());
        }
    }
}
