// Copyright 2019 The Brave Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.os.Bundle;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.view.View;
import org.chromium.chrome.browser.BraveRewardsNativeWorker;

/**
 * The dialog used to reset Brave Rewards.
 */
public class BraveRewardsResetPreferenceDialog extends PreferenceDialogFragmentCompat {
    public static final String TAG = "BraveRewardsResetPreferenceDialog";

    public static BraveRewardsResetPreferenceDialog newInstance(
            BraveRewardsResetPreference preference) {
        BraveRewardsResetPreferenceDialog fragment = new BraveRewardsResetPreferenceDialog();
        Bundle bundle = new Bundle(1);
        bundle.putString(PreferenceDialogFragmentCompat.ARG_KEY, preference.getKey());
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
    }

    @Override
    public void onDialogClosed(boolean positive) {
        if (positive) {
            BraveRewardsNativeWorker.getInstance().SetRewardsMainEnabled(false);
        }
    }
}
