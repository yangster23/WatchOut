package com.example.jonathanyang.watchout;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Used from DrJukka demo as indication for speech
 */

public class TextSpeech implements TextToSpeech.OnInitListener {
    private TextToSpeech speech;


    public TextSpeech(Context context) {
        speech = new TextToSpeech(context, this);
    }

    public void stop() {
        if (speech != null) {
            speech.stop();
            speech.shutdown();
        }
    }


    @Override
    public void onInit(int i) {
        if (i == TextToSpeech.SUCCESS) {
            int result = speech.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                //
            }
            else {
                String msg = "hi there, i'm ready";
                speak(msg);
            }
        }

    }

    public void speak(final String text) {
        speech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }
}
