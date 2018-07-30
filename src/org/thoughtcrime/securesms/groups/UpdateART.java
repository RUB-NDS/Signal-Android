package org.thoughtcrime.securesms.groups;

import com.facebook.research.asynchronousratchetingtree.art.ART;
import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.AuthenticatedMessage;

import java.util.Map;

import static com.facebook.research.asynchronousratchetingtree.art.ART.*;

public class UpdateART {

    public Map<Integer, Byte[]> updateART(ARTState state, AuthenticatedMessage message, int flag){
        Map<Integer, Byte[]> updateMessages = null;

        int peerNum = state.getPeerNum();
        int peerCount = state.getPeerCount();

        switch (flag){
            case 0: return null;
            case 1:  AuthenticatedMessage updateMessage = updateKey(state);
            case 2:  processUpdateMessage(state, message);
        }


        return updateMessages;
    }


}
