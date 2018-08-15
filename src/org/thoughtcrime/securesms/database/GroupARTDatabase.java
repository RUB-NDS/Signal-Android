package org.thoughtcrime.securesms.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.annimon.stream.Stream;
import com.facebook.research.asynchronousratchetingtree.KeyServer;
import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.SetupMessage;
import com.google.gson.Gson;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.groups.SignalART;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;

import java.sql.Blob;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class GroupARTDatabase extends Database {
    static final String TABLE_NAME          = "group_arts";
    private static final String ID                  = "_id";
    static final String GROUP_ID            = "group_id";
    private static final String MEMBER_ID               = "member_id";
    private static final String ART_STATE              = "art_state";
    private static String ART_MESSAGE_SER        = "art_message_ser";
    private static String LEAF_NUM               = "leaf_num";






    public GroupARTDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME +
                    " (" + ID + " INTEGER PRIMARY KEY, " +
                    GROUP_ID + " TEXT, " +
                    MEMBER_ID + " TEXT, " +

                    ART_STATE + " BLOB " +
                    ART_MESSAGE_SER +"BLOB"+
                    LEAF_NUM +"INTEGER"+
                   ");";

    public static final String[] CREATE_INDEXS = {
            "CREATE UNIQUE INDEX IF NOT EXISTS grp_member_id_index ON " + TABLE_NAME + " (" + GROUP_ID + ","+ MEMBER_ID+");",
    };



    Optional<GroupDatabase.GroupRecord> getGroup(Cursor cursor) {
        GroupDatabase.Reader reader = new GroupDatabase.Reader(cursor);
        return Optional.fromNullable(reader.getCurrent());
    }


    public GroupDatabase.Reader getGroups() {
        @SuppressLint("Recycle")
        Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null);
        return new GroupDatabase.Reader(cursor);
    }


    public void create(@NonNull String groupId,
                       @NonNull Address member,
                       @Nullable ARTState state,
                       @Nullable byte[] message,
                       @Nullable int leafNum)
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(GROUP_ID, groupId);
        contentValues.put(MEMBER_ID, String.valueOf(member));
        byte[] serizalizedART = ARTStateSerializer.getInstance().toByteArray(state);
        contentValues.put(ART_STATE, serizalizedART);
        contentValues.put(ART_MESSAGE_SER, message);
        contentValues.put(LEAF_NUM, leafNum);

        databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
    }

    public void update(String groupId, Address member, ARTState state) {

        ContentValues contentValues = new ContentValues();

        contentValues.put(MEMBER_ID, String.valueOf(member));
        byte[] serializedART = ARTStateSerializer.getInstance().toByteArray(state);

        contentValues.put(ART_STATE, serializedART);


        databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues,
                GROUP_ID + " = ? AND "+ MEMBER_ID + " = ?",
                new String[] {groupId, String.valueOf(member)});


        notifyConversationListListeners();
    }

    public void delete (String groupId){
        ContentValues contentValues = new ContentValues();

        contentValues.put(GROUP_ID, groupId);

        databaseHelper.getWritableDatabase().delete(TABLE_NAME,GROUP_ID + " = ?", new String[]{groupId} );
    }

    public ARTState getARTStateByLeafNum (String groupId, String leafNum){
        Cursor cursor = null;

        try {
            cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[] {GROUP_ID, MEMBER_ID, ART_STATE},
                    GROUP_ID + " = ? AND "+LEAF_NUM+"=?",
                    new String[] {groupId, leafNum},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                byte[] serializedArt = cursor.getBlob(cursor.getColumnIndexOrThrow(ART_STATE));
                return ARTStateSerializer.getInstance().fromByteArray(serializedArt);
            }

            return null;
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }



    public ARTState getARTState(String groupId, String memberId) {
        Cursor cursor = null;

        try {
            cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[] {GROUP_ID, MEMBER_ID, ART_STATE},
                    GROUP_ID + " = ? AND "+MEMBER_ID+"=?",
                    new String[] {groupId, memberId},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                byte[] serializedArt = cursor.getBlob(cursor.getColumnIndexOrThrow(ART_STATE));
                return ARTStateSerializer.getInstance().fromByteArray(serializedArt);
            }

            return null;
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

        public void update(String groupId, ARTState state, int leafNum) {

            ContentValues contentValues = new ContentValues();

            contentValues.put(LEAF_NUM, String.valueOf(leafNum));
            byte[] serializedART = ARTStateSerializer.getInstance().toByteArray(state);

            contentValues.put(ART_STATE, serializedART);


            databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues,
                    GROUP_ID + " = ? AND "+ LEAF_NUM + " = ?",
                    new String[] {groupId, String.valueOf(leafNum)});


            notifyConversationListListeners();
        }

}
