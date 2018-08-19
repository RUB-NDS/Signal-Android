package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.util.Log;

import com.facebook.research.asynchronousratchetingtree.Utils;
import com.facebook.research.asynchronousratchetingtree.art.ART;
import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.AuthenticatedMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.SetupMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.UpdateMessage;
import com.facebook.research.asynchronousratchetingtree.crypto.Crypto;
import com.facebook.research.asynchronousratchetingtree.crypto.DHKeyPair;
import com.facebook.research.asynchronousratchetingtree.crypto.DHPubKey;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.KeyStoreHelper;
import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.database.ARTStateSerializer;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupARTDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.OneTimePreKeyDatabase;
import org.thoughtcrime.securesms.database.SessionDatabase;
import org.thoughtcrime.securesms.groups.protocol.JsonARTMessage;
import org.thoughtcrime.securesms.groups.protocol.JsonMessageDeserializer;
import org.thoughtcrime.securesms.groups.protocol.WrappedARTMessage;
import org.thoughtcrime.securesms.groups.protocol.WrappedConversationMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.util.guava.Optional;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;

public class ARTGroupManager {
    private static final String LOG_TAG = ARTGroupManager.class.getSimpleName();

    public final static  String ART_CONFIG_IDENTIFIER = "<ART_CONFIG_MESSAGE>";
    private final UpdateART updateArt;

    private Context ctx;

    private GroupARTDatabase artDb;
    private IdentityDatabase identityDb;
    private SessionDatabase sessionDb;

    private MmsDatabase mmsDb;
    private ARTStateSerializer stateSerializer;
    private Gson gson;

    private static ARTGroupManager instance;

    public static ARTGroupManager getInstance(Context ctx) {
        if (instance==null){
            instance = new ARTGroupManager(ctx);
        }

        return instance;
    }

    private ARTGroupManager(Context ctx) {
        this.ctx = ctx;

        this.artDb = DatabaseFactory.getGroupARTDatabase(ctx);
        this.mmsDb =  DatabaseFactory.getMmsDatabase(ctx);
        this.identityDb =  DatabaseFactory.getIdentityDatabase(ctx);
        this.sessionDb = DatabaseFactory.getSessionDatabase(ctx);

        this.stateSerializer = ARTStateSerializer.getInstance();

        this.gson = new GsonBuilder()
                .registerTypeAdapter(JsonARTMessage.class,new JsonMessageDeserializer())
                .create();

        this.updateArt = new UpdateART();

    }


    public void updateKey(String groupId) {

        Address ownAddress = Address.fromSerialized(TextSecurePreferences.getLocalNumber(ctx));

        Optional<ARTState> optState = artDb.getARTState(groupId);

        if ( ! optState.isPresent()) {
            Utils.except("ARTState not found");
        }

        ARTState state = optState.get();

        AuthenticatedMessage authenticatedUpdateMessage = updateArt.updateMyKey(state);

        Optional<GroupDatabase.GroupRecord> group = DatabaseFactory.getGroupDatabase(ctx).getGroup(groupId);

        Address grpAddress = Address.fromSerialized(groupId);

        Log.d(LOG_TAG,"is Group Addr?: "+grpAddress.isGroup());

        String wrappedARTMessage = wrapMessage(groupId, authenticatedUpdateMessage, UpdateMessage.class);

        /*OutgoingTextMessage sendArtMessage =

                new OutgoingEncryptedMessage(Recipient.from(ctx, grpAddress, false),
                        wrappedARTMessage, 0);*/
        /*MessageSender.send(ctx,sendArtMessage,-1,false,null);*/

        artDb.update(groupId,state);
    }

    public void sendSetupMessages(String groupId, SetupResult setupResult){

        for (ARTGroupMember member :setupResult.getMembers()) {
            WrappedARTMessage wrappedMsg = new WrappedARTMessage();
            wrappedMsg.setGroupId(groupId);
            wrappedMsg.setArtMessageClass(SetupMessage.class.getSimpleName());
            wrappedMsg.setSerializedMessage(setupResult.getArtState().getSetupMessage());

            wrappedMsg.setLeafNum(member.getLeafNum());

            String body = serializeWrappedMessage(wrappedMsg);

            Recipient recipient = Recipient.from(ctx,member.getAddress(),false);

            OutgoingTextMessage msg = new OutgoingEncryptedMessage(recipient, body,0);

            MessageSender.send(ctx,msg,-1,false,null);
        }
    }

    public boolean isWrappedARTMessage(String body){
        return body.startsWith(ART_CONFIG_IDENTIFIER);
    }

    private String wrapMessage(String id, AuthenticatedMessage authMsg, Class classOfMsg) {
        WrappedARTMessage wrappedMsg = new WrappedARTMessage();

        byte[] serializedMsg = authMsg.serialise();
        wrappedMsg.setSerializedMessage(serializedMsg);
        wrappedMsg.setArtMessageClass(classOfMsg.getSimpleName());
        wrappedMsg.setGroupId(id);


        return gson.toJson(wrappedMsg);
    }


    public UpdateMessage unwrapUpdateMessage(WrappedARTMessage wrappedMsg) {
        AuthenticatedMessage authMsg = new AuthenticatedMessage(wrappedMsg.getSerializedMessage());

        byte[] serializedMsg = authMsg.getMessage();

        UpdateMessage retval = new UpdateMessage(serializedMsg);

        return retval;

    }

    public SetupMessage unwrapSetupMessage(WrappedARTMessage wrappedMsg) {
        AuthenticatedMessage authMsg = new AuthenticatedMessage(wrappedMsg.getSerializedMessage());

        byte[] serializedMsg = authMsg.getMessage();

        SetupMessage retval = new SetupMessage(serializedMsg);

        return retval;
    }

    /**
     * wrapper around ART.processSetupMessage
     * we load an art state (if exists) or enrich it with Identity and PreKey info from Signal
     * then ART.processSetupMessage is called
     *
     * @param wrappedMsg
     * @throws IllegalStateException
     */
    public void processSetupMessage(WrappedARTMessage wrappedMsg) throws IllegalStateException {
        AuthenticatedMessage authenticatedMessage = new AuthenticatedMessage(wrappedMsg.getSerializedMessage());

        final String groupId = wrappedMsg.getGroupId();

        if (! SetupMessage.class.getSimpleName().equals(wrappedMsg.getArtMessageClass())) {
            throw new IllegalStateException("Message is not a setup message");
        }

        Optional<GroupDatabase.GroupRecord> optionalGroup = DatabaseFactory.getGroupDatabase(ctx).getGroup(groupId);
        int peerCount = optionalGroup.get().getMembers().size();

        Optional<ARTState> optArtState = artDb.getARTState(groupId);

        if (! optArtState.isPresent()) {
            // no state yet => create
            ARTState state = new ARTState(wrappedMsg.getLeafNum(),peerCount);

            IdentityKeyPair myIdKeyPair = IdentityKeyUtil.getIdentityKeyPair(ctx);

            // TODO: use real ephemeral keys - but how?
            // int keyId = PreKeyUtil.getActiveSignedPreKeyId(ctx);
            //OneTimePreKeyDatabase preKeyDb = DatabaseFactory.getPreKeyDatabase(ctx);
            //PreKeyRecord preKey = preKeyDb.getPreKey(keyId);

            // DHKeyPair dhPreKey = convertToDHKeyPair(preKey.getKeyPair());

            DHKeyPair dhIdKey = convertToDHKeyPair(myIdKeyPair.getPrivateKey());
            DHKeyPair dhPreKey = dhIdKey;

            state.setIdentityKeyPair(dhIdKey);
            state.setMyPreKeyPair(dhPreKey);

            ART.processSetupMessage(state, authenticatedMessage,wrappedMsg.getLeafNum());

            artDb.create(wrappedMsg.getGroupId(),state);
        } else {
            Log.w(LOG_TAG,"setup message received with existing ART - ignoring");
        }
    }

    private DHKeyPair convertToDHKeyPair(ECPrivateKey privateKey) {
        return DHKeyPair.fromBytes(privateKey.serialize(),true);
    }

    private DHPubKey convertToDHPubKey(ECPublicKey pubKey) {
        // works because same EC !
        return DHPubKey.pubKey(pubKey.serialize());
    }

    private DHKeyPair convertToDHKeyPair(ECKeyPair keyPair) {
        // works because same EC !
        return DHKeyPair.fromBytes(keyPair.getPrivateKey().serialize(),true);
    }

    public void processUpdateMessage(WrappedARTMessage wrappedMsg) {

        if (! SetupMessage.class.getSimpleName().equals(wrappedMsg.getArtMessageClass())) {
            throw new IllegalStateException("Message is not an update message");
        }
        AuthenticatedMessage authenticatedMessage = new AuthenticatedMessage(wrappedMsg.getSerializedMessage());
        UpdateMessage updateMessage = new UpdateMessage(authenticatedMessage.getMessage());
        String groupID = wrappedMsg.getGroupId();

        Optional<ARTState> optState = artDb.getARTState(groupID);

        if (! optState.isPresent()) {
            Utils.except("ART state for "+groupID+" not found.");
        }

        byte[] mac = Crypto.hmacSha256(authenticatedMessage.getMessage(), optState.get().getStageKey());
        if (!Arrays.equals(mac, authenticatedMessage.getAuthenticator())) {
            Utils.except("MAC is incorrect for update message.");
        }

        updateArt.updateStateFromMessage(optState.get(), updateMessage);
        artDb.update(groupID, optState.get());
    }

    public WrappedARTMessage createUpdateMessage(String groupID, int peerNo, ARTState myArtState) {
        throw new IllegalStateException("not implemented");
    }

    public ARTState setupInitialART(int peerNum, int peerCount) {

        ARTState state = new ARTState(peerNum,peerCount);

        return state;
    }

    public String serializeWrappedMessage(JsonARTMessage msg) {
        return ART_CONFIG_IDENTIFIER+gson.toJson(msg);
    }

    public String filterBody(String rawBody) {
        if (isWrappedARTMessage(rawBody)) {

            JsonARTMessage jsonMsg = deserializeMessage(rawBody);


            if (jsonMsg.getOriginalBody()!=null) {
                return jsonMsg.getOriginalBody();
            } else {
                return "We dont have a body";
            }

        } else {
            return rawBody;
        }
    }

    public String filterBody(IncomingTextMessage message) {

        return filterBody(message.getMessageBody());
    }

    public void checkMessage(IncomingTextMessage textMessage) {
        Log.d(LOG_TAG,"checking incoming message");

        final String body = textMessage.getMessageBody();

        boolean isWrapped = isWrappedARTMessage(body);

        Log.d(LOG_TAG,"is Secure?: "+ textMessage.isSecureMessage());
        Log.d(LOG_TAG,"is ART Message?: "+isWrapped);
        Log.d(LOG_TAG,"Body: "+body);

        if (isWrapped) {
            JsonARTMessage jsonMsg = deserializeMessage(textMessage.getMessageBody());

            if ( jsonMsg instanceof WrappedConversationMessage) {
                WrappedConversationMessage conversationMessage = (WrappedConversationMessage) jsonMsg;
                boolean checkResult = verifyGroupIdSignature(String.valueOf(textMessage.getGroupId()),conversationMessage.getSignature());
                Log.d(LOG_TAG,"Signature check: "+checkResult);
            }
        }
    }

    public byte[] signGroupId(String groupId) {
        Optional<ARTState> optGrpState = artDb.getARTState(groupId);

        if (! optGrpState.isPresent()) {
            // TODO: we should throw an exception here ...
            Log.w(LOG_TAG,"No art state found - faking signature!");
            return new byte[] { 0,1,2,3,4,5,6,7,8 };
        } else {
            byte[] secret = optGrpState.get().getStageKey();

            return Crypto.hmacSha256(groupId.getBytes(),secret);
        }
    }

    public boolean verifyGroupIdSignature(String groupId, byte[] signature) {
        Optional<ARTState> optGrpState = artDb.getARTState(groupId);

        if (! optGrpState.isPresent()) {
            // TODO: we should throw an exception here ...
            Log.w(LOG_TAG,"No art state found - verifying fake signature!");
            return true;
        } else {
            byte[] secret = optGrpState.get().getStageKey();

            byte[] verifySignature = Crypto.hmacSha256(groupId.getBytes(),secret);

            return Arrays.equals(signature,verifySignature);
        }
    }

    public JsonARTMessage deserializeMessage(String rawMessageBody) {

        if (! isWrappedARTMessage(rawMessageBody)) {
            Log.e(LOG_TAG,"BUG: message is not a wrapped message!");
            return null;
        }

        Log.d(LOG_TAG,"Incoming ART message");

        String jsonString = rawMessageBody.substring(ART_CONFIG_IDENTIFIER.length());

        Log.d(LOG_TAG,"Incoming ART message: filtered: "+jsonString);


        return gson.fromJson(jsonString,JsonARTMessage.class);
    }

    /* OFF
     public static void processSetupMessage(Context context, WrappedARTMessage wrappedMsg){

        AuthenticatedMessage message = new AuthenticatedMessage(wrappedMsg.getSerializedMessage());
        SetupMessage setupMsg = new SetupMessage(message.getMessage());
        ARTGroupManager grpMgr = ARTGroupManager.getInstance(context);

        grpMgr.processSetupMessage(wrappedMsg);




        ART.processSetupMessage(wrappedMsg.getArtState(), message, wrappedMsg.getLeafNum());
    }

     */

    /**
     * Used by @see {@link GroupManager}
     * @param groupId group ID
     * @param pMembers list of group members excluding self
     */
    public SetupResult setupART(@NonNull String groupId, @NonNull Set<Address> pMembers ) {

        SetupResult result = new SetupResult();
        HashSet<ARTGroupMember> artMemberSet = new HashSet<>();

        // we don't care for the peerCount - Facebook crap
        ARTState state = new ARTState(0,pMembers.size()+1);


        Address ownAddress = Address.fromSerialized(TextSecurePreferences.getLocalNumber(ctx));
        List<Address> members = new ArrayList<Address>();

        // add self as 0
        Address myAddress = Address.fromExternal(ctx, String.valueOf(ownAddress));

        members.add(myAddress);

        artMemberSet.add(new ARTGroupMember(myAddress,0));

        List<DHPubKey> peers = new ArrayList<>();
        List<IdentityKey> idKeys = new ArrayList<>();

        int peerNum = 0;
        IdentityKeyPair myIdKeyPair = IdentityKeyUtil.getIdentityKeyPair(ctx);

        DHKeyPair myDhIdKeyPair = convertToDHKeyPair(myIdKeyPair.getPrivateKey());
        state.setIdentityKeyPair(myDhIdKeyPair);

        peers.add(myDhIdKeyPair.getPubKey());

        for (Address memberAddr: pMembers) { // get all members of the group
            peerNum++;
            members.add(memberAddr);

            artMemberSet.add(new ARTGroupMember(memberAddr,peerNum));

            // for simplicity we assume it is always device #1

            Log.d(LOG_TAG, "Loading session for " + memberAddr);
            SessionRecord session = sessionDb.load(memberAddr, 1);

            if (session == null) {
                Log.e(LOG_TAG, "could not find session for " + memberAddr);
            } else {

                if (session != null) {
                    Log.d(LOG_TAG, "found a session for " + memberAddr);
                }

                IdentityKey idKey = session.getSessionState().getRemoteIdentityKey();
                ECPublicKey idPubkey = idKey.getPublicKey();

                idKeys.add(idKey);
                DHPubKey idDhPub = DHPubKey.pubKey(idPubkey.serialize());
                state.setPreKeyFor(peerNum, idDhPub);
                peers.add(idDhPub);
            }
        }

        generateSetupMessage(state, peers);

        Log.d(LOG_TAG,"Storing GroupART for groupId "+groupId);
        artDb.create(groupId, state);

        result.setArtState(state);
        result.setMembers(artMemberSet);

        return result;
    }

    /**
     * generates the setup message and puts the serialized version into the passed state
     *
     * @param state the ART state
     * @param peerIdentities list of identity pubkeys
     * @return the non-serialized authenticated setup message
     */
    private void generateSetupMessage(ARTState state, List<DHPubKey> peerIdentities){
        byte[] setupMessageSerialised = state.getSetupMessage();

        if (setupMessageSerialised == null){
            Map<Integer, DHPubKey> preKeys = new HashMap<>();

            for (int i = 1; i<peerIdentities.size(); i++){
                preKeys.put(i,state.getPreKeyFor(i));
            }
            AuthenticatedMessage setupMessage = ART.setupGroup(state, peerIdentities.toArray(new DHPubKey[]{}), preKeys);
            setupMessageSerialised = setupMessage.serialise();
            state.setSetupMessage(setupMessageSerialised);
        }
    }
}
