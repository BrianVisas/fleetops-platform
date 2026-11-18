package com.fleetops.e2e.support;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Sets up test data through fleet-api, so each test creates exactly the state it needs. */
public class FleetApi {

    // Digits and capitals without I, O and Q: the characters a real VIN may use.
    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Release(String id, String version, String targetModel) {
    }

    private final APIRequestContext request;

    FleetApi(APIRequestContext request) {
        this.request = request;
    }

    /**
     * A model no other test uses, so a filter on it sees only this test's vehicles
     * even though the database is shared and never reset.
     */
    public static String randomModel() {
        return "UI-" + pick(VIN_CHARS, 8);
    }

    /** Registers vehicles of one model and returns their ids, in order. */
    public List<String> registerVehicles(String model, int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            APIResponse response = request.post("/api/v1/vehicles", RequestOptions.create()
                    .setData(Map.of("vin", pick(VIN_CHARS, 17), "model", model, "swVersion", "1.0.0")));
            assertThat(response).isOK();
            ids.add(json(response).get("id").getAsString());
        }
        return ids;
    }

    public Release publishRelease(String model) {
        byte[] checksum = new byte[32];
        RANDOM.nextBytes(checksum);
        APIResponse response = request.post("/api/v1/releases", RequestOptions.create().setData(Map.of(
                "version", "8." + pick("0123456789", 9),
                "targetModel", model,
                "checksum", HexFormat.of().formatHex(checksum),
                "sizeBytes", 1024)));
        assertThat(response).isOK();
        JsonObject body = json(response);
        return new Release(body.get("id").getAsString(), body.get("version").getAsString(),
                body.get("targetModel").getAsString());
    }

    /** Starts a rollout and returns its id. */
    public String startRollout(String releaseId, int percentage) {
        APIResponse response = request.post("/api/v1/rollouts", RequestOptions.create()
                .setData(Map.of("releaseId", releaseId, "percentage", percentage)));
        assertThat(response).isOK();
        return json(response).get("id").getAsString();
    }

    /** The vehicle asks for its update, then reports how the installation went. */
    public void install(String vehicleId, String version, boolean success) {
        APIResponse claim = request.get("/api/v1/vehicles/" + vehicleId + "/update");
        assertEquals(200, claim.status(), claim.text());
        APIResponse result = request.post("/api/v1/vehicles/" + vehicleId + "/update/result",
                RequestOptions.create().setData(Map.of("version", version, "success", success)));
        assertEquals(202, result.status(), result.text());
    }

    public APIResponse get(String path) {
        return request.get(path);
    }

    public static JsonObject json(APIResponse response) {
        return JsonParser.parseString(response.text()).getAsJsonObject();
    }

    private static String pick(String chars, int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return result.toString();
    }
}
