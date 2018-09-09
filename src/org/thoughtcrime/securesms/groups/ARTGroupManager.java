package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.graphics.Path;
import android.support.annotation.NonNull;
import android.util.Log;

import com.facebook.research.asynchronousratchetingtree.Utils;
import com.facebook.research.asynchronousratchetingtree.art.ART;
import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.AuthenticatedMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.SetupMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.UpdateMessage;
import com.facebook.research.asynchronousratchetingtree.art.tree.Node;
import com.facebook.research.asynchronousratchetingtree.art.tree.ParentNode;
import com.facebook.research.asynchronousratchetingtree.art.tree.PublicLeafNode;
import com.facebook.research.asynchronousratchetingtree.art.tree.PublicParentNode;
import com.facebook.research.asynchronousratchetingtree.art.tree.SecretLeafNode;
import com.facebook.research.asynchronousratchetingtree.art.tree.SecretNode;
import com.facebook.research.asynchronousratchetingtree.art.tree.SecretParentNode;
import com.facebook.research.asynchronousratchetingtree.crypto.Crypto;
import com.facebook.research.asynchronousratchetingtree.crypto.DHKeyPair;
import com.facebook.research.asynchronousratchetingtree.crypto.DHPubKey;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.database.ARTStateSerializer;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupARTDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SessionDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.groups.protocol.JsonARTMessage;
import org.thoughtcrime.securesms.groups.protocol.JsonMessageDeserializer;
import org.thoughtcrime.securesms.groups.protocol.WrappedARTGroupContext;
import org.thoughtcrime.securesms.groups.protocol.WrappedARTMessage;
import org.thoughtcrime.securesms.groups.protocol.WrappedConversationMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.math.BigInteger;
import java.security.acl.Group;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ARTGroupManager {
    private static final String LOG_TAG = ARTGroupManager.class.getSimpleName();

    public final static  String ART_CONFIG_IDENTIFIER = "<ART_CONFIG_MESSAGE>";

    private Context ctx;

    private GroupARTDatabase artDb;
    private SessionDatabase sessionDb;

    private Gson gson;

    private static ARTGroupManager instance;

    public static ARTGroupManager getInstance(Context ctx) {
        if (instance==null){
            instance = new ARTGroupManager(ctx);
        }

        return instance;
    }

    public static ARTGroupManager getInstance() {
        if (instance == null) {
            throw new NullPointerException("ART Group Manager not yet created!");
        }

        return instance;
    }

    private ARTGroupManager(Context ctx) {
        this.ctx = ctx;

        this.artDb = DatabaseFactory.getGroupARTDatabase(ctx);
        this.sessionDb = DatabaseFactory.getSessionDatabase(ctx);

        this.gson = new GsonBuilder()
                .registerTypeAdapter(JsonARTMessage.class,new JsonMessageDeserializer())
                .create();

    }

    public void updateKey(String groupId) {

        Optional<ARTState> optState = artDb.getARTState(groupId);
        ARTState state;

        if ( ! optState.isPresent()) {
            Utils.except("ARTState not found, generating new ART and setupMessage");
            state = createNewTree(groupId);
        } else{
            state = optState.get();
        }

        AuthenticatedMessage authenticatedUpdateMessage = updateMyKey(state);

        Address    groupAddress     = Address.fromSerialized(groupId);
        Recipient  groupRecipient   = Recipient.from(ctx, groupAddress, false);

        Log.d(LOG_TAG,"is Group Addr?: "+groupAddress.isGroup());

        WrappedARTMessage wrappedMsg = wrapMessage(groupId, authenticatedUpdateMessage, UpdateMessage.class);
        String body = serializeWrappedMessage(wrappedMsg);

        Log.w(LOG_TAG,"sending update Message to "+groupRecipient);

        List<Attachment> attachments = new ArrayList<>();
        List<Contact> contacts = new ArrayList<>();
        int distributionType = ThreadDatabase.DistributionTypes.CONVERSATION;

        OutgoingMediaMessage msg = new OutgoingSecureMediaMessage(groupRecipient, body,
                attachments, System.currentTimeMillis(),distributionType, 0, null, contacts);

        // update before send!
        artDb.update(groupId,state);

        MessageSender.send(ctx,msg,-1,false,null);
    }

    public void sendSetupMessages(String groupId, SetupResult setupResult){

        for (ARTGroupMember member :setupResult.getMembers()) {
            if (member.equals(Address.fromSerialized(TextSecurePreferences.getLocalNumber(ctx)))){
                Log.w(LOG_TAG,"do not send setup message to myself");

            } else {

                WrappedARTMessage wrappedMsg = wrapMessage(groupId, setupResult.getArtState().getSetupMessage(), SetupMessage.class);

                wrappedMsg.setLeafNum(member.getLeafNum());

                String body = serializeWrappedMessage(wrappedMsg);

                Recipient recipient = Recipient.from(ctx, member.getAddress(), false);
                Log.w(LOG_TAG,"send setup Message to "+recipient);

                OutgoingTextMessage msg = new OutgoingEncryptedMessage(recipient, body, 0);

                MessageSender.send(ctx, msg, -1, false, null);
            }
        }
    }


    public boolean isWrappedARTMessage(String body){
        return body.startsWith(ART_CONFIG_IDENTIFIER);
    }

    private WrappedARTMessage wrapMessage(String id, AuthenticatedMessage authMsg, Class classOfMsg) {
        return wrapMessage(id,authMsg.serialise(),classOfMsg);
    }

    private WrappedARTMessage wrapMessage(String id, byte[] serializedMsg, Class classOfMsg) {
        WrappedARTMessage wrappedMsg = new WrappedARTMessage();

        wrappedMsg.setSerializedMessage(serializedMsg);
        wrappedMsg.setArtMessageClass(classOfMsg.getSimpleName());
        wrappedMsg.setGroupId(id);

        return wrappedMsg;
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

        Log.i(LOG_TAG,"Process setup Message for group"+groupId);


        if (! SetupMessage.class.getSimpleName().equals(wrappedMsg.getArtMessageClass())) {
            throw new IllegalStateException("Message is not a setup message");
        }

        Optional<ARTState> optArtState = artDb.getARTState(groupId);

        if (! optArtState.isPresent()) {
            int peerCount = 42;

            ARTState state = new ARTState(wrappedMsg.getLeafNum(),peerCount);

            IdentityKeyPair myIdKeyPair = IdentityKeyUtil.getIdentityKeyPair(ctx);

            // TODO: use real ephemeral keys

            DHKeyPair dhIdKey = convertToDHKeyPair(myIdKeyPair.getPrivateKey());
            DHKeyPair dhPreKey = dhIdKey;

            state.setIdentityKeyPair(dhIdKey);
            state.setMyPreKeyPair(dhPreKey);

            ART.processSetupMessage(state, authenticatedMessage,wrappedMsg.getLeafNum());
            Log.w(LOG_TAG,"setup message received and tree created for group"+groupId+" I'm peer number "+state.getPeerNum());

            ParentNode root =  (SecretParentNode) state.getTree();

            dumpSimpleTree(root);

            artDb.create(wrappedMsg.getGroupId(),state);
            Log.w(LOG_TAG,"State stored in database:" +artDb.getARTState(groupId).isPresent());

        } else {
            Log.w(LOG_TAG,"setup message received although ART exist - ignoring");
        }
    }

    private void dumpSimpleTree(ParentNode root) {

        if (root==null) {
            Log.e(LOG_TAG, "Tree is null");
            return;
        }

        Node leftChild = root.getLeft();
        Node rightChild = root.getRight();

        Log.i(LOG_TAG,"Root pubkey: "+root.getPubKey());
        Log.i(LOG_TAG, "Left pubkey: "+hexDump(leftChild.getPubKey().getPubKeyBytes()));

        Log.i(LOG_TAG, "left is leaf? "+(leftChild.numLeaves()==1));
        Log.i(LOG_TAG, "Right is leaf? "+(rightChild.numLeaves()==1));

        Log.i(LOG_TAG,"Right pubkey: "+hexDump(rightChild.getPubKey().getPubKeyBytes()));

    }

    private DHKeyPair convertToDHKeyPair(ECPrivateKey privateKey) {
        return DHKeyPair.fromBytes(privateKey.serialize(),true);
    }

    private DHPubKey convertToDHPubKey(ECPublicKey pubKey) {
        // works because same EC !

        // the serialized key contains the Curve Type which must be removed!
        byte[] serializedKey = pubKey.serialize();

        // We have to strip the EC Type prefix!;
        byte[] purePubKey = new byte[ECPublicKey.KEY_SIZE-1];

        System.arraycopy(serializedKey,1, purePubKey,0,purePubKey.length);
        return DHPubKey.pubKey(purePubKey);
    }


    public void processUpdateMessage(WrappedARTMessage wrappedMsg) {

        if (! UpdateMessage.class.getSimpleName().equals(wrappedMsg.getArtMessageClass())) {
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

        Log.w(LOG_TAG,"Update state from Message");

        updateStateFromMessage(optState.get(), updateMessage);
        artDb.update(groupID, optState.get());
    }


    public String serializeWrappedMessage(JsonARTMessage msg) {
        return ART_CONFIG_IDENTIFIER+gson.toJson(msg);
    }

    public String filterBody(String rawBody) {
        if (isWrappedARTMessage(rawBody)) {

            JsonARTMessage jsonMsg = deserializeWrappedMessage(rawBody);


            if (jsonMsg.getOriginalBody()!=null) {
                return jsonMsg.getOriginalBody().toString();
            } else {
                return "";
            }

        } else {
            return rawBody;
        }
    }

    public byte[] signGroupId(String groupId) {
        Optional<ARTState> optGrpState = artDb.getARTState(groupId);

        if (! optGrpState.isPresent()) {
            // TODO: we should throw an exception here ...
            Log.w(LOG_TAG,"No art state found - faking signature!");
            return new byte[] { 0,1,2,3,4,5,6,7,8 };
        } else {
            updateStageKey(optGrpState);
            byte[] secret = optGrpState.get().getStageKey();
            artDb.update(groupId, optGrpState.get());
            Log.w(LOG_TAG,"Secret: "+hexDump(secret));

            return Crypto.hmacSha256(groupId.getBytes(),secret);
        }
    }

    public boolean verifyGroupIdSignature(String groupId, byte[] signature) {
        Optional<ARTState> optGrpState = artDb.getARTState(groupId);
        Log.w(LOG_TAG,"State from database for group"+groupId+"found"+artDb.getARTState(groupId).isPresent());


        if (!optGrpState.isPresent()) {
            // TODO: we should throw an exception here ...
            Log.w(LOG_TAG,"No art state found - verifying fake signature!");
            return false;
        } else {
            byte[] oldsecret = optGrpState.get().getStageKey();

            updateStageKey(optGrpState);
            byte[] secret = optGrpState.get().getStageKey();
            byte[] verifySignature = Crypto.hmacSha256(groupId.getBytes(),secret);
            boolean verified = Arrays.equals(signature,verifySignature);
            boolean oldverified = Arrays.equals(signature, Crypto.hmacSha256(groupId.getBytes(), oldsecret));

            Log.w(LOG_TAG,"art state found: signature verification: "+verified +":"+oldverified);
            artDb.update(groupId, optGrpState.get());

            return verified;
        }
    }

    public JsonARTMessage deserializeWrappedMessage(String rawMessageBody) {

        if (! isWrappedARTMessage(rawMessageBody)) {
            Log.e(LOG_TAG,"BUG: message is not a wrapped message!");
            return null;
        }

        Log.i(LOG_TAG,"Incoming ART message");

        String jsonString = rawMessageBody.substring(ART_CONFIG_IDENTIFIER.length());

        Log.i(LOG_TAG,"Incoming ART message: filtered: "+jsonString);


        return gson.fromJson(jsonString,JsonARTMessage.class);
    }

    /**
     * Used by @see {@link GroupManager}
     * @param groupId group ID
     * @param pMembers list of group members excluding self
     */
    public SetupResult setupART(@NonNull String groupId, @NonNull Set<Address> pMembers ) {

        SetupResult result = new SetupResult();
        HashSet<ARTGroupMember> artMemberSet = new HashSet<>();

        ARTState state = new ARTState(0,pMembers.size()+1);

        Address ownAddress = Address.fromSerialized(TextSecurePreferences.getLocalNumber(ctx));
        List<Address> members = new ArrayList<Address>();

        // add self as 0
        Address myAddress = Address.fromExternal(ctx, String.valueOf(ownAddress));

        //members.add(myAddress);

        artMemberSet.add(new ARTGroupMember(myAddress,0));

        DHPubKey[] peers = new DHPubKey[pMembers.size()+1];
        Map<Integer, IdentityKey> idKeys = new HashMap<>();

        int peerNum = 0;
        IdentityKeyPair myIdKeyPair = IdentityKeyUtil.getIdentityKeyPair(ctx);

        DHKeyPair myDhIdKeyPair = convertToDHKeyPair(myIdKeyPair.getPrivateKey());

        Log.d(LOG_TAG,"my id pubkey: "+hexDump(myDhIdKeyPair.getPubKeyBytes()));
        state.setIdentityKeyPair(myDhIdKeyPair);

        peers[0] = myDhIdKeyPair.getPubKey();

        for (Address memberAddr: pMembers) { // get all members of the group
            peerNum++;
            //members.add(memberAddr);

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

                idKeys.put(peerNum,idKey);
                DHPubKey peerIdPubKey = convertToDHPubKey(idPubkey);

                Log.d(LOG_TAG,"Peer id pubkey #"+peerNum+":" +hexDump(peerIdPubKey.getPubKeyBytes()));

                state.setPreKeyFor(peerNum, peerIdPubKey);
                peers[peerNum] = peerIdPubKey;
            }
        }

        generateSetupMessage(state, peers);

        dumpSimpleTree((ParentNode) state.getTree());


        Log.d(LOG_TAG,"Storing GroupART for groupId "+groupId);
        artDb.create(groupId, state);

        result.setArtState(state);
        result.setMembers(artMemberSet);

        return result;
    }

    private String hexDump(byte[] pubKeyBytes) {
        BigInteger bi = new BigInteger(pubKeyBytes);
        return bi.toString(16);
    }

    /**
     * generates the setup message and puts the serialized version into the passed state
     *
     * @param state the ART state
     * @param peerIdentities list of identity pubkeys
     * @return the non-serialized authenticated setup message
     */
    private void generateSetupMessage(ARTState state, DHPubKey[] peerIdentities){
        byte[] setupMessageSerialised = state.getSetupMessage();

        if (setupMessageSerialised == null){
            Map<Integer, DHPubKey> preKeys = new HashMap<>();

            for (int i = 1; i<peerIdentities.length; i++){
                Log.i(LOG_TAG,"Pre key #"+i+": "+ hexDump(state.getPreKeyFor(i).getPubKeyBytes()));
                preKeys.put(i,state.getPreKeyFor(i));
            }
            AuthenticatedMessage setupMessage = ART.setupGroup(state, peerIdentities, preKeys);
            setupMessageSerialised = setupMessage.serialise();
            state.setSetupMessage(setupMessageSerialised);
        }
    }


    public String handleConversationMessage(WrappedConversationMessage wrappedConversationMessage, String groupId) {
        byte[] signature = wrappedConversationMessage.getSignature();
        String origBody = wrappedConversationMessage.getOriginalBody();
        boolean verfySign = verifyGroupIdSignature(groupId, signature);
        Log.w(LOG_TAG, "GroupId signature verification result: " + verfySign);
        if (verfySign) {
            return origBody;
        } else {
            return "not verified member send: " + origBody;
        }
    }

    public AuthenticatedMessage updateMyKey(ARTState state) {
        SecretLeafNode newNode = new SecretLeafNode(DHKeyPair.generate(false)); // generate new key pair
        SecretNode newTree = updateTreeWithSecretLeaf(state.getTree(), state.getPeerNum(), newNode); //update local tree with secret leaf
        state.setTree(newTree); //set tree in state
        UpdateMessage m = new UpdateMessage( //generate new updateMessage
                state.getPeerNum(),
                pathNodeKeys(state.getTree(), state.getPeerNum())
        );
        byte[] serialisedUpdateMessage = m.serialise();
        byte[] mac = Crypto.hmacSha256(serialisedUpdateMessage, state.getStageKey());
        deriveStageKey(state);
        return new AuthenticatedMessage(serialisedUpdateMessage, mac);
    }



    private void deriveStageKey(ARTState state) {
        state.setStageKey(
                Crypto.artKDF(
                        state.getStageKey(),
                        ((SecretParentNode)state.getTree()).getRawSecretKey(),
                        state.getIdentities(),
                        state.getTree()
                )
        );
    }

    private static SecretNode updateTreeWithSecretLeaf(Node tree, int i, SecretLeafNode newLeaf) {
        int l = leftTreeSize(tree.numLeaves());
        if (tree.numLeaves() == 1) {
            return newLeaf;
        }
        SecretParentNode result;
        ParentNode treeParent = (ParentNode)tree;

        if (i < l) {
            result = new SecretParentNode(
                    updateTreeWithSecretLeaf(treeParent.getLeft(), i, newLeaf),
                    treeParent.getRight()
            );
        } else {
            result = new SecretParentNode(
                    treeParent.getLeft(),
                    updateTreeWithSecretLeaf(treeParent.getRight(), i - l, newLeaf)
            );
        }
        return result;
    }

    private static int leftTreeSize(int numLeaves) {
        return (int)Math.pow(2, Math.ceil(Math.log(numLeaves) / Math.log(2)) - 1);
    }

    protected static DHPubKey[] pathNodeKeys(Node tree, int i) {
        List<DHPubKey> keys = new ArrayList<DHPubKey>();

        while (tree.numLeaves() > 1) {
            int l = leftTreeSize(tree.numLeaves());
            ParentNode parentNode = (ParentNode) tree;
            keys.add(tree.getPubKey());
            if (i < l) {
                tree = parentNode.getLeft();
            } else {
                tree = parentNode.getRight();
                i -= l;
            }
        }
        keys.add(tree.getPubKey());
        return keys.toArray(new DHPubKey[] {});
    }

    private Node updateTreeWithPublicPath(Node tree, int i, DHPubKey[] newPath, int pathIndex) {
        int l = leftTreeSize(tree.numLeaves());
        if (newPath.length - 1 == pathIndex) {
            return new PublicLeafNode(newPath[pathIndex]);
        }

        ParentNode result;
        ParentNode treeAsParent = (ParentNode) tree;
        Node newLeft;
        Node newRight;

        if (i < l) {
            newLeft = updateTreeWithPublicPath(treeAsParent.getLeft(), i, newPath, pathIndex + 1);
            newRight = treeAsParent.getRight();
        } else {
            newLeft = treeAsParent.getLeft();
            newRight = updateTreeWithPublicPath(treeAsParent.getRight(), i - l, newPath, pathIndex + 1);
        }

        if (newLeft instanceof SecretNode) {
            result = new SecretParentNode((SecretNode)newLeft, newRight);
        } else if (newRight instanceof SecretNode) {
            result = new SecretParentNode(newLeft, (SecretNode)newRight);
        } else {
            result = new PublicParentNode(
                    newPath[pathIndex],
                    newLeft,
                    newRight
            );
        }
        if (!Arrays.equals(result.getPubKey().getPubKeyBytes(), newPath[pathIndex].getPubKeyBytes())) {
            Utils.printTree(result);
            Utils.except("Update operation inconsistent with provided path.");
        }

        return result;
    }

    public void updateStateFromMessage(ARTState state, UpdateMessage updateMessage) {
        Node tree = state.getTree();

        tree = updateTreeWithPublicPath(tree, updateMessage.getLeafNum(), updateMessage.getPath(), 0);
        state.setTree((SecretNode) tree);
        deriveStageKey(state);
    }

    public void updateStageKey(Optional<ARTState> optGrpState) {
        ARTState state = optGrpState.get();
        deriveStageKey(state);
    }

    public ARTState createNewTree(String groupId) {
        GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(ctx);
        Optional<GroupDatabase.GroupRecord> group = groupDatabase.getGroup(groupId);
        List<Address> members = group.get().getMembers();
        final Set<Address> results = new HashSet<>();
        for (Address member : members) {
            Recipient recipient = Recipient.from(ctx, member,false );
            results.add(recipient.getAddress());
        }

        SetupResult setupResult = setupART(groupId,results);
        sendSetupMessages(groupId, setupResult);

        return setupResult.getArtState();

    }

    public SignalServiceProtos.GroupContext processGroupUpdateMessage(WrappedARTGroupContext wrappedARTGroupContext) {
        String base64GrpCtx = wrappedARTGroupContext.getGroupContext();

        SignalServiceProtos.GroupContext groupContext = null;
        try {
            groupContext = SignalServiceProtos.GroupContext.parseFrom(Base64.decode(base64GrpCtx));
        } catch (IOException e) {
            // cannot happen
        }
        byte[] signature = wrappedARTGroupContext.getSignature();
        boolean verified = verifyGroupIdSignature(wrappedARTGroupContext.getGroupID(),signature);
        if (verified){
            return groupContext;
        } else {
            Log.w(LOG_TAG, "Group Update verification failed");
            return null;
        }
    }

    public static void createInstance(Context context) {
        instance = new ARTGroupManager(context);
    }

    public <T> T deserializeMessage(String rawMessage, Class<T> classOfMsg) {

        JsonARTMessage jsonMsg = deserializeWrappedMessage(rawMessage);

        if (! jsonMsg.getClass().equals(classOfMsg)) {
            throw new IllegalArgumentException("Message is not of class "+classOfMsg.getSimpleName());
        }

        return (T) jsonMsg;
    }
}
