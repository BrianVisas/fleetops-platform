package com.fleetops.acceptance;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Thin client for fleet-api. Every call returns the raw response, so the steps
 * decide what counts as success. Failed requests are logged in full.
 */
public final class FleetApi {

    private FleetApi() {
    }

    private static RequestSpecification request() {
        return given()
                .baseUri(World.FLEET_API_URL)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .log().ifValidationFails();
    }

    public static Response register(String vin, String model, String swVersion) {
        return request()
                .body(json("vin", vin, "model", model, "swVersion", swVersion))
                .post("/api/v1/vehicles");
    }

    public static Response get(String id) {
        return request().get("/api/v1/vehicles/{id}", id);
    }

    public static Response update(String id, String model, String swVersion) {
        return request()
                .body(json("model", model, "swVersion", swVersion))
                .put("/api/v1/vehicles/{id}", id);
    }

    public static Response delete(String id) {
        return request().delete("/api/v1/vehicles/{id}", id);
    }

    /**
     * A flat JSON object from name/value pairs. Written by hand so the request
     * bodies do not depend on which JSON mapper REST Assured finds.
     */
    static String json(String... namesAndValues) {
        StringBuilder body = new StringBuilder("{");
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (i > 0) {
                body.append(',');
            }
            body.append('"').append(namesAndValues[i]).append("\":\"")
                    .append(namesAndValues[i + 1].replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"');
        }
        return body.append('}').toString();
    }

    public static Response list(String model, int page, int size) {
        return request()
                .queryParam("model", model)
                .queryParam("page", page)
                .queryParam("size", size)
                .get("/api/v1/vehicles");
    }
}
