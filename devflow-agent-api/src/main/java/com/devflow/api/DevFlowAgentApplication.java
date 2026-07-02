package com.devflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * DevFlow Agent 启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.devflow")
public class DevFlowAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevFlowAgentApplication.class, args);
    }
}
