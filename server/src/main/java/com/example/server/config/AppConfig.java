package com.example.server.config;

import com.example.server.CRDTfiles.CRDTManager;
import com.example.server.CRDTfiles.CRDTManager.NetworkHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// import app.Operation; // Removed as it cannot be resolved
import com.example.server.Operation; // Replace with the correct package path for Operation

@Configuration
public class AppConfig {

    @Bean
    public CRDTManager crdtManager() {
        // Provide the required parameters for the CRDTManager constructor
        int userId = 1; // Example user ID
        boolean isEditor = true; // Example editor flag
        NetworkHandler networkHandler = new NetworkHandler() {
            // Provide a dummy implementation of NetworkHandler for now
            @Override
            public void setOnOperationReceived(OnOperationReceived callback) {}

            @Override
            public void setOnCursorPositionReceived(OnCursorPositionReceived callback) {}

            @Override
            public void setOnUserListReceived(OnUserListReceived callback) {}

            @Override
            public void sendOperation(Operation operation) {}

            @Override
            public void sendCursorPosition(int position, int selectionStart, int selectionEnd) {}

            @Override
            public void joinSession(String sessionCode) {}

            @Override
            public void requestSharingCodes() {}
        };

        return new CRDTManager(userId, isEditor, networkHandler);
    }
}
