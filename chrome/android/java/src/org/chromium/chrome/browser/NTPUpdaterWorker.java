/* Copyright (c) 2020 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import android.content.Context;
import java.util.Random;
import android.content.SharedPreferences;

import org.chromium.base.Log;
import org.chromium.chrome.browser.init.NTPUpdater;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.preferences.developer.QAPreferences;

public class NTPUpdaterWorker {

    private static final int BASE_TIME_INTERVAL = 1000 * 60 * 60 * 5;    // 5 hours in Milliseconds
    private static final double MIN_VARIANCE = 1.10; //10%
    private static final double MAX_VARIANCE = 1.14; //14%
    private static final int INTERVAL_TO_UPDATE = (int) (BASE_TIME_INTERVAL * (MIN_VARIANCE + (new Random().nextDouble() * (MAX_VARIANCE - MIN_VARIANCE))));

    private UpdateThread mUpdateThread;

    private Context mContext;
    private boolean mStopThread;

    public NTPUpdaterWorker(Context context) {
        mStopThread = false;
        mContext = context;
        mUpdateThread = new UpdateThread();
        if (null != mUpdateThread) {
            mUpdateThread.start();
        }
    }

    public void Stop() {
        mStopThread = true;
        if (mUpdateThread != null) {
            mUpdateThread.interrupt();
            mUpdateThread = null;
        }
    }

    public int getUpdateInterval() {
        SharedPreferences mSharedPreferences = ContextUtils.getAppSharedPreferences();
        if (mSharedPreferences.getBoolean(QAPreferences.PREF_QA_DEBUG_NTP, false)) {
            return 1000 * 60 * 5;  // 5 minutes in Milliseconds
        } else {
            return INTERVAL_TO_UPDATE;
        }
    }

    class UpdateThread extends Thread {

        @Override
        public void run() {
          for (;;) {
              try {
                  Thread.sleep(getUpdateInterval());
                  NTPUpdater.UpdateNTP(mContext);
              }
              catch(Exception exc) {
                  // Just ignore it if we cannot update
                  Log.e("NTP", "Update loop exception: " + exc.getMessage());
              }
              if (mStopThread) {
                  break;
              }
          }
        }
    }
}
