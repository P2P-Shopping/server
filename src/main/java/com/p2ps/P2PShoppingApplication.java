package com.p2ps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableCaching

public class P2PShoppingApplication {

    public static void main(String[] args) {
        SpringApplication.run(P2PShoppingApplication.class, args);
    }

}
