package vn.movehome.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test JwtTokenProvider — sinh/xac thuc access token, sinh refresh token, hash SHA-256.
 * Bien @Value duoc set thu cong qua ReflectionTestUtils vi class nay khong duoc Spring quan ly trong test.
 */
class JwtTokenProviderTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "move-home-test-secret-key-must-be-at-least-32-bytes-long".getBytes());

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "accessExpiryMinutes", 15);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpiryDays", 7);
    }

    @Test
    void generateAndValidateAccessTokenRoundTripsUserId() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("driver1@movehome.vn")
                .role(UserRole.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();

        String token = jwtTokenProvider.generateAccessToken(user);
        Optional<UUID> userId = jwtTokenProvider.validateAccessToken(token);

        assertThat(token).isNotBlank();
        assertThat(userId).contains(user.getId());
    }

    @Test
    void validateAccessTokenReturnsEmptyWhenTokenExpired() {
        // Het han ngay lap tuc (accessExpiryMinutes am) de kich hoat nhanh ExpiredJwtException
        ReflectionTestUtils.setField(jwtTokenProvider, "accessExpiryMinutes", -1);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("customer1@movehome.vn")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        String expiredToken = jwtTokenProvider.generateAccessToken(user);
        Optional<UUID> userId = jwtTokenProvider.validateAccessToken(expiredToken);

        assertThat(userId).isEmpty();
    }

    @Test
    void validateAccessTokenReturnsEmptyWhenTokenIsMalformedOrTampered() {
        Optional<UUID> userId = jwtTokenProvider.validateAccessToken("not-a-valid-jwt-token");

        assertThat(userId).isEmpty();
    }

    @Test
    void validateAccessTokenReturnsEmptyWhenSignatureDoesNotMatch() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("customer2@movehome.vn")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        String token = jwtTokenProvider.generateAccessToken(user);

        JwtTokenProvider otherProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(otherProvider, "secret",
                Base64.getEncoder().encodeToString("different-test-secret-key-at-least-32-bytes!!".getBytes()));
        ReflectionTestUtils.setField(otherProvider, "accessExpiryMinutes", 15);
        ReflectionTestUtils.setField(otherProvider, "refreshExpiryDays", 7);

        Optional<UUID> userId = otherProvider.validateAccessToken(token);

        assertThat(userId).isEmpty();
    }

    @Test
    void generateRefreshTokenProducesUnique43CharUrlSafeString() {
        String token1 = jwtTokenProvider.generateRefreshToken();
        String token2 = jwtTokenProvider.generateRefreshToken();

        assertThat(token1).hasSize(43);
        assertThat(token1).matches("^[A-Za-z0-9_-]+$");
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void refreshTokenExpiryIsApproximatelySevenDaysFromNow() {
        Instant before = Instant.now();

        Instant expiry = jwtTokenProvider.refreshTokenExpiry();

        assertThat(expiry)
                .isAfterOrEqualTo(before.plus(7, ChronoUnit.DAYS).minusSeconds(5))
                .isBeforeOrEqualTo(Instant.now().plus(7, ChronoUnit.DAYS).plusSeconds(5));
    }

    @Test
    void hashTokenProduces64CharHexAndIsDeterministic() {
        String hash1 = jwtTokenProvider.hashToken("raw-token-value");
        String hash2 = jwtTokenProvider.hashToken("raw-token-value");
        String hash3 = jwtTokenProvider.hashToken("different-token-value");

        assertThat(hash1).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(hash3);
    }

    @Test
    void validateAccessTokenReturnsEmptyForMalformedToken() {
        assertThat(jwtTokenProvider.validateAccessToken("abc")).isEmpty();
    }
}
