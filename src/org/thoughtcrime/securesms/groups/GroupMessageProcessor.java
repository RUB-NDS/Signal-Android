package org.thoughtcrime.securesms.groups;


import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.AuthenticatedMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.SetupMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.UpdateMessage;
import com.facebook.research.asynchronousratchetingtree.crypto.Crypto;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.ARTStateSerializer;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupARTDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase.InsertResult;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.jobs.AvatarDownloadJob;
import org.thoughtcrime.securesms.jobs.PushGroupUpdateJob;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.IncomingGroupMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup.Type;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.AttachmentPointer;
import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

public class GroupMessageProcessor {

  public final static  String ART_CONFIG_IDENTIFIER = "<ART_CONFIG_MESSAGE>";


  private static final String TAG = GroupMessageProcessor.class.getSimpleName();

  public static @Nullable Long process(@NonNull Context context,
                                       @NonNull SignalServiceEnvelope envelope,
                                       @NonNull SignalServiceDataMessage message,
                                       boolean outgoing)
  {//Wichtig: Nur Nachrichten mit GroupID oder nicht ex. groupInfo akzeptiert
    if (!message.getGroupInfo().isPresent() || message.getGroupInfo().get().getGroupId() == null) {
      Log.w(TAG, "Received group message with no id! Ignoring...");
      return null;
    }
 //Wichtig: Rufe Gruppendatenbank auf, hole groupInfo, GroupID, (GroupRecord)
    GroupDatabase         database = DatabaseFactory.getGroupDatabase(context);
    SignalServiceGroup    group    = message.getGroupInfo().get();
    String                id       = GroupUtil.getEncodedId(group.getGroupId(), false);
    Optional<GroupRecord> record   = database.getGroup(id);

    if (record.isPresent() && group.getType() == Type.UPDATE) { //Wichtig: GroupREcord existiert & Typ=Update
      return handleGroupUpdate(context, envelope, group, record.get(), outgoing); //GroupUpdate mit hole Record
    } else if (!record.isPresent() && group.getType() == Type.UPDATE) {// GroupRecord existiert nicht und Typ=Update
      return handleGroupCreate(context, envelope, group, outgoing); //GroupUpdate ohne hole Record
    } else if (record.isPresent() && group.getType() == Type.QUIT) { //Record existiert und Typ=Quit
      return handleGroupLeave(context, envelope, group, record.get(), outgoing); //Behandle Gruppe verlassen
    } else if (record.isPresent() && group.getType() == Type.REQUEST_INFO) { //Record existiert und Typ=Info
      return handleGroupInfoRequest(context, envelope, group, record.get()); //Behandle Gruppenauskunft
    } else {
      Log.w(TAG, "Received unknown type, ignoring..."); //sonst ignoriere
      return null;
    }
  }

  private static @Nullable Long handleGroupCreate(@NonNull Context context,
                                                  @NonNull SignalServiceEnvelope envelope,
                                                  @NonNull SignalServiceGroup group,
                                                  boolean outgoing)
  {
    GroupDatabase        database = DatabaseFactory.getGroupDatabase(context);
    String               id       = GroupUtil.getEncodedId(group.getGroupId(), false);
    GroupContext.Builder builder  = createGroupContext(group);
    builder.setType(GroupContext.Type.UPDATE); //Wichtig: Setze Typ auf Update

    BuildART buildART = new BuildART();
    buildART.setupART(context, group);

    SignalServiceAttachment avatar  = group.getAvatar().orNull();
    List<Address>           members = group.getMembers().isPresent() ? new LinkedList<Address>() : null;

    int memberCount = 0;
    if (group.getMembers().isPresent()) { //Wichtig: Erzeuge Liste der Mitglieder
      for (String member : group.getMembers().get()) {
        members.add(Address.fromExternal(context, member));
        memberCount +=1;
      }
    }
//erzeuge Datenbankeintrag
    database.create(id, group.getName().orNull(), members,
                    avatar != null && avatar.isPointer() ? avatar.asPointer() : null,
                    envelope.getRelay());

    return storeMessage(context, envelope, group, builder.build(), outgoing);
  }

  private static @Nullable Long handleGroupUpdate(@NonNull Context context,
                                                  @NonNull SignalServiceEnvelope envelope,
                                                  @NonNull SignalServiceGroup group,
                                                  @NonNull GroupRecord groupRecord,
                                                  boolean outgoing)
  {

    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    String        id       = GroupUtil.getEncodedId(group.getGroupId(), false);

    Set<Address> recordMembers = new HashSet<>(groupRecord.getMembers());
    Set<Address> messageMembers = new HashSet<>();

    for (String messageMember : group.getMembers().get()) {
      messageMembers.add(Address.fromExternal(context, messageMember));
    }

    Set<Address> addedMembers = new HashSet<>(messageMembers);
    addedMembers.removeAll(recordMembers);

    Set<Address> missingMembers = new HashSet<>(recordMembers);
    missingMembers.removeAll(messageMembers);

    GroupContext.Builder builder = createGroupContext(group);
    builder.setType(GroupContext.Type.UPDATE);

    GroupARTDatabase groupARTDatabase = DatabaseFactory.getGroupARTDatabase(context);
    Address ownAddress = Address.fromSerialized(TextSecurePreferences.getLocalNumber(context));
    byte[] stageKey =  groupARTDatabase.getARTState(String.valueOf(group), String.valueOf(ownAddress)).getStageKey();
    byte[] cryptoMessage = Crypto.encrypt(id.getBytes(), stageKey);

    if (addedMembers.size() > 0) {//Wichtig: Fuege neue Mitgleider hinzu
      Set<Address> unionMembers = new HashSet<>(recordMembers);
      unionMembers.addAll(messageMembers);
      database.updateMembers(id, new LinkedList<>(unionMembers));

      builder.clearMembers();

      BuildART buildART = new BuildART();
      buildART.setupART(context, group);

      for (Address addedMember : addedMembers) {
        builder.addMembers(addedMember.serialize());
        sendARTState(groupARTDatabase, id, addedMember, context, envelope, false);
      }
    } else {
      builder.clearMembers();

      if (outgoing){
        for (Address recipient: addedMembers){
            sendARTState(groupARTDatabase, id, recipient, context, envelope,true);
        }
      }
    }

    if (missingMembers.size() > 0) {
      // TODO We should tell added and missing about each-other.
    }

    if (group.getName().isPresent() || group.getAvatar().isPresent()) {
      SignalServiceAttachment avatar = group.getAvatar().orNull();
      database.update(id, group.getName().orNull(), avatar != null ? avatar.asPointer() : null);
    }

    if (group.getName().isPresent() && group.getName().get().equals(groupRecord.getTitle())) {
      builder.clearName();
    }

    if (!groupRecord.isActive()) database.setActive(id, true);


    return storeMessage(context, envelope, group, builder.build(), outgoing);
  }

  private static Long handleGroupInfoRequest(@NonNull Context context,
                                             @NonNull SignalServiceEnvelope envelope,
                                             @NonNull SignalServiceGroup group,
                                             @NonNull GroupRecord record)
  {
    if (record.getMembers().contains(Address.fromExternal(context, envelope.getSource()))) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new PushGroupUpdateJob(context, envelope.getSource(), group.getGroupId()));
    }

    return null;
  }

  private static Long handleGroupLeave(@NonNull Context context,
                                       @NonNull SignalServiceEnvelope envelope,
                                       @NonNull SignalServiceGroup group,
                                       @NonNull GroupRecord record,
                                       boolean outgoing)
  {
    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    String        id       = GroupUtil.getEncodedId(group.getGroupId(), false);
    List<Address> members  = record.getMembers();

    GroupContext.Builder builder = createGroupContext(group);
    builder.setType(GroupContext.Type.QUIT);

    if (members.contains(Address.fromExternal(context, envelope.getSource()))) {
      database.remove(id, Address.fromExternal(context, envelope.getSource()));
      if (outgoing) database.setActive(id, false);

      return storeMessage(context, envelope, group, builder.build(), outgoing);
    }

    return null;
  }


  private static @Nullable Long storeMessage(@NonNull Context context,
                                             @NonNull SignalServiceEnvelope envelope,
                                             @NonNull SignalServiceGroup group,
                                             @NonNull GroupContext storage,
                                             boolean  outgoing)
  {
    if (group.getAvatar().isPresent()) { //Wichtig: Falls Avatar existiert downloaden
      ApplicationContext.getInstance(context).getJobManager()
                        .add(new AvatarDownloadJob(context, group.getGroupId()));
    }

    try { //Wichtig: Falls Outgoing==true setze: mmsDatabase und Recipient aus Context, Adresse aus Context und encoded GroupID
      if (outgoing) { //erzeuge neue ausgehende Gruppennachricht, setze ThreadID und MessageID
        MmsDatabase               mmsDatabase     = DatabaseFactory.getMmsDatabase(context);
        Address                   addres          = Address.fromExternal(context, GroupUtil.getEncodedId(group.getGroupId(), false));
        Recipient                 recipient       = Recipient.from(context, addres, false);
        OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(recipient, storage, null, envelope.getTimestamp(), 0, null, Collections.emptyList());
        long                      threadId        = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
        long                      messageId       = mmsDatabase.insertMessageOutbox(outgoingMessage, threadId, false, null);

        mmsDatabase.markAsSent(messageId, true); //markiere Nachricht als gesendet

        return threadId;
      } else { //Wichtig: falls Outgoing==false: hole smsDatabase, encodiere body, erzeuge incomingTextMessage und incommingGroupMessage
        SmsDatabase          smsDatabase  = DatabaseFactory.getSmsDatabase(context);
        String               body         = Base64.encodeBytes(storage.toByteArray());
        IncomingTextMessage  incoming     = new IncomingTextMessage(Address.fromExternal(context, envelope.getSource()), envelope.getSourceDevice(), envelope.getTimestamp(), body, Optional.of(group), 0);
        IncomingGroupMessage groupMessage = new IncomingGroupMessage(incoming, storage, body);


        Optional<InsertResult> insertResult = smsDatabase.insertMessageInbox(groupMessage);

        if (insertResult.isPresent()) {
          MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
          return insertResult.get().getThreadId();
        } else {
          return null;
        }
      }
    } catch (MmsException e) {
      Log.w(TAG, e);
    }

    return null;
  }

  private static GroupContext.Builder createGroupContext(SignalServiceGroup group) {
    GroupContext.Builder builder = GroupContext.newBuilder();
    builder.setId(ByteString.copyFrom(group.getGroupId()));

    if (group.getAvatar().isPresent() && group.getAvatar().get().isPointer()) {
      builder.setAvatar(AttachmentPointer.newBuilder()
                                         .setId(group.getAvatar().get().asPointer().getId())
                                         .setKey(ByteString.copyFrom(group.getAvatar().get().asPointer().getKey()))
                                         .setContentType(group.getAvatar().get().getContentType()));
    }

    if (group.getName().isPresent()) {
      builder.setName(group.getName().get());
    }

    if (group.getMembers().isPresent()) {
      builder.addAllMembers(group.getMembers().get());
    }

    return builder;
  }

  private static void sendARTState(GroupARTDatabase groupARTDatabase, String id, Address member, Context context, SignalServiceEnvelope envelope, boolean isUpdate){
    //Sende Nachricht an jedes Gruppenmitglied mit neuem State bzw. update oder setupMessage
    ARTState artState = groupARTDatabase.getARTState(id, String.valueOf(member));
    byte[] serializedArtState = ARTStateSerializer.getInstance().toByteArray(artState);

    WrappedARTMessage wrappedMsg = new WrappedARTMessage();

    Gson gson = new Gson();
    if (isUpdate) {
      AuthenticatedMessage authenticatedMessage = UpdateART.update(id, context);

      byte[] updateMessage = authenticatedMessage.serialise();
      wrappedMsg.setSerializedMessage(updateMessage);
      wrappedMsg.setMessageClass(UpdateMessage.class.getSimpleName());
      wrappedMsg.setGroupId(id);
    } else {
      byte[] setupMessage = artState.getSetupMessage();
      wrappedMsg.setSerializedMessage(setupMessage);
      wrappedMsg.setMessageClass(SetupMessage.class.getSimpleName());
      wrappedMsg.setArtState(artState);
      wrappedMsg.setGroupId(id);
      wrappedMsg.setLeafNum(artState.getPeerNum());
    }
    String wrappedSerialized = gson.toJson(wrappedMsg);

    String artMessage = ART_CONFIG_IDENTIFIER + wrappedSerialized;


    OutgoingSecureMediaMessage outgoingSecureMediaMessage = new OutgoingSecureMediaMessage(Recipient.from(context, member, false ),artMessage, null,  envelope.getTimestamp(), -1, 1000 , null, Collections.emptyList());
    MmsDatabase               mmsDatabase     = DatabaseFactory.getMmsDatabase(context);

    long                      threadId        = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(Recipient.from(context, member, false ));
    long                      messageId       = 0;
    try {
      messageId = mmsDatabase.insertMessageOutbox(outgoingSecureMediaMessage, threadId, false, null);
    } catch (MmsException e) {
      e.printStackTrace();
    }

    mmsDatabase.markAsSent(messageId, true); //markiere Nachricht als gesendet
  }
}
