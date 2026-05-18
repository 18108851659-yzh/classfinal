package com.test.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    
    private static final String SECRET_VALUE = "MySecretPassword123!";
    
    @GetMapping("/hello")
    public String hello() {
        return "Hello, ClassFinal! This is a test application.";
    }
    
    @GetMapping("/secret")
    public String secret() {
        return "Secret value: " + SECRET_VALUE;
    }
}
