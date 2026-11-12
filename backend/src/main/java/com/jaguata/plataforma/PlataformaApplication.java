package com.jaguata.plataforma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PlataformaApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlataformaApplication.class, args);
    }
}
