package com.example.server.CRDTfiles;

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
            int cmp = Long.compare(this.timestamp, o.timestamp);
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
            while (i < children.size() && children.get(i).id.compareTo(child.id) < 0) {
                i++;
            }
            children.add(i, child);
        }
    }

    private final Node root = new Node(null, null, '\0', false);
    public final Map<CharacterId, Node> nodeMap = new HashMap<>();
    public final List<Node> visibleNodes = new ArrayList<>();

    public CRDT() {
        nodeMap.put(null, root);
    }

    public void insert(CharacterId id, char value) {
        CharacterId parentId = findInsertParent(id);
        Node newNode = new Node(id, parentId, value, false);
        nodeMap.put(id, newNode);

        Node parentNode = nodeMap.get(parentId);
        parentNode.addChild(newNode);
    }

    private CharacterId findInsertParent(CharacterId id) {
        CharacterId best = null;
        for (CharacterId existingId : nodeMap.keySet()) {
            if (existingId == null) continue;
            if (existingId.compareTo(id) < 0) {
                if (best == null || existingId.compareTo(best) > 0) {
                    best = existingId;
                }
            }
        }
        return best;
    }

    public boolean delete(CharacterId id) {
        Node node = nodeMap.get(id);
        if (node == null || node.isDeleted) {
            return false; // not found or already deleted
        }
        node.isDeleted = true;
        return true;
    }

    public String getVisibleString() {
        StringBuilder sb = new StringBuilder();
        traverseVisible(root, sb);
        return sb.toString();
    }

    private void traverseVisible(Node node, StringBuilder sb) {
        for (Node child : node.children) {
            if (!child.isDeleted) {
                sb.append(child.value);
            }
            traverseVisible(child, sb);
        }
    }

    public CharacterId getCharacterIdAtPosition(int pos) {
        List<CharacterId> visibleIds = new ArrayList<>();
        collectVisibleIds(root, visibleIds);
        if (pos < 0 || pos >= visibleIds.size()) return null;
        return visibleIds.get(pos);
    }
    
    private void collectVisibleIds(Node node, List<CharacterId> result) {
        for (Node child : node.children) {
            if (!child.isDeleted) {
                result.add(child.id);
            }
            collectVisibleIds(child, result);
        }
    }
    

    public void printTree() {
        printSubtree(root, 0);
    }

    private void printSubtree(Node node, int depth) {
        String indent = "  ".repeat(depth);
        if (node != root) {
            System.out.println(indent + "- " + node.value + " " + node.id + (node.isDeleted ? " (deleted)" : ""));
        }
        node.children.sort(Comparator.comparing(n -> n.id));
        for (Node child : node.children) {
            printSubtree(child, depth + 1);
        }
    }
}
