package app;

public class Operation{

    private String op;
    private int ID;
    private long timestamp;
    private String value;
    private int parentID;
    private long parentTimestamp;

    public Operation()
    {

    }

    public Operation(String op, int ID, long timestamp, String value, int parentID, long parentTimestamp) {
        this.op = op;
        this.ID = ID;
        this.timestamp = timestamp;
        this.value = value;
        this.parentID = parentID;
        this.parentTimestamp = parentTimestamp;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
    public int getParentID() {
        return parentID;
    }
    public void setParentID(int parentID) {
        this.parentID = parentID;
    }
    public long getParentTimestamp() {
        return parentTimestamp;
    }
    public void setParentTimestamp(long parentTimestamp) {
        this.parentTimestamp = parentTimestamp;
    }

}