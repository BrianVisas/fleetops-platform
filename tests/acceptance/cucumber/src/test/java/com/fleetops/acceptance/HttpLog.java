package com.fleetops.acceptance;

import static io.restassured.RestAssured.given;

import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Every request and response of one scenario, in full. Hooks attaches it to the
 * Cucumber report when the scenario fails, so a failure shows what was sent and
 * what came back, not only which assertion broke.
 */
public class HttpLog {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream stream = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    public RequestSpecification request(String baseUri) {
        return given()
                .baseUri(baseUri)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter(stream))
                .filter(new ResponseLoggingFilter(stream));
    }

    public String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
