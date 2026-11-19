package tn.digitworks.naanayamthavanai.user;

import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.common.RateLimiter;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import tn.digitworks.naanayamthavanai.user.UserDtos.ChangePinRequest;
import tn.digitworks.naanayamthavanai.user.UserDtos.LoginRequest;
import tn.digitworks.naanayamthavanai.user.UserDtos.RefreshRequest;
import tn.digitworks.naanayamthavanai.user.UserDtos.RegisterRequest;
import tn.digitworks.naanayamthavanai.user.UserDtos.TokenResponse;
import tn.digitworks.naanayamthavanai.user.UserDtos.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;
    private final RateLimiter rateLimiter;
    private final int registrationsPerHour;

    public AuthController(AuthService service, RateLimiter rateLimiter,
                          @Value("${app.registration.max-per-hour-per-ip:5}") int registrationsPerHour) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.registrationsPerHour = registrationsPerHour;
    }

    /**
     * At most {@value #LOGIN_ATTEMPTS} attempts per mobile number per 15 minutes: a 4-digit PIN has only
     * 10,000 values. In memory, per server instance.
     */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest in) {
        if (!rateLimiter.tryAcquire("login:" + in.phone(), LOGIN_ATTEMPTS, Duration.ofMinutes(15))) {
            throw tooMany();
        }
        return service.login(in.phone(), in.pin());
    }

    /** Signed-in user (any role) changes their own PIN; other devices are signed out. */
    @PostMapping("/change-pin")
    public TokenResponse changePin(@Valid @RequestBody ChangePinRequest in) {
        if (!rateLimiter.tryAcquire("pin:" + CurrentUser.get().userId(), PIN_CHANGE_ATTEMPTS, Duration.ofMinutes(15))) {
            throw tooMany();
        }
        return service.changePin(in.currentPin(), in.newPin());
    }

    static final int LOGIN_ATTEMPTS = 10;
    static final int PIN_CHANGE_ATTEMPTS = 5;

    private static ApiException tooMany() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                "Too many PIN attempts. Wait 15 minutes and try again.");
    }

    /** Public: a financier registers and gets signed in as OWNER on a free trial. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest in, HttpServletRequest request) {
        if (!rateLimiter.tryAcquire("register:" + request.getRemoteAddr(), registrationsPerHour, Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "Too many registrations from this network. Try again in an hour.");
        }
        return service.register(in);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest in) {
        return service.refresh(in.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me() {
        return service.me();
    }
}
