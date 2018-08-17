package org.thoughtcrime.securesms.groups.protocol;

import com.facebook.research.asynchronousratchetingtree.art.message.UpdateMessage;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;

import org.thoughtcrime.securesms.groups.GroupSetupBroadcastMessage;

import java.lang.reflect.Type;

public class JsonMessageDeserializer implements JsonDeserializer<JsonARTMessage> {


    @Override
    public JsonARTMessage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();

        JsonElement messageClassElement = obj.get("jsonMessageClass");
        String messageClass = messageClassElement.getAsString();

        if (GroupSetupBroadcastMessage.class.getSimpleName().equals(messageClass)) {
            context.deserialize(json, GroupSetupBroadcastMessage.class);
        } else if (WrappedARTMessage.class.getSimpleName().equals(messageClass)) {
            return context.deserialize(json,WrappedARTMessage.class);
        }

        return null;
    }


}
