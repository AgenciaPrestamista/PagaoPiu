package com.example.pagaopiu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PagaopiuApplication {

    public static void main(String[] args) {
        SpringApplication.run(PagaopiuApplication.class, args);
    }

}
