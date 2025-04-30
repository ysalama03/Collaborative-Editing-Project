package app.CRDTfiles;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class CRDT {
    private final int userId;
    private long clock;
    private final Node root;
    private final ConcurrentHashMap<CharacterId, Node> nodeMap; // Changed to ConcurrentHashMap
    private final Deque<Operation> undoStack;
    private final Deque<Operation> redoStack;

    /**
     * Creates a new CRDT instance with the specified user ID.
     *
     * @param userId the unique ID of the user
     */
    public CRDT(int userId) {
        this.userId = userId;
        this.clock = 0;
        this.root = new Node(null, null, (char) 0, false);
        this.nodeMap = new ConcurrentHashMap<>(); // Initialize as ConcurrentHashMap
        this.undoStack = new ArrayDeque<>();
        this.redoStack = new ArrayDeque<>();
    }

    /**
     * Represents a character ID in the CRDT.
     */
    public static class CharacterId implements Comparable<CharacterId> {
        private final int userId;
        private final long timestamp;

        public CharacterId(int userId, long timestamp) {
            this.userId = userId;
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CharacterId that = (CharacterId) o;
            return userId == that.userId && timestamp == that.timestamp;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, timestamp);
        }

        @Override
        public int compareTo(CharacterId other) {
            // Compare by timestamp first (descending)
            int timeCompare = Long.compare(other.timestamp, this.timestamp);
            if (timeCompare != 0) {
                return timeCompare;
            }
            // If timestamps are equal, compare by userId
            return Integer.compare(this.userId, other.userId);
        }

        @Override
        public String toString() {
            return "[" + userId + "," + timestamp + "]";
        }
    }

    /**
     * Represents a node in the CRDT tree.
     */
    public static class Node {
        private final CharacterId id;
        private final CharacterId parentId;
        private final char value;
        private boolean deleted;
        private final List<Node> children;

        public Node(CharacterId id, CharacterId parentId, char value, boolean deleted) {
            this.id = id;
            this.parentId = parentId;
            this.value = value;
            this.deleted = deleted;
            this.children = new ArrayList<>();
        }

        public CharacterId getId() {
            return id;
        }

        public CharacterId getParentId() {
            return parentId;
        }

        public char getValue() {
            return value;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public void setDeleted(boolean deleted) {
            this.deleted = deleted;
        }

        public List<Node> getChildren() {
            return children;
        }

        public void addChild(Node child) {
            children.add(child);
            // Sort children according to CRDT ordering rules
            children.sort(Comparator.comparing(Node::getId));
        }
    }

    /**
     * Abstract base class for operations on the CRDT.
     */
    public abstract static class Operation {
        protected final CharacterId targetId;
        protected final CRDT crdt;
        
        public Operation(CharacterId targetId, CRDT crdt) {
            this.crdt = crdt;
            this.targetId = targetId;
        }
        
        public abstract Operation reverse();
        public abstract void apply(CRDT crdt);
    }

    /**
     * Insert operation.
     */
    public static class InsertOperation extends Operation {
        private final CharacterId parentId;
        private final char value;
        
        public InsertOperation(CRDT crdt, CharacterId targetId, CharacterId parentId, char value) {
            super(targetId, crdt);
            this.parentId = parentId;
            this.value = value;
        }
        
        @Override
        public Operation reverse() {
            return new DeleteOperation(crdt, targetId);
        }
        
        @Override
        public void apply(CRDT crdt) {
            crdt.insertLocal(targetId, parentId, value);
        }
    }

    /**
     * Delete operation.
     */
    public static class DeleteOperation extends Operation {
        public DeleteOperation(CRDT crdt, CharacterId targetId) {
            super(targetId, crdt);
        }
        
        @Override
        public Operation reverse() {
            Node node = null;
            try {
                node = crdt.nodeMap.get(targetId);
            } catch (Exception e) {
                // Node might not exist yet
            }
            
            if (node != null) {
                return new InsertOperation(crdt, targetId, node.getParentId(), node.getValue());
            }
            return null; // Cannot reverse if node doesn't exist
        }
        
        @Override
        public void apply(CRDT crdt) {
            crdt.deleteLocal(targetId);
        }
    }

    /**
     * Inserts a character after the specified position.
     *
     * @param position the position after which to insert the character
     * @param value the character to insert
     * @return the operation that was performed
     */
    public Operation insertAfterPosition(int position, char value) {
        // Find the node at the specified position
        List<Node> flatTree = flattenTree();
        if (position < 0 || position > flatTree.size()) {
            throw new IndexOutOfBoundsException("Position out of bounds");
        }
        
        CharacterId parentId = position == 0 ? null : flatTree.get(position - 1).getId();
        CharacterId newId = new CharacterId(userId, clock++);
        
        InsertOperation op = new InsertOperation(this, newId, parentId, value);
        op.apply(this);
        
        // Save operation for undo
        undoStack.push(op);
        redoStack.clear(); // Clear redo stack on new operation
        
        return op;
    }

    /**
     * Deletes the character at the specified position.
     *
     * @param position the position of the character to delete
     * @return the operation that was performed
     */
    public Operation deleteAtPosition(int position) {
        // Find the node at the specified position
        List<Node> flatTree = flattenTree();
        if (position < 0 || position >= flatTree.size()) {
            throw new IndexOutOfBoundsException("Position out of bounds");
        }
        
        CharacterId targetId = flatTree.get(position).getId();
        DeleteOperation op = new DeleteOperation(this, targetId);
        op.apply(this);
        
        // Save operation for undo
        undoStack.push(op);
        redoStack.clear(); // Clear redo stack on new operation
        
        return op;
    }

    /**
     * Undoes the last operation performed by this user.
     *
     * @return the operation that was undone, or null if there is nothing to undo
     */
    public Operation undo() {
        if (undoStack.isEmpty()) {
            return null;
        }
        
        Operation lastOp = undoStack.pop();
        Operation reverseOp = lastOp.reverse();
        
        if (reverseOp != null) {
            reverseOp.apply(this);
            redoStack.push(lastOp);
            return reverseOp;
        }
        
        return null;
    }

    /**
     * Redoes the last undone operation.
     *
     * @return the operation that was redone, or null if there is nothing to redo
     */
    public Operation redo() {
        if (redoStack.isEmpty()) {
            return null;
        }
        
        Operation redoOp = redoStack.pop();
        redoOp.apply(this);
        undoStack.push(redoOp);
        
        return redoOp;
    }

    /**
     * Inserts a character at the specified location in the CRDT.
     *
     * @param id the ID of the new character
     * @param parentId the ID of the parent character
     * @param value the character to insert
     */
    public void insertLocal(CharacterId id, CharacterId parentId, char value) {
        Node node = new Node(id, parentId, value, false);
        nodeMap.put(id, node);
        
        if (parentId == null) {
            // Insert at root
            root.addChild(node);
        } else {
            // Find parent and insert
            Node parent = nodeMap.get(parentId);
            if (parent != null) {
                parent.addChild(node);
            } else {
                // Parent not found, add to pending nodes
                // In a real implementation, you might want to handle this
                throw new IllegalStateException("Parent node not found");
            }
        }
    }

    /**
     * Applies a remote insert operation.
     *
     * @param userId the ID of the user who performed the operation
     * @param timestamp the timestamp of the operation
     * @param parentUserId the user ID component of the parent character's ID
     * @param parentTimestamp the timestamp component of the parent character's ID
     * @param value the character to insert
     */
    public void insertRemote(int userId, long timestamp, Integer parentUserId, Long parentTimestamp, char value) {
        CharacterId id = new CharacterId(userId, timestamp);
        CharacterId parentId = parentUserId != null ? new CharacterId(parentUserId, parentTimestamp) : null;
        
        insertLocal(id, parentId, value);
    }

    /**
     * Marks a character as deleted.
     *
     * @param id the ID of the character to delete
     */
    public void deleteLocal(CharacterId id) {
        Node node = nodeMap.get(id);
        if (node != null) {
            node.setDeleted(true);
        }
    }

    /**
     * Applies a remote delete operation.
     *
     * @param userId the user ID component of the character's ID
     * @param timestamp the timestamp component of the character's ID
     */
    public void deleteRemote(int userId, long timestamp) {
        CharacterId id = new CharacterId(userId, timestamp);
        deleteLocal(id);
    }

    /**
     * Flattens the tree into a list of visible characters.
     *
     * @return a list of nodes in document order
     */
    private List<Node> flattenTree() {
        List<Node> result = new ArrayList<>();
        flattenNode(root, result);
        return result;
    }

    /**
     * Recursively flattens a node and its children.
     *
     * @param node the node to flatten
     * @param result the list to add the flattened nodes to
     */
    private void flattenNode(Node node, List<Node> result) {
        // Skip root node
        if (node.getId() != null && !node.isDeleted()) {
            result.add(node);
        }
        
        // Recursively add children in sorted order
        for (Node child : node.getChildren()) {
            flattenNode(child, result);
        }
    }

    /**
     * Gets the current document text.
     *
     * @return the document text
     */
    public String getText() {
        return flattenTree().stream()
                .map(node -> String.valueOf(node.getValue()))
                .collect(Collectors.joining());
    }

    /**
     * Gets the document size (number of visible characters).
     *
     * @return the document size
     */
    public int size() {
        return flattenTree().size();
    }
    
    /**
     * Creates an operation message for sending to other clients.
     * 
     * @param operation the operation to convert to a message
     * @return a string representation of the operation in JSON format
     */
    public String createOperationMessage(Operation operation) {
        StringBuilder json = new StringBuilder();
        if (operation instanceof InsertOperation) {
            InsertOperation insertOp = (InsertOperation) operation;
            json.append("{\n");
            json.append("  \"Op\":\"insert\",\n");
            json.append("  \"UID\":").append(insertOp.targetId.userId).append(",\n");
            json.append("  \"Clock\":\"").append(insertOp.targetId.timestamp).append("\",\n");
            json.append("  \"Value\":'").append(insertOp.value).append("',\n");
            
            if (insertOp.parentId != null) {
                json.append("  \"Parent\":[")
                    .append(insertOp.parentId.userId).append(",\"")
                    .append(insertOp.parentId.timestamp).append("\"")
                    .append("]\n");
            } else {
                json.append("  \"Parent\":null\n");
            }
            
            json.append("}");
        } else if (operation instanceof DeleteOperation) {
            DeleteOperation deleteOp = (DeleteOperation) operation;
            json.append("{\n");
            json.append("  \"Op\":\"delete\",\n");
            json.append("  \"UID\":").append(deleteOp.targetId.userId).append(",\n");
            json.append("  \"Clock\":\"").append(deleteOp.targetId.timestamp).append("\",\n");
            json.append("  \"ID\":[")
                .append(deleteOp.targetId.userId).append(",\"")
                .append(deleteOp.targetId.timestamp).append("\"")
                .append("]\n");
            json.append("}");
        }
        
        return json.toString();
    }
}
