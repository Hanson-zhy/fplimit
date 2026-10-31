package com.hanson.fplimit.hooks

import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.hanson.fplimit.util.callOrNull
import com.hanson.fplimit.util.fieldOrNull

private const val TAG = "FPLimit"

/**
 * UI 层：com.android.settings
 *
 * ColorOS 的指纹设置页里，"添加指纹" 按钮的可用性完全由
 * OplusFingerprintSettings$FingerprintSettingsFragment#updateAddPreference() 决定：
 *
 *   max   = resources.getInteger(config_fingerprintMaxTemplatesPerUser)   // 本机 = 5
 *   count = FingerprintUtils.getFingersCount()
 *   enabled = count < max
 *   summary = count >= max ? "最多可添加 N 个指纹" : ""
 *
 * 我们在该方法执行之后再兜一次：强制 setEnabled(true) 并清空提示文案。
 */
class SettingsHook : YukiBaseHooker() {

    override fun onHook() = loadApp(name = "com.android.settings") {
        val className =
            "com.oplus.settings.feature.fingerprint.OplusFingerprintSettings\$FingerprintSettingsFragment"
        val fragment = className.toClassOrNull()
        if (fragment == null) {
            Log.w(TAG, "settings: $className not found, skip")
            return@loadApp
        }
        Log.i(TAG, "settings: hooking $className")

        fragment.apply {
            // 每次刷新指纹列表都会走到这里（handleMessage -> updateAddPreference）
            method {
                name = "updateAddPreference"
                emptyParam()
            }.hook {
                after {
                    instance.forceEnableAdd()
                }
            }

            // 进入页面时再补一次，避免列表刷新时机早于 fragment 初始化
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
        Log.w(TAG, "settings: add-fingerprint preference not found")
        return
    }
    pref.callOrNull("setEnabled", true)
    pref.callOrNull("setSummary", "")
    Log.i(TAG, "settings: add-fingerprint button forced enabled")
}
