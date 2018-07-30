package org.thoughtcrime.securesms.groups;

import com.facebook.research.asynchronousratchetingtree.art.ART;
import com.facebook.research.asynchronousratchetingtree.art.ARTState;

public class SignalART {
    private ARTState artState;
    private int artFlag;


    public SignalART(ARTState[] states, int i) {
    }

    public void setArtFlag(int artFlag) {
        this.artFlag = artFlag;
    }

    public void setArtState(ARTState artState) {
        this.artState = artState;
    }

    public ARTState getArtState() {
        return artState;
    }

    public int getArtFlag() {
        return artFlag;
    }
}
