package com.trustsign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TrustSignApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrustSignApplication.class, args);
    }
}
