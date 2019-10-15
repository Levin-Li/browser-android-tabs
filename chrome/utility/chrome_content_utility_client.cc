// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/utility/chrome_content_utility_client.h"

#include <stddef.h>

#include <utility>

#include "base/command_line.h"
#include "base/files/file_path.h"
#include "base/lazy_instance.h"
#include "chrome/utility/browser_exposed_utility_interfaces.h"
#include "chrome/utility/services.h"
#include "services/service_manager/sandbox/switches.h"

#if BUILDFLAG(ENABLE_PRINT_PREVIEW) && defined(OS_WIN)
#include "chrome/utility/printing_handler.h"
#endif

#include "brave/components/brave_ads/browser/buildflags/buildflags.h"
#include "brave/components/brave_rewards/browser/buildflags/buildflags.h"

#if BUILDFLAG(BRAVE_ADS_ENABLED)
#include "brave/components/services/bat_ads/bat_ads_app.h"
#include "brave/components/services/bat_ads/public/interfaces/bat_ads.mojom.h"
#endif

#if BUILDFLAG(BRAVE_REWARDS_ENABLED)
#include "brave/components/services/bat_ledger/bat_ledger_app.h"
#include "brave/components/services/bat_ledger/public/interfaces/bat_ledger.mojom.h"
#endif

namespace {

#if BUILDFLAG(BRAVE_ADS_ENABLED) || BUILDFLAG(BRAVE_REWARDS_ENABLED)
void RunServiceAsyncThenTerminateProcess(
    std::unique_ptr<service_manager::Service> service) {
  service_manager::Service::RunAsyncUntilTermination(
      std::move(service),
      base::BindOnce([] { content::UtilityThread::Get()->ReleaseProcess(); }));
}
#endif

#if BUILDFLAG(BRAVE_ADS_ENABLED)
std::unique_ptr<service_manager::Service> CreateBatAdsService(
    service_manager::mojom::ServiceRequest request) {
  return std::make_unique<bat_ads::BatAdsApp>(
      std::move(request));
}
#endif

#if BUILDFLAG(BRAVE_REWARDS_ENABLED)
std::unique_ptr<service_manager::Service> CreateBatLedgerService(
    service_manager::mojom::ServiceRequest request) {
  return std::make_unique<bat_ledger::BatLedgerApp>(
      std::move(request));
}
#endif

}  // namespace

namespace {

base::LazyInstance<ChromeContentUtilityClient::NetworkBinderCreationCallback>::
    Leaky g_network_binder_creation_callback = LAZY_INSTANCE_INITIALIZER;

}  // namespace

ChromeContentUtilityClient::ChromeContentUtilityClient()
    : utility_process_running_elevated_(false) {
#if BUILDFLAG(ENABLE_PRINT_PREVIEW) && defined(OS_WIN)
  printing_handler_ = std::make_unique<printing::PrintingHandler>();
#endif
}

ChromeContentUtilityClient::~ChromeContentUtilityClient() = default;

void ChromeContentUtilityClient::ExposeInterfacesToBrowser(
    mojo::BinderMap* binders) {
#if defined(OS_WIN)
  base::CommandLine* command_line = base::CommandLine::ForCurrentProcess();
  utility_process_running_elevated_ = command_line->HasSwitch(
      service_manager::switches::kNoSandboxAndElevatedPrivileges);
#endif

  // If our process runs with elevated privileges, only add elevated Mojo
  // interfaces to the BinderMap.
  //
  // NOTE: Do not add interfaces directly from within this method. Instead,
  // modify the definition of |ExposeElevatedChromeUtilityInterfacesToBrowser()|
  // to ensure security review coverage.
  if (!utility_process_running_elevated_)
    ExposeElevatedChromeUtilityInterfacesToBrowser(binders);
}

bool ChromeContentUtilityClient::OnMessageReceived(
    const IPC::Message& message) {
  if (utility_process_running_elevated_)
    return false;

#if BUILDFLAG(ENABLE_PRINT_PREVIEW) && defined(OS_WIN)
  if (printing_handler_->OnMessageReceived(message))
    return true;
#endif
  return false;
}

void ChromeContentUtilityClient::RegisterNetworkBinders(
    service_manager::BinderRegistry* registry) {
  if (g_network_binder_creation_callback.Get())
    std::move(g_network_binder_creation_callback.Get()).Run(registry);
}

mojo::ServiceFactory*
ChromeContentUtilityClient::GetMainThreadServiceFactory() {
  if (utility_process_running_elevated_)
    return ::GetElevatedMainThreadServiceFactory();
  return ::GetMainThreadServiceFactory();
}

mojo::ServiceFactory* ChromeContentUtilityClient::GetIOThreadServiceFactory() {
  return ::GetIOThreadServiceFactory();
}

// static
void ChromeContentUtilityClient::SetNetworkBinderCreationCallback(
    NetworkBinderCreationCallback callback) {
  g_network_binder_creation_callback.Get() = std::move(callback);
}

bool ChromeContentUtilityClient::HandleServiceRequest(
    const std::string& service_name,
    service_manager::mojom::ServiceRequest request) {
#if BUILDFLAG(BRAVE_ADS_ENABLED)
  if (service_name == bat_ads::mojom::kServiceName) {
    RunServiceAsyncThenTerminateProcess(
        CreateBatAdsService(std::move(request)));
    return true;
  }
#endif

#if BUILDFLAG(BRAVE_REWARDS_ENABLED)
  if (service_name == bat_ledger::mojom::kServiceName) {
    RunServiceAsyncThenTerminateProcess(
        CreateBatLedgerService(std::move(request)));
    return true;
  }
#endif

  return false;
}