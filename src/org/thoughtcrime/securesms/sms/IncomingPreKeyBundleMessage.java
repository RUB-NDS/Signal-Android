package org.thoughtcrime.securesms.sms;

import android.util.Log;

public class IncomingPreKeyBundleMessage extends IncomingTextMessage {

  private static final String LOG_TAG= IncomingPreKeyBundleMessage.class.getSimpleName();
  private final boolean legacy;

  public IncomingPreKeyBundleMessage(IncomingTextMessage base, String newBody, boolean legacy) {
    super(base, newBody);
    this.legacy = legacy;
    Log.d(LOG_TAG,"IncomingPreKeyBundle message");
  }

  @Override
  public IncomingPreKeyBundleMessage withMessageBody(String messageBody) {
    return new IncomingPreKeyBundleMessage(this, messageBody, legacy);
  }

  @Override
  public boolean isLegacyPreKeyBundle() {
    return legacy;
  }

  @Override
  public boolean isContentPreKeyBundle() {
    return !legacy;
  }

}
