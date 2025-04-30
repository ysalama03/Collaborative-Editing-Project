package app.CRDTfiles;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import app.Operation;

/**
 * Manager for CRDT collaborative sessions.
 * Handles synchronization between local and remote operations.
 */
@Service
public class CRDTManager {
    private final CRDT crdt;
    private final int userId;
    private final NetworkHandler networkHandler;
    private final Map<Integer, CursorPosition> cursorPositions;
    private final Set<Integer> activeUsers;
    private final AtomicInteger cursorPosition;
    private final ExecutorService executorService;
    private boolean isEditor;
    
    /**
     * Creates a new CRDT manager.
     *
     * @param userId the unique ID of the user
     * @param isEditor whether the user has editor permissions
     * @param networkHandler the handler for network operations
     */
    public CRDTManager(int userId, boolean isEditor, NetworkHandler networkHandler) {
        this.crdt = new CRDT(userId);
        this.userId = userId;
        this.isEditor = isEditor;
        this.networkHandler = networkHandler;
        this.cursorPositions = new ConcurrentHashMap<>();
        this.activeUsers = new HashSet<>();
        this.cursorPosition = new AtomicInteger(0);
        this.executorService = Executors.newSingleThreadExecutor();
        
        // Set up network handler callbacks
        networkHandler.setOnOperationReceived((operation) -> handleRemoteOperation(operation));
        networkHandler.setOnCursorPositionReceived((remoteUserId, position) -> cursorPositions.put(remoteUserId, position));
        networkHandler.setOnUserListReceived((users) -> {
            activeUsers.clear();
            activeUsers.addAll(users);
        });
    }
    
    /**
     * Represents a cursor position and selection.
     */
    public static class CursorPosition {
        private final int position;
        private final int selectionStart;
        private final int selectionEnd;
        
        public CursorPosition(int position, int selectionStart, int selectionEnd) {
            this.position = position;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
        }
        
        public int getPosition() {
            return position;
        }
        
        public int getSelectionStart() {
            return selectionStart;
        }
        
        public int getSelectionEnd() {
            return selectionEnd;
        }
        
        public boolean hasSelection() {
            return selectionStart != selectionEnd;
        }
    }
    
    /**
     * Interface for network operations.
     */
    public interface NetworkHandler {
        /**
         * Called when an operation is received from the network.
         */
        interface OnOperationReceived {
            void onOperationReceived(Operation operation);
        }
        
        /**
         * Called when a cursor position is received from the network.
         */
        interface OnCursorPositionReceived {
            void onCursorPositionReceived(int userId, CursorPosition position);
        }
        
        /**
         * Called when the user list is received from the network.
         */
        interface OnUserListReceived {
            void onUserListReceived(Set<Integer> users);
        }
        
        /**
         * Sets the callback for when an operation is received.
         */
        void setOnOperationReceived(OnOperationReceived callback);
        
        /**
         * Sets the callback for when a cursor position is received.
         */
        void setOnCursorPositionReceived(OnCursorPositionReceived callback);
        
        /**
         * Sets the callback for when the user list is received.
         */
        void setOnUserListReceived(OnUserListReceived callback);
        
        /**
         * Sends an operation to the network.
         */
        void sendOperation(Operation operation);
        
        /**
         * Sends a cursor position to the network.
         */
        void sendCursorPosition(int position, int selectionStart, int selectionEnd);
        
        /**
         * Joins a collaborative session.
         */
        void joinSession(String sessionCode);
        
        /**
         * Requests sharing codes for a document.
         */
        void requestSharingCodes();
    }
    
    /**
     * Handles an operation from the network.
     *
     * @param operation the operation received from the network
     */
    public void handleRemoteOperation(Operation operation) {
        executorService.submit(() -> {
            String op = operation.getOp();
            int remoteUserId = operation.getID();
            long timestamp = operation.getTimestamp();
            
            if ("insert".equals(op)) {
                // Handle insert operation
                char value = operation.getValue().charAt(remoteUserId);
                   
                int parentUserId = operation.getParentID();
                long parentTimestamp = operation.getParentTimestamp();
                
                
                crdt.insertRemote(remoteUserId, timestamp, parentUserId, parentTimestamp, value);
            } else if ("delete".equals(op)) {
                // Handle delete operation
                crdt.deleteRemote(remoteUserId, timestamp);
            }
            
            // Notify UI that document has changed
            notifyDocumentChanged();
        });
    }
    
    /**
     * Notifies that the document has changed.
     * In a real implementation, this would update the UI.
     */
    private void notifyDocumentChanged() {
        // This would be implemented by the UI
    }
    
    /**
     * Inserts a character at the current cursor position.
     *
     * @param c the character to insert
     * @return true if the insertion was successful
     */
    public boolean insertCharacter(char c) {
        if (!isEditor) {
            return false;
        }
        
        int pos = cursorPosition.get();
        try {
            CRDT.Operation operation = crdt.insertAfterPosition(pos, c);
            cursorPosition.incrementAndGet();
            
            // Convert operation to network message and send
            String message = crdt.createOperationMessage(operation);
            Operation operationMap = parseOperationMessage(message);
            networkHandler.sendOperation(operationMap);
            
            // Notify UI that document has changed
            notifyDocumentChanged();
            return true;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }
    
    /**
     * Deletes the character at the current cursor position.
     *
     * @return true if the deletion was successful
     */
    public boolean deleteCharacter() {
        if (!isEditor) {
            return false;
        }
        
        int pos = cursorPosition.get() - 1;
        if (pos < 0) {
            return false;
        }
        
        try {
            CRDT.Operation operation = crdt.deleteAtPosition(pos);
            cursorPosition.decrementAndGet();
            
            // Convert operation to network message and send
            String message = crdt.createOperationMessage(operation);
            Operation operationMap = parseOperationMessage(message);
            networkHandler.sendOperation(operationMap);
            
            // Notify UI that document has changed
            notifyDocumentChanged();
            return true;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }
    
    /**
     * Undoes the last operation.
     *
     * @return true if the undo was successful
     */
    public boolean undo() {
        if (!isEditor) {
            return false;
        }
        
        CRDT.Operation operation = crdt.undo();
        if (operation != null) {
            // Update cursor position
            if (operation instanceof CRDT.InsertOperation) {
                cursorPosition.decrementAndGet();
            } else if (operation instanceof CRDT.DeleteOperation) {
                cursorPosition.incrementAndGet();
            }
            
            // Convert operation to network message and send
            String message = crdt.createOperationMessage(operation);
            Operation operationMap = parseOperationMessage(message);
            networkHandler.sendOperation(operationMap);
            
            // Notify UI that document has changed
            notifyDocumentChanged();
            return true;
        }
        
        return false;
    }
    
    /**
     * Redoes the last undone operation.
     *
     * @return true if the redo was successful
     */
    public boolean redo() {
        if (!isEditor) {
            return false;
        }
        
        CRDT.Operation operation = crdt.redo();
        if (operation != null) {
            // Update cursor position
            if (operation instanceof CRDT.InsertOperation) {
                cursorPosition.incrementAndGet();
            } else if (operation instanceof CRDT.DeleteOperation) {
                cursorPosition.decrementAndGet();
            }
            
            // Convert operation to network message and send
            String message = crdt.createOperationMessage(operation);
            Operation operationMap = parseOperationMessage(message);
            networkHandler.sendOperation(operationMap);
            
            // Notify UI that document has changed
            notifyDocumentChanged();
            return true;
        }
        
        return false;
    }
    
    /**
     * Updates the cursor position.
     *
     * @param position the new cursor position
     * @param selectionStart the start of the selection
     * @param selectionEnd the end of the selection
     */
    public void updateCursorPosition(int position, int selectionStart, int selectionEnd) {
        cursorPosition.set(position);
        networkHandler.sendCursorPosition(position, selectionStart, selectionEnd);
    }
    
    /**
     * Gets the current document text.
     *
     * @return the document text
     */
    public String getText() {
        return crdt.getText();
    }
    
    /**
     * Gets the cursor positions of all users.
     *
     * @return a map of user IDs to cursor positions
     */
    public Map<Integer, CursorPosition> getCursorPositions() {
        return new HashMap<>(cursorPositions);
    }
    
    /**
     * Gets the list of active users.
     *
     * @return the list of active users
     */
    public Set<Integer> getActiveUsers() {
        return new HashSet<>(activeUsers);
    }
    
    /**
     * Sets whether the user is an editor.
     *
     * @param isEditor whether the user is an editor
     */
    public void setEditor(boolean isEditor) {
        this.isEditor = isEditor;
    }
    
    /**
     * Parses an operation message into a map.
     *
     * @param message the message to parse
     * @return a map representation of the operation
     */
    private Operation parseOperationMessage(String message) {
        // In a real implementation, this would use a JSON parser like Gson
        // For this example, we'll simulate it with a simple implementation
        Operation result = new Operation();
        
        if (message.contains("\"Op\":\"insert\"")) {
            result.setOp("insert");;
            
            // Extract UID
            int uidStart = message.indexOf("\"UID\":") + 6;
            int uidEnd = message.indexOf(",", uidStart);
            result.setID(Integer.parseInt(message.substring(uidStart, uidEnd).trim()));
            
            // Extract Clock
            int clockStart = message.indexOf("\"Clock\":\"") + 9;
            int clockEnd = message.indexOf("\"", clockStart);
            result.setTimestamp(clockEnd);//?
            
            // Extract Value
            int valueStart = message.indexOf("\"Value\":'") + 9;
            int valueEnd = message.indexOf("'", valueStart);
            result.setValue(message.substring(valueStart, valueEnd));
            
            // Extract Parent
            if (message.contains("\"Parent\":null")) {
                result.setParentID(-1);
                result.setParentTimestamp(-1);
            } else {
                int parentStart = message.indexOf("\"Parent\":[") + 10;
                int parentEnd = message.indexOf("]", parentStart);
                String parentStr = message.substring(parentStart, parentEnd);
                String[] parts = parentStr.split(",");

                result.setParentID(Integer.parseInt(parts[0].trim()));
                result.setParentTimestamp(Long.parseLong(parts[1].trim()));
            }
        } else if (message.contains("\"Op\":\"delete\"")) {
            result.setOp("delete");
            
            // Extract UID
            int uidStart = message.indexOf("\"UID\":") + 6;
            int uidEnd = message.indexOf(",", uidStart);
            result.setID(Integer.parseInt(message.substring(uidStart, uidEnd).trim()));
            
            // Extract Clock
            int clockStart = message.indexOf("\"Clock\":\"") + 9;
            int clockEnd = message.indexOf("\"", clockStart);
            result.setTimestamp(clockEnd);
            
        }
        
        return result;
    }
    
    /**
     * Joins a collaborative session.
     *
     * @param sessionCode the code for the session to join
     */
    public void joinSession(String sessionCode) {
        networkHandler.joinSession(sessionCode);
    }
    
    /**
     * Requests sharing codes for the document.
     */
    public void requestSharingCodes() {
        networkHandler.requestSharingCodes();
    }
    
    /**
     * Closes the CRDT manager and releases resources.
     */
    public void close() {
        executorService.shutdown();
    }
}
