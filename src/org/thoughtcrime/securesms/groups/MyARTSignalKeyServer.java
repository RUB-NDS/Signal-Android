package org.thoughtcrime.securesms.groups;

import android.content.Context;

import com.facebook.research.asynchronousratchetingtree.crypto.DHPubKey;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.database.Address;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;

import java.util.HashMap;
import java.util.Map;

import javax.crypto.interfaces.DHPublicKey;

public class MyARTSignalKeyServer {

    private static MyARTSignalKeyServer instance;

    public static final MyARTSignalKeyServer getInstance(Context context) {
        if (instance == null) {
            instance = new MyARTSignalKeyServer(context);
        }

        return instance;
    }

    private final Context context;
    private Map<String,IdentityKey> identityKeys;
    private Map<String,DHPublicKey> ephemeralKeys;


    private MyARTSignalKeyServer(Context context) {
        this.context = context;
        
        this.identityKeys = new HashMap<>();
        this.ephemeralKeys = new HashMap<>();
    }

    public IdentityKeyPair getMyIdentityKeyPair() {
        return IdentityKeyUtil.getIdentityKeyPair(context);
    }

    public IdentityKey getIdentityKey(String id) {

        return identityKeys.get(id);

    }


    public DHPubKey getActivePreKey(Address memberAddr) {
        IdentityKey identityKey = identityKeys.get(String.valueOf(memberAddr));

        // TODO ECKey vs DHKEY????
        return null;

    }
}
