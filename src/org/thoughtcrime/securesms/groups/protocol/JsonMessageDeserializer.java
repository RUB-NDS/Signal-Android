package org.thoughtcrime.securesms.groups.protocol;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

/**
 * JSON Type Adapter for Gson to generically deserialize JsonARTMessages to their "real" Java class.
 *
 */
public class JsonMessageDeserializer implements JsonDeserializer<JsonARTMessage> {


    // deserialization relies on the jsonMessageClass attribute of the JSON data
    // to identify the real Java class of this message.
    @Override
    public JsonARTMessage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

        // convert the json message to a Json object to facilitate access to the attributes.
        JsonObject obj = json.getAsJsonObject();


        // retrieve the message class from the JsonObject
        JsonElement messageClassElement = obj.get("jsonMessageClass");
        String messageClass = messageClassElement.getAsString();

        // compare the jsonMessageClass attribute to the well known classes
        // to do the correct deserialization
        if (WrappedARTMessage.class.getSimpleName().equals(messageClass)) {
            return context.deserialize(json,WrappedARTMessage.class);
        } else if(WrappedConversationMessage.class.getSimpleName().equals(messageClass)) {
            return context.deserialize(json,WrappedConversationMessage.class);
        } else if (WrappedARTGroupContext.class.getSimpleName().equals(messageClass)) {
            return context.deserialize(json,WrappedARTGroupContext.class);
        }

        // if we cannot identify the original class we simply return NULL
        return null;
    }
}
