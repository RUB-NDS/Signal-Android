package org.thoughtcrime.securesms.groups;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.attachments.Attachment;

public class GroupMessagingARTAttachment extends Attachment {

    public GroupMessagingARTAttachment(@NonNull String contentType, int transferState, long size, @Nullable String fileName, @Nullable String location, @Nullable String key, @Nullable String relay, @Nullable byte[] digest, @Nullable String fastPreflightId, boolean voiceNote, int width, int height, boolean quote) {
        super(contentType, transferState, size, fileName, location, key, relay, digest, fastPreflightId, voiceNote, width, height, quote);
    }

    @Nullable
    @Override
    public Uri getDataUri() {
        return null;
    }

    @Nullable
    @Override
    public Uri getThumbnailUri() {
        return null;
    }
}
