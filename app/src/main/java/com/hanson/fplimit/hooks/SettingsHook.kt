package com.hanson.fplimit.hooks

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.hanson.fplimit.util.callOrNull
import com.hanson.fplimit.util.callStaticOrNull
import com.hanson.fplimit.util.fieldOrNull
import com.hanson.fplimit.util.logInfo
import com.hanson.fplimit.util.logWarn

/**
 * 想放行到的指纹数量上限。
 * 原始值来自 com.android.internal.R.integer.config_fingerprintMaxTemplatesPerUser，本机 = 5。
 */
private const val TARGET_MAX = 20

/**
 * UI 层：com.android.settings
 *
 * ColorOS 指纹设置页的「添加指纹」可用性由
 * OplusFingerprintSettings$FingerprintSettingsFragment#updateAddPreference() 决定：
 *
 *   max   = resources.getInteger(config_fingerprintMaxTemplatesPerUser)   // 本机 = 5
 *   count = FingerprintUtils.getFingersCount()
 *   enabled = count < max
 *   summary = count >= max ? "您最多可以添加 N 个指纹" : ""
 *
 * 两道保险：
 *  1. 直接把该资源 id 的 getInteger 结果改成 TARGET_MAX（从源头放宽）；
 *  2. updateAddPreference 执行后再强制 setEnabled(true) + 清空提示。
 */
class SettingsHook : YukiBaseHooker() {

    override fun onHook() = loadApp(name = "com.android.settings") {
        logInfo("settings: module loaded, installing hooks")
        try {
            hookMaxTemplates()
        } catch (t: Throwable) {
            logWarn("settings: hookMaxTemplates failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            hookFragment()
        } catch (t: Throwable) {
            logWarn("settings: hookFragment failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** 放宽 config_fingerprintMaxTemplatesPerUser 的取值 */
    private fun hookMaxTemplates() {
        val resLoad = "com.oplus.settings.internal.SettingsInternalResLoad".toClassOrNull()
        if (resLoad == null) {
            logWarn("settings: SettingsInternalResLoad not found")
            return
        }
        val key = "com.android.internal.R.integer.config_fingerprintMaxTemplatesPerUser"
        val id = resLoad.callStaticOrNull("getInternalResId", key) as? Int
        if (id == null || id <= 0) {
            logWarn("settings: cannot resolve $key")
            return
        }
        logInfo("settings: maxTemplates resId=$id -> override to $TARGET_MAX")
        "android.content.res.Resources".toClass().apply {
            method {
                name = "getInteger"
                param(IntType)
            }.hook {
                before {
                    if (args[0] == id) result = TARGET_MAX
                }
            }
        }
    }

    /** 兜底：强制「添加指纹」可用 */
    private fun hookFragment() {
        val className =
            "com.oplus.settings.feature.fingerprint.OplusFingerprintSettings\$FingerprintSettingsFragment"
        val fragment = className.toClassOrNull()
        if (fragment == null) {
            logWarn("settings: $className not found")
            return
        }
        logInfo("settings: hooking $className")

        fragment.apply {
            method {
                name = "updateAddPreference"
                emptyParam()
            }.hook {
                after {
                    instance.forceEnableAdd()
                }
            }
            method {
                name = "onResume"
                emptyParam()
            }.hook {
                after {
                    instance.callOrNull("updateAddPreference")
                    instance.forceEnableAdd()
                }
            }
        }
    }
}

private fun Any?.forceEnableAdd() {
    if (this == null) return
    val pref = fieldOrNull("mAddFingerPreference")
        ?: callOrNull("findPreference", "key_fingerprint_add")
    if (pref == null) {
        logWarn("settings: add-fingerprint preference not found")
        return
    }
    pref.callOrNull("setEnabled", true)
    pref.callOrNull("setSummary", "")
    logInfo("settings: add-fingerprint button forced enabled")
}
