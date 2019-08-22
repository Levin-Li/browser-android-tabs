// Copyright 2019 The Brave Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;

/**
 * Provides information regarding Closing all tabs closes brave enabled states and URI.
 *
 * This class serves as a single Closing all tabs closes brave logic gateway.
 */
public class ClosingTabsManager {

    private static final String PREF_CLOSING_TABS_ENABLED = "closing_tabs_enabled";
    private static final String PREF_CLOSING_TABS_OPTION_ENABLED = "closing_tabs_option_enabled";

    private static ClosingTabsManager sInstance;

    private final SharedPreferences mSharedPreferences;

    private ClosingTabsManager() {
        mSharedPreferences = ContextUtils.getAppSharedPreferences();
    }

    /**
     * Returns the singleton instance of ClosingTabsManager, creating it if needed.
     */
    public static ClosingTabsManager getInstance() {
        if (sInstance == null) {
            sInstance = new ClosingTabsManager();
        }
        return sInstance;
    }

    /**
     * @return Whether or not closing all tabs closes brave is enabled.
     */
    public static boolean isClosingAllTabsClosesBraveEnabled() {
        return getInstance().getPrefClosingAllTabsClosesBraveEnabled();
    }

    /**
     * @return Whether or not closing tabs closes brave option is enabled.
     */
    public static boolean isClosingTabsOptionEnabled() {
        return getInstance().getPrefClosingTabsOptionEnabled();
    }

    /**
     * @return Whether to close the app when the user has zero tabs.
     */
    public static boolean shouldCloseAppWithZeroTabs() {
        return ClosingTabsManager.isClosingAllTabsClosesBraveEnabled()
                && !NewTabPage.isNTPUrl(HomepageManager.getHomepageUri());
    }

    /**
     * Returns the user preference for whether the Closing all tabs closes brave is enabled.
     *
     * @see #isClosingAllTabsClosesBraveEnabled
     */
    public boolean getPrefClosingAllTabsClosesBraveEnabled() {
        return mSharedPreferences.getBoolean(PREF_CLOSING_TABS_ENABLED, false);
    }

    /**
     * Returns the user preference for whether the Closing tabs option is enabled.
     *
     * @see #isClosingTabsOptionEnabled
     */
    public boolean getPrefClosingTabsOptionEnabled() {
        return mSharedPreferences.getBoolean(PREF_CLOSING_TABS_OPTION_ENABLED, false);
    }

    /**
     * Sets the user preference for whether the Closing all tabs closes brave is enabled.
     */
    public void setPrefClosingAllTabsClosesBraveEnabled(boolean enabled) {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_CLOSING_TABS_ENABLED, enabled);
        sharedPreferencesEditor.apply();
    }

    /**
     * Sets the user preference for whether the Closing tabs option is enabled.
     */
    public void setClosingTabsOption() {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_CLOSING_TABS_OPTION_ENABLED, true);
        sharedPreferencesEditor.apply();
    }
}
