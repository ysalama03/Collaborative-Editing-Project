package com.example.server.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.server.Operation;
import com.example.server.CRDTfiles.CRDTManager;

@Controller
public class WebSocketController {
    private final SimpMessagingTemplate messagingTemplate;
    private final CRDTManager crdtManager;


    @Autowired
    public WebSocketController(SimpMessagingTemplate messagingTemplate, CRDTManager crdtManager) {
        this.crdtManager = crdtManager;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/document/{documentId}/operation")
    public void handleOperation(@DestinationVariable String documentId, @Payload Operation operation) {
        
        crdtManager.handleRemoteOperation(operation); // Apply the operation to the CRDT manager
        // Broadcast the operation to all connected clients

        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/operation", operation);
    }


}


