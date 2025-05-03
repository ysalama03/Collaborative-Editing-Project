package com.example.server.config;

import java.util.List;
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
        
        if (operation.getOp().equals("insert")) {
            // Handle insert operation
            crdtManager.insertRemote(documentId, operation); // Apply the operation to the CRDT manager
            System.out.println("Insert operation: " + operation.getValue() + " ID = " + operation.getID());
        } else if (operation.getOp().equals("delete")) {
            // Handle delete operation
            crdtManager.deleteRemote(documentId, operation); // Apply the operation to the CRDT manager
            System.out.println("Delete operation: " + operation.getValue() + " ID = " + operation.getID());
        }

        String viewerCode = crdtManager.getViewerCode(documentId);

        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/operation", operation);
        messagingTemplate.convertAndSend("/topic/document/" + viewerCode + "/operation", operation);

    }

    @MessageMapping("/session/{sessionCode}/users")
    public void handleActiveUsers(@DestinationVariable String sessionCode, @Payload String userId) {
        // Broadcast the active users to all subscribers of the session topic
        String viewerCode = crdtManager.getViewerCode(sessionCode);

        System.out.println("User ID: " + userId + " joined session: " + sessionCode);

        List<Integer> userIds = crdtManager.getUserIds(sessionCode);
        System.out.println("Active users in session " + sessionCode + ": " + userIds);

        messagingTemplate.convertAndSend("/topic/session/" + viewerCode + "/users", userIds);
        messagingTemplate.convertAndSend("/topic/session/" + sessionCode + "/users", userIds);
    }


}


