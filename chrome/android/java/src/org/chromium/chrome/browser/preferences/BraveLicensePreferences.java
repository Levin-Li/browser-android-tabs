// Copyright 2019 The Brave Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.os.Bundle;
import org.chromium.base.Log;
import org.chromium.chrome.browser.BraveRewardsHelper;
import org.chromium.chrome.browser.preferences.BravePreferenceFragment;
import org.chromium.chrome.browser.preferences.TextMessagePreference;
import org.chromium.chrome.R;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * Fragment to display Brave license information.
 */
public class BraveLicensePreferences extends BravePreferenceFragment {
    private static final String TAG = "BraveLicense";

    private static final String PREF_BRAVE_LICENSE_TEXT = "brave_license_text";
    private static final String ASSET_BRAVE_LICENSE = "BRAVE_LICENSE.html";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String s) {
        PreferenceUtils.addPreferencesFromResource(this, R.xml.brave_license_preferences);
        getActivity().setTitle(R.string.brave_license_text);
        TextMessagePreference licenseText = (TextMessagePreference) findPreference(PREF_BRAVE_LICENSE_TEXT);
        try {
            InputStream in = getActivity().getAssets().open(ASSET_BRAVE_LICENSE);
            Scanner scanner = new Scanner(in).useDelimiter("\\A");
            String summary = scanner.hasNext() ? scanner.next() : "";
            in.close();
            licenseText.setSummary(BraveRewardsHelper.spannedFromHtmlString(summary));
        } catch (IOException exc) {
            Log.e(TAG, "Could not load license text");
        }
    }
}
