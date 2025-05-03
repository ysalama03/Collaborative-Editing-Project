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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;

import app.Operation;
import app.CRDTfiles.CRDTManager;
import app.EditorUI;

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
            SockJsClient sockJsClient = null;
            List<MessageConverter> converters = new ArrayList<>();   

            ///////////////
            sockJsClient = new SockJsClient(transports);
            stompClient = new WebSocketStompClient(sockJsClient);
            
            converters.add(new MappingJackson2MessageConverter());
            stompClient.setMessageConverter(new MappingJackson2MessageConverter());

            // Connect to the server
            String url = "ws://localhost:8080/ws";
            stompSession = stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
                @Override
                public void handleException(@NonNull StompSession session,@NonNull StompCommand command, @NonNull StompHeaders headers,@NonNull byte[] payload, @NonNull Throwable exception) {
                        System.err.println("Error in STOMP session: " + exception.getMessage());
                    exception.printStackTrace();
                }
            }).get();

            System.out.println("Connected to WebSocket server at " + url);
            //////////////// 
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

    public void close() {
        this.stompSession.disconnect();
    }
}