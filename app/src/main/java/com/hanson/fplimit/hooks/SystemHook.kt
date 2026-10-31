package com.hanson.fplimit.hooks

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.hanson.fplimit.util.logInfo
import com.hanson.fplimit.util.logWarn

/**
 * 底层：system_server（包名 android）
 *
 * Android 16 / ColorOS 16 里没有 FingerprintService#getMaxFingerprintsForUser，
 * 真正的 Java 侧闸门在：
 *
 *   com.android.server.biometrics.sensors.fingerprint.aidl.FingerprintEnrollClient
 *       .hasReachedEnrollmentLimit(): Boolean
 *     -> getBiometricsForUser(ctx, userId).size >= mMaxTemplatesPerUser
 *
 * 父类 EnrollClient#start() 在开始 enroll 前调用它，命中就打日志
 * "Reached enrollment limit" 并直接结束整场录入。
 * 这里让它恒返回 false，把上限判断交给底层 HAL/TEE —— 那才是真·硬件限制。
 */
class SystemHook : YukiBaseHooker() {

    override fun onHook() = loadSystem {
        logInfo("system: module loaded in system_server, installing hooks")
        listOf(
            "com.android.server.biometrics.sensors.fingerprint.aidl.FingerprintEnrollClient",
            "com.android.server.biometrics.sensors.fingerprint.hidl.FingerprintEnrollClient"
        ).forEach { className ->
            val clazz = className.toClassOrNull()
            if (clazz == null) {
                logWarn("system: $className not found")
                return@forEach
            }
            try {
                clazz.apply {
                    method {
                        name = "hasReachedEnrollmentLimit"
                        emptyParam()
                        returnType = BooleanType
                    }.hook {
                        replaceToFalse()
                    }
                }
                logInfo("system: hooked $className#hasReachedEnrollmentLimit")
            } catch (t: Throwable) {
                logWarn("system: hook $className failed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }
}
