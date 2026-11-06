// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.utils;

import android.os.Bundle;
import android.speech.RecognitionListener;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class MyRecognitionListener implements RecognitionListener {
    private static final String TAG = MyRecognitionListener.class.getName();
    private OnCompleteListener listener;

    public interface OnCompleteListener {
        void onEnd();

        void onError(int i);

        void onVoiceRecognitionComplete(String str);
    }

    @Override // android.speech.RecognitionListener
    public void onBeginningOfSpeech() {
    }

    @Override // android.speech.RecognitionListener
    public void onBufferReceived(byte[] bArr) {
    }

    @Override // android.speech.RecognitionListener
    public void onEvent(int i, Bundle bundle) {
    }

    @Override // android.speech.RecognitionListener
    public void onPartialResults(Bundle bundle) {
    }

    @Override // android.speech.RecognitionListener
    public void onReadyForSpeech(Bundle bundle) {
    }

    @Override // android.speech.RecognitionListener
    public void onRmsChanged(float f) {
    }

    public MyRecognitionListener(OnCompleteListener onCompleteListener) {
        this.listener = onCompleteListener;
    }

    public MyRecognitionListener() {
    }

    public void setListener(OnCompleteListener onCompleteListener) {
        this.listener = onCompleteListener;
    }

    @Override // android.speech.RecognitionListener
    public void onEndOfSpeech() {
        OnCompleteListener onCompleteListener = this.listener;
        if (onCompleteListener != null) {
            onCompleteListener.onEnd();
        }
    }

    @Override // android.speech.RecognitionListener
    public void onError(int i) {
        OnCompleteListener onCompleteListener = this.listener;
        if (onCompleteListener != null) {
            onCompleteListener.onError(i);
        }
    }

    @Override // android.speech.RecognitionListener
    public void onResults(Bundle bundle) {
        ArrayList<String> stringArrayList = bundle.getStringArrayList("results_recognition");
        if (stringArrayList.size() > 0) {
            String str = stringArrayList.get(0);
            OnCompleteListener onCompleteListener = this.listener;
            if (onCompleteListener != null) {
                onCompleteListener.onVoiceRecognitionComplete(str);
                return;
            }
            return;
        }
        OnCompleteListener onCompleteListener2 = this.listener;
        if (onCompleteListener2 != null) {
            onCompleteListener2.onError(0);
        }
    }
}
