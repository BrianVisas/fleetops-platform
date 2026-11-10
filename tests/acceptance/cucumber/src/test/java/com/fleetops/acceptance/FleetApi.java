package com.fleetops.acceptance;

import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin client for fleet-api. Every call returns the raw response, so the steps
 * decide what counts as success.
 */
public class FleetApi {

    private static final String URL =
            System.getenv().getOrDefault("FLEET_API_URL", "http://localhost:8080");

    private final HttpLog log;

    public FleetApi(HttpLog log) {
        this.log = log;
    }

    public Response register(String vin, String model, String swVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vin", vin);
        body.put("model", model);
        body.put("swVersion", swVersion);
        return log.request(URL).body(Json.object(body)).post("/api/v1/vehicles");
    }

    public Response get(String id) {
        return log.request(URL).get("/api/v1/vehicles/{id}", id);
    }

    public Response update(String id, String model, String swVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("swVersion", swVersion);
        return log.request(URL).body(Json.object(body)).put("/api/v1/vehicles/{id}", id);
    }

    public Response delete(String id) {
        return log.request(URL).delete("/api/v1/vehicles/{id}", id);
    }

    public Response list(String model, int page, int size) {
        return log.request(URL)
                .queryParam("model", model)
                .queryParam("page", page)
                .queryParam("size", size)
                .get("/api/v1/vehicles");
    }
}
