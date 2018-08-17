package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.facebook.research.asynchronousratchetingtree.KeyServer;
import com.facebook.research.asynchronousratchetingtree.Utils;
import com.facebook.research.asynchronousratchetingtree.art.ART;
import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.AuthenticatedMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.SetupMessage;
import com.facebook.research.asynchronousratchetingtree.crypto.DHPubKey;
import com.facebook.research.asynchronousratchetingtree.crypto.SignedDHPubKey;

import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupARTDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.OneTimePreKeyDatabase;
import org.thoughtcrime.securesms.groups.protocol.WrappedARTMessage;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;

import java.util.*;

public class BuildART {

    private static final String LOG_TAG = BuildART.class.getSimpleName();

    // for update messages
    public void setupART(@NonNull Context context, @NonNull SignalServiceGroup group) {
        List<Address>           members = group.getMembers().isPresent() ? new LinkedList<Address>() : null;
        Address ownAddress = Address.fromSerialized(TextSecurePreferences.getLocalNumber(context));
        members.add(Address.fromExternal(context, String.valueOf(ownAddress)));
        int peerCount = 0;
        int peerNum =0;

        String groupId = String.valueOf(group.getGroupId());

        if (group.getMembers().isPresent()) {
            for (String member : group.getMembers().get()) { // get all members of the group
                if (member != String.valueOf(ownAddress)){
                    peerCount = peerCount + 1; //increment peer counter
                    members.add(Address.fromExternal(context, member));
                }
            }
        }

        ARTState state = new ARTState(peerNum,peerCount);



        ARTState[] states = new ARTState[peerCount];
        // GroupMessagingState[] groupMessagingStates = new GroupMessagingState[peerCount];
        DHPubKey[] peers = new DHPubKey[peerCount];

        int                activeSignedPreKeyId = PreKeyUtil.getActiveSignedPreKeyId(context);
        SignedPreKeyRecord record = DatabaseFactory.getSignedPreKeyDatabase(context).getSignedPreKey(activeSignedPreKeyId);
        record.getKeyPair().getPrivateKey();

        Optional<IdentityDatabase.IdentityRecord> myIdentity = DatabaseFactory.getIdentityDatabase(context).getIdentity(ownAddress);




        Map<Integer, PreKeyRecord> preKeys = new HashMap<>();

        Log.d(LOG_TAG, "Peer count: "+peerCount);

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
        GroupARTDatabase groupARTDatabase = DatabaseFactory.getGroupARTDatabase(context);
        groupARTDatabase.delete(groupId);

        for (int i=0; i<peerCount; i++){
            Log.d(LOG_TAG,"Creating state for peer #"+i);
            ARTState mystate = new ARTState(i, peerCount);
            byte[] setupMessageSerialised = setupMessageForPeer(mystate, peers, keyServer, i);
            groupARTDatabase.create(groupId, mystate);
        }

    }


    /**
     * Used by @see {@link GroupManager}
     * @param context the android context for DB access etc
     * @param groupId group ID
     * @param pMembers list of group members excluding self
     */
    public void setupART(@NonNull Context context, @NonNull String groupId,  @NonNull Set<Address> pMembers ) {

        Address ownAddress = Address.fromSerialized(TextSecurePreferences.getLocalNumber(context));
        List<Address> members = new ArrayList<Address>();

        members.add(Address.fromExternal(context, String.valueOf(ownAddress)));
        int peerCount = 0;

        IdentityDatabase identityDb = DatabaseFactory.getIdentityDatabase(context);

        Optional<IdentityDatabase.IdentityRecord> optIdentity = identityDb.getIdentity(ownAddress);

        IdentityKey myKey = optIdentity.get().getIdentityKey();

        myKey.getPublicKey();

        List<IdentityKey> identities;

        // TODO: ADD SELF

        for (Address memberAddr: pMembers) { // get all members of the group
            members.add(memberAddr);
        }
        peerCount = members.size();

        // we don't care for the peerCount - Facebook crap
        ARTState states = new ARTState(0,42);

        DHPubKey[] peers = new DHPubKey[peerCount];

        Map<Integer, PreKeyRecord> preKeys = new HashMap<>();

        Log.d(LOG_TAG, "Peer count: "+peerCount);


        OneTimePreKeyDatabase keyDb = DatabaseFactory.getPreKeyDatabase(context);


        // get all prekeys for the others
        for (int i=1; i<peerCount; i++){
            // TODO: HOW?
            int activeSignedPreKeyId = PreKeyUtil.getActiveSignedPreKeyId(context);
            PreKeyRecord preKeyRecord = keyDb.getPreKey(activeSignedPreKeyId);
            preKeys.put(i,preKeyRecord);

            //get DH public key for every member from protocol and safe it locally
        }



        // KeyServer keyServer = new KeyServer(states);
        GroupARTDatabase groupARTDatabase = DatabaseFactory.getGroupARTDatabase(context);
        groupARTDatabase.delete(groupId);

        for (int i=0; i<peerCount; i++){
            Log.d(LOG_TAG,"Creating state for peer #"+i);
            ARTState state = new ARTState(i, peerCount);
            //byte[] setupMessageSerialised = setupMessageForPeer(state, peers, keyServer, i);
            //groupARTDatabase.create(groupId, state);
        }
    }

    private byte[] setupMessageForPeer(ARTState state, DHPubKey[] peers, KeyServer
            keyServer, int peer){
        byte[] setupMessageSerialised = state.getSetupMessage();
        if (setupMessageSerialised == null){
            Map<Integer, DHPubKey> preKeys = new HashMap<>();
            for (int i =1; i<peers.length; i++){
                SignedDHPubKey signedPreKey = keyServer.getSignedPreKey(state, i);
                Log.d(LOG_TAG,signedPreKey.toString());

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

    public static void processSetupMessage(WrappedARTMessage wrappedMsg){

        AuthenticatedMessage message = new AuthenticatedMessage(wrappedMsg.getSerializedMessage());
        SetupMessage setupMsg = new SetupMessage(message.getMessage());
        ART.processSetupMessage(wrappedMsg.getArtState(), message, wrappedMsg.getLeafNum());
    }






}
