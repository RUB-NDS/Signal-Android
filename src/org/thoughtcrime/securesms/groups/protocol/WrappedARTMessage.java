package org.thoughtcrime.securesms.groups.protocol;

import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.SetupMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.UpdateMessage;

/**
 * used to encapsulate serialized ART messages (THRIFT format)
 * in JSON. To avoid explicit serialization / type adapter implenentation for
 * the SetupMessage and UpdateMessage we use the existing serialization mechanism to store it in
 * a byte array
 *
 *
 */
public class WrappedARTMessage extends JsonARTMessage {
    private byte[] serializedMessage;

    // the related groupId
    private String groupId;

    // Message class of the serialized ART message
    private String artMessageClass;

    // the leaf number is required to identify the own leaf in the setup message
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

}
