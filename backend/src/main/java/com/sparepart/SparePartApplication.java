package com.sparepart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.sparepart.mapper")
@EnableScheduling
public class SparePartApplication {

    public static void main(String[] args) {
        SpringApplication.run(SparePartApplication.class, args);
    }
}