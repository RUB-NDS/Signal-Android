package org.thoughtcrime.securesms.groups;

import com.facebook.research.asynchronousratchetingtree.MessageDistributer;
import com.facebook.research.asynchronousratchetingtree.art.ART;
import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.ARTMessageDistributer;
import com.facebook.research.asynchronousratchetingtree.art.message.AuthenticatedMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.CiphertextMessage;
import com.facebook.research.asynchronousratchetingtree.crypto.Crypto;

public class MessageART {
    public MessageART() {

    }

    public CiphertextMessage generateMessagePerPeer(ARTState state, byte[] plaintext) {

        //only true for facebook test implementation // All peers have the same key, so the "withPeer(0)" aspect of this is a no-op.
        //byte[] key = state.getKeyWithPeer(0);
        //byte[] ciphertext = Crypto.encrypt(plaintext, key);

        byte[] key = state.getKeyWithPeer(state.getPeerNum());
        byte[] ciphertext = Crypto.encrypt(plaintext, key);

        AuthenticatedMessage updateMessage = ART.updateKey(state);
        CiphertextMessage message = new CiphertextMessage(updateMessage, ciphertext);
        return message;
    }
}
