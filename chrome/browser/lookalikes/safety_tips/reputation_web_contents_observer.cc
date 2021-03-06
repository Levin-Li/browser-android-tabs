// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/lookalikes/safety_tips/reputation_web_contents_observer.h"

#include <string>
#include <utility>

#include "base/metrics/histogram_functions.h"
#include "base/metrics/histogram_macros.h"
#include "build/build_config.h"
#include "chrome/browser/profiles/profile.h"
#include "components/security_state/core/features.h"
#include "content/public/browser/navigation_entry.h"

namespace {

void OnSafetyTipClosed(security_state::SafetyTipStatus safety_tip_status,
                       base::Time start_time,
                       safety_tips::SafetyTipInteraction action) {
  std::string action_suffix;
  bool warning_dismissed = false;
  switch (action) {
    case safety_tips::SafetyTipInteraction::kNoAction:
      action_suffix = "NoAction";
      break;
    case safety_tips::SafetyTipInteraction::kLeaveSite:
      action_suffix = "LeaveSite";
      break;
    case safety_tips::SafetyTipInteraction::kDismiss:
      NOTREACHED();
      // Do nothing because the dismissal action passed to this method should
      // be the more specific version (esc, close, or ignore).
      break;
    case safety_tips::SafetyTipInteraction::kDismissWithEsc:
      action_suffix = "DismissWithEsc";
      warning_dismissed = true;
      break;
    case safety_tips::SafetyTipInteraction::kDismissWithClose:
      action_suffix = "DismissWithClose";
      warning_dismissed = true;
      break;
    case safety_tips::SafetyTipInteraction::kDismissWithIgnore:
      action_suffix = "DismissWithIgnore";
      warning_dismissed = true;
      break;
    case safety_tips::SafetyTipInteraction::kLearnMore:
      action_suffix = "LearnMore";
      break;
  }
  if (warning_dismissed) {
    base::UmaHistogramCustomTimes(
        security_state::GetSafetyTipHistogramName(
            std::string("Security.SafetyTips.OpenTime.Dismiss"),
            safety_tip_status),
        base::Time::Now() - start_time, base::TimeDelta::FromMilliseconds(1),
        base::TimeDelta::FromHours(1), 100);
  }
  base::UmaHistogramCustomTimes(
      security_state::GetSafetyTipHistogramName(
          std::string("Security.SafetyTips.OpenTime.") + action_suffix,
          safety_tip_status),
      base::Time::Now() - start_time, base::TimeDelta::FromMilliseconds(1),
      base::TimeDelta::FromHours(1), 100);
}

}  // namespace

namespace safety_tips {

ReputationWebContentsObserver::~ReputationWebContentsObserver() {}

void ReputationWebContentsObserver::DidFinishNavigation(
    content::NavigationHandle* navigation_handle) {
  if (!navigation_handle->IsInMainFrame() ||
      navigation_handle->IsSameDocument() ||
      !navigation_handle->HasCommitted()) {
    MaybeCallReputationCheckCallback();
    return;
  }

  last_navigation_safety_tip_info_ = {security_state::SafetyTipStatus::kUnknown,
                                      GURL()};
  last_safety_tip_navigation_entry_id_ = 0;

  MaybeShowSafetyTip();
}

void ReputationWebContentsObserver::OnVisibilityChanged(
    content::Visibility visibility) {
  MaybeShowSafetyTip();
}

security_state::SafetyTipInfo
ReputationWebContentsObserver::GetSafetyTipInfoForVisibleNavigation() const {
  content::NavigationEntry* entry =
      web_contents()->GetController().GetVisibleEntry();
  if (!entry)
    return {security_state::SafetyTipStatus::kUnknown, GURL()};
  return last_safety_tip_navigation_entry_id_ == entry->GetUniqueID()
             ? last_navigation_safety_tip_info_
             : security_state::SafetyTipInfo(
                   {security_state::SafetyTipStatus::kUnknown, GURL()});
}

void ReputationWebContentsObserver::RegisterReputationCheckCallbackForTesting(
    base::OnceClosure callback) {
  reputation_check_callback_for_testing_ = std::move(callback);
}

ReputationWebContentsObserver::ReputationWebContentsObserver(
    content::WebContents* web_contents)
    : WebContentsObserver(web_contents),
      profile_(Profile::FromBrowserContext(web_contents->GetBrowserContext())),
      weak_factory_(this) {
  last_navigation_safety_tip_info_ = {security_state::SafetyTipStatus::kUnknown,
                                      GURL()};
}

void ReputationWebContentsObserver::MaybeShowSafetyTip() {
  if (web_contents()->GetMainFrame()->GetVisibilityState() !=
      content::PageVisibilityState::kVisible) {
    return;
  }

  const GURL& url = web_contents()->GetLastCommittedURL();
  if (!url.SchemeIsHTTPOrHTTPS()) {
    return;
  }

  ReputationService* service = ReputationService::Get(profile_);
  service->GetReputationStatus(
      url, base::BindRepeating(
               &ReputationWebContentsObserver::HandleReputationCheckResult,
               weak_factory_.GetWeakPtr()));
}

void ReputationWebContentsObserver::HandleReputationCheckResult(
    security_state::SafetyTipStatus safety_tip_status,
    const GURL& url,
    const GURL& suggested_url) {
  UMA_HISTOGRAM_ENUMERATION("Security.SafetyTips.SafetyTipShown",
                            safety_tip_status);

  // Set this field independent of whether the feature to show the UI is
  // enabled/disabled. Metrics code uses this field and we want to record
  // metrics regardless of the feature being enabled/disabled.
  last_navigation_safety_tip_info_ = {safety_tip_status, suggested_url};

  // A navigation entry should always exist because reputation checks are only
  // triggered when a committed navigation finishes.
  last_safety_tip_navigation_entry_id_ =
      web_contents()->GetController().GetLastCommittedEntry()->GetUniqueID();
  // Since we downgrade indicator when a safety tip is triggered, update the
  // visible security state if we have a non-kNone status. This has to happen
  // after last_safety_tip_navigation_entry_id_ is updated.
  if (safety_tip_status != security_state::SafetyTipStatus::kNone) {
    web_contents()->DidChangeVisibleSecurityState();
  }

  if (safety_tip_status == security_state::SafetyTipStatus::kNone ||
      safety_tip_status == security_state::SafetyTipStatus::kBadKeyword) {
    MaybeCallReputationCheckCallback();
    return;
  }

  if (safety_tip_status == security_state::SafetyTipStatus::kLookalikeIgnored ||
      safety_tip_status ==
          security_state::SafetyTipStatus::kBadReputationIgnored) {
    UMA_HISTOGRAM_ENUMERATION("Security.SafetyTips.SafetyTipIgnoredPageLoad",
                              safety_tip_status);
    MaybeCallReputationCheckCallback();
    return;
  }

  MaybeCallReputationCheckCallback();

  if (!base::FeatureList::IsEnabled(security_state::features::kSafetyTipUI)) {
    return;
  }
  ShowSafetyTipDialog(
      web_contents(), safety_tip_status, url, suggested_url,
      base::BindOnce(OnSafetyTipClosed, safety_tip_status, base::Time::Now()));
}

void ReputationWebContentsObserver::MaybeCallReputationCheckCallback() {
  if (reputation_check_callback_for_testing_.is_null())
    return;
  std::move(reputation_check_callback_for_testing_).Run();
}

WEB_CONTENTS_USER_DATA_KEY_IMPL(ReputationWebContentsObserver)

}  // namespace safety_tips
