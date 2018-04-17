// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.NativeMethods;

/**
 * PrefServiceBridge is a singleton which provides read and write access to native PrefService for
 * preferences enumerated in chrome/browser/android/preferences/prefs.h.
 */
public class PrefServiceBridge {
    // Singleton constructor. Do not call directly unless for testing purpose.
    @VisibleForTesting
    protected PrefServiceBridge() {}

    private static PrefServiceBridge sInstance;

    /**
     * @return The singleton PrefServiceBridge instance.
     */
    public static PrefServiceBridge getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) {
            sInstance = new PrefServiceBridge();
        }
        return sInstance;
    }

    /**
     * @param preference The name of the preference.
     * @return Whether the specified preference is enabled.
     */
    public boolean getBoolean(@Pref int preference) {
        return PrefServiceBridgeJni.get().getBoolean(preference);
    }

    /**
     * @param preference The name of the preference.
     * @param value The value the specified preference will be set to.
     */
    public void setBoolean(@Pref int preference, boolean value) {
        PrefServiceBridgeJni.get().setBoolean(preference, value);
    }

    /**
     * @param preference The name of the preference.
     * @return value The value of the specified preference.
     */
    public int getInteger(@Pref int preference) {
        return PrefServiceBridgeJni.get().getInteger(preference);
    }

    /**
     * @param preference The name of the preference.
     * @param value The value the specified preference will be set to.
     */
    public void setInteger(@Pref int preference, int value) {
        PrefServiceBridgeJni.get().setInteger(preference, value);
    }

    /**
     * @param preference The name of the preference.
     * @return value The value of the specified preference.
     */
    @NonNull
    public String getString(@Pref int preference) {
        return PrefServiceBridgeJni.get().getString(preference);
    }

    /**
     * @param preference The name of the preference.
     * @param value The value the specified preference will be set to.
     */
    public void setString(@Pref int preference, @NonNull String value) {
        PrefServiceBridgeJni.get().setString(preference, value);
    }

    /**
     * @param preference The name of the preference.
     * @return Whether the specified preference is managed.
     */
    public boolean isManagedPreference(@Pref int preference) {
        return PrefServiceBridgeJni.get().isManagedPreference(preference);
    }

    /**
     * @param whether Tracking Protection should be enabled.
     */
    public void setTrackingProtectionEnabled(boolean enabled) {
        PrefServiceBridgeJni.get().setTrackingProtectionEnabled(enabled);
    }

    /**
     * @param whether AdBlock should be enabled.
     */
    public void setAdBlockEnabled(boolean enabled) {
        PrefServiceBridgeJni.get().setAdBlockEnabled(enabled);
    }

    /**
     * @param whether HTTPSE should be enabled.
     */
    public void setHTTPSEEnabled(boolean enabled) {
        PrefServiceBridgeJni.get().setHTTPSEEnabled(enabled);
    }

    /**
     * @param whether Fingerprinting Protection should be enabled.
     */
    public void setFingerprintingProtectionEnabled(boolean enabled) {
        PrefServiceBridgeJni.get().setFingerprintingProtectionEnabled(enabled);
    }

    /**
     * @param whether AdBlock should be enabled.
     */
    public void setAdBlockRegionalEnabled(boolean enabled) {
        PrefServiceBridgeJni.get().setAdBlockRegionalEnabled(enabled);
    }

    /**
     * @return true if Desktop View is enabled.
     * The default is false.
     */
    public boolean desktopViewEnabled() {
        return PrefServiceBridgeJni.get().getDesktopViewEnabled();
    }

    /**
     * @return Whether Desktop View is managed by policy.
     */
    public boolean desktopViewManaged() {
        return isContentSettingManaged(ContentSettingsType.CONTENT_SETTINGS_TYPE_DESKTOP_VIEW);
    }

    /**
     * Enable or disable Desktop View .
     */
    public void setDesktopViewEnabled(boolean enabled) {
        PrefServiceBridgeJni.get().setDesktopViewEnabled(enabled);
    }

    /**
     * Returns all the currently saved exceptions for a given content settings type,
     * from incognito profile.
     * @param contentSettingsType The type to fetch exceptions for.
     */
    public List<ContentSettingException> getContentSettingsExceptionsIncognito(int contentSettingsType) {
        List<ContentSettingException> list = new ArrayList<ContentSettingException>();
        PrefServiceBridgeJni.get().getContentSettingsExceptionsIncognito(contentSettingsType, list);
        return list;
    }

    /**
     * @return true if 'Play video in background' is enabled.
     * The default is false.
     */
    public boolean playVideoInBackgroundEnabled() {
        return PrefServiceBridgeJni.getPlayVideoInBackgroundEnabled();
    }

    /**
     * @return Whether 'Play video in background' is managed by policy.
     */
    public boolean playVideoInBackgroundManaged() {
        return isContentSettingManaged(ContentSettingsType.CONTENT_SETTINGS_TYPE_PLAY_VIDEO_IN_BACKGROUND);
    }

    /**
     * Enable or disable 'Play video in background' option
     */
    public void setPlayVideoInBackgroundEnabled(boolean enabled) {
        PrefServiceBridgeJni.setPlayVideoInBackgroundEnabled(enabled);
    }

    /**
     * @return true if 'Play YouTube video in browser' is enabled.
     * The default is true.
     */
    public boolean playYTVideoInBrowserEnabled() {
        return PrefServiceBridgeJni.getPlayYTVideoInBrowserEnabled();
    }

    /**
     * @return Whether 'Play YouTube video in browser' is managed by policy.
     */
    public boolean playYTVideoInBrowserManaged() {
        return isContentSettingManaged(ContentSettingsType.CONTENT_SETTINGS_TYPE_PLAY_YT_VIDEO_IN_BROWSER);
    }

    /**
     * Enable or disable 'Play YouTube video in browser' option
     */
    public void setPlayYTVideoInBrowserEnabled(boolean enabled) {
        PrefServiceBridgeJni.setPlayYTVideoInBrowserEnabled(enabled);
    }

    @VisibleForTesting
    public static void setInstanceForTesting(@Nullable PrefServiceBridge instanceForTesting) {
        sInstance = instanceForTesting;
    }

    @NativeMethods
    interface Natives {
        boolean getBoolean(int preference);
        void setBoolean(int preference, boolean value);
        int getInteger(int preference);
        void setInteger(int preference, int value);
        String getString(int preference);
        void setString(int preference, String value);
        boolean isManagedPreference(int preference);
        void setTrackingProtectionEnabled(boolean enabled);
        void setAdBlockEnabled(boolean enabled);
        void setHTTPSEEnabled(boolean enabled);
        void setFingerprintingProtectionEnabled(boolean enabled);
        void setAdBlockRegionalEnabled(boolean enabled);
        boolean getDesktopViewEnabled();
        void setDesktopViewEnabled(boolean enabled);
        void getContentSettingsExceptionsIncognito(
            int contentSettingsType, List<ContentSettingException> list);
        void setContentSettingForPatternIncognito(
                int contentSettingType, String pattern, int setting);
        boolean getPlayVideoInBackgroundEnabled();
        void setPlayVideoInBackgroundEnabled(boolean enabled);
        boolean getPlayYTVideoInBrowserEnabled();
        void setPlayYTVideoInBrowserEnabled(boolean enabled);
    }
}
