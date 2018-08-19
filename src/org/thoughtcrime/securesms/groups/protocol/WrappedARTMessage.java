package org.thoughtcrime.securesms.groups.protocol;

import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.SetupMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.UpdateMessage;

public class WrappedARTMessage extends JsonARTMessage {
    private byte[] serializedMessage;
    private String groupId;
    private String artMessageClass;
    private int leafNum;

    public WrappedARTMessage() {
        super(WrappedARTMessage.class);
    }

    public String getArtMessageClass() {
        return artMessageClass;
    }

    public void setArtMessageClass(String artMessageClass) {
        this.artMessageClass = artMessageClass;
    }

    public int getLeafNum() {
        return leafNum;
    }

    public void setLeafNum(int leafNum) {
        this.leafNum = leafNum;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setSerializedMessage(byte[] serializedMessage) {
        this.serializedMessage = serializedMessage;
    }

    public byte[] getSerializedMessage() {
        return serializedMessage;
    }

    public SetupMessage unwrapAsSetupMessage() {
        SetupMessage msg = new SetupMessage(serializedMessage);
        return msg;
    }

    public UpdateMessage unwrapAsUpdateMessage() {
        UpdateMessage msg = new UpdateMessage(serializedMessage);
        return msg;
    }


}
