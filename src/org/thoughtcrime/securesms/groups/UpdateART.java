package org.thoughtcrime.securesms.groups;

import android.content.Context;

import com.facebook.research.asynchronousratchetingtree.MessageDistributer;
import com.facebook.research.asynchronousratchetingtree.Utils;
import com.facebook.research.asynchronousratchetingtree.art.ART;
import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.ARTMessageDistributer;
import com.facebook.research.asynchronousratchetingtree.art.message.AuthenticatedMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.CiphertextMessage;
import com.facebook.research.asynchronousratchetingtree.art.message.UpdateMessage;
import com.facebook.research.asynchronousratchetingtree.art.tree.Node;
import com.facebook.research.asynchronousratchetingtree.art.tree.ParentNode;
import com.facebook.research.asynchronousratchetingtree.art.tree.PublicLeafNode;
import com.facebook.research.asynchronousratchetingtree.art.tree.PublicParentNode;
import com.facebook.research.asynchronousratchetingtree.art.tree.SecretLeafNode;
import com.facebook.research.asynchronousratchetingtree.art.tree.SecretNode;
import com.facebook.research.asynchronousratchetingtree.art.tree.SecretParentNode;
import com.facebook.research.asynchronousratchetingtree.crypto.Crypto;
import com.facebook.research.asynchronousratchetingtree.crypto.DHKeyPair;
import com.facebook.research.asynchronousratchetingtree.crypto.DHPubKey;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.Database;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupARTDatabase;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.facebook.research.asynchronousratchetingtree.art.ART.*;

public class UpdateART {

    public UpdateART() {

    }


    /**
     * Called to update
     * @param state
     * @return
     */
    public AuthenticatedMessage updateMyKey(ARTState state) {
        SecretLeafNode newNode = new SecretLeafNode(DHKeyPair.generate(false)); // generate new key pair
        SecretNode newTree = updateTreeWithSecretLeaf(state.getTree(), state.getPeerNum(), newNode); //update local tree with secret leaf
        state.setTree(newTree); //set tree in state
        UpdateMessage m = new UpdateMessage( //generate new updateMessage
                state.getPeerNum(),
                pathNodeKeys(state.getTree(), state.getPeerNum())
        );
        byte[] serialisedUpdateMessage = m.serialise();
        byte[] mac = Crypto.hmacSha256(serialisedUpdateMessage, state.getStageKey());
        deriveStageKey(state);
        return new AuthenticatedMessage(serialisedUpdateMessage, mac);
    }

    private void deriveStageKey(ARTState state) {
        state.setStageKey(
                Crypto.artKDF(
                        state.getStageKey(),
                        ((SecretParentNode)state.getTree()).getRawSecretKey(),
                        state.getIdentities(),
                        state.getTree()
                )
        );
    }

    private static SecretNode updateTreeWithSecretLeaf(Node tree, int i, SecretLeafNode newLeaf) {
        int l = leftTreeSize(tree.numLeaves());
        if (tree.numLeaves() == 1) {
            return newLeaf;
        }
        SecretParentNode result;
        ParentNode treeParent = (ParentNode)tree;

        if (i < l) {
            result = new SecretParentNode(
                    updateTreeWithSecretLeaf(treeParent.getLeft(), i, newLeaf),
                    treeParent.getRight()
            );
        } else {
            result = new SecretParentNode(
                    treeParent.getLeft(),
                    updateTreeWithSecretLeaf(treeParent.getRight(), i - l, newLeaf)
            );
        }
        return result;
    }

    private static int leftTreeSize(int numLeaves) {
        return (int)Math.pow(2, Math.ceil(Math.log(numLeaves) / Math.log(2)) - 1);
    }

    protected static DHPubKey[] pathNodeKeys(Node tree, int i) {
        List<DHPubKey> keys = new ArrayList<DHPubKey>();

        while (tree.numLeaves() > 1) {
            int l = leftTreeSize(tree.numLeaves());
            ParentNode parentNode = (ParentNode) tree;
            keys.add(tree.getPubKey());
            if (i < l) {
                tree = parentNode.getLeft();
            } else {
                tree = parentNode.getRight();
                i -= l;
            }
        }
        keys.add(tree.getPubKey());
        return keys.toArray(new DHPubKey[] {});
    }

    private Node updateTreeWithPublicPath(Node tree, int i, DHPubKey[] newPath, int pathIndex) {
        int l = leftTreeSize(tree.numLeaves());
        if (newPath.length - 1 == pathIndex) {
            return new PublicLeafNode(newPath[pathIndex]);
        }

        ParentNode result;
        ParentNode treeAsParent = (ParentNode) tree;
        Node newLeft;
        Node newRight;

        if (i < l) {
            newLeft = updateTreeWithPublicPath(treeAsParent.getLeft(), i, newPath, pathIndex + 1);
            newRight = treeAsParent.getRight();
        } else {
            newLeft = treeAsParent.getLeft();
            newRight = updateTreeWithPublicPath(treeAsParent.getRight(), i - l, newPath, pathIndex + 1);
        }

        if (newLeft instanceof SecretNode) {
            result = new SecretParentNode((SecretNode)newLeft, newRight);
        } else if (newRight instanceof SecretNode) {
            result = new SecretParentNode(newLeft, (SecretNode)newRight);
        } else {
            result = new PublicParentNode(
                    newPath[pathIndex],
                    newLeft,
                    newRight
            );
        }
        if (!Arrays.equals(result.getPubKey().getPubKeyBytes(), newPath[pathIndex].getPubKeyBytes())) {
            Utils.printTree(result);
            Utils.except("Update operation inconsistent with provided path.");
        }

        return result;
    }

    public void updateStateFromMessage(ARTState state, UpdateMessage updateMessage) {
        Node tree = state.getTree();

        tree = updateTreeWithPublicPath(tree, updateMessage.getLeafNum(), updateMessage.getPath(), 0);
        state.setTree((SecretNode) tree);
        deriveStageKey(state);
    }
}
