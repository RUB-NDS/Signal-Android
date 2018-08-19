package org.thoughtcrime.securesms.groups;

import org.thoughtcrime.securesms.database.Address;

class ARTGroupMember {
    private int leafNum;
    private Address address;

    public ARTGroupMember(Address addr, int leafNum) {
        this.address=addr;
        this.leafNum=leafNum;
    }

    public int getLeafNum() {
        return leafNum;
    }

    public Address getAddress() {
        return address;
    }

    public void setLeafNum(int leafNum) {
        this.leafNum = leafNum;
    }

    public void setAddress(Address address) {
        this.address = address;
    }
}
