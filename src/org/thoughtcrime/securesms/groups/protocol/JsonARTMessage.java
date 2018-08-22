package org.thoughtcrime.securesms.groups.protocol;

import org.whispersystems.libsignal.util.guava.Optional;

public abstract class JsonARTMessage {
    private String jsonMessageClass;
    private String originalBody;


    public JsonARTMessage(Class<? extends JsonARTMessage> jsonMessageClass) {
        this.jsonMessageClass=jsonMessageClass.getSimpleName();
    }

    public void setJsonMessageClass(String messageClass) {
        this.jsonMessageClass = messageClass;
    }
    public String getJsonMessageClass() {
        return jsonMessageClass;
    }

    public String getOriginalBody() {
        return originalBody;
    }

    public void setOriginalBody(String originalBody) {
        this.originalBody = originalBody;
    }
}
