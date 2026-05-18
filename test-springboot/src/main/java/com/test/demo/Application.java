package com.test.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("========================================");
        System.out.println("  Spring Boot Test App Started!");
        System.out.println("  Visit: http://localhost:8080/hello");
        System.out.println("========================================");
    }
}
