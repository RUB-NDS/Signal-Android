package org.thoughtcrime.securesms.groups;

import android.content.Context;

import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.SetupMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.UpdateMessage;

public class WrappedARTMessage {
    private byte[] serializedMessage;
    private String messageClass;
    private String body;
    private String groupId;
    private ARTState artState;
    private int leafNum;

    public ARTState getArtState() {
        return artState;
    }

    public int getLeafNum() {
        return leafNum;
    }

    public void setArtState(ARTState artState) {
        this.artState = artState;
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

    public void setMessageClass(String messageClass) {
        this.messageClass = messageClass;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getMessageClass() {
        return messageClass;
    }

    public String getBody() {
        return body;
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
