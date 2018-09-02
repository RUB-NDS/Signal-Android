package org.thoughtcrime.securesms.mms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.groups.ARTGroupManager;
import org.thoughtcrime.securesms.groups.protocol.WrappedARTGroupContext;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class OutgoingGroupMediaMessage extends OutgoingSecureMediaMessage {

  private static final String LOG_TAG = OutgoingGroupMediaMessage.class.getSimpleName();
  private final GroupContext group;

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull String wrapped,
                                   @NonNull List<Attachment> avatar,
                                   long sentTimeMillis,
                                   long expiresIn,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts)
      throws IOException
  {
    super(recipient, wrapped, avatar, sentTimeMillis,
          ThreadDatabase.DistributionTypes.CONVERSATION, expiresIn, quote, contacts);

    ARTGroupManager mgr = ARTGroupManager.getInstance();
    WrappedARTGroupContext wrappedMsg = mgr.deserializeMessage(wrapped,WrappedARTGroupContext.class);
    Log.i(LOG_TAG,"Outgoing Group message base64 ctx:"+wrappedMsg.getGroupContext());
    Log.i(LOG_TAG,"body: "+body);
    this.group = GroupContext.parseFrom(Base64.decode(wrappedMsg.getGroupContext()));
    //GroupContext.parseFrom(Base64.decode(wrapped));

  }

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull GroupContext group,
                                   @Nullable final Attachment avatar,
                                   long sentTimeMillis,
                                   long expireIn,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts
                                   )
  {
    super(recipient, getWrappedARTMessage(group,recipient.getAddress().toString()),
          new LinkedList<Attachment>() {{if (avatar != null) add(avatar);}},
          System.currentTimeMillis(),
          ThreadDatabase.DistributionTypes.CONVERSATION, expireIn, quote, contacts);

    this.group = group;
  }

  private static String getWrappedARTMessage(GroupContext group,String groupId) {
    ARTGroupManager artGrpM = ARTGroupManager.getInstance();
    WrappedARTGroupContext wrappedARTGroupContext = new WrappedARTGroupContext();
    wrappedARTGroupContext.setGroupContext(Base64.encodeBytes(group.toByteArray()));

    wrappedARTGroupContext.setSignature(artGrpM.signGroupId(groupId));
    wrappedARTGroupContext.setGroupID(groupId);

    String wrappedMessage = artGrpM.serializeWrappedMessage(wrappedARTGroupContext);

    Log.i(LOG_TAG,"wrappedOutMsg: "+wrappedMessage);

    return wrappedMessage;//Base64.encodeBytes(wrappedMessage.getBytes());
  }


  @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isGroupUpdate() {
    return group.getType().getNumber() == GroupContext.Type.UPDATE_VALUE;
  }

  public boolean isGroupQuit() {
    return group.getType().getNumber() == GroupContext.Type.QUIT_VALUE;
  }

  public GroupContext getGroupContext() {
    return group;
  }
}
