package tn.digitworks.naanayamthavanai.user;

import tn.digitworks.naanayamthavanai.area.AreaDtos.AreaResponse;
import tn.digitworks.naanayamthavanai.tenant.Plan;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class UserDtos {

    public static final String PHONE = "^[6-9]\\d{9}$";
    public static final String PIN = "^\\d{4,6}$";

    private UserDtos() {
    }

    public record LoginRequest(
            @NotBlank @Pattern(regexp = PHONE) String phone,
            @NotBlank @Pattern(regexp = PIN, message = "PIN must be 4 to 6 digits") String pin) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** Signed-in user changes their own PIN. */
    public record ChangePinRequest(
            @NotBlank @Pattern(regexp = PIN, message = "PIN must be 4 to 6 digits") String currentPin,
            @NotBlank @Pattern(regexp = PIN, message = "PIN must be 4 to 6 digits") String newPin) {
    }

    /**
     * Self-registration of a financier. The app also asks the owner to confirm the PIN; only one PIN is sent.
     * plan: only TRIAL until payments exist (null = TRIAL).
     */
    public record RegisterRequest(
            @NotBlank @Size(max = 150) String businessName,
            @Size(max = 50) String licenceNo,
            @NotNull @DecimalMin("0.00") @DecimalMax("999.99") BigDecimal maxInterestRate,
            @NotBlank @Size(max = 100) String ownerName,
            @NotBlank @Pattern(regexp = PHONE) String phone,
            @NotBlank @Pattern(regexp = PIN, message = "PIN must be 4 to 6 digits") String pin,
            Plan plan,
            @NotNull @AssertTrue(message = "the terms must be accepted") Boolean acceptTerms) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
    }

    public record UserResponse(Long id, String name, String phone, Role role, boolean isActive,
                               List<AreaResponse> areas, LocalDateTime lastLoginAt) {
    }

    public record UserCreate(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = PHONE) String phone,
            @NotBlank @Pattern(regexp = PIN, message = "PIN must be 4 to 6 digits") String pin,
            List<Long> areaIds) {
    }

    public record UserUpdate(
            @Size(min = 1, max = 100) String name,
            Boolean isActive,
            @Pattern(regexp = PIN, message = "PIN must be 4 to 6 digits") String pin) {
    }

    public record AreaAssignment(@NotNull List<Long> areaIds) {
    }
}
