package com.anil.logging.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class DemoLoggingApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoLoggingApplication.class, args);
    }
}
