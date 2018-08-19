package org.thoughtcrime.securesms.groups;

import com.facebook.research.asynchronousratchetingtree.art.ARTState;

import java.util.Set;

class SetupResult {
    private Set<ARTGroupMember> members;
    private ARTState artState;

    public ARTState getArtState() {
        return artState;
    }

    public void setArtState(ARTState artState) {
        this.artState = artState;
    }

    public Set<ARTGroupMember> getMembers() {
        return members;
    }

    public void setMembers(Set<ARTGroupMember> members) {
        this.members = members;
    }
}
