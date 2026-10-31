package com.hanson.fplimit.hooks

import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.BooleanType

private const val TAG = "FPLimit"

/**
 * 底层：system_server（包名 android）
 *
 * Android 16 / ColorOS 16 里已经没有 FingerprintService#getMaxFingerprintsForUser，
 * 真正的 Java 侧闸门在：
 *
 *   com.android.server.biometrics.sensors.fingerprint.aidl.FingerprintEnrollClient
 *       .hasReachedEnrollmentLimit(): Boolean
 *     -> getBiometricsForUser(ctx, userId).size >= mMaxTemplatesPerUser
 *
 * 父类 EnrollClient 在开始 enroll 前会调用它，命中就整场录入直接失败。
 * 这里让它永远返回 false，把上限判断交给底层 HAL/TEE（那才是真·硬件限制）。
 */
class SystemHook : YukiBaseHooker() {

    override fun onHook() = loadSystem {
        listOf(
            "com.android.server.biometrics.sensors.fingerprint.aidl.FingerprintEnrollClient",
            "com.android.server.biometrics.sensors.fingerprint.hidl.FingerprintEnrollClient"
        ).forEach { className ->
            val clazz = className.toClassOrNull()
            if (clazz == null) {
                Log.w(TAG, "system: $className not found")
                return@forEach
            }
            Log.i(TAG, "system: hooking $className#hasReachedEnrollmentLimit")
            clazz.apply {
                method {
                    name = "hasReachedEnrollmentLimit"
                    emptyParam()
                    returnType = BooleanType
                }.hook {
                    replaceToFalse()
                }
            }
        }
    }
}
