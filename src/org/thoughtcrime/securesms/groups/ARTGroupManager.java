package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.util.Log;

import com.facebook.research.asynchronousratchetingtree.Utils;
import com.facebook.research.asynchronousratchetingtree.art.ART;
import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.AuthenticatedMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.SetupMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.UpdateMessage;
import com.facebook.research.asynchronousratchetingtree.crypto.Crypto;
import com.google.gson.Gson;

import org.thoughtcrime.securesms.database.ARTStateSerializer;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupARTDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.groups.protocol.WrappedARTMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class ARTGroupManager {
    private static final String LOG_TAG = ARTGroupManager.class.getSimpleName();

    public final static  String ART_CONFIG_IDENTIFIER = "<ART_CONFIG_MESSAGE>";
    private final UpdateART updateArt;

    private Context ctx;

    private GroupARTDatabase artDb;
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

        this.stateSerializer = ARTStateSerializer.getInstance();

        this.gson = new Gson();

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

        Address grpAddress = Address.fromExternal(ctx, groupId);
        String wrappedARTMessage = wrapMessage(groupId, authenticatedUpdateMessage, UpdateMessage.class);

        OutgoingSecureMediaMessage sendArtMessage =

                new OutgoingSecureMediaMessage(Recipient.from(ctx, grpAddress, false),
                        wrappedARTMessage, null, System.currentTimeMillis(), -1,
                        1000, null, Collections.emptyList());
        MessageSender.send(ctx,sendArtMessage,-1,false,null);

        artDb.update(groupId,state);
    }

    public void sendARTStates(String peerId, Set<Address> members, boolean isUpdate){

        //public void sendARTState(String id, Address member, Context context, SignalServiceEnvelope envelope, boolean isUpdate){
        //Sende Nachricht an jedes Gruppenmitglied mit neuem State bzw. update oder setupMessage

        for (Address member: members) {

            Log.d(LOG_TAG,"Send ART state to: "+member.toString());

            Optional<ARTState> optState = artDb.getARTState(peerId);

            ARTState artState = optState.get();


            byte[] serializedArtState = stateSerializer.toByteArray(artState);

            // TODO: BROKEN !
            //WrappedARTMessage wrappedMsg = wrapMessage(peerId,artState,SetupMessage.class);


            //String wrappedSerialized = gson.toJson(wrappedMsg);

           /* String artMessage = ART_CONFIG_IDENTIFIER + wrappedSerialized;

            OutgoingSecureMediaMessage sendArtMessage =

                    new OutgoingSecureMediaMessage(Recipient.from(ctx, member, false),
                            artMessage, null, System.currentTimeMillis(), -1,
                            1000, null, Collections.emptyList());

            MessageSender.send(ctx,sendArtMessage,-1,false,null);*/
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

    public void processSetupMessage(WrappedARTMessage wrappedMsg) throws IllegalStateException {
        AuthenticatedMessage authenticatedMessage = new AuthenticatedMessage(wrappedMsg.getSerializedMessage());

        final String groupId = wrappedMsg.getGroupId();

        if (! SetupMessage.class.getSimpleName().equals(wrappedMsg.getArtMessageClass())) {
            throw new IllegalStateException("Message is not a setup message");
        }

        Optional<GroupDatabase.GroupRecord> optionalGroup = DatabaseFactory.getGroupDatabase(ctx).getGroup(groupId);
        int peerCount = optionalGroup.get().getMembers().size();

        Optional<ARTState> optArtstate = artDb.getARTState(groupId);

        ARTState newState = new ARTState(wrappedMsg.getLeafNum(),peerCount);
        ART.processSetupMessage(newState, authenticatedMessage,wrappedMsg.getLeafNum());

        artDb.create(wrappedMsg.getGroupId(),newState);

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
}
