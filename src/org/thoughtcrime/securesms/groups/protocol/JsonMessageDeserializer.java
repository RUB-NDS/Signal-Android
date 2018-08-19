package org.thoughtcrime.securesms.groups.protocol;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class JsonMessageDeserializer implements JsonDeserializer<JsonARTMessage> {


    @Override
    public JsonARTMessage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();

        JsonElement messageClassElement = obj.get("jsonMessageClass");
        String messageClass = messageClassElement.getAsString();

        /*if (GroupSetupBroadcastMessage.class.getSimpleName().equals(messageClass)) {
            return context.deserialize(json, GroupSetupBroadcastMessage.class);
        } else */

        if (WrappedARTMessage.class.getSimpleName().equals(messageClass)) {
            return context.deserialize(json,WrappedARTMessage.class);
        } else if(WrappedConversationMessage.class.getSimpleName().equals(messageClass)) {
            return context.deserialize(json,WrappedConversationMessage.class);
        }

        return null;
    }
}
