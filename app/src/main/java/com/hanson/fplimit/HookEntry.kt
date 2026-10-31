package com.hanson.fplimit

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hanson.fplimit.hooks.SettingsHook
import com.hanson.fplimit.hooks.SystemHook

/**
 * 模块入口。
 *
 * 这里刻意不依赖 OShin：独立 APK、独立包名，可与 OShin 共存，
 * 不需要跟随 OShin 的更新节奏。
 */
@InjectYukiHookWithXposed(entryClassName = "FPEntry")
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        debugLog {
            tag = "FPLimit"
        }
        isDebug = true
    }

    override fun onHook() = encase {
        loadHooker(SettingsHook())
        loadHooker(SystemHook())
    }
}
