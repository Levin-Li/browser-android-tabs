// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "services/network/network_service_network_delegate.h"

#include <string>

#include "base/bind.h"
#include "base/strings/utf_string_conversions.h"
#include "build/build_config.h"
#include "components/domain_reliability/monitor.h"
#include "net/base/load_flags.h"
#include "net/base/net_errors.h"
#include "net/url_request/url_request.h"
#include "services/network/cookie_manager.h"
#include "services/network/network_context.h"
#include "services/network/network_service.h"
#include "services/network/network_service_proxy_delegate.h"
#include "services/network/pending_callback_chain.h"
#include "services/network/public/cpp/features.h"
#include "services/network/url_loader.h"
#include "url/gurl.h"

#if !defined(OS_IOS)
#include "services/network/websocket.h"
#endif

#include "chrome/browser/net/blockers/blockers_worker.h"
#include "chrome/browser/net/blockers/shields_config.h"
#include "chrome/browser/stats_updater.h"
#include "chrome/common/chrome_features.h"
#include "content/public/common/resource_type.h"
#include "net/base/upload_bytes_element_reader.h"
#include "content/public/browser/web_contents.h"
#include "brave_src/browser/brave_tab_url_web_contents_observer.h"

#define TRANSPARENT1PXGIF "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"

namespace network {

brave::TabProperties GetTabProperties(const net::URLRequest* request) {
  DCHECK(request);
  URLLoader* url_loader = URLLoader::ForRequest(*request);
  if (url_loader) {
    int render_process_id = url_loader->GetProcessId();
    int render_frame_id = url_loader->GetRenderFrameId();
    int frame_tree_node_id = -1;
    return brave::BraveTabUrlWebContentsObserver::GetTabPropertiesFromRenderFrameInfo(
                                render_process_id,
                                render_frame_id,
                                frame_tree_node_id);
  }
  return brave::TabProperties(false);
}

namespace {

void SetCustomHeaders(
    net::URLRequest* request,
    net::HttpRequestHeaders* headers) {
  if (request && headers) {
    // Look for and setup custom headers
    std::string customHeaders = stats_updater::GetCustomHeadersForHost(
        request->url().host());
    if (customHeaders.size()) {
      std::string key;
      std::string value;
      size_t pos = customHeaders.find("\n");
      if (pos == std::string::npos) {
          key = customHeaders;
          value = "";
      } else {
          key = customHeaders.substr(0, pos);
          value = customHeaders.substr(pos + 1);
      }
      if (key.size()) {
          headers->SetHeader(key, value);
      }
    }
  }
}

}  // namespace

namespace {

const char kClearSiteDataHeader[] = "Clear-Site-Data";

}  // anonymous namespace

struct OnBeforeURLRequestContext
{
  OnBeforeURLRequestContext() {}
  ~OnBeforeURLRequestContext() {}

  int adsBlocked = 0;
  int trackersBlocked = 0;
  int httpsUpgrades = 0;

  bool isGlobalBlockEnabled = true;
  bool blockAdsAndTracking = true;
  bool isAdBlockRegionalEnabled = true;
  bool isHTTPSEEnabled = true;

  bool needPerformAdBlock = false;
  bool needPerformHTTPSE = false;

  bool block = false;

  URLLoader* url_loader = nullptr;
  GURL tab_url;
  bool isValidUrl = true;
  std::string firstparty_host = "";
  bool check_httpse_redirect = true;
  GURL UrlCopy;
  std::string newURL;
  uint64_t request_identifier = 0;
  bool incognito;

  DISALLOW_COPY_AND_ASSIGN(OnBeforeURLRequestContext);
};

NetworkServiceNetworkDelegate::NetworkServiceNetworkDelegate(
    bool enable_referrers,
    bool validate_referrer_policy_on_initial_request,
    mojo::PendingRemote<mojom::ProxyErrorClient> proxy_error_client_remote,
    NetworkContext* network_context)
    : enable_referrers_(enable_referrers),
      validate_referrer_policy_on_initial_request_(
          validate_referrer_policy_on_initial_request),
      network_context_(network_context) {
  if (proxy_error_client_remote)
    proxy_error_client_.Bind(std::move(proxy_error_client_remote));
}

NetworkServiceNetworkDelegate::~NetworkServiceNetworkDelegate() = default;

void NetworkServiceNetworkDelegate::MaybeTruncateReferrer(
    net::URLRequest* const request,
    const GURL& effective_url) {
  if (!enable_referrers_) {
    request->SetReferrer(std::string());
    request->set_referrer_policy(net::URLRequest::NO_REFERRER);
    return;
  }

  if (base::FeatureList::IsEnabled(
          features::kCapReferrerToOriginOnCrossOrigin)) {
    url::Origin destination_origin = url::Origin::Create(effective_url);
    url::Origin source_origin = url::Origin::Create(GURL(request->referrer()));
    if (!destination_origin.IsSameOriginWith(source_origin))
      request->SetReferrer(source_origin.GetURL().spec());
  }
}

int NetworkServiceNetworkDelegate::OnBeforeURLRequest(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    GURL* new_url) {
  DCHECK(request);

  auto* const loader = URLLoader::ForRequest(*request);
  const GURL* effective_url = nullptr;
  if (loader && loader->new_redirect_url()) {
    DCHECK(new_url);
    *new_url = loader->new_redirect_url().value();
    effective_url = new_url;
  } else {
    effective_url = &request->url();
  }

  MaybeTruncateReferrer(request, *effective_url);

  NetworkService* network_service = network_context_->network_service();
  if (network_service)
    network_service->OnBeforeURLRequest();

  if (!loader)
    return net::OK;

  if (network_service) {
    loader->SetAllowReportingRawHeaders(network_service->HasRawHeadersAccess(
        loader->GetProcessId(), *effective_url));
  }
  return net::OK;
}

int NetworkServiceNetworkDelegate::OnBeforeStartTransaction(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    net::HttpRequestHeaders* headers) {
  SetCustomHeaders(request, headers);
  if (network_context_->proxy_delegate()) {
    network_context_->proxy_delegate()->OnBeforeStartTransaction(request,
                                                                 headers);
  }
  URLLoader* url_loader = URLLoader::ForRequest(*request);
  if (url_loader)
    return url_loader->OnBeforeStartTransaction(std::move(callback), headers);

#if !defined(OS_IOS)
  WebSocket* web_socket = WebSocket::ForRequest(*request);
  if (web_socket)
    return web_socket->OnBeforeStartTransaction(std::move(callback), headers);
#endif  // !defined(OS_IOS)

  return net::OK;
}

void NetworkServiceNetworkDelegate::OnBeforeSendHeaders(
    net::URLRequest* request,
    const net::ProxyInfo& proxy_info,
    const net::ProxyRetryInfoMap& proxy_retry_info,
    net::HttpRequestHeaders* headers) {
  if (network_context_->proxy_delegate()) {
    network_context_->proxy_delegate()->OnBeforeSendHeaders(request, proxy_info,
                                                            headers);
  }
}

int NetworkServiceNetworkDelegate::OnHeadersReceived(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    const net::HttpResponseHeaders* original_response_headers,
    scoped_refptr<net::HttpResponseHeaders>* override_response_headers,
    const net::IPEndPoint& endpoint,
    base::Optional<GURL>* preserve_fragment_on_redirect_url) {
  auto chain = base::MakeRefCounted<PendingCallbackChain>(std::move(callback));
  URLLoader* url_loader = URLLoader::ForRequest(*request);
  if (url_loader) {
    chain->AddResult(url_loader->OnHeadersReceived(
        chain->CreateCallback(), original_response_headers,
        override_response_headers, endpoint,
        preserve_fragment_on_redirect_url));
  }

#if !defined(OS_IOS)
  WebSocket* web_socket = WebSocket::ForRequest(*request);
  if (web_socket) {
    chain->AddResult(web_socket->OnHeadersReceived(
        chain->CreateCallback(), original_response_headers,
        override_response_headers, preserve_fragment_on_redirect_url));
  }
#endif  // !defined(OS_IOS)

  chain->AddResult(HandleClearSiteDataHeader(request, chain->CreateCallback(),
                                             original_response_headers));

  return chain->GetResult();
}

void NetworkServiceNetworkDelegate::OnBeforeRedirect(net::URLRequest* request,
                                                     const GURL& new_location) {
  if (network_context_->domain_reliability_monitor())
    network_context_->domain_reliability_monitor()->OnBeforeRedirect(request);
}

void NetworkServiceNetworkDelegate::OnResponseStarted(net::URLRequest* request,
                                                      int net_error) {
  ForwardProxyErrors(net_error);
}

void NetworkServiceNetworkDelegate::OnCompleted(net::URLRequest* request,
                                                bool started,
                                                int net_error) {
  // TODO(mmenke): Once the network service ships on all platforms, can move
  // this logic into URLLoader's completion method.
  DCHECK_NE(net::ERR_IO_PENDING, net_error);

  if (network_context_->domain_reliability_monitor()) {
    network_context_->domain_reliability_monitor()->OnCompleted(request,
                                                                started);
  }

  ForwardProxyErrors(net_error);
}

void NetworkServiceNetworkDelegate::OnPACScriptError(
    int line_number,
    const base::string16& error) {
  if (!proxy_error_client_)
    return;

  proxy_error_client_->OnPACScriptError(line_number, base::UTF16ToUTF8(error));
}

bool NetworkServiceNetworkDelegate::OnCanGetCookies(
    const net::URLRequest& request,
    const net::CookieList& cookie_list,
    bool allowed_from_caller) {
  bool allowed = allowed_from_caller &&
                 network_context_->cookie_manager()
                     ->cookie_settings()
                     .IsCookieAccessAllowed(
                         request.url(), request.site_for_cookies(),
                         request.network_isolation_key().GetTopFrameOrigin());

  if (!allowed)
    return false;

  URLLoader* url_loader = URLLoader::ForRequest(request);
  if (url_loader)
    return url_loader->AllowCookies(request.url(), request.site_for_cookies());
#if !defined(OS_IOS)
  WebSocket* web_socket = WebSocket::ForRequest(request);
  if (web_socket)
    return web_socket->AllowCookies(request.url());
#endif  // !defined(OS_IOS)
  return true;
}

bool NetworkServiceNetworkDelegate::OnCanSetCookie(
    const net::URLRequest& request,
    const net::CanonicalCookie& cookie,
    net::CookieOptions* options,
    bool allowed_from_caller) {
  bool allowed = allowed_from_caller &&
                 network_context_->cookie_manager()
                     ->cookie_settings()
                     .IsCookieAccessAllowed(
                         request.url(), request.site_for_cookies(),
                         request.network_isolation_key().GetTopFrameOrigin());
  if (!allowed)
    return false;
  URLLoader* url_loader = URLLoader::ForRequest(request);
  if (url_loader)
    return url_loader->AllowCookies(request.url(), request.site_for_cookies());
#if !defined(OS_IOS)
  WebSocket* web_socket = WebSocket::ForRequest(request);
  if (web_socket)
    return web_socket->AllowCookies(request.url());
#endif  // !defined(OS_IOS)
  return true;
}

bool NetworkServiceNetworkDelegate::OnForcePrivacyMode(
    const GURL& url,
    const GURL& site_for_cookies,
    const base::Optional<url::Origin>& top_frame_origin) const {
  return !network_context_->cookie_manager()
              ->cookie_settings()
              .IsCookieAccessAllowed(url, site_for_cookies, top_frame_origin);
}

bool NetworkServiceNetworkDelegate::
    OnCancelURLRequestWithPolicyViolatingReferrerHeader(
        const net::URLRequest& request,
        const GURL& target_url,
        const GURL& referrer_url) const {
  // TODO(mmenke): Once the network service has shipped on all platforms,
  // consider moving this logic into URLLoader, and removing this method from
  // NetworkDelegate. Can just have a DCHECK in URLRequest instead.
  if (!validate_referrer_policy_on_initial_request_)
    return false;

  LOG(ERROR) << "Cancelling request to " << target_url
             << " with invalid referrer " << referrer_url;
  return true;
}

bool NetworkServiceNetworkDelegate::OnCanQueueReportingReport(
    const url::Origin& origin) const {
  return network_context_->cookie_manager()
      ->cookie_settings()
      .IsCookieAccessAllowed(origin.GetURL(), origin.GetURL());
}

void NetworkServiceNetworkDelegate::OnCanSendReportingReports(
    std::set<url::Origin> origins,
    base::OnceCallback<void(std::set<url::Origin>)> result_callback) const {
  auto* client = network_context_->client();
  if (!client) {
    origins.clear();
    std::move(result_callback).Run(std::move(origins));
    return;
  }

  if (network_context_->SkipReportingPermissionCheck()) {
    std::move(result_callback).Run(std::move(origins));
    return;
  }

  std::vector<url::Origin> origin_vector;
  std::copy(origins.begin(), origins.end(), std::back_inserter(origin_vector));
  client->OnCanSendReportingReports(
      origin_vector,
      base::BindOnce(
          &NetworkServiceNetworkDelegate::FinishedCanSendReportingReports,
          weak_ptr_factory_.GetWeakPtr(), std::move(result_callback)));
}

bool NetworkServiceNetworkDelegate::OnCanSetReportingClient(
    const url::Origin& origin,
    const GURL& endpoint) const {
  return network_context_->cookie_manager()
      ->cookie_settings()
      .IsCookieAccessAllowed(origin.GetURL(), origin.GetURL());
}

bool NetworkServiceNetworkDelegate::OnCanUseReportingClient(
    const url::Origin& origin,
    const GURL& endpoint) const {
  return network_context_->cookie_manager()
      ->cookie_settings()
      .IsCookieAccessAllowed(origin.GetURL(), origin.GetURL());
}

int NetworkServiceNetworkDelegate::HandleClearSiteDataHeader(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    const net::HttpResponseHeaders* original_response_headers) {
  DCHECK(request);
  if (!original_response_headers || !network_context_->client())
    return net::OK;

  URLLoader* url_loader = URLLoader::ForRequest(*request);
  if (!url_loader)
    return net::OK;

  std::string header_value;
  if (!original_response_headers->GetNormalizedHeader(kClearSiteDataHeader,
                                                      &header_value)) {
    return net::OK;
  }

  network_context_->client()->OnClearSiteData(
      url_loader->GetProcessId(), url_loader->GetRenderFrameId(),
      request->url(), header_value, request->load_flags(),
      base::BindOnce(&NetworkServiceNetworkDelegate::FinishedClearSiteData,
                     weak_ptr_factory_.GetWeakPtr(), request->GetWeakPtr(),
                     std::move(callback)));

  return net::ERR_IO_PENDING;
}

void NetworkServiceNetworkDelegate::FinishedClearSiteData(
    base::WeakPtr<net::URLRequest> request,
    net::CompletionOnceCallback callback) {
  if (request)
    std::move(callback).Run(net::OK);
}

void NetworkServiceNetworkDelegate::FinishedCanSendReportingReports(
    base::OnceCallback<void(std::set<url::Origin>)> result_callback,
    const std::vector<url::Origin>& origins) {
  std::set<url::Origin> origin_set(origins.begin(), origins.end());
  std::move(result_callback).Run(origin_set);
}

void NetworkServiceNetworkDelegate::ForwardProxyErrors(int net_error) {
  if (!proxy_error_client_)
    return;

  // TODO(https://crbug.com/876848): Provide justification for the currently
  // enumerated errors.
  switch (net_error) {
    case net::ERR_PROXY_AUTH_UNSUPPORTED:
    case net::ERR_PROXY_CONNECTION_FAILED:
    case net::ERR_TUNNEL_CONNECTION_FAILED:
      proxy_error_client_->OnRequestMaybeFailedDueToProxySettings(net_error);
      break;
  }
}

int NetworkServiceNetworkDelegate::OnBeforeURLRequest(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    GURL* new_url,
    bool call_callback) {
  int rv = net::OK;
  std::shared_ptr<OnBeforeURLRequestContext> ctx(new OnBeforeURLRequestContext());
  ctx->url_loader = URLLoader::ForRequest(*request);
  brave::TabProperties tab_prop = GetTabProperties(request);
  ctx->incognito = tab_prop.is_incognito;
  if (!request->site_for_cookies().is_empty()) {
    ctx->tab_url = request->site_for_cookies();
  } else if (tab_prop.is_valid) {
    ctx->tab_url = tab_prop.tab_url.GetOrigin();
  }
  if (ctx->url_loader && tab_prop.is_valid) {
    rv = OnBeforeURLRequest_PreBlockersWork(
        request,
        std::move(callback),
        new_url,
        ctx);
  }
  if (call_callback && !callback.is_null()) {
    std::move(callback).Run(rv);
  }
  return rv;
}

int NetworkServiceNetworkDelegate::OnBeforeURLRequest_PreBlockersWork(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    GURL* new_url,
    std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  ctx->firstparty_host = "";
  if (request) {
    ctx->firstparty_host = request->site_for_cookies().host();
    ctx->request_identifier = request->identifier();
  }
  // (TODO)find a better way to handle last first party
  if (0 == ctx->firstparty_host.length()) {
    ctx->firstparty_host = last_first_party_url_.host();
  } else if (request) {
    last_first_party_url_ = request->site_for_cookies();
  }
  // We want to block first party ads as well
  /*bool firstPartyUrl = false;
  if (request && (last_first_party_url_ == request->url())) {
    firstPartyUrl = true;
  }*/
  net::blockers::ShieldsConfig* shieldsConfig = net::blockers::ShieldsConfig::getShieldsConfig();
  if (!blockers_worker_ && shieldsConfig) blockers_worker_ = shieldsConfig->getBlockersWorker();
  // Ad Block and tracking protection
  ctx->isGlobalBlockEnabled = true;
  ctx->blockAdsAndTracking = shieldsConfig ? shieldsConfig->getPrivacyAdBlock() : true;
  ctx->isAdBlockRegionalEnabled = shieldsConfig ? shieldsConfig->getPrivacyAdBlockRegional() : true;
  ctx->isHTTPSEEnabled = shieldsConfig ? shieldsConfig->getPrivacyHTTPSE() : true;
  if (request && nullptr != shieldsConfig) {
      std::string hostConfig = shieldsConfig->getHostSettings(ctx->incognito, ctx->firstparty_host);
      // It is a length of ALL_SHIELDS_DEFAULT_MASK in ShieldsConfig.java
      if (hostConfig.length() == 11) {
        if ('0' == hostConfig[0]) {
            ctx->isGlobalBlockEnabled = false;
        }
        if (ctx->isGlobalBlockEnabled) {
            if ('0' ==  hostConfig[2]) {
                ctx->blockAdsAndTracking = false;
            }
            if ('0' ==  hostConfig[4]) {
                ctx->isHTTPSEEnabled = false;
            }
        }
      }
  } else if (nullptr == shieldsConfig){
      ctx->isGlobalBlockEnabled = false;
  }

  ctx->isValidUrl = true;
  if (request) {
      ctx->isValidUrl = request->url().is_valid();
      std::string scheme = request->url().scheme();
      if (scheme.length()) {
          std::transform(scheme.begin(), scheme.end(), scheme.begin(), ::tolower);
          if ("http" != scheme && "https" != scheme/* && "blob" != scheme*/) {
              ctx->isValidUrl = false;
          }
      }
  }
  ctx->block = false;
  ctx->adsBlocked = 0;
  ctx->trackersBlocked = 0;
  ctx->httpsUpgrades = 0;

  int rv = OnBeforeURLRequest_AdBlockPreFileWork(request, std::move(callback), new_url, ctx);
  return rv;
}

int NetworkServiceNetworkDelegate::OnBeforeURLRequest_AdBlockPreFileWork(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    GURL* new_url,
    std::shared_ptr<OnBeforeURLRequestContext> ctx) {
	if (!ctx->block
      //&& !firstPartyUrl
      && ctx->isValidUrl
      && ctx->isGlobalBlockEnabled
      && ctx->blockAdsAndTracking
      && request
      && ctx->url_loader
      && (uint32_t)content::ResourceType::kMainFrame != ctx->url_loader->GetResourceType()) {
    ctx->needPerformAdBlock = true;
    if (!blockers_worker_->isAdBlockerInitialized() ||
      (ctx->isAdBlockRegionalEnabled && !blockers_worker_->isAdBlockerRegionalInitialized()) ) {
      OnBeforeURLRequest_AdBlockFileWork(ctx);
    }
  }

  int rv = OnBeforeURLRequest_AdBlockPostFileWork(request, std::move(callback), new_url, ctx);
  return rv;
}

void NetworkServiceNetworkDelegate::OnBeforeURLRequest_AdBlockFileWork(std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  base::internal::AssertBlockingAllowed();
  blockers_worker_->InitAdBlock();

  if (ctx->isAdBlockRegionalEnabled &&
    !blockers_worker_->isAdBlockerRegionalInitialized()) {
    blockers_worker_->InitAdBlockRegional();
  }
}

int NetworkServiceNetworkDelegate::OnBeforeURLRequest_AdBlockPostFileWork(
  net::URLRequest* request,
  net::CompletionOnceCallback callback,
  GURL* new_url,
  std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  if (ctx->needPerformAdBlock) {
    if (blockers_worker_->shouldAdBlockUrl(
        ctx->tab_url.spec(),
        request->url().spec(),
        (unsigned int)ctx->url_loader->GetResourceType(),
        ctx->isAdBlockRegionalEnabled)) {
          ctx->block = true;
          ctx->adsBlocked++;
      }
  }

  ctx->check_httpse_redirect = true;
  if (ctx->block && ctx->url_loader && (unsigned int)content::ResourceType::kImage == ctx->url_loader->GetResourceType()) {
    ctx->check_httpse_redirect = false;
    *new_url = GURL(TRANSPARENT1PXGIF);
  }

   int rv = OnBeforeURLRequest_HttpsePreFileWork(request, std::move(callback), new_url, ctx);
   return rv;
}

int NetworkServiceNetworkDelegate::OnBeforeURLRequest_HttpsePreFileWork(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    GURL* new_url,
    std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  ctx->needPerformHTTPSE = false;
  // HTTPSE work
  if (!ctx->block
      && request
      && ctx->isValidUrl
      && ctx->isGlobalBlockEnabled
      && ctx->isHTTPSEEnabled
      && ctx->check_httpse_redirect) {
    ctx->needPerformHTTPSE = true;
  }
  if (ctx->needPerformHTTPSE) {
    ctx->newURL = blockers_worker_->getHTTPSURLFromCacheOnly(&request->url(), request->identifier());
    if (ctx->newURL == request->url().spec()) {
      ctx->UrlCopy = request->url();
      OnBeforeURLRequest_HttpseFileWork(request, ctx);
    }
  }

  int rv = OnBeforeURLRequest_HttpsePostFileWork(request, std::move(callback), new_url, ctx);
  return rv;
}

void NetworkServiceNetworkDelegate::OnBeforeURLRequest_HttpseFileWork(net::URLRequest* request,
    std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  base::internal::AssertBlockingAllowed();
  DCHECK(ctx->request_identifier != 0);
  ctx->newURL = blockers_worker_->getHTTPSURL(&ctx->UrlCopy, ctx->request_identifier);
}

int NetworkServiceNetworkDelegate::OnBeforeURLRequest_HttpsePostFileWork(net::URLRequest* request,net::CompletionOnceCallback callback,GURL* new_url,
    std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  if (!ctx->newURL.empty() &&
    ctx->needPerformHTTPSE &&
    ctx->newURL != request->url().spec()) {
    *new_url = GURL(ctx->newURL);
    if (last_first_party_url_ != request->url()) {
      ctx->httpsUpgrades++;
    }
  }

  int rv = OnBeforeURLRequest_PostBlockers(request, std::move(callback), new_url, ctx);
  return rv;
}

int NetworkServiceNetworkDelegate::OnBeforeURLRequest_PostBlockers(
    net::URLRequest* request,
    net::CompletionOnceCallback callback,
    GURL* new_url,
    std::shared_ptr<OnBeforeURLRequestContext> ctx) {
  net::blockers::ShieldsConfig* shieldsConfig =
     net::blockers::ShieldsConfig::getShieldsConfig();
  if (nullptr != shieldsConfig && (0 != ctx->trackersBlocked || 0 != ctx->adsBlocked || 0 != ctx->httpsUpgrades)) {
    shieldsConfig->setBlockedCountInfo(last_first_party_url_.spec()
        , ctx->trackersBlocked
        , ctx->adsBlocked
        , ctx->httpsUpgrades
        , 0
        , 0);
  }

  if (ctx->block && (nullptr == ctx->url_loader || (uint32_t)content::ResourceType::kImage != ctx->url_loader->GetResourceType())) {
    *new_url = GURL("");
    return net::ERR_BLOCKED_BY_ADMINISTRATOR;
  }
  return net::OK;
}

}  // namespace network
