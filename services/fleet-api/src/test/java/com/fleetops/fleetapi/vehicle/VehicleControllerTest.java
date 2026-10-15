package com.fleetops.fleetapi.vehicle;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fleetops.fleetapi.common.DuplicateVinException;
import com.fleetops.fleetapi.common.VehicleNotFoundException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleService service;

    private static final String VIN = "WVWZZZ1JZXW000001";

    private static final String VALID_BODY = """
            {
              "vin": "WVWZZZ1JZXW000001",
              "model": "ID.4",
              "swVersion": "1.0.0"
            }
            """;

    private static final String SHORT_VIN_BODY = """
            {
              "vin": "TOOSHORT",
              "model": "ID.4",
              "swVersion": "1.0.0"
            }
            """;

    @Test
    void returns201AndLocationWhenVehicleIsRegistered() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.register(any())).thenReturn(new Vehicle(id, VIN, "ID.4", "1.0.0", OffsetDateTime.now()));

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/vehicles/" + id))
                .andExpect(jsonPath("$.vin").value(VIN));
    }

    @Test
    void returns400WhenVinIsTooShort() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SHORT_VIN_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("vin")));
    }

    @Test
    void returns409WhenVinIsAlreadyRegistered() throws Exception {
        when(service.register(any())).thenThrow(new DuplicateVinException(VIN));

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void returns404WhenVehicleIsMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new VehicleNotFoundException(id));

        mockMvc.perform(get("/api/v1/vehicles/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/v1/vehicles/" + id));
    }
}
