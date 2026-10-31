package org.example.revoenglishwebsite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.time.ZoneOffset;
import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RevoEnglishWebsiteApplication {

    public static void main(String[] args) {
        // Run in UTC regardless of the host. The PostgreSQL driver sends the JVM time zone when connecting,
        // and some host zone ids (e.g. the legacy alias "Asia/Saigon" reported by Windows) are rejected by
        // PostgreSQL, which would prevent startup. Timestamps are stored as Instants anyway.
        // Note: IntelliJ's Run mode attaches to the JVM after start and resets the zone to the OS zone again;
        // the local database (compose.yaml, postgres:17-bookworm) therefore also accepts legacy zone names.
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
        SpringApplication.run(RevoEnglishWebsiteApplication.class, args);
    }

}
