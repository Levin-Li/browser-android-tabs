/* Copyright (c) 2020 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

 package org.chromium.chrome.browser.settings;

import android.os.Bundle;
import org.chromium.chrome.browser.init.StatsUpdater;
import org.chromium.chrome.R;
 
 /**
  * Fragment to display referrer program information.
  */
 public class QAReferrerProgramInfoPreferences extends BravePreferenceFragment {
     private static final String TAG = "ReferrerProgram";
 
     private static final String PREF_REFERRER_PROGRAM_INFO = "referrer_program_info";
 
     @Override
     public void onCreatePreferences(Bundle savedInstanceState, String s) {
         SettingsUtils.addPreferencesFromResource(this, R.xml.referrer_program_info_preferences);
         getActivity().setTitle("Referrer program info");
         TextMessagePreference info = (TextMessagePreference) findPreference(PREF_REFERRER_PROGRAM_INFO);
         String summary = "Urpc: " + (StatsUpdater.GetUrpc(getActivity()).isEmpty() ? "<empty>" : StatsUpdater.GetUrpc(getActivity())) + "\n" +
                "DownloadId: " + (StatsUpdater.GetDownloadId(getActivity()).isEmpty() ? "<empty>" : StatsUpdater.GetDownloadId(getActivity())) + "\n" +
                "PartnerOfferPage: " + (StatsUpdater.GetPartnerOfferPage().isEmpty() ? "<empty>" : StatsUpdater.GetPartnerOfferPage());
         info.setSummary(summary);
     }
 }
 