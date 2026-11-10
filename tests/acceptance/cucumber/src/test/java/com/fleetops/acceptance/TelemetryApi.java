package com.fleetops.acceptance;

import io.restassured.response.Response;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thin client for telemetry-ingest. */
public class TelemetryApi {

    private static final String URL =
            System.getenv().getOrDefault("TELEMETRY_URL", "http://localhost:8081");

    private final HttpLog log;

    public TelemetryApi(HttpLog log) {
        this.log = log;
    }

    /** A valid reading, recorded the given number of minutes before now. */
    public static Map<String, Object> reading(String vehicleId, double speed, int minutesAgo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicleId", vehicleId);
        body.put("recordedAt", Instant.now().minus(minutesAgo, ChronoUnit.MINUTES).toString());
        body.put("speedKmh", speed);
        body.put("batteryTemp", 25.0);
        body.put("voltage", 400.0);
        return body;
    }

    public Response send(Map<String, Object> reading) {
        return log.request(URL).body(Json.object(reading)).post("/telemetry");
    }

    public Response latest(String vehicleId) {
        return log.request(URL).get("/vehicles/{id}/latest", vehicleId);
    }
}
