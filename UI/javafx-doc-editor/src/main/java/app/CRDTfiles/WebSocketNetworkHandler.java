package app.CRDTfiles;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.websocket.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import app.Operation;
/**
 * Network handler implementation using WebSockets.
 */
@ClientEndpoint
public class WebSocketNetworkHandler implements CRDTManager.NetworkHandler {
    private Session session;
    private URI serverUri;
    private String sessionCode;
    private final int userId;
    private final Gson gson;
    private final Queue<Operation> operationsQueue;
    private final Queue<Map<String, Object>> cursorQueue;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private boolean connected;
    private boolean reconnecting;
    
    private OnOperationReceived onOperationReceived;
    private OnCursorPositionReceived onCursorPositionReceived;
    private OnUserListReceived onUserListReceived;
    
    /**
     * Creates a new WebSocket network handler.
     *
     * @param serverUri the URI of the WebSocket server
     * @param userId the unique ID of the user
     */
    public WebSocketNetworkHandler(URI serverUri, int userId) {
        this.serverUri = serverUri;
        this.userId = userId;
        this.gson = new Gson();
        this.operationsQueue = new ConcurrentLinkedQueue<>();
        this.cursorQueue = new ConcurrentLinkedQueue<>();
        this.executorService = Executors.newSingleThreadExecutor();
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        this.connected = false;
        this.reconnecting = false;
        
        // Schedule periodic cursor updates (throttled to reduce network traffic)
        scheduledExecutor.scheduleAtFixedRate(() -> {
            if (connected && !cursorQueue.isEmpty()) {
                Map<String, Object> latestCursor = null;
                while (!cursorQueue.isEmpty()) {
                    latestCursor = cursorQueue.poll();
                }
                
                if (latestCursor != null) {
                    sendMessage("cursor", latestCursor);
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void setOnOperationReceived(OnOperationReceived callback) {
        this.onOperationReceived = callback;
    }
    
    @Override
    public void setOnCursorPositionReceived(OnCursorPositionReceived callback) {
        this.onCursorPositionReceived = callback;
    }
    
    @Override
    public void setOnUserListReceived(OnUserListReceived callback) {
        this.onUserListReceived = callback;
    }
    
    /**
     * Connects to the WebSocket server.
     */
    public void connect() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            session = container.connectToServer(this, serverUri);
            connected = true;
            
            // Process any queued operations
            processQueues();
        } catch (Exception e) {
            e.printStackTrace();
            scheduleReconnect();
        }
    }
    
    /**
     * Schedules a reconnection attempt.
     */
    private void scheduleReconnect() {
        if (!reconnecting) {
            reconnecting = true;
            scheduledExecutor.schedule(() -> {
                reconnecting = false;
                connect();
            }, 5, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Processes queued operations and cursor updates.
     */
    private void processQueues() {
        executorService.submit(() -> {
            // Process operations
            while (!operationsQueue.isEmpty()) {
                Operation operation = operationsQueue.poll();
                sendMessage("operation", operation);
            }
        });
    }
    
    /**
     * Sends a message to the server.
     *
     * @param type the type of message
     * @param data the message data
     */
    private void sendMessage(String type, Object data) {
        if (session != null && session.isOpen()) {
            try {
                JsonObject message = new JsonObject();
                message.addProperty("type", type);
                message.add("data", gson.toJsonTree(data));
                
                session.getAsyncRemote().sendText(gson.toJson(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        this.connected = true;
        
        // Send join message if session code is set
        if (sessionCode != null) {
            JsonObject joinData = new JsonObject();
            joinData.addProperty("code", sessionCode);
            joinData.addProperty("userId", userId);
            
            sendMessage("join", joinData);
        }
        
        // Process any queued operations
        processQueues();
    }
    
    @OnClose
    public void onClose(Session session, CloseReason reason) {
        this.connected = false;
        scheduleReconnect();
    }
    
    @OnError
    public void onError(Session session, Throwable throwable) {
        throwable.printStackTrace();
        this.connected = false;
        scheduleReconnect();
    }
    
    @OnMessage
    public void onMessage(String message) {
        executorService.submit(() -> {
            try {
                JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
                String type = jsonMessage.get("type").getAsString();
                
                switch (type) {
                    case "operation":
                        if (onOperationReceived != null) {
                            Operation operation = gson.fromJson(
                                jsonMessage.get("data"),
                                new TypeToken<Map<String, Object>>() {}.getType()
                            );
                            onOperationReceived.onOperationReceived(operation);
                        }
                        break;
                    
                    case "cursor":
                        if (onCursorPositionReceived != null) {
                            JsonObject data = jsonMessage.getAsJsonObject("data");
                            int cursorUserId = data.get("userId").getAsInt();
                            int position = data.get("position").getAsInt();
                            int selectionStart = data.get("selectionStart").getAsInt();
                            int selectionEnd = data.get("selectionEnd").getAsInt();
                            
                            onCursorPositionReceived.onCursorPositionReceived(
                                cursorUserId,
                                new CRDTManager.CursorPosition(position, selectionStart, selectionEnd)
                            );
                        }
                        break;
                    
                    case "users":
                        if (onUserListReceived != null) {
                            Set<Integer> users = gson.fromJson(
                                jsonMessage.get("data"),
                                new TypeToken<Set<Integer>>() {}.getType()
                            );
                            onUserListReceived.onUserListReceived(users);
                        }
                        break;
                    
                    case "sharingCodes":
                        // Handle sharing codes
                        // This would normally update the UI with the codes
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    @Override
    public void sendOperation(Operation operation) {
        if (connected) {
            sendMessage("operation", operation);
        } else {
            operationsQueue.add(operation);
        }
    }
    
    @Override
    public void sendCursorPosition(int position, int selectionStart, int selectionEnd) {
        Map<String, Object> cursorData = new HashMap<>();
        cursorData.put("userId", userId);
        cursorData.put("position", position);
        cursorData.put("selectionStart", selectionStart);
        cursorData.put("selectionEnd", selectionEnd);
        
        cursorQueue.add(cursorData);
    }
    
    @Override
    public void joinSession(String sessionCode) {
        this.sessionCode = sessionCode;
        
        if (connected) {
            JsonObject joinData = new JsonObject();
            joinData.addProperty("code", sessionCode);
            joinData.addProperty("userId", userId);
            
            sendMessage("join", joinData);
        } else {
            connect();
        }
    }
    
    @Override
    public void requestSharingCodes() {
        sendMessage("requestSharingCodes", null);
    }
    
    /**
     * Closes the network handler and releases resources.
     */
    public void close() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        executorService.shutdown();
        scheduledExecutor.shutdown();
    }
}
