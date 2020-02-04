/* Copyright (c) 2020 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.init;

import android.content.Context;

import java.util.concurrent.Semaphore;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.util.LocaleUtil;
import org.chromium.base.Log;
import org.chromium.chrome.browser.init.NTPDownloadUtils;
import org.chromium.chrome.browser.BraveAdsNativeHelper;

public class NTPUpdater {
    private static final String TAG = "NTP";
    private static Semaphore mAvailable = new Semaphore(1);
    private static boolean mReceivedAnUpdate;
    private static final String UPDATE_NTP = "update_ntp";

    public static void UpdateNTP(Context context) {
        try {
            mAvailable.acquire();
            try {
              mReceivedAnUpdate = false;
              DownloadNTPData(context);
              if (mReceivedAnUpdate) {
                // Don't set an update flag on initial download
                ContextUtils.getAppSharedPreferences().edit()
                  .putBoolean(UPDATE_NTP, true)
                  .apply();
              }
            } finally {
                mAvailable.release();
            }
        } catch (InterruptedException exc) {
            Log.e("NTP", "InterruptedException" + exc.getMessage());
        }
    }

    // NTP data download
    public static void DownloadNTPData(Context context) {
        String countryCode = LocaleUtil.getCountryCode();
        if (NTPDownloadUtils.downloadNTPData(context, countryCode)) {
          mReceivedAnUpdate = true;
        }
    }
}
