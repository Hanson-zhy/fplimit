package com.hanson.fplimit.hooks

import android.content.res.Resources
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.hanson.fplimit.util.callOrNull
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
 *  1. 在 Resources#getInteger 上按资源名拦截，把该资源值直接改成 TARGET_MAX（从源头放宽）；
 *  2. updateAddPreference 执行后再强制 setEnabled(true) + 清空提示。
 */
class SettingsHook : YukiBaseHooker() {

    override fun onHook() = loadApp(name = "com.android.settings") {
        logInfo("settings: module loaded, installing hooks")
        try {
            hookMaxTemplatesResource()
        } catch (t: Throwable) {
            logWarn("settings: hookMaxTemplatesResource failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            hookFragment()
        } catch (t: Throwable) {
            logWarn("settings: hookFragment failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** 按资源名拦 getInteger，放宽 config_fingerprintMaxTemplatesPerUser */
    private fun hookMaxTemplatesResource() {
        "android.content.res.Resources".toClass().apply {
            method {
                name = "getInteger"
                param(IntType)
            }.hook {
                before {
                    val res = instance as? Resources ?: return@before
                    val resId = args[0] as? Int ?: return@before
                    val name = runCatching { res.getResourceName(resId) }.getOrNull() ?: return@before
                    if (name.endsWith("integer/config_fingerprintMaxTemplatesPerUser")) {
                        result = TARGET_MAX
                        logInfo("settings: override $name -> $TARGET_MAX")
                    }
                }
            }
        }
        logInfo("settings: Resources.getInteger hook installed")
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
                    instance.forceEnableAdd("updateAddPreference")
                }
            }
            method {
                name = "onResume"
                emptyParam()
            }.hook {
                after {
                    instance.callOrNull("updateAddPreference")
                    instance.forceEnableAdd("onResume")
                }
            }
            method {
                name = "handleFingerprintsUpdated"
                param(List::class)
            }.hook {
                after {
                    instance.forceEnableAdd("handleFingerprintsUpdated")
                }
            }
        }
    }
}

private fun Any?.forceEnableAdd(from: String) {
    if (this == null) return
    val pref = fieldOrNull("mAddFingerPreference")
        ?: callOrNull("findPreference", "key_fingerprint_add")
    if (pref == null) {
        logWarn("settings[$from]: add-fingerprint preference not found")
        return
    }
    pref.callOrNull("setEnabled", true)
    pref.callOrNull("setSummary", "")
    logInfo("settings[$from]: add-fingerprint button forced enabled")
}
