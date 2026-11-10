package com.fleetops.acceptance;

import io.restassured.response.Response;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * What one scenario remembers between its steps. Cucumber creates a new World
 * for every scenario and hands the same instance to every step class.
 */
public class World {

    // Digits and capitals without I, O and Q: the characters a real VIN may use.
    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
    private static final String MODEL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public Response response;
    public final Map<String, String> vehicle = new HashMap<>();
    public String firstModel;
    public int pageSize;
    public String unknownVehicleId;

    public static String randomVin() {
        return random(VIN_CHARS, 17);
    }

    /**
     * A model name no other scenario uses, so list filters see only this
     * scenario's vehicles even though the database is shared and never reset.
     */
    public static String randomModel() {
        return "AT-" + random(MODEL_CHARS, 8);
    }

    private static String random(String alphabet, int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return value.toString();
    }
}
