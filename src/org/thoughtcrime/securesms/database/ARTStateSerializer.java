package org.thoughtcrime.securesms.database;

import com.facebook.research.asynchronousratchetingtree.art.ARTState;
import com.facebook.research.asynchronousratchetingtree.art.message.NodeTypeAdapter;
import com.facebook.research.asynchronousratchetingtree.art.tree.Node;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ARTStateSerializer {
    private static ARTStateSerializer instance;
    private final Gson gson;

    private ARTStateSerializer() {
        this.gson = new GsonBuilder().registerTypeAdapter(Node.class, new NodeTypeAdapter()).create();
    }
    public static ARTStateSerializer getInstance() {
        if (instance ==  null) {
            instance = new ARTStateSerializer();
        }
        return instance;
    }

    public ARTState fromByteArray(byte[] bytes) {
        String jsonString = new String(bytes);
        return gson.fromJson(jsonString,ARTState.class);
    }

    public byte[] toByteArray(ARTState artState) {
        return gson.toJson(artState).getBytes();
    }
}
