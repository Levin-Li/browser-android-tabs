// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.app.Activity;
import android.graphics.Point;
import android.view.Display;

import org.chromium.base.Log;
import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.crash.MinidumpUploadService.ProcessType;

import java.util.HashSet;
import java.util.Set;

/**
 * ChromePreferenceManager stores and retrieves values in Android shared preferences for specific
 * features.
 *
 * TODO(crbug.com/1022102): Finish moving feature-specific methods out of this class and delete it.
 */
public class ChromePreferenceManager {
    // For new int values with a default of 0, just document the key and its usage, and call
    // #readInt and #writeInt directly.
    // For new boolean values, document the key and its usage, call #readBoolean and #writeBoolean
    // directly. While calling #readBoolean, default value is required.

    private static final String USE_CUSTOM_TABS =
            "use_custom_tabs";
    public static final String BOTTOM_TOOLBAR_ENABLED_KEY = "bottom_toolbar_enabled";
    public static final String BRAVE_BOTTOM_TOOLBAR_SET_KEY = "brave_bottom_toolbar_enabled";

    private static final int SMALL_SCREEN_WIDTH = 360;
    private static final int SMALL_SCREEN_HEIGHT = 640;

    private static class LazyHolder {
        static final ChromePreferenceManager INSTANCE = new ChromePreferenceManager();
    }

    private final SharedPreferencesManager mManager;

    private ChromePreferenceManager() {
        mManager = SharedPreferencesManager.getInstance();
    }

    /**
     * Get the static instance of ChromePreferenceManager if exists else create it.
     * @return the ChromePreferenceManager singleton
     */
    public static ChromePreferenceManager getInstance() {
        return LazyHolder.INSTANCE;
    }

    /**
     * @return Number of times of successful crash upload.
     */
    public int getCrashSuccessUploadCount(@ProcessType String process) {
        // Convention to keep all the key in preference lower case.
        return mManager.readInt(successUploadKey(process));
    }

    public void setCrashSuccessUploadCount(@ProcessType String process, int count) {
        // Convention to keep all the key in preference lower case.
        mManager.writeInt(successUploadKey(process), count);
    }

    public void incrementCrashSuccessUploadCount(@ProcessType String process) {
        setCrashSuccessUploadCount(process, getCrashSuccessUploadCount(process) + 1);
    }

    private String successUploadKey(@ProcessType String process) {
        switch (process) {
            case ProcessType.BROWSER:
                return ChromePreferenceKeys.CRASH_UPLOAD_SUCCESS_BROWSER;
            case ProcessType.RENDERER:
                return ChromePreferenceKeys.CRASH_UPLOAD_SUCCESS_RENDERER;
            case ProcessType.GPU:
                return ChromePreferenceKeys.CRASH_UPLOAD_SUCCESS_GPU;
            case ProcessType.OTHER:
                return ChromePreferenceKeys.CRASH_UPLOAD_SUCCESS_OTHER;
            default:
                throw new IllegalArgumentException("Process type unknown: " + process);
        }
    }

    /**
     * @return Number of times of failure crash upload after reaching the max number of tries.
     */
    public int getCrashFailureUploadCount(@ProcessType String process) {
        return mManager.readInt(failureUploadKey(process));
    }

    public void setCrashFailureUploadCount(@ProcessType String process, int count) {
        mManager.writeInt(failureUploadKey(process), count);
    }

    public void incrementCrashFailureUploadCount(@ProcessType String process) {
        setCrashFailureUploadCount(process, getCrashFailureUploadCount(process) + 1);
    }

    private String failureUploadKey(@ProcessType String process) {
        switch (process) {
            case ProcessType.BROWSER:
                return ChromePreferenceKeys.CRASH_UPLOAD_FAILURE_BROWSER;
            case ProcessType.RENDERER:
                return ChromePreferenceKeys.CRASH_UPLOAD_FAILURE_RENDERER;
            case ProcessType.GPU:
                return ChromePreferenceKeys.CRASH_UPLOAD_FAILURE_GPU;
            case ProcessType.OTHER:
                return ChromePreferenceKeys.CRASH_UPLOAD_FAILURE_OTHER;
            default:
                throw new IllegalArgumentException("Process type unknown: " + process);
        }
    }

    /**
     * Returns Chrome major version number when signin promo was last shown, or 0 if version number
     * isn't known.
     */
    public int getSigninPromoLastShownVersion() {
        return mManager.readInt(ChromePreferenceKeys.SIGNIN_PROMO_LAST_SHOWN_MAJOR_VERSION);
    }

    /**
     * Sets Chrome major version number when signin promo was last shown.
     */
    public void setSigninPromoLastShownVersion(int majorVersion) {
        mManager.writeInt(ChromePreferenceKeys.SIGNIN_PROMO_LAST_SHOWN_MAJOR_VERSION, majorVersion);
    }

    /**
     * Returns a set of account names on the device when signin promo was last shown,
     * or null if promo hasn't been shown yet.
     */
    public Set<String> getSigninPromoLastAccountNames() {
        return mManager.readStringSet(
                ChromePreferenceKeys.SIGNIN_PROMO_LAST_SHOWN_ACCOUNT_NAMES, null);
    }

    /**
     * Stores a set of account names on the device when signin promo is shown.
     */
    public void setSigninPromoLastAccountNames(Set<String> accountNames) {
        mManager.writeStringSet(
                ChromePreferenceKeys.SIGNIN_PROMO_LAST_SHOWN_ACCOUNT_NAMES, accountNames);
    }

    /**
     * Returns timestamp of the suppression period start if signin promos in the New Tab Page are
     * temporarily suppressed; zero otherwise.
     * @return the epoch time in milliseconds (see {@link System#currentTimeMillis()}).
     */
    public long getNewTabPageSigninPromoSuppressionPeriodStart() {
        return mManager.readLong(ChromePreferenceKeys.NTP_SIGNIN_PROMO_SUPPRESSION_PERIOD_START);
    }

    /**
     * Sets timestamp of the suppression period start if signin promos in the New Tab Page are
     * temporarily suppressed.
     * @param timeMillis the epoch time in milliseconds (see {@link System#currentTimeMillis()}).
     */
    public void setNewTabPageSigninPromoSuppressionPeriodStart(long timeMillis) {
        mManager.writeLong(
                ChromePreferenceKeys.NTP_SIGNIN_PROMO_SUPPRESSION_PERIOD_START, timeMillis);
    }

    /**
     * Removes the stored timestamp of the suppression period start when signin promos in the New
     * Tab Page are no longer suppressed.
     */
    public void clearNewTabPageSigninPromoSuppressionPeriodStart() {
        mManager.removeKey(ChromePreferenceKeys.NTP_SIGNIN_PROMO_SUPPRESSION_PERIOD_START);
    }

    /**
     * Get whether or not the new tab page button is enabled.
     * @return True if the new tab page button is enabled.
     */
    public boolean isNewTabPageButtonEnabled() {
        return true;
        // return mManager.readBoolean(NTP_BUTTON_ENABLED_KEY, false);
    }
  
    /**
     * Get whether or not the bottom toolbar is enabled.
     * @return True if the bottom toolbar is enabled.
     */
    public boolean isBottomToolbarEnabled() {
        if (mManager.readBoolean(BRAVE_BOTTOM_TOOLBAR_SET_KEY, false)) {
            return mManager.readBoolean(BOTTOM_TOOLBAR_ENABLED_KEY, true);
        } else {
            mManager.writeBoolean(BRAVE_BOTTOM_TOOLBAR_SET_KEY, true);
            boolean enable = true;
            if (isSmallScreen()) {
                enable = false;
            }
            mManager.writeBoolean(BOTTOM_TOOLBAR_ENABLED_KEY, enable);

            return enable;
        }
    }

    private boolean isSmallScreen() {
        Activity currentActivity = null;
        for (Activity ref : ApplicationStatus.getRunningActivities()) {
            if (!(ref instanceof ChromeActivity)) continue;

            currentActivity = ref;
            break;
        }
        Display screensize = currentActivity.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        screensize.getSize(size);
        int width = size.x;
        int height = size.y;

        return (width <= SMALL_SCREEN_WIDTH) && (height <= SMALL_SCREEN_HEIGHT);
    }

    /**
     * Gets a set of Strings representing digital asset links that have been verified.
     * Set by {@link #setVerifiedDigitalAssetLinks(Set)}.
     */
    public Set<String> getVerifiedDigitalAssetLinks() {
        // From the official docs, modifying the result of a SharedPreferences.getStringSet can
        // cause bad things to happen including exceptions or ruining the data.
        return new HashSet<>(
                mManager.readStringSet(ChromePreferenceKeys.VERIFIED_DIGITAL_ASSET_LINKS));
    }

    /**
     * Sets a set of digital asset links (represented a strings) that have been verified.
     * Can be retrieved by {@link #getVerifiedDigitalAssetLinks()}.
     */
    public void setVerifiedDigitalAssetLinks(Set<String> links) {
        mManager.writeStringSet(ChromePreferenceKeys.VERIFIED_DIGITAL_ASSET_LINKS, links);
    }

    /** Do not modify the set returned by this method. */
    private Set<String> getTrustedWebActivityDisclosureAcceptedPackages() {
        return mManager.readStringSet(
                ChromePreferenceKeys.TRUSTED_WEB_ACTIVITY_DISCLOSURE_ACCEPTED_PACKAGES);
    }

    /**
     * Sets that the user has accepted the Trusted Web Activity "Running in Chrome" disclosure for
     * TWAs launched by the given package.
     */
    public void setUserAcceptedTwaDisclosureForPackage(String packageName) {
        mManager.addToStringSet(
                ChromePreferenceKeys.TRUSTED_WEB_ACTIVITY_DISCLOSURE_ACCEPTED_PACKAGES,
                packageName);
    }

    /**
     * Removes the record of accepting the Trusted Web Activity "Running in Chrome" disclosure for
     * TWAs launched by the given package.
     */
    public void removeTwaDisclosureAcceptanceForPackage(String packageName) {
        mManager.removeFromStringSet(
                ChromePreferenceKeys.TRUSTED_WEB_ACTIVITY_DISCLOSURE_ACCEPTED_PACKAGES,
                packageName);
    }

    /**
     * Checks whether the given package was previously passed to
     * {@link #setUserAcceptedTwaDisclosureForPackage(String)}.
     */
    public boolean hasUserAcceptedTwaDisclosureForPackage(String packageName) {
        return getTrustedWebActivityDisclosureAcceptedPackages().contains(packageName);
    }

    /**
     * Get whether or not use custom tabs.
     * @return True if we can use custom tabs.
     */
    public boolean useCustomTabs() {
        return mManager.readBoolean(USE_CUSTOM_TABS, true);
    }
}
