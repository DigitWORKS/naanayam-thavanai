package tn.digitworks.naanayamthavanai.common;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class AppConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    /** Business clock (IST). Inject this instead of calling LocalDate.now() so tests can fix time. */
    @Bean
    Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }

    /** Money is sent as decimal strings ("1250.00"), never JSON numbers. */
    @Bean
    JsonMapperBuilderCustomizer moneyAsString() {
        SimpleModule module = new SimpleModule("money");
        module.addSerializer(BigDecimal.class, new ValueSerializer<>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctx) {
                gen.writeString(value.toPlainString());
            }
        });
        return builder -> builder.addModule(module);
    }
}
