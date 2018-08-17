package org.thoughtcrime.securesms.groups;

public class GroupSetupBroadcastMessage {
    private byte[] identityPubKey;
    private byte[] ephemeralPubKey;

    public byte[] getEphemeralPubKey() {
        return ephemeralPubKey;
    }

    public byte[] getIdentityPubKey() {
        return identityPubKey;
    }

    public void setEphemeralPubKey(byte[] ephemeralPubKey) {
        this.ephemeralPubKey = ephemeralPubKey;
    }

    public void setIdentityPubKey(byte[] identityPubKey) {
        this.identityPubKey = identityPubKey;
    }
}
