package tn.digitworks.naanayamthavanai.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first SUPER_ADMIN on startup when none exists, from
 * app.bootstrap.super-admin.phone / .pin (set via env vars, not committed).
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String phone;
    private final String pin;
    private final String name;

    public SuperAdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder,
                               @Value("${app.bootstrap.super-admin.phone:}") String phone,
                               @Value("${app.bootstrap.super-admin.pin:}") String pin,
                               @Value("${app.bootstrap.super-admin.name:Super Admin}") String name) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.phone = phone;
        this.pin = pin;
        this.name = name;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.SUPER_ADMIN)) {
            return;
        }
        if (phone.isBlank() || pin.isBlank()) {
            log.warn("No SUPER_ADMIN exists. Set SUPER_ADMIN_PHONE and SUPER_ADMIN_PIN to create one on startup.");
            return;
        }
        if (!phone.matches(UserDtos.PHONE) || !pin.matches(UserDtos.PIN)) {
            log.error("SUPER_ADMIN_PHONE must be a 10-digit mobile number and SUPER_ADMIN_PIN 4 to 6 digits");
            return;
        }
        users.save(new AppUser(null, name, phone, passwordEncoder.encode(pin), Role.SUPER_ADMIN));
        log.info("Created SUPER_ADMIN user for phone ending {}", phone.substring(phone.length() - 4));
    }
}
