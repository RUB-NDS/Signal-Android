package org.thoughtcrime.securesms.groups.protocol;

/**
 * encapsulates a GroupContext in a JSON Message.
 * This type of message is sent when the client does a group update
 *
 */
public class WrappedARTGroupContext extends JsonARTMessage {
    // base64 serialized groupContext (uses existing serialization mechanism)
    private String groupContext;

    // signature of the message ( as usually the HMAC of the groupId under the stageKey
    private byte[] signature;

    // related groupID
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
