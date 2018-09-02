package org.thoughtcrime.securesms.groups.protocol;


public class WrappedARTGroupContext extends JsonARTMessage {
    private String groupContext;
    private byte[] signature;
    private String groupID;

    public String getGroupID() {
        return groupID;
    }

    public void setGroupID(String groupID) {
        this.groupID = groupID;
    }

    public WrappedARTGroupContext() {
        super(WrappedARTGroupContext.class);
    }

    public byte[] getSignature() {
        return signature;
    }


    public String getGroupContext() {
        return groupContext;
    }


    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public void setGroupContext(String groupContext) {
        this.groupContext = groupContext;

    }
}
