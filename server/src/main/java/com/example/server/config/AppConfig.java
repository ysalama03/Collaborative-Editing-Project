package com.example.server.config;

import com.example.server.CRDTfiles.CRDTManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// import app.Operation; // Removed as it cannot be resolved

@Configuration
public class AppConfig {

    @Bean
    public CRDTManager crdtManager() {
        // Provide the required parameters for the CRDTManager constructor
        int userId = 1; // Example user ID
        return new CRDTManager(userId);
    }
}
