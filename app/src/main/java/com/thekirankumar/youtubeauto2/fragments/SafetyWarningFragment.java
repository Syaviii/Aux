// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import com.thekirankumar.youtubeauto2.BuildConfig;
import com.thekirankumar.youtubeauto2.R;

/* JADX INFO: loaded from: classes.dex */
public class SafetyWarningFragment extends Fragment {
    private TextView appInfo;
    private View continueButton;
    private FragmentInteractionListener listener;

    public interface FragmentInteractionListener {
        void onReadyToExitSafetyInstructions(SafetyWarningFragment safetyWarningFragment);
    }

    @Override // androidx.fragment.app.Fragment
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        return layoutInflater.inflate(R.layout.safety_warning, viewGroup, false);
    }

    @Override // androidx.fragment.app.Fragment
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        TextView textView = (TextView) view.findViewById(R.id.app_info_view);
        this.appInfo = textView;
        textView.setText(getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME);
        View viewFindViewById = view.findViewById(R.id.continue_button);
        this.continueButton = viewFindViewById;
        viewFindViewById.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                SafetyWarningFragment.this.listener.onReadyToExitSafetyInstructions(SafetyWarningFragment.this);
            }
        });
        this.continueButton.setFocusable(true);
        this.continueButton.setFocusableInTouchMode(true);
        this.continueButton.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override // android.view.View.OnFocusChangeListener
            public void onFocusChange(View view2, boolean z) {
                if (z) {
                    return;
                }
                SafetyWarningFragment.this.continueButton.requestFocus();
            }
        });
        this.continueButton.setOnKeyListener(new View.OnKeyListener() {
            @Override // android.view.View.OnKeyListener
            public boolean onKey(View view2, int i, KeyEvent keyEvent) {
                if (i != 4) {
                    return i == 20 || i == 19 || i == 21 || i == 22;
                }
                SafetyWarningFragment.this.listener.onReadyToExitSafetyInstructions(SafetyWarningFragment.this);
                return true;
            }
        });
    }

    @Override // androidx.fragment.app.Fragment
    public void onResume() {
        super.onResume();
        this.continueButton.requestFocus();
    }

    @Override // androidx.fragment.app.Fragment
    public void onAttach(Context context) {
        super.onAttach(context);
        LifecycleOwner parentFragment = getParentFragment();
        if (parentFragment instanceof FragmentInteractionListener) {
            this.listener = (FragmentInteractionListener) parentFragment;
        }
    }
}
