package app.CRDTfiles;
import org.springframework.messaging.simp.stomp.StompSession;

import app.Operation;
import app.Client.ClientWebsocket;

public class CRDTManager {
    private final CRDT crdt = new CRDT();
    private final int localUserId;
    StompSession stompSession;
    ClientWebsocket clientWebsocket;


    public CRDTManager(int localUserId , ClientWebsocket clientWebsocket) {
        this.clientWebsocket = clientWebsocket;
        this.localUserId = localUserId;
    }

    public void insertLocal(char value, int ID, String DocumentCode) {
        long timestamp = System.currentTimeMillis();
        CRDT.CharacterId id = new CRDT.CharacterId(timestamp, ID);
        crdt.insert(id, value);
        Operation op = new Operation();
        op.setOp("insert");
        op.setID(id.userId);
        op.setTimestamp(id.timestamp); 
        op.setValue(String.valueOf(value));
        if(crdt.nodeMap.get(id).parentId != null) {
            op.setParentID(crdt.nodeMap.get(id).parentId.userId);
            op.setParentTimestamp(crdt.nodeMap.get(id).parentId.timestamp);
        } else {
            op.setParentID(-1);
            op.setParentTimestamp(-1);
        }
        clientWebsocket.sendOperation(op, DocumentCode);
    }

    public void deleteLocal(CRDT.CharacterId id, String DocumentCode) {
        boolean deleted = crdt.delete(id);
        if (deleted) {
            Operation op = new Operation();
            op.setOp("delete");
            op.setID(id.userId);
            op.setTimestamp(id.timestamp);
            clientWebsocket.sendOperation(op, DocumentCode);
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
