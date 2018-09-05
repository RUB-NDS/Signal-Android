package org.thoughtcrime.securesms.groups.protocol;

import org.whispersystems.libsignal.util.guava.Optional;

/**
 * Abstract class to encapsulate all ART related messages in JSON format.
 * The jsonMessageClass is used to identify the "real" java class,
 *
 *
 * @see JsonMessageDeserializer
 *
 */
public abstract class JsonARTMessage {
    // automatically populated by the JsonMessageSerializer
    // to represent the "real" Java Class that is represented by this JSON
    private String jsonMessageClass;

    // potential original body of the message
    private String originalBody;


    public JsonARTMessage(Class<? extends JsonARTMessage> jsonMessageClass) {
        this.jsonMessageClass=jsonMessageClass.getSimpleName();
    }

    public String getOriginalBody() {
        return originalBody;
    }

    public void setOriginalBody(String originalBody) {
        this.originalBody = originalBody;
    }
}
