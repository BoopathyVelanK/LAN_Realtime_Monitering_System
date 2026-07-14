package com.securesoc;

import com.securesoc.config.AgentProperties;
import com.securesoc.config.CorsProperties;
import com.securesoc.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, AgentProperties.class, CorsProperties.class})
public class SecureSocBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureSocBackendApplication.class, args);
    }
}
