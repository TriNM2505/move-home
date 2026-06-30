package vn.movehome.backend.driver.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Disabled("Requires PostgreSQL because DriverLocationRepository uses native ON CONFLICT upsert; default test profile uses H2.")
class DriverLocationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DriverLocationRepository driverLocationRepository;

    @Test
    void driverUpdatePersistsLocationAndCustomerGetReturnsIt() throws Exception {
        User driver = userRepository.saveAndFlush(activeUser(UserRole.DRIVER));
        User customer = userRepository.saveAndFlush(activeUser(UserRole.CUSTOMER));
        ServiceOrder order = orderRepository.saveAndFlush(ServiceOrder.builder()
                .orderCode("M3" + compactId())
                .customerId(customer.getId())
                .driverId(driver.getId())
                .pickupAddress("1 Tran Duy Hung, Ha Noi")
                .dropoffAddress("2 Nguyen Trai, Ha Noi")
                .scheduledAt(OffsetDateTime.now().plusHours(1))
                .status("IN_PROGRESS")
                .totalQuote(new BigDecimal("1000000"))
                .build());

        mockMvc.perform(post("/api/driver/location")
                        .with(user(driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lat": 21.0285110,
                                  "lng": 105.8048170,
                                  "heading": 120.5,
                                  "speed_kmh": 24.2
                                }
                                """))
                .andExpect(status().isNoContent());

        DriverLocation persisted = driverLocationRepository.findById(driver.getId()).orElseThrow();
        assertThat(persisted.getCurrentOrderId()).isEqualTo(order.getId());
        assertThat(persisted.getLat()).isEqualByComparingTo("21.0285110");
        assertThat(persisted.getLng()).isEqualByComparingTo("105.8048170");
        assertThat(persisted.getHeading()).isEqualByComparingTo("120.50");
        assertThat(persisted.getSpeedKmh()).isEqualByComparingTo("24.20");
        assertThat(persisted.getRecordedAt()).isNotNull();

        String responseBody = mockMvc.perform(get("/api/customer/orders/{orderId}/location", order.getId())
                        .with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver_id").value(driver.getId().toString()))
                .andExpect(jsonPath("$.stale").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        assertThat(response.get("lat").decimalValue()).isEqualByComparingTo("21.0285110");
        assertThat(response.get("lng").decimalValue()).isEqualByComparingTo("105.8048170");
        assertThat(response.get("heading").decimalValue()).isEqualByComparingTo("120.50");
        assertThat(response.get("speed_kmh").decimalValue()).isEqualByComparingTo("24.20");
        assertThat(response.get("recorded_at").asText()).isNotBlank();
    }

    private User activeUser(UserRole role) {
        return User.builder()
                .email(role.name().toLowerCase() + ".m3." + compactId() + "@movehome.test")
                .passwordHash("not-used-by-this-test")
                .fullName(role == UserRole.DRIVER ? "M3 Driver" : "M3 Customer")
                .role(role)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
    }

    private String compactId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
