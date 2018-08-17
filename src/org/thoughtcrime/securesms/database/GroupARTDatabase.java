package org.thoughtcrime.securesms.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;

import com.facebook.research.asynchronousratchetingtree.art.ARTState;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.whispersystems.libsignal.util.guava.Function;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.libsignal.util.guava.Supplier;

import java.util.Set;

public class GroupARTDatabase extends Database {
    static final String TABLE_NAME          = "group_arts";
    private static final String ID                  = "_id";
    static final String GROUP_ID            = "group_id";
    private static final String ART_STATE              = "art_state";






    public GroupARTDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME +
                    " (" + ID + " INTEGER PRIMARY KEY, " +
                    GROUP_ID + " TEXT, " +
                    ART_STATE + " BLOB " +

                   ");";

    public static final String[] CREATE_INDEXES = {
            "CREATE UNIQUE INDEX IF NOT EXISTS grp_member_id_index ON " + TABLE_NAME + " (" + GROUP_ID + ");",
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
                       @NonNull ARTState state
                      )
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(GROUP_ID, groupId);
        byte[] serizalizedART = ARTStateSerializer.getInstance().toByteArray(state);
        contentValues.put(ART_STATE, serizalizedART);

        databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
    }

    public void update(String groupId, ARTState state) {

        ContentValues contentValues = new ContentValues();

        byte[] serializedART = ARTStateSerializer.getInstance().toByteArray(state);

        contentValues.put(ART_STATE, serializedART);


        databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues,
                GROUP_ID + " = ?",
                new String[] {groupId});


        notifyConversationListListeners();
    }

    public void delete (String groupId){
        ContentValues contentValues = new ContentValues();

        contentValues.put(GROUP_ID, groupId);

        databaseHelper.getWritableDatabase().delete(TABLE_NAME,GROUP_ID + " = ?", new String[]{groupId} );
    }

    public Optional<ARTState>  getARTState(String groupId) {
        Cursor cursor = null;


        try {
            cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[] {GROUP_ID, ART_STATE},
                    GROUP_ID + " = ?",
                    new String[] {groupId },
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                byte[] serializedArt = cursor.getBlob(cursor.getColumnIndexOrThrow(ART_STATE));
                return Optional.fromNullable(ARTStateSerializer.getInstance().fromByteArray(serializedArt));
            }

            return Optional.absent();
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }
}
