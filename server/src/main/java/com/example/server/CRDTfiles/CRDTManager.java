package com.example.server.CRDTfiles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.server.Operation;

public class CRDTManager {
    private final Map<Integer, CRDT> crdtMap = new HashMap<>();
    private final CRDTNetworkService network;
    private final int localUserId;
    // Data structure to store all generated viewer and editor code pairs
    private final Map<Integer, List<String>> generatedCodes = new HashMap<>();
    int docID; // Document ID to be used for each new document
    int userId;

    public interface NetworkHandler {
        void sendOperation(Operation op);
    }

    public HashMap<String, Object> CreateDocument() {
        // Generate unique viewer and editor codes
        String viewerCode;
        String editorCode;
        
        viewerCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        editorCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        System.out.println("Viewer Code: " + viewerCode);
        System.out.println("Editor Code: " + editorCode);

        // Generate a unique user ID starting from 1 and incrementing for each new user
        userId = generatedCodes.size() + 1;
        generatedCodes.put(docID, List.of(viewerCode, editorCode));
        crdtMap.put(docID, new CRDT()); // Create a new CRDT instance for the document
        docID++; // Increment the document ID for the next document

        HashMap<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("viewerCode", viewerCode);
        response.put("editorCode", editorCode);

        return response;
    }

    public HashMap<String, String> joinDocument(String documentCode) {

        // Check if the document code exists in the generated codes
        for (Map.Entry<Integer, List<String>> entry : generatedCodes.entrySet()) {
            List<String> codes = entry.getValue();
            if (codes.contains(documentCode)) {
                if (codes.get(0).equals(documentCode)) {
                    // Viewer code found
                    System.out.println("Viewer code found: " + documentCode);
                    CRDT crdt = crdtMap.get(entry.getKey()); // Return the CRDT instance for the document
                    crdt.printTree();
                    HashMap<String, String> response = new HashMap<>();
                    String key = "V" + userId;
                    response.put(key, crdt != null ? crdt.getVisibleString() : "");
                    return response;
                } else if (codes.get(1).equals(documentCode)) {
                    // Editor code found
                    System.out.println("Editor code found: " + documentCode);
                    CRDT crdt = crdtMap.get(entry.getKey()); // Return the CRDT instance for the document
                    crdt.printTree();
                    HashMap<String, String> response = new HashMap<>();
                    String key = "E" + userId;
                    response.put(key, crdt != null ? crdt.getVisibleString() : "");
                    return response;
                }

                userId++;
            }
        }

        System.out.println("Document code not found: " + documentCode);
        HashMap<String, String> response = new HashMap<>();
        response.put("error", "Document code not found");
        return response; // Document not found
    }

    public CRDTManager(int localUserId) {
        docID = 0; // Initialize document ID
        this.localUserId = localUserId;
        this.network = new CRDTNetworkService(new NetworkHandler() {
            @Override
            public void sendOperation(Operation op) {
                // TODO: implement your actual network send logic here
                System.out.println("Sending operation: " + op);
            }
        });
    }

    public void insertLocal(String Documentcode, char value, int ID) {
        long timestamp = System.currentTimeMillis();
        CRDT.CharacterId id = new CRDT.CharacterId(timestamp, ID);
        int documentKey = generatedCodes.entrySet().stream()
            .filter(entry -> entry.getValue().contains(Documentcode))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Document code not found"));
        CRDT crdt = crdtMap.get(documentKey);
        crdt.insert(id, value);
        network.sendInsert(id, value, crdt.nodeMap.get(id).parentId);
    }

    public void deleteLocal(String Documentcode, CRDT.CharacterId id) {
        int documentKey = generatedCodes.entrySet().stream()
            .filter(entry -> entry.getValue().contains(Documentcode))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Document code not found"));
        CRDT crdt = crdtMap.get(documentKey);
        boolean deleted = crdt.delete(id);
        if (deleted) {
            network.sendDelete(id);
        } else {
            System.out.println("Delete failed: ID not found or already deleted: " + id);
        }
    }

    public void insertRemote(String Documentcode, Operation op) {
        CRDT.CharacterId id = new CRDT.CharacterId(op.getTimestamp(), op.getID());
        CRDT.CharacterId parentId = (op.getParentID() != -1)
            ? new CRDT.CharacterId(op.getParentTimestamp(), op.getParentID())
            : null;
        CRDT.Node node = new CRDT.Node(id, parentId, op.getValue().charAt(0), false);
        int documentKey = generatedCodes.entrySet().stream()
            .filter(entry -> entry.getValue().contains(Documentcode))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Document code not found"));
        System.out.println("Document code found: " + documentKey);
        CRDT crdt = crdtMap.get(documentKey);
        crdt.nodeMap.put(id, node);

        CRDT.Node parent = crdt.nodeMap.get(parentId);
        if (parent != null) {
            parent.addChild(node);
        } else {
            crdt.nodeMap.get(null).addChild(node);
        }
    }

    public void deleteRemote(String Documentcode, Operation op) {
        CRDT.CharacterId id = new CRDT.CharacterId(op.getTimestamp(), op.getID());
        int documentKey = generatedCodes.entrySet().stream()
            .filter(entry -> entry.getValue().contains(Documentcode))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Document code not found"));
        CRDT crdt = crdtMap.get(documentKey);
        crdt.delete(id);
    }

    public String getDocumentText(String Documentcode) {
        int documentKey = generatedCodes.entrySet().stream()
            .filter(entry -> entry.getValue().contains(Documentcode))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Document code not found"));
        CRDT crdt = crdtMap.get(documentKey);
        return crdt.getVisibleString();
    }

    public void printCRDT(String Documentcode) {
        int documentKey = generatedCodes.entrySet().stream()
            .filter(entry -> entry.getValue().contains(Documentcode))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Document code not found"));
        CRDT crdt = crdtMap.get(documentKey);
        crdt.printTree();
    }

    public CRDT getCRDT(String Documentcode) {
        int documentKey = generatedCodes.entrySet().stream()
            .filter(entry -> entry.getValue().contains(Documentcode))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Document code not found"));
        CRDT crdt = crdtMap.get(documentKey);
        return crdt;
    }
}
