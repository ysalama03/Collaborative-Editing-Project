package com.example.server.CRDTfiles;
import com.example.server.Operation;

public class CRDTNetworkService {
    private final CRDTManager.NetworkHandler handler;

    public CRDTNetworkService(CRDTManager.NetworkHandler handler) {
        this.handler = handler;
    }

    public void sendInsert(CRDT.CharacterId id, char value, CRDT.CharacterId parentId) {
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
        handler.sendOperation(op);
    }

    public void sendDelete(CRDT.CharacterId id) {
        Operation op = new Operation();
        op.setOp("delete");
        op.setID(id.userId);
        op.setTimestamp(id.timestamp);
        handler.sendOperation(op);
    }
}
