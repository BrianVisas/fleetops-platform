package com.fleetops.acceptance;

import static io.cucumber.junit.platform.engine.Constants.FEATURES_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * Entry point for Surefire. The feature files are shared with the pytest-bdd
 * suite, so they are read from ../features on disk rather than the classpath.
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("com.fleetops.acceptance")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "../features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.fleetops.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:reports/cucumber-report.html, json:reports/cucumber.json")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
public class RunCucumberTest {
}
