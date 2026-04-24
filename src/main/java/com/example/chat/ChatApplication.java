package com.example.chat;

import com.example.chat.config.OpenRouterProperties;
import com.example.chat.config.ChatAppProperties;
import com.example.chat.config.ImageApiProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.example.chat.mapper")
@EnableConfigurationProperties({OpenRouterProperties.class, ImageApiProperties.class, ChatAppProperties.class})
public class ChatApplication {

    /**
     * Standard Spring Boot bootstrap entry point.
     */
    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
