package app.Client;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.WebSocketClient;

import java.lang.reflect.Type;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.common.lang.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;

import app.Operation;
import app.CRDTfiles.CRDTManager;
import app.EditorUI;
import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

public class ClientWebsocket {
    
    StompSession stompSession;
    WebSocketStompClient stompClient;
    CRDTManager crdtManager;
    EditorUI editorUI;
    // Use a counter to batch operations for UI updates
    private AtomicInteger operationsReceived = new AtomicInteger(0);
    private static final int BATCH_SIZE = 5; // Update UI after every 5 operations or when idle

    public void connectToWebSocket(EditorUI editorUI) {
        this.editorUI = editorUI;
        try {
            List<Transport> transports = Collections.singletonList(new WebSocketTransport(new StandardWebSocketClient()));
            SockJsClient sockJsClient = new SockJsClient(transports);
            stompClient = new WebSocketStompClient(sockJsClient);

            // Add both StringMessageConverter and MappingJackson2MessageConverter
            List<MessageConverter> converters = new ArrayList<>();
            converters.add(new StringMessageConverter()); // For plain text messages
            converters.add(new MappingJackson2MessageConverter()); // For JSON messages
            stompClient.setMessageConverter(new CompositeMessageConverter(converters));

            // Connect to the server
            String url = "ws://localhost:8080/ws";
            stompSession = stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
                @Override
                public void handleException(@NonNull StompSession session, @NonNull StompCommand command,
                                            @NonNull StompHeaders headers, @NonNull byte[] payload, @NonNull Throwable exception) {
                    System.err.println("Error in STOMP session: " + exception.getMessage());
                    exception.printStackTrace();
                }
            }).get();

            System.out.println("Connected to WebSocket server at " + url);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void subscribeToDocument(String DocumentCode, CRDTManager crdtManager) {
        this.crdtManager = crdtManager;
        try {
            // Subscribe to the poll topic
            String topic = "/topic/document/" + DocumentCode + "/operation";
            
            stompSession.subscribe(topic, new StompFrameHandler() {
                @Override
                @NonNull
                public Type getPayloadType(@NonNull StompHeaders headers) {
                    return Operation.class;
                }

                @Override
                public void handleFrame(@NonNull StompHeaders headers, @NonNull Object payload) {
                    Operation result = (Operation) payload;

                    System.out.println("Received operation: " + result.getOp() + " from user: " + result.getID() + " with value: " + result.getValue());
                    
                    if (result.getOp().equals("delete")) {
                        crdtManager.deleteRemote(result);
                        // Get the latest document text after each delete
                        String currentText = crdtManager.getDocumentText();
                        // Update the UI with the current text
                        editorUI.updateDocumentWithString(currentText);
                    }

                    if (result.getID() != crdtManager.getLocalUserId()) {
                        if (result.getOp().equals("insert")) {
                            crdtManager.insertRemote(result);
                            crdtManager.printCRDT();
                            // Get the latest document text after each insert
                            String currentText = crdtManager.getDocumentText();
                            // Update the UI with the current text
                            editorUI.updateDocumentWithString(currentText);
                        }  else if (result.getOp().equals("sync")) {
                            // Only apply syncs from other users, not our own bounced back
                            if (result.getID() != crdtManager.getLocalUserId()) {
                                System.out.println("Processing sync from user " + result.getID());
                                // Process the sync only if it's from another user
                                if (result.getCrdtState() != null && !result.getCrdtState().equals("{}")) {
                                    // Apply the sync...
                                    // This would require implementing a method to update the local CRDT
                                    crdtManager.updateFromSerialized(result.getCrdtState());
                                    
                                    // Update UI with synced state
                                    String currentText = crdtManager.getDocumentText();
                                    editorUI.updateDocumentWithString(currentText);
                                }
                            }
                        }
                        
                    }
                }
            });
            System.out.println("Subscribed to Document: " + DocumentCode);
        } catch (Exception e) {
            System.err.println("Error subscribing to poll: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void subscribeToActiveUsers(int userID, String sessionCode, ListView<String> activeUsersList) {
        try {
            String topic = "/topic/session/" + sessionCode + "/users";

            stompSession.subscribe(topic, new StompFrameHandler() {
                @Override
                @NonNull
                public Type getPayloadType(@NonNull StompHeaders headers) {
                    return List.class; // Expecting a List<Integer> payload
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(@NonNull StompHeaders headers, @NonNull Object payload) {
                    List<Integer> userIds = (List<Integer>) payload;

                    System.out.println("Received active user IDs: " + userIds);

                    // Update the active users list in the UI
                    Platform.runLater(() -> {
                        for (Integer id : userIds) {
                            String userIdString = "User" + id;
                            if (!activeUsersList.getItems().contains(userIdString)) {
                                activeUsersList.getItems().add(userIdString);
                                System.out.println("User joined: " + userIdString);
                            }
                        }
                    });
                }
            });

            // Customize the ListView to display each user with a different color
            activeUsersList.setCellFactory(listView -> new ListCell<String>() {
                private final Map<String, String> userColors = new HashMap<>();
                private final List<String> colors = List.of(
                    "#FF5733", "#33FF57", "#3357FF", "#F333FF", "#FFC300", "#33FFF5", "#FF33A8"
                );

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);

                        // Assign a unique color to each user
                        userColors.putIfAbsent(item, colors.get(userColors.size() % colors.size()));
                        setStyle("-fx-text-fill: " + userColors.get(item) + ";");
                    }
                }
            });

            System.out.println("Subscribed to active users topic: " + topic);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error subscribing to active users topic: " + e.getMessage());
        }
    }

    public void subscribeToCursor(String sessionCode, ListView<String> activeUsersList) {
        try {
            String topic = "/topic/session/" + sessionCode + "/cursor";

            stompSession.subscribe(topic, new StompFrameHandler() {
                @Override
                @NonNull
                public Type getPayloadType(@NonNull StompHeaders headers) {
                    return Map.class; // Expecting a Map<String, Integer> payload
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(@NonNull StompHeaders headers, @NonNull Object payload) {
                    Map<String, Object> cursorPositionsRaw = (Map<String, Object>) payload;
                    System.out.println("Received cursor positions: " + cursorPositionsRaw);

                    Platform.runLater(() -> {
                        for (Map.Entry<String, Object> entry : cursorPositionsRaw.entrySet()) {
                            int userId = Integer.parseInt(entry.getKey());
                            String posString = entry.getValue().toString(); // Should be "row,column"
                            String[] parts = posString.split(",");
                            int lineNumber = parts.length > 0 ? Integer.parseInt(parts[0]) : -1;
                            int columnNumber = parts.length > 1 ? Integer.parseInt(parts[1]) : -1;
                            String userIdString = "User" + userId;
                            String displayText = userIdString + " (Line: " + lineNumber + ", Col: " + columnNumber + ")";

                            int index = -1;
                            for (int i = 0; i < activeUsersList.getItems().size(); i++) {
                                if (activeUsersList.getItems().get(i).startsWith(userIdString)) {
                                    index = i;
                                    break;
                                }
                            }

                            if (index != -1) {
                                activeUsersList.getItems().set(index, displayText);
                            } else {
                                activeUsersList.getItems().add(displayText);
                            }
                        }
                    });
                }
            });

            System.out.println("Subscribed to cursor positions topic: " + topic);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error subscribing to cursor positions topic: " + e.getMessage());
        }
    }

    private String serializeCRDT(app.CRDTfiles.CRDT crdt) {
    try {
        // Create a serializable representation of the CRDT
        Map<String, Object> serializedCRDT = new HashMap<>();
        
        // Serialize nodes
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map.Entry<app.CRDTfiles.CRDT.CharacterId, app.CRDTfiles.CRDT.Node> entry : crdt.nodeMap.entrySet()) {
            app.CRDTfiles.CRDT.CharacterId id = entry.getKey();
            app.CRDTfiles.CRDT.Node node = entry.getValue();
            
            if (id == null) continue; // Skip root node
            
            Map<String, Object> nodeMap = new HashMap<>();
            nodeMap.put("id_timestamp", node.id.timestamp);
            nodeMap.put("id_userId", node.id.userId);
            nodeMap.put("value", String.valueOf(node.value));
            nodeMap.put("isDeleted", node.isDeleted);
            
            if (node.parentId != null) {
                nodeMap.put("parentId_timestamp", node.parentId.timestamp);
                nodeMap.put("parentId_userId", node.parentId.userId);
            } else {
                nodeMap.put("parentId_timestamp", -1);
                nodeMap.put("parentId_userId", -1);
            }
            
            nodes.add(nodeMap);
        }
        
        serializedCRDT.put("nodes", nodes);
        
        // Use Jackson to convert to JSON
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(serializedCRDT);
    } catch (Exception e) {
        System.err.println("Error serializing CRDT: " + e.getMessage());
        e.printStackTrace();
        return "{}";
    }
}


    public void syncFullCRDT(String documentCode) {
        // Serialize entire CRDT and send to server
        Operation syncOp = new Operation();
        syncOp.setOp("sync");
        syncOp.setID(crdtManager.getLocalUserId());
        //syncOp.setVersion(crdtManager.getCurrentVersion()); may need later, must implement setVersion
        // Need to implement serialization of CRDT
        syncOp.setCrdtState(serializeCRDT(crdtManager.getCRDT()));
        sendOperation(syncOp, documentCode);
    }


    public void sendOperation(Operation operation, String DocumentCode) {
        try {
            // Send the operation to the server
            String destination = "/app/document/" + DocumentCode + "/operation";
            stompSession.send(destination, operation);
        } catch (Exception e) {
            System.err.println("Error sending operation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendSyncRequest(String DocumentCode) {
        try {
            String topic = "/topic/document/" + DocumentCode + "/sync";
            stompSession.subscribe(topic, new StompFrameHandler() {
                @Override
                @NonNull
                public Type getPayloadType(@NonNull StompHeaders headers) {
                    // Expect a single Operation object per message
                    return Operation.class;
                }

                @Override
                public void handleFrame(@NonNull StompHeaders headers, @NonNull Object payload) {
                    Operation op = (Operation) payload;
                    System.out.println("Operation sync: " + op.getOp() + " from user: " + op.getID() + " with timestamp: " + op.getTimestamp());
                    // Handle the operation as needed (apply to CRDT, update UI, etc.)
                    
                    if (op.getOp().equals("delete")) {
                        crdtManager.deleteRemote(op);
                    }

                    // if(op.getID() == crdtManager.getLocalUserId()) {
                    //     // Ignore operations from the local user
                    //     return;
                    // }
                    if (op.getOp().equals("insert")) {
                        crdtManager.insertRemote(op);
                    }
                    // Update UI if needed
                    //String currentText = crdtManager.getDocumentText();
                    editorUI.updateDocumentAfterSync();
                }
            });

            // Send a sync request to the server
            String destination = "/app/document/" + DocumentCode + "/sync";
            stompSession.send(destination, "sync");

        } catch (Exception e) {
            System.err.println("Error sending sync request: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendUserId(int userId, String sessionCode) {
        try {
            // Send the user ID to the server
            String destination = "/app/session/" + sessionCode + "/users";
            stompSession.send(destination, String.valueOf(userId));
        } catch (Exception e) {
            System.err.println("Error sending user ID: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendCursorPosition(int userId, String sessionCode, int lineNumber, int columnPosition) {
        try {
            String destination = "/app/session/" + sessionCode + "/cursor";
            // Send as a map of userId -> "row,column"
            String positionString = lineNumber + "," + columnPosition;
            Map<String, String> cursorPositionMap = Collections.singletonMap(String.valueOf(userId), positionString);
            stompSession.send(destination, cursorPositionMap);
            System.out.println("Sent cursor position: " + positionString + " for user: " + userId);
        } catch (Exception e) {
            System.err.println("Error sending cursor position: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void close() {
        this.stompSession.disconnect();
    }
}