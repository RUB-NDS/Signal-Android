package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.facebook.research.asynchronousratchetingtree.GroupMessagingState;
import com.facebook.research.asynchronousratchetingtree.KeyServer;
import com.facebook.research.asynchronousratchetingtree.Utils;
import com.facebook.research.asynchronousratchetingtree.art.ART;
import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.AuthenticatedMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.SetupMessage;
import com.facebook.research.asynchronousratchetingtree.crypto.DHPubKey;
import com.facebook.research.asynchronousratchetingtree.crypto.SignedDHPubKey;

import org.thoughtcrime.securesms.crypto.storage.TextSecurePreKeyStore;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupARTDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.SessionDatabase;
import org.thoughtcrime.securesms.groups.protocol.WrappedARTMessage;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.util.*;

public class BuildART {

    private static final String LOG_TAG = BuildART.class.getSimpleName();

    /*
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

    }*/



}
