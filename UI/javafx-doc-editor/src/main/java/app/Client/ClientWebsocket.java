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
                    if (result.getID() != crdtManager.getLocalUserId()) {
                        if (result.getOp().equals("insert")) {
                            crdtManager.insertRemote(result);
                            crdtManager.printCRDT();
                            // Get the latest document text after each insert
                            String currentText = crdtManager.getDocumentText();
                            // Update the UI with the current text
                            editorUI.updateDocumentWithString(currentText);
                        } else if (result.getOp().equals("delete")) {
                            crdtManager.deleteRemote(result);
                            // Get the latest document text after each delete
                            String currentText = crdtManager.getDocumentText();
                            // Update the UI with the current text
                            editorUI.updateDocumentWithString(currentText);
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
                    Map<String, Integer> cursorPositionsString = (Map<String, Integer>) payload;

                    // Convert Map<String, Integer> to Map<Integer, Integer>
                    Map<Integer, Integer> cursorPositions = new HashMap<>();
                    for (Map.Entry<String, Integer> entry : cursorPositionsString.entrySet()) {
                        cursorPositions.put(Integer.parseInt(entry.getKey()), entry.getValue());
                    }

                    System.out.println("Received cursor positions: " + cursorPositions);

                    // Update the active users list in the UI
                    Platform.runLater(() -> {
                        for (Map.Entry<Integer, Integer> entry : cursorPositions.entrySet()) {
                            int userId = entry.getKey();
                            int lineNumber = entry.getValue();
                            String userIdString = "User" + userId;
                            String displayText = userIdString + " (Line: " + lineNumber + ")";
                            
                            // Update the activeUsersList with the cursor position
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

    public void sendCursorPosition(int userId, String sessionCode, int lineNumber) {
        try {
            String destination = "/app/session/" + sessionCode + "/cursor";
            Map<Integer, Integer> cursorPositionMap = Collections.singletonMap(userId, lineNumber);
            stompSession.send(destination, cursorPositionMap);
            System.out.println("Sent cursor position (line): " + lineNumber + " for user: " + userId);
        } catch (Exception e) {
            System.err.println("Error sending cursor position: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void close() {
        this.stompSession.disconnect();
    }
}