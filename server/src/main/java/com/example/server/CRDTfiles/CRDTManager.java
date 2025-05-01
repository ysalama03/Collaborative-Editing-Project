package com.example.server.CRDTfiles;
import com.example.server.Operation;

public class CRDTManager {
    private final CRDT crdt = new CRDT();
    private final CRDTNetworkService network;
    private final int localUserId;

    public interface NetworkHandler {
        void sendOperation(Operation op);
    }

    public CRDTManager(int localUserId) {
        this.localUserId = localUserId;
        this.network = new CRDTNetworkService(new NetworkHandler() {
            @Override
            public void sendOperation(Operation op) {
                // TODO: implement your actual network send logic here
                System.out.println("Sending operation: " + op);
            }
        });
    }

    public void insertLocal(char value, int ID) {
        long timestamp = System.currentTimeMillis();
        CRDT.CharacterId id = new CRDT.CharacterId(timestamp, ID);
        crdt.insert(id, value);
        network.sendInsert(id, value, crdt.nodeMap.get(id).parentId);
    }

    public void deleteLocal(CRDT.CharacterId id) {
        boolean deleted = crdt.delete(id);
        if (deleted) {
            network.sendDelete(id);
        } else {
            System.out.println("Delete failed: ID not found or already deleted: " + id);
        }
    }

    public void insertRemote(Operation op) {
        CRDT.CharacterId id = new CRDT.CharacterId(op.getTimestamp(), op.getID());
        CRDT.CharacterId parentId = (op.getParentID() != -1)
            ? new CRDT.CharacterId(op.getParentTimestamp(), op.getParentID())
            : null;
        CRDT.Node node = new CRDT.Node(id, parentId, op.getValue().charAt(0), false);
        crdt.nodeMap.put(id, node);

        CRDT.Node parent = crdt.nodeMap.get(parentId);
        if (parent != null) {
            parent.addChild(node);
        } else {
            crdt.nodeMap.get(null).addChild(node);
        }
    }

    public void deleteRemote(Operation op) {
        CRDT.CharacterId id = new CRDT.CharacterId(op.getTimestamp(), op.getID());
        crdt.delete(id);
    }

    public String getDocumentText() {
        return crdt.getVisibleString();
    }

    public void printCRDT() {
        crdt.printTree();
    }

    public CRDT getCRDT() {
        return crdt;
    }
}
