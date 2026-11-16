package tn.digitworks.naanayamthavanai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class NaanayamApplication {

    public static void main(String[] args) {
        // Business day boundaries (daily dues, collections) are in IST
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(NaanayamApplication.class, args);
    }
}
