package org.thoughtcrime.securesms.groups.protocol;

import org.thoughtcrime.securesms.groups.ARTGroupManager;

import javax.crypto.Cipher;

public class WrappedConversationMessage extends JsonARTMessage {

    private byte[] signature;

    public WrappedConversationMessage() {
        super(WrappedConversationMessage.class);
    }

    public boolean isSigned() {
        return signature != null;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public byte[] getSignature() {
        return signature;
    }
}
