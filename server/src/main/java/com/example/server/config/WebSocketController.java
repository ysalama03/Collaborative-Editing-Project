package com.example.server.config;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    Map<String, LinkedHashMap<Long, Operation>> operations = new HashMap<>(); // Map to store user sessions

    @Autowired
    public WebSocketController(SimpMessagingTemplate messagingTemplate, CRDTManager crdtManager) {
        this.crdtManager = crdtManager;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/document/{documentId}/operation")
    public void handleOperation(@DestinationVariable String documentId, @Payload Operation operation) {
        
        LinkedHashMap<Long, Operation> operationMap = operations.computeIfAbsent(documentId, k -> new LinkedHashMap<>());
        operationMap.put(operation.getTimestamp(), operation);
        operations.put(documentId, operationMap);

        String viewerCode = crdtManager.getViewerCode(documentId);
        operations.put(viewerCode, operationMap); // If you want to support viewerCode as well

        if (operation.getOp().equals("insert")) {
            // Handle insert operation
            crdtManager.insertRemote(documentId, operation); // Apply the operation to the CRDT manager
            System.out.println("Insert operation: " + operation.getValue() + " ID = " + operation.getID());
        } else if (operation.getOp().equals("delete")) {
            // Handle delete operation
            crdtManager.deleteRemote(documentId, operation); // Apply the operation to the CRDT manager
            System.out.println("Delete operation: " + operation.getValue() + " ID = " + operation.getID());
        }

        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/operation", operation);
        messagingTemplate.convertAndSend("/topic/document/" + viewerCode + "/operation", operation);

    }

    @MessageMapping("/document/{documentId}/sync")
    public void handleSync(@DestinationVariable String documentId, @Payload String userId) {
        System.out.println("Received sync request for document " + documentId);

        LinkedHashMap<Long, Operation> operationMap = operations.get(documentId); // Retrieve the operations for the session

        if (operationMap != null) {
            // Send the operations to the client
            for (Operation op : operationMap.values()) {
                messagingTemplate.convertAndSend("/topic/document/" + documentId + "/sync", op);
                System.out.println("Operation sync: " + op.getOp() + " from user: " + op.getID() + " with timestamp: " + op.getTimestamp());
            }
            System.out.println("Sent sync response for document " + documentId + ": " + operationMap);
        } else {
            System.out.println("No operations found for document " + documentId);
        }
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

    @MessageMapping("/session/{sessionCode}/cursor")
    public void handleCursorPosition(@DestinationVariable String sessionCode, @Payload Map<String, String> cursorPositions) {
        System.out.println("Received cursor positions for session " + sessionCode + ": " + cursorPositions);

        String viewerCode = crdtManager.getViewerCode(sessionCode);

        messagingTemplate.convertAndSend("/topic/session/" + sessionCode + "/cursor", cursorPositions);
        messagingTemplate.convertAndSend("/topic/session/" + viewerCode + "/cursor", cursorPositions);

        System.out.println("Broadcasted cursor positions to session " + sessionCode);
    }
    
}


