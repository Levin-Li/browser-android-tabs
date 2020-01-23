// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.settings.privacy;

import android.os.Bundle;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.chromium.base.BuildInfo;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.preferences.Pref;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.settings.ChromeBaseCheckBoxPreference;
import org.chromium.chrome.browser.settings.ManagedPreferenceDelegate;
import org.chromium.chrome.browser.settings.PreferencesLauncher;
import org.chromium.chrome.browser.settings.SettingsUtils;
import org.chromium.chrome.browser.settings.sync.SyncAndServicesPreferences;
import org.chromium.chrome.browser.usage_stats.UsageStatsConsentDialog;
import org.chromium.ui.text.NoUnderlineClickableSpan;
import org.chromium.ui.text.SpanApplier;

import org.chromium.chrome.browser.preferences.BravePreferenceFragment;
import org.chromium.chrome.browser.settings.website.WebsitePreferenceBridge;
import android.content.SharedPreferences;
import org.chromium.base.ContextUtils;

/**
 * Fragment to keep track of the all the privacy related preferences.
 */
public class PrivacyPreferences
        extends BravePreferenceFragment implements Preference.OnPreferenceChangeListener {
    private static final String PREF_CAN_MAKE_PAYMENT = "can_make_payment";
    private static final String PREF_NETWORK_PREDICTIONS = "preload_pages";
    private static final String PREF_USAGE_STATS = "usage_stats_reporting";    
    private static final String PREF_DO_NOT_TRACK = "do_not_track";
    // private static final String PREF_SYNC_AND_SERVICES_LINK = "sync_and_services_link";
    private static final String PREF_AD_BLOCK = "ad_block";
    private static final String PREF_HTTPSE = "httpse";
    private static final String PREF_FINGERPRINTING_PROTECTION = "fingerprinting_protection";
    private static final String PREF_AD_BLOCK_REGIONAL = "ad_block_regional";
    private static final String PREF_CLOSE_TABS_ON_EXIT = "close_tabs_on_exit";
    private static final String PREF_SEARCH_SUGGESTIONS = "search_suggestions";

    private ManagedPreferenceDelegate mManagedPreferenceDelegate;

    private ChromeBaseCheckBoxPreference mSearchSuggestions;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PrivacyPreferencesManager privacyPrefManager = PrivacyPreferencesManager.getInstance();
        privacyPrefManager.migrateNetworkPredictionPreferences();
        SettingsUtils.addPreferencesFromResource(this, R.xml.privacy_preferences);
        getActivity().setTitle(R.string.prefs_privacy);
        setHasOptionsMenu(true);

        mManagedPreferenceDelegate = createManagedPreferenceDelegate();

        ChromeBaseCheckBoxPreference canMakePaymentPref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_CAN_MAKE_PAYMENT);
        canMakePaymentPref.setOnPreferenceChangeListener(this);

        ChromeBaseCheckBoxPreference networkPredictionPref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_NETWORK_PREDICTIONS);
        networkPredictionPref.setChecked(
                PrivacyPreferencesManager.getInstance().getNetworkPredictionEnabled());
        networkPredictionPref.setOnPreferenceChangeListener(this);
        networkPredictionPref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        // Preference syncAndServicesLink = findPreference(PREF_SYNC_AND_SERVICES_LINK);
        // NoUnderlineClickableSpan linkSpan = new NoUnderlineClickableSpan(getResources(), view -> {
        //     PreferencesLauncher.launchSettingsPage(getActivity(), SyncAndServicesPreferences.class,
        //             SyncAndServicesPreferences.createArguments(false));
        // });
        // syncAndServicesLink.setSummary(
        //         SpanApplier.applySpans(getString(R.string.privacy_sync_and_services_link),
        //                 new SpanApplier.SpanInfo("<link>", "</link>", linkSpan)));

        ChromeBaseCheckBoxPreference adBlockPref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_AD_BLOCK);
        adBlockPref.setOnPreferenceChangeListener(this);
        adBlockPref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        ChromeBaseCheckBoxPreference httpsePref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_HTTPSE);
        httpsePref.setOnPreferenceChangeListener(this);
        httpsePref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        ChromeBaseCheckBoxPreference fingerprintingProtectionPref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_FINGERPRINTING_PROTECTION);
        fingerprintingProtectionPref.setOnPreferenceChangeListener(this);
        fingerprintingProtectionPref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        ChromeBaseCheckBoxPreference adBlockRegionalPref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_AD_BLOCK_REGIONAL);
        adBlockRegionalPref.setOnPreferenceChangeListener(this);
        adBlockRegionalPref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        ChromeBaseCheckBoxPreference closeTabsOnExitPref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_CLOSE_TABS_ON_EXIT);
        closeTabsOnExitPref.setOnPreferenceChangeListener(this);
        closeTabsOnExitPref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        mSearchSuggestions = (ChromeBaseCheckBoxPreference) findPreference(PREF_SEARCH_SUGGESTIONS);
        mSearchSuggestions.setOnPreferenceChangeListener(this);
        mSearchSuggestions.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        updateSummaries();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (PREF_CAN_MAKE_PAYMENT.equals(key)) {
            PrefServiceBridge.getInstance().setBoolean(
                    Pref.CAN_MAKE_PAYMENT_ENABLED, (boolean) newValue);
        } else if (PREF_NETWORK_PREDICTIONS.equals(key)) {
            PrivacyPreferencesManager.getInstance().setNetworkPredictionEnabled((boolean) newValue);
        } else if (PREF_AD_BLOCK.equals(key)) {
            WebsitePreferenceBridge.setAdBlockEnabled((boolean) newValue);
        } else if (PREF_HTTPSE.equals(key)) {
            WebsitePreferenceBridge.setHTTPSEEnabled((boolean) newValue);
        } else if (PREF_FINGERPRINTING_PROTECTION.equals(key)) {
            WebsitePreferenceBridge.setFingerprintingProtectionEnabled((boolean) newValue);
        } else if (PREF_AD_BLOCK_REGIONAL.equals(key)) {
            WebsitePreferenceBridge.setAdBlockRegionalEnabled((boolean) newValue);
        } else if (PREF_CLOSE_TABS_ON_EXIT.equals(key)) {
            SharedPreferences.Editor sharedPreferencesEditor = ContextUtils.getAppSharedPreferences().edit();
            sharedPreferencesEditor.putBoolean(PREF_CLOSE_TABS_ON_EXIT, (boolean)newValue);
            sharedPreferencesEditor.apply();
        } else if (PREF_SEARCH_SUGGESTIONS.equals(key)) {
            PrefServiceBridge.getInstance().setBoolean(Pref.SEARCH_SUGGEST_ENABLED, (boolean) newValue);
        }

        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummaries();
    }

    /**
     * Updates the summaries for several preferences.
     */
    public void updateSummaries() {
        PrefServiceBridge prefServiceBridge = PrefServiceBridge.getInstance();

        CheckBoxPreference canMakePaymentPref =
                (CheckBoxPreference) findPreference(PREF_CAN_MAKE_PAYMENT);
        if (canMakePaymentPref != null) {
            canMakePaymentPref.setChecked(
                    prefServiceBridge.getBoolean(Pref.CAN_MAKE_PAYMENT_ENABLED));
        }

        // Preference doNotTrackPref = findPreference(PREF_DO_NOT_TRACK);
        // if (doNotTrackPref != null) {
        //     doNotTrackPref.setSummary(prefServiceBridge.getBoolean(Pref.ENABLE_DO_NOT_TRACK)
        //                     ? R.string.text_on
        //                     : R.string.text_off);
        // }

        Preference regionalAdBlockPref = findPreference(PREF_AD_BLOCK_REGIONAL);
        if (null == regionalAdBlockPref) {
            return;
        }
        if (PrivacyPreferencesManager.getInstance().isRegionalAdBlockEnabled()) {
            regionalAdBlockPref.setSummary(getActivity().getResources().getText(R.string.ad_block_regional_summary));
            regionalAdBlockPref.setEnabled(true);
        } else {
            regionalAdBlockPref.setSummary(getActivity().getResources().getText(R.string.ad_block_regional_summary_no_list));
            regionalAdBlockPref.setEnabled(false);
        }

        Preference usageStatsPref = findPreference(PREF_USAGE_STATS);
        if (usageStatsPref != null) {
            if (BuildInfo.isAtLeastQ() && prefServiceBridge.getBoolean(Pref.USAGE_STATS_ENABLED)) {
                usageStatsPref.setOnPreferenceClickListener(preference -> {
                    UsageStatsConsentDialog
                            .create(getActivity(), true,
                                    (didConfirm) -> {
                                        if (didConfirm) {
                                            updateSummaries();
                                        }
                                    })
                            .show();
                    return true;
                });
            } else {
                getPreferenceScreen().removePreference(usageStatsPref);
            }
        }
    }

    private ManagedPreferenceDelegate createManagedPreferenceDelegate() {
        return preference -> {
            String key = preference.getKey();
            if (PREF_NETWORK_PREDICTIONS.equals(key)) {
                return PrivacyPreferencesManager.getInstance().isNetworkPredictionManaged();
            }
            if (PREF_SEARCH_SUGGESTIONS.equals(key)) {
                return PrefServiceBridge.getInstance().isManagedPreference(Pref.SEARCH_SUGGEST_ENABLED);
            }
            return false;
        };
    }

    // @Override
    // public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    //     menu.clear();
    //     MenuItem help = menu.add(
    //             Menu.NONE, R.id.menu_id_targeted_help, Menu.NONE, R.string.menu_help);
    //     help.setIcon(VectorDrawableCompat.create(
    //             getResources(), R.drawable.ic_help_and_feedback, getActivity().getTheme()));
    // }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_id_targeted_help) {
            HelpAndFeedback.getInstance().show(getActivity(),
                    getString(R.string.help_context_privacy), Profile.getLastUsedProfile(), null);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
