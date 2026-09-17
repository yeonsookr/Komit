package com.a501.escrow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EscrowApplication {

    public static void main(String[] args) {
        SpringApplication.run(EscrowApplication.class, args);
    }
}
