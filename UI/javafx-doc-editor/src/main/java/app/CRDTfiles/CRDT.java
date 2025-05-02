package app.CRDTfiles;

import java.util.*;

public class CRDT {
    public static class CharacterId implements Comparable<CharacterId> {
        public final long timestamp;
        public final int userId;

        public CharacterId(long timestamp, int userId) {
            this.timestamp = timestamp;
            this.userId = userId;
        }

        @Override
        public int compareTo(CharacterId o) {
            // Modified ordering: Descending timestamp order (newer timestamps first)
            // This implements the ordering rule from the slides
            int cmp = Long.compare(o.timestamp, this.timestamp);
            return cmp != 0 ? cmp : Integer.compare(this.userId, o.userId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CharacterId)) return false;
            CharacterId other = (CharacterId) obj;
            return this.timestamp == other.timestamp && this.userId == other.userId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(timestamp, userId);
        }

        @Override
        public String toString() {
            return "[" + userId + "," + timestamp + "]";
        }
    }

    public static class Node {
        public final CharacterId id;
        public final CharacterId parentId;
        public final char value;
        public boolean isDeleted;
        public final List<Node> children = new ArrayList<>();

        public Node(CharacterId id, CharacterId parentId, char value, boolean isDeleted) {
            this.id = id;
            this.parentId = parentId;
            this.value = value;
            this.isDeleted = isDeleted;
        }

        public void addChild(Node child) {
            int i = 0;
            // Modified ordering: CharacterId.compareTo has been updated so this works correctly
            while (i < children.size() && children.get(i).id.compareTo(child.id) < 0) {
                i++;
            }
            children.add(i, child);
        }
    }

    private final Node root = new Node(null, null, '\0', false);
    public final Map<CharacterId, Node> nodeMap = new HashMap<>();
    private final List<Node> flatOrderedNodes = new ArrayList<>();

    public CRDT() {
        nodeMap.put(null, root);
    }

    /**
     * Insert a character at a specific position in the document
     * @param id The unique identifier for the new character
     * @param value The character value to insert
     * @param position The position at which to insert (0 = beginning)
     */
    public void insert(CharacterId id, char value, int position) {
        // Find the parent node based on position
        CharacterId parentId = getParentIdForPosition(position);
        
        // Create and store the new node
        Node newNode = new Node(id, parentId, value, false);
        nodeMap.put(id, newNode);
        
        // Add the new node to its parent
        Node parentNode = nodeMap.get(parentId);
        parentNode.addChild(newNode);
        
        // Invalidate the cached flattened structure
        flatOrderedNodes.clear();
    }
    
    /**
     * Get the parent ID for inserting at a specific position
     */
    private CharacterId getParentIdForPosition(int position) {
        if (position <= 0) {
            // If inserting at the beginning, parent is root (null)
            return null;
        }
        
        // Get all visible characters in order
        List<Node> visibleNodes = getOrderedVisibleNodes();
        
        // If position is beyond the end, use the last character as parent
        if (position >= visibleNodes.size()) {
            return visibleNodes.isEmpty() ? null : visibleNodes.get(visibleNodes.size() - 1).id;
        }
        
        // Otherwise return the ID of the character at position-1
        return visibleNodes.get(position - 1).id;
    }

    /**
     * Get all visible nodes in document order
     */
    private List<Node> getOrderedVisibleNodes() {
        if (!flatOrderedNodes.isEmpty()) {
            return flatOrderedNodes;
        }
        
        List<Node> result = new ArrayList<>();
        flattenTree(root, result);
        
        this.flatOrderedNodes.clear();
        this.flatOrderedNodes.addAll(result);
        
        return result;
    }
    
    /**
     * Recursively flatten the tree into a list of visible nodes
     */
    private void flattenTree(Node node, List<Node> result) {
        for (Node child : node.children) {
            if (!child.isDeleted) {
                result.add(child);
            }
            flattenTree(child, result);
        }
    }

    public boolean delete(CharacterId id) {
        Node node = nodeMap.get(id);
        if (node == null || node.isDeleted) {
            return false; // not found or already deleted
        }
        node.isDeleted = true;
        // Invalidate the cached flattened structure
        flatOrderedNodes.clear();
        return true;
    }

    public String getVisibleString() {
        StringBuilder sb = new StringBuilder();
        for (Node node : getOrderedVisibleNodes()) {
            sb.append(node.value);
        }
        return sb.toString();
    }

    public CharacterId getCharacterIdAtPosition(int pos) {
        List<Node> visibleNodes = getOrderedVisibleNodes();
        if (pos < 0 || pos >= visibleNodes.size()) return null;
        return visibleNodes.get(pos).id;
    }

    public void printTree() {
        printSubtree(root, 0);
    }

    private void printSubtree(Node node, int depth) {
        String indent = "  ".repeat(depth);
        if (node != root) {
            System.out.println(indent + "- " + node.value + " " + node.id + (node.isDeleted ? " (deleted)" : ""));
        }
        for (Node child : node.children) {
            printSubtree(child, depth + 1);
        }
    }
}