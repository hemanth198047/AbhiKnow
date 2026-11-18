package com.abhiknow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AbhiKnowApplication {
    public static void main(String[] args) {
        // Servers store and compare instants in UTC; also avoids PostgreSQL rejecting legacy zone names such as Asia/Calcutta.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AbhiKnowApplication.class, args);
    }
}
