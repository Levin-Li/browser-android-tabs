/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef SHIELDS_CONFIG_H_
#define SHIELDS_CONFIG_H_

#include <jni.h>
#include "../../../../base/android/jni_weak_ref.h"

namespace net {
namespace blockers {

class BlockersWorker;

class ShieldsConfig {
public:
    ShieldsConfig(JNIEnv* env, jobject obj);
    ~ShieldsConfig();

    bool getPrivacyAdBlock();
    bool getPrivacyAdBlockRegional();
    bool getPrivacyHTTPSE();
    std::string getHostSettings(const bool &incognitoTab, const std::string& host);
    void setBlockedCountInfo(const std::string& url, int trackersBlocked, int adsBlocked, int httpsUpgrades,
            int scriptsBlocked, int fingerprintingBlocked);
    bool needUpdateAdBlocker();
    void resetUpdateAdBlockerFlag();
    std::shared_ptr<BlockersWorker> getBlockersWorker(bool force_reset = false);

    static ShieldsConfig* getShieldsConfig();
    // Register the ShieldsConfig's native methods through JNI.
    //static bool RegisterShieldsConfig(JNIEnv* env);

private:
    std::shared_ptr<BlockersWorker> blockers_worker_;
    JavaObjectWeakGlobalRef weak_java_shields_config_;
};
}
}

#endif //SHIELDS_CONFIG_H_
