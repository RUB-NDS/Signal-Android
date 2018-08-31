package org.thoughtcrime.securesms.groups.protocol;

import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class WrappedARTGroupContext extends JsonARTMessage {
    private String groupContext;
    private byte[] signature;
    private String groupId;

    public WrappedARTGroupContext() {
        super(WrappedARTGroupContext.class);
    }

    public byte[] getSignature() {
        return signature;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupContext() {
        return groupContext;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public void setGroupContext(String groupContext) {
        this.groupContext = groupContext;

    }
}
