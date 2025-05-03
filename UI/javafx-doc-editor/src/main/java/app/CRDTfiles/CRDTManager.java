package app.CRDTfiles;
import org.springframework.messaging.simp.stomp.StompSession;

import app.Operation;
import app.Client.ClientWebsocket;
import java.util.Stack;

public class CRDTManager {
    private final CRDT crdt;
    private final int localUserId;
    private final Stack<Operation> undoStack = new Stack<>();
    private final Stack<Operation> redoStack = new Stack<>();
    StompSession stompSession;
    ClientWebsocket clientWebsocket;

    public CRDTManager(int localUserId, ClientWebsocket clientWebsocket) {
        this.clientWebsocket = clientWebsocket;
        this.localUserId = localUserId;
        this.crdt = new CRDT();
    }

    public CRDTManager(int localUserId, ClientWebsocket clientWebsocket, String text, Boolean local, String documentCode) {
        this.clientWebsocket = clientWebsocket;
        this.localUserId = localUserId;
        this.crdt = new CRDT();

        System.out.println("Text length: " + text.length());
        System.out.println("Text: " + text);
        for (int i = 0; i < text.length(); i++) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread was interrupted: " + e.getMessage());
            }

            char value = text.charAt(i);
            long timestamp = System.currentTimeMillis() + i; // Ensure unique timestamp for each character
            CRDT.CharacterId id = new CRDT.CharacterId(timestamp, localUserId);
            CRDT.CharacterId parentId = (i > 0) 
                ? new CRDT.CharacterId(timestamp - 1, localUserId) 
                : null;

            Operation op = new Operation();
            op.setOp("insert");
            op.setID(id.userId);
            op.setTimestamp(id.timestamp);
            op.setValue(String.valueOf(value));

            if (parentId != null) {
                op.setParentID(parentId.userId);
                op.setParentTimestamp(parentId.timestamp);
            } else {
                op.setParentID(-1);
                op.setParentTimestamp(-1);
            }

            if (local) {
                insertLocalAtPosition(value, i, documentCode);
                System.out.println("Inserted locally: " + value + " at position " + i);
            } else {
                insertRemote(op);
            }
        }

        System.out.println("--------------------- CRDT Imported ---------------------");
        printCRDT();
        System.out.println("----------------------------------------------------------");
    }

    /**
     * Insert a character locally at the specified position and broadcast the operation
     * @param value Character to insert
     * @param position Position where to insert (0 = beginning of document)
     * @param documentCode Document code for broadcasting
     */
    public void insertLocalAtPosition(char value, int position, String documentCode) {
        long timestamp = System.currentTimeMillis();
        CRDT.CharacterId id = new CRDT.CharacterId(timestamp, localUserId);

        crdt.insert(id, value, position);
        CRDT.CharacterId parentId = crdt.nodeMap.get(id).parentId;

        Operation op = new Operation();
        op.setOp("insert");
        op.setID(id.userId);
        op.setTimestamp(id.timestamp);
        op.setValue(String.valueOf(value));

        if (parentId != null) {
            op.setParentID(parentId.userId);
            op.setParentTimestamp(parentId.timestamp);
        } else {
            op.setParentID(-1);
            op.setParentTimestamp(-1);
        }

        undoStack.push(op); // Push the operation onto the undo stack
        redoStack.clear(); // Clear the redo stack when a new operation is performed

        clientWebsocket.sendOperation(op, documentCode);
    }

    /**
     * Delete a character at the specified position and broadcast the operation
     * @param position Position to delete (0 = first character)
     * @param documentCode Document code for broadcasting
     * @return Whether the deletion was successful
     */
    public boolean deleteLocalAtPosition(int position, String documentCode) {
        CRDT.CharacterId id = crdt.getCharacterIdAtPosition(position);
        if (id == null) {
            System.out.println("Delete failed: No character at position " + position);
            return false;
        }

        char value = crdt.getVisibleString().charAt(position);
        boolean success = crdt.delete(id);
        if (success) {
            Operation op = new Operation();
            op.setOp("delete");
            op.setID(id.userId);
            op.setTimestamp(id.timestamp);
            op.setValue(String.valueOf(value));
            op.setOriginalPosition(position); // Store the original position

            undoStack.push(op); // Push the operation onto the undo stack
            redoStack.clear(); // Clear the redo stack when a new operation is performed

            clientWebsocket.sendOperation(op, documentCode);
            return true;
        } else {
            System.out.println("Delete failed: ID already deleted: " + id);
            return false;
        }
    }

    /**
     * Process a remote insert operation
     */
    public void insertRemote(Operation op) {
        CRDT.CharacterId id = new CRDT.CharacterId(op.getTimestamp(), op.getID());
        CRDT.CharacterId parentId = (op.getParentID() != -1)
            ? new CRDT.CharacterId(op.getParentTimestamp(), op.getParentID())
            : null;
            
        // Create the new node and add it to the nodeMap
        CRDT.Node node = new CRDT.Node(id, parentId, op.getValue().charAt(0), false);
        crdt.nodeMap.put(id, node);

        // Add the node as a child of its parent
        CRDT.Node parent = crdt.nodeMap.get(parentId);
        if (parent != null) {
            parent.addChild(node);
        } else {
            // If parent is null or not found, add to root
            crdt.nodeMap.get(null).addChild(node);
        }
    }

    /**
     * Process a remote delete operation
     */
    public void deleteRemote(Operation op) {
        CRDT.CharacterId id = new CRDT.CharacterId(op.getTimestamp(), op.getID());
        crdt.delete(id);
    }

    /**
     * Get the current document text
     */
    public String getDocumentText() {
        String text = crdt.getVisibleString();
        System.out.println("CRDT document text: \"" + text + "\"");
        return text;
    }

    /**
     * Print the CRDT tree structure for debugging
     */
    public void printCRDT() {
        synchronized (crdt) {
            crdt.printTree();
        }
    }

    /**
     * Get the CRDT instance
     */
    public CRDT getCRDT() {
        return crdt;
    }
    
    /**
     * For backward compatibility with existing code
     * @deprecated Use insertLocalAtPosition instead
     */
    @Deprecated
    public void insertLocal(char value, int userId, String documentCode) {
        // Insert at the end of the document
        String currentText = getDocumentText();
        insertLocalAtPosition(value, currentText.length(), documentCode);
    }
    
    /**
     * For backward compatibility with existing code
     * @deprecated Use deleteLocalAtPosition instead
     */
    @Deprecated
    public void deleteLocal(CRDT.CharacterId id, String documentCode) {
        boolean deleted = crdt.delete(id);
        if (deleted) {
            Operation op = new Operation();
            op.setOp("delete");
            op.setID(id.userId);
            op.setTimestamp(id.timestamp);
            clientWebsocket.sendOperation(op, documentCode);
        } else {
            System.out.println("Delete failed: ID not found or already deleted: " + id);
        }
    }

    public int getLocalUserId() {
        return localUserId;
    }

    public void undo(String documentCode) {
        if (!undoStack.isEmpty()) {
            Operation op = undoStack.pop();
            Operation reverseOp = null;

            if (op.getOp().equals("insert")) {
                // Reverse insert -> delete
                CRDT.CharacterId id = new CRDT.CharacterId(op.getTimestamp(), op.getID());
                crdt.delete(id);
                reverseOp = new Operation("delete", op.getID(), op.getTimestamp(), op.getValue(), -1, -1);
            } else if (op.getOp().equals("delete")) {
                // Reverse delete -> insert
                CRDT.CharacterId id = new CRDT.CharacterId(op.getTimestamp(), op.getID());
                int originalPosition = op.getOriginalPosition(); // Retrieve the original position
                crdt.insert(id, op.getValue().charAt(0), originalPosition);
                reverseOp = new Operation("insert", op.getID(), op.getTimestamp(), op.getValue(), -1, -1);
                reverseOp.setOriginalPosition(originalPosition); // Include the original position
            }

            if (reverseOp != null) {
                redoStack.push(op); // Push the original operation onto the redo stack
                clientWebsocket.sendOperation(reverseOp, documentCode); // Broadcast the reverse operation
            }
        } else {
            System.out.println("Undo stack is empty.");
        }
    }

    public void redo(String documentCode) {
        if (!redoStack.isEmpty()) {
            Operation op = redoStack.pop();
            Operation reverseOp = null;

            if (op.getOp().equals("insert")) {
                // Redo insert
                CRDT.CharacterId id = new CRDT.CharacterId(op.getTimestamp(), op.getID());
                int position = crdt.getPositionForCharacterId(id);

                // If the position is invalid, calculate the correct position
                if (position == -1) {
                    position = crdt.getOrderedVisibleNodes().size(); // Insert at the end if position is not found
                }

                crdt.insert(id, op.getValue().charAt(0), position);
                reverseOp = new Operation("delete", op.getID(), op.getTimestamp(), op.getValue(), -1, -1);
            } else if (op.getOp().equals("delete")) {
                // Redo delete
                CRDT.CharacterId id = new CRDT.CharacterId(op.getTimestamp(), op.getID());
                crdt.delete(id);
                reverseOp = new Operation("insert", op.getID(), op.getTimestamp(), op.getValue(), -1, -1);
            }

            if (reverseOp != null) {
                undoStack.push(op); // Push the original operation back onto the undo stack
                clientWebsocket.sendOperation(op, documentCode); // Broadcast the operation
            }
        } else {
            System.out.println("Redo stack is empty.");
        }
    }
}