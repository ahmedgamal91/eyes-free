// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.marvin.talkingdialer;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.os.Vibrator;
import android.provider.Contacts.PeopleColumns;
import android.provider.Contacts.Phones;
import android.provider.Contacts.PhonesColumns;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * Allows the user to select the contact they wish to call by moving through
 * their phonebook.
 * 
 * The contact name being spoken is actually the contact's ringtone. By setting
 * the ringtone to an audio file that is the same as the contact's name, the
 * user gets a talking caller ID feature automatically without needing any
 * additional code.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class ContactsView extends TextView {
  private static final long[] PATTERN = {0, 1, 40, 41};
  private static final int NAME = 0;
  private static final int NUMBER = 1;
  private static final int TYPE = 2;
  private static final int RINGTONE = 3;
  // An array specifying which columns to return.
  private static final String[] PROJECTION =
      new String[] {PeopleColumns.NAME, PhonesColumns.NUMBER, PhonesColumns.TYPE,
          PeopleColumns.CUSTOM_RINGTONE, };

  private SlideDial parent;
  private Cursor managedCursor;
  private float downY;
  private float startY;
  private boolean confirmed;
  private Vibrator vibe;

  public ContactsView(Context context) {
    super(context);

    parent = ((SlideDial) context);

    vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);

    // Get the base URI for People table in Contacts content provider.
    // ie. content://contacts/people/
    Uri mContacts = Phones.CONTENT_URI;

    // Best way to retrieve a query; returns a managed query.
    managedCursor = parent.managedQuery(mContacts, PROJECTION, // Which columns
        // to return.
        null, // WHERE clause--we won't specify.
        null, // no selection args
        PeopleColumns.NAME + " ASC"); // Order-by clause.

    managedCursor.moveToFirst();
    requestFocus();
  }


  private void nextContact() {
    boolean moveSucceeded = managedCursor.moveToNext();
    if (!moveSucceeded) {
      managedCursor.moveToFirst();
    }
    vibe.vibrate(PATTERN, -1);
    speakCurrentContact(true);
  }

  private void prevContact() {
    boolean moveSucceeded = managedCursor.moveToPrevious();
    if (!moveSucceeded) {
      managedCursor.moveToLast();
    }
    vibe.vibrate(PATTERN, -1);
    speakCurrentContact(true);
  }

  private void speakCurrentContact(boolean interrupt) {
    String ringtoneFileName = null;
    try {
      ringtoneFileName = managedCursor.getString(RINGTONE);
    } catch (CursorIndexOutOfBoundsException e) {
      managedCursor.moveToFirst();
    } finally {
      if (ringtoneFileName != null) {
        String name = managedCursor.getString(NAME);

        parent.tts.addSpeech(name, ringtoneFileName);
        if (interrupt) {
          parent.tts.speak(name, 0, null);
        } else {
          parent.tts.speak(name, 1, null);
        }
        int phoneType = Integer.parseInt(managedCursor.getString(TYPE));
        if (phoneType == PhonesColumns.TYPE_HOME) {
          parent.tts.speak("home", 1, null);
        } else if (phoneType == PhonesColumns.TYPE_MOBILE) {
          parent.tts.speak("cell", 1, null);
        } else if (phoneType == PhonesColumns.TYPE_WORK) {
          parent.tts.speak("work", 1, null);
        }
      }
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getAction();
    float x = event.getX();
    float y = event.getY();
    float distance = 100;
    if (action == MotionEvent.ACTION_DOWN) {
      downY = y;
      startY = y;
      return true;
    } else if (action == MotionEvent.ACTION_UP) {
      if (Math.abs(y - downY) < distance) {
        dialActionHandler();
      }
      return true;
    } else {
      if (y > (startY + distance)) {
        confirmed = false;
        startY = y;
        nextContact();
      } else if (y < (startY - distance)) {
        confirmed = false;
        startY = y;
        prevContact();
      }
      return true;
    }
  }

  private void dialActionHandler() {
    if (!confirmed) {
      parent.tts.speak("You are about to dial", 0, null);
      speakCurrentContact(false);
      confirmed = true;
    } else {
      parent.returnResults(managedCursor.getString(NUMBER));
    }
  }


  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_CALL:
        dialActionHandler();
        return true;
      case KeyEvent.KEYCODE_MENU:
        parent.switchToDialingView();
        return true;
    }
    confirmed = false;
    return false;
  }

}