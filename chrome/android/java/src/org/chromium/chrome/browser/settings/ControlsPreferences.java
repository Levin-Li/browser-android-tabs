package org.chromium.chrome.browser.settings;

import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceClickListener;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.widget.ListView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.settings.BravePreferenceFragment;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.settings.SettingsUtils;
import org.chromium.chrome.browser.settings.website.SiteSettingsCategory;
import org.chromium.chrome.browser.settings.website.SingleCategoryPreferences;
import org.chromium.chrome.browser.settings.website.ContentSettingsResources;
import org.chromium.chrome.browser.settings.website.ContentSettingValues;

import java.util.ArrayList;
import java.util.List;
import org.chromium.chrome.browser.settings.website.WebsitePreferenceBridge;

/**
 * The main Controls screen, which shows all the 'Background video playback' option.
 *
 * It enables play audio from video in the background.
 */
public class ControlsPreferences extends BravePreferenceFragment
        implements OnPreferenceClickListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.prefs_section_controls);
        SettingsUtils.addPreferencesFromResource(this, R.xml.controls_preferences);

        updatePreferenceStates();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    private Preference findPreference(@SiteSettingsCategory.Type int type) {
        return findPreference(SiteSettingsCategory.preferenceKey(type));
    }

    private void configurePreferences() {
        getPreferenceScreen().removePreference(findPreference(SiteSettingsCategory.Type.PLAY_VIDEO_IN_BACKGROUND));
    }

    private void updatePreferenceStates() {
        PrefServiceBridge prefServiceBridge = PrefServiceBridge.getInstance();

        Preference p = findPreference(SiteSettingsCategory.Type.PLAY_VIDEO_IN_BACKGROUND);
            int contentType = SiteSettingsCategory.contentSettingsType(SiteSettingsCategory.Type.PLAY_VIDEO_IN_BACKGROUND);
            boolean requiresTriStateSetting =
                    WebsitePreferenceBridge.requiresTriStateContentSetting(contentType);

            boolean checked = false;
            @ContentSettingValues
            int setting = ContentSettingValues.DEFAULT;

            if (requiresTriStateSetting) {
                setting = WebsitePreferenceBridge.getContentSetting(contentType);
            } else {
                checked = WebsitePreferenceBridge.isCategoryEnabled(contentType);
            }

            p.setTitle(ContentSettingsResources.getTitle(contentType));
            p.setOnPreferenceClickListener(this);

            p.setSummary( checked ? ContentSettingsResources.getPlayVideoInBackgroundEnabledSummary() : ContentSettingsResources.getPlayVideoInBackgroundDisabledSummary());

            if (p.isEnabled()) {
                p.setIcon(SettingsUtils.getTintedIcon(
                        getActivity(), ContentSettingsResources.getIcon(contentType)));
            } else {
                p.setIcon(ContentSettingsResources.getDisabledIcon(contentType, getResources()));
            }
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferenceStates();
    }

    // OnPreferenceClickListener:

    @Override
    public boolean onPreferenceClick(Preference preference) {
        preference.getExtras().putString(
                SingleCategoryPreferences.EXTRA_CATEGORY, preference.getKey());
        preference.getExtras().putString(SingleCategoryPreferences.EXTRA_TITLE,
                preference.getTitle().toString());
        return false;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
    }
}