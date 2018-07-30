package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.support.annotation.NonNull;

import com.facebook.research.asynchronousratchetingtree.GroupMessagingState;
import com.facebook.research.asynchronousratchetingtree.KeyServer;
import com.facebook.research.asynchronousratchetingtree.Utils;
import com.facebook.research.asynchronousratchetingtree.art.ART;
import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.AuthenticatedMessage;
import com.facebook.research.asynchronousratchetingtree.crypto.DHPubKey;
import com.facebook.research.asynchronousratchetingtree.crypto.SignedDHPubKey;

import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;

import java.util.*;

public class BuildART {

    public Map<Integer, byte[]> setupART(@NonNull Context context, @NonNull SignalServiceGroup group, Optional<GroupDatabase.GroupRecord> record ) {
        List<Address>           members = group.getMembers().isPresent() ? new LinkedList<Address>() : null;
        Address ownAddress = Address.fromSerialized(TextSecurePreferences.getLocalNumber(context));
        members.add(Address.fromExternal(context, String.valueOf(ownAddress)));
        int peerCount = 0;
        int peerNum =0;

        byte[] groupId = group.getGroupId();

        if (group.getMembers().isPresent()) {
            for (String member : group.getMembers().get()) { // get all members of the group
                if (member != String.valueOf(ownAddress)){
                    peerCount = peerCount + 1; //increment peer counter
                    members.add(Address.fromExternal(context, member));
                }
            }
        }

        ARTState[] states = new ARTState[peerCount];
        GroupMessagingState[] groupMessagingStates = new GroupMessagingState[peerCount];
        DHPubKey[] peers = new DHPubKey[peerCount];

        int                activeSignedPreKeyId = PreKeyUtil.getActiveSignedPreKeyId(context);
        DatabaseFactory.getSignedPreKeyDatabase(context).getSignedPreKey(activeSignedPreKeyId);
        //todo: get own DH key pair

        Map<Integer, PreKeyRecord> preKeys = null;
        Map<Integer, byte[]> setupMessages = null;

        for (int i=0; i<peerCount; i++){
            states[i] = new ARTState(i, peerCount);
            peers[i] = states[i].getPreKeyFor(i);

            activeSignedPreKeyId = PreKeyUtil.getActiveSignedPreKeyId(context);
            PreKeyRecord preKeyRecord = DatabaseFactory.getPreKeyDatabase(context).getPreKey(activeSignedPreKeyId);
            preKeys.put(i,preKeyRecord);
            //todo: get DH public key for every member from protocol and safe it locally
            //todo: generate group messaging state for every peer
        }

        KeyServer keyServer = new KeyServer(states);


        for (int i=0; i<peerCount; i++){
            ARTState state = new ARTState(i, peerCount);
            byte[] setupMessageSerialised = setupMessageForPeer(state, peers, keyServer, i);
            setupMessages.put(i, setupMessageSerialised);
            //processSetupMessage(state, setupMessageSerialised, i);
        }

        SignalART signalART = new SignalART(states, 0);

    return setupMessages;

    }

    public byte[] setupMessageForPeer (ARTState state, DHPubKey[]peers, KeyServer
            keyServer,int peer){
        byte[] setupMessageSerialised = state.getSetupMessage();
        if (setupMessageSerialised == null){
            Map<Integer, DHPubKey> preKeys = new HashMap<>();
            for (int i =1; i<peers.length; i++){
                SignedDHPubKey signedPreKey = keyServer.getSignedPreKey(state, i);
                if (!peers[i].verify(signedPreKey.getPubKeyBytes(), signedPreKey.getSignature())){
                    Utils.except("PreKeySignature failed");
                }
                preKeys.put(i, signedPreKey);
            }
            AuthenticatedMessage setupMessage = ART.setupGroup(state, peers, preKeys);
            setupMessageSerialised = setupMessage.serialise();
            state.setSetupMessage(setupMessageSerialised);
        }

        return setupMessageSerialised;
    }

    public void processSetupMessage(ARTState state, byte[] serialisedMessage, int leafNum){
        AuthenticatedMessage message = new AuthenticatedMessage(serialisedMessage);
        ART.processSetupMessage(state, message, leafNum);
    }






}
