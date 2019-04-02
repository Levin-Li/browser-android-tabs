// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/android/preferences/pref_service_bridge.h"

#include <jni.h>

#include <string>

#include "base/android/jni_string.h"
#include "base/android/scoped_java_ref.h"
#include "chrome/browser/android/preferences/prefs.h"
#include "chrome/browser/preferences/jni_headers/PrefServiceBridge_jni.h"
#include "chrome/browser/profiles/profile_manager.h"
#include "components/prefs/pref_service.h"

namespace {

PrefService* GetPrefService() {
  return ProfileManager::GetActiveUserProfile()
      ->GetOriginalProfile()
      ->GetPrefs();
}

}  // namespace

const char* PrefServiceBridge::GetPrefNameExposedToJava(int pref_index) {
  DCHECK_GE(pref_index, 0);
  DCHECK_LT(pref_index, Pref::PREF_NUM_PREFS);
  return kPrefsExposedToJava[pref_index];
}

// ----------------------------------------------------------------------------
// Native JNI methods
// ----------------------------------------------------------------------------

static jboolean JNI_PrefServiceBridge_GetBoolean(
    JNIEnv* env,
    const jint j_pref_index) {
  return GetPrefService()->GetBoolean(
      PrefServiceBridge::GetPrefNameExposedToJava(j_pref_index));
}

static void JNI_PrefServiceBridge_SetBoolean(JNIEnv* env,
                                             const jint j_pref_index,
                                             const jboolean j_value) {
  GetPrefService()->SetBoolean(
      PrefServiceBridge::GetPrefNameExposedToJava(j_pref_index), j_value);
}

static jint JNI_PrefServiceBridge_GetInteger(JNIEnv* env,
                                             const jint j_pref_index) {
  return GetPrefService()->GetInteger(
      PrefServiceBridge::GetPrefNameExposedToJava(j_pref_index));
}

static void JNI_PrefServiceBridge_SetInteger(JNIEnv* env,
                                             const jint j_pref_index,
                                             const jint j_value) {
  GetPrefService()->SetInteger(
      PrefServiceBridge::GetPrefNameExposedToJava(j_pref_index), j_value);
}

static base::android::ScopedJavaLocalRef<jstring>
JNI_PrefServiceBridge_GetString(JNIEnv* env, const jint j_pref_index) {
  return base::android::ConvertUTF8ToJavaString(
      env, GetPrefService()->GetString(
               PrefServiceBridge::GetPrefNameExposedToJava(j_pref_index)));
}

static void JNI_PrefServiceBridge_SetString(
    JNIEnv* env,
    const jint j_pref_index,
    const base::android::JavaParamRef<jstring>& j_value) {
  GetPrefService()->SetString(
      PrefServiceBridge::GetPrefNameExposedToJava(j_pref_index),
      base::android::ConvertJavaStringToUTF8(env, j_value));
}

static jboolean JNI_PrefServiceBridge_IsManagedPreference(
    JNIEnv* env,
    const jint j_pref_index) {
  return GetPrefService()->IsManagedPreference(
      PrefServiceBridge::GetPrefNameExposedToJava(j_pref_index));
}

static void JNI_PrefServiceBridge_SetFingerprintingProtectionEnabled(JNIEnv* env,
                                   const JavaParamRef<jobject>& obj,
                                   jboolean enabled) {
  GetPrefService()->SetBoolean(prefs::kFingerprintingProtectionEnabled, enabled);
}

static jboolean JNI_PrefServiceBridge_GetFingerprintingProtectionEnabled(JNIEnv* env,
                                       const JavaParamRef<jobject>& obj) {
  return GetPrefService()->GetBoolean(prefs::kFingerprintingProtectionEnabled);
}

static void JNI_PrefServiceBridge_SetHTTPSEEnabled(JNIEnv* env,
                                   const JavaParamRef<jobject>& obj,
                                   jboolean enabled) {
   GetPrefService()->SetBoolean(prefs::kHTTPSEEnabled, enabled);
}

static jboolean JNI_PrefServiceBridge_GetHTTPSEEnabled(JNIEnv* env,
                                       const JavaParamRef<jobject>& obj) {
  return GetPrefService()->GetBoolean(prefs::kHTTPSEEnabled);
}

static void JNI_PrefServiceBridge_SetAdBlockEnabled(JNIEnv* env,
                                   const JavaParamRef<jobject>& obj,
                                   jboolean enabled) {
   GetPrefService()->SetBoolean(prefs::kAdBlockEnabled, enabled);
}

static jboolean JNI_PrefServiceBridge_GetAdBlockEnabled(JNIEnv* env,
                                       const JavaParamRef<jobject>& obj) {
  return GetPrefService()->GetBoolean(prefs::kAdBlockEnabled);
}

static void JNI_PrefServiceBridge_SetAdBlockRegionalEnabled(JNIEnv* env,
                                   const JavaParamRef<jobject>& obj,
                                   jboolean enabled) {
   GetPrefService()->SetBoolean(prefs::kAdBlockRegionalEnabled, enabled);
}

static jboolean JNI_PrefServiceBridge_GetAdBlockRegionalEnabled(JNIEnv* env,
                                       const JavaParamRef<jobject>& obj) {
  return GetPrefService()->GetBoolean(prefs::kAdBlockRegionalEnabled);
}

static void JNI_PrefServiceBridge_SetTrackingProtectionEnabled(JNIEnv* env,
                                   const JavaParamRef<jobject>& obj,
                                   jboolean enabled) {
   GetPrefService()->SetBoolean(prefs::kTrackingProtectionEnabled, enabled);
}

static jboolean JNI_PrefServiceBridge_GetDesktopViewEnabled(JNIEnv* env,
                                         const JavaParamRef<jobject>& obj) {
  return GetBooleanForContentSetting(CONTENT_SETTINGS_TYPE_DESKTOP_VIEW);
}

static void JNI_PrefServiceBridge_SetDesktopViewEnabled(JNIEnv* env,
                                     const JavaParamRef<jobject>& obj,
                                     jboolean allow) {
  HostContentSettingsMap* host_content_settings_map =
      HostContentSettingsMapFactory::GetForProfile(GetOriginalProfile());
  host_content_settings_map->SetDefaultContentSetting(
      CONTENT_SETTINGS_TYPE_DESKTOP_VIEW,
      allow ? CONTENT_SETTING_ALLOW : CONTENT_SETTING_BLOCK);
}

static void JNI_PrefServiceBridge_SetContentSettingForPattern(
    JNIEnv* env,
    const JavaParamRef<jobject>& obj,
    int content_settings_type,
    const JavaParamRef<jstring>& pattern,
    int setting) {
  HostContentSettingsMap* host_content_settings_map =
       HostContentSettingsMapFactory::GetForProfile(GetOriginalProfile());
  host_content_settings_map->SetContentSettingCustomScope(
      ContentSettingsPattern::FromString(ConvertJavaStringToUTF8(env, pattern)),
      ContentSettingsPattern::Wildcard(),
      static_cast<ContentSettingsType>(content_settings_type), std::string(),
      static_cast<ContentSetting>(setting));
}

static void JNI_PrefServiceBridge_SetContentSettingForPatternIncognito(JNIEnv* env,
                                        const JavaParamRef<jobject>& obj,
                                        int content_settings_type,
                                        const JavaParamRef<jstring>& pattern,
                                        int setting) {
  Profile *profile = GetOriginalProfile()->GetOffTheRecordProfile();

  HostContentSettingsMap* host_content_settings_map =
    HostContentSettingsMapFactory::GetForProfile(profile);

  host_content_settings_map->SetContentSettingCustomScope(
      ContentSettingsPattern::FromString(ConvertJavaStringToUTF8(env, pattern)),
      ContentSettingsPattern::Wildcard(),
      static_cast<ContentSettingsType>(content_settings_type), std::string(),
      static_cast<ContentSetting>(setting));
}

static void JNI_PrefServiceBridge_GetContentSettingsExceptionsIncognito(JNIEnv* env,
                                         const JavaParamRef<jobject>& obj,
                                         int content_settings_type,
                                         const JavaParamRef<jobject>& list) {
  Profile *profile = GetOriginalProfile()->GetOffTheRecordProfile();

  HostContentSettingsMap* host_content_settings_map =
      HostContentSettingsMapFactory::GetForProfile(profile);
  ContentSettingsForOneType entries;
  host_content_settings_map->GetSettingsForOneType(
      static_cast<ContentSettingsType>(content_settings_type), "", &entries);
  for (size_t i = 0; i < entries.size(); ++i) {
    Java_PrefServiceBridge_addContentSettingExceptionToList(
        env, list, content_settings_type,
        ConvertUTF8ToJavaString(env, entries[i].primary_pattern.ToString()),
        entries[i].GetContentSetting(),
        ConvertUTF8ToJavaString(env, entries[i].source));
  }
}

static jboolean JNI_PrefServiceBridge_GetPlayVideoInBackgroundEnabled(JNIEnv* env,
                                         const JavaParamRef<jobject>& obj) {
  return GetBooleanForContentSetting(CONTENT_SETTINGS_TYPE_PLAY_VIDEO_IN_BACKGROUND);
}

static void JNI_PrefServiceBridge_SetPlayVideoInBackgroundEnabled(JNIEnv* env,
                                     const JavaParamRef<jobject>& obj,
                                     jboolean allow) {
  HostContentSettingsMap* host_content_settings_map =
      HostContentSettingsMapFactory::GetForProfile(GetOriginalProfile());
  host_content_settings_map->SetDefaultContentSetting(
      CONTENT_SETTINGS_TYPE_PLAY_VIDEO_IN_BACKGROUND,
      allow ? CONTENT_SETTING_ALLOW : CONTENT_SETTING_BLOCK);
}

static jboolean JNI_PrefServiceBridge_GetPlayYTVideoInBrowserEnabled(JNIEnv* env,
                                         const JavaParamRef<jobject>& obj) {
  return GetBooleanForContentSetting(CONTENT_SETTINGS_TYPE_PLAY_YT_VIDEO_IN_BROWSER);
}

static void JNI_PrefServiceBridge_SetPlayYTVideoInBrowserEnabled(JNIEnv* env,
                                     const JavaParamRef<jobject>& obj,
                                     jboolean allow) {
  HostContentSettingsMap* host_content_settings_map =
      HostContentSettingsMapFactory::GetForProfile(GetOriginalProfile());
  host_content_settings_map->SetDefaultContentSetting(
      CONTENT_SETTINGS_TYPE_PLAY_YT_VIDEO_IN_BROWSER,
      allow ? CONTENT_SETTING_ALLOW : CONTENT_SETTING_BLOCK);
}

static void JNI_PrefServiceBridge_SetSafetynetCheckFailed(JNIEnv* env,
                                   const JavaParamRef<jobject>& obj,
                                   jboolean value) {
  GetPrefService()->SetBoolean(prefs::kSafetynetCheckFailed, value);
}

static jboolean JNI_PrefServiceBridge_GetSafetynetCheckFailed(JNIEnv* env,
                                       const JavaParamRef<jobject>& obj) {
  return GetPrefService()->GetBoolean(prefs::kSafetynetCheckFailed);
}
