package com.hanson.fplimit.util

import android.util.Log
import com.highcapable.yukihookapi.hook.log.YLog

private const val TAG = "FPLimit"

fun logInfo(msg: String) {
    Log.i(TAG, msg)
    runCatching { YLog.info(msg) }
}

fun logWarn(msg: String) {
    Log.w(TAG, msg)
    runCatching { YLog.warn(msg) }
}

/**
 * 反射工具：按名字找字段（含父类链），找不到返回 null，绝不抛异常。
 * 自己实现而不是依赖 Hook 框架的扩展函数，避免 API 版本漂移导致编译失败。
 */
fun Any?.fieldOrNull(name: String): Any? {
    val target = this ?: return null
    var c: Class<*>? = target.javaClass
    while (c != null) {
        try {
            val f = c.getDeclaredField(name)
            f.isAccessible = true
            return f.get(target)
        } catch (_: NoSuchFieldException) {
            c = c.superclass
        } catch (t: Throwable) {
            logWarn("fieldOrNull($name) failed: ${t.javaClass.simpleName}: ${t.message}")
            return null
        }
    }
    return null
}

/**
 * 反射调用无重载歧义的方法（按名字 + 参数个数匹配，含父类链）。
 */
fun Any?.callOrNull(name: String, vararg args: Any?): Any? {
    val target = this ?: return null
    var c: Class<*>? = target.javaClass
    while (c != null) {
        val m = c.declaredMethods.firstOrNull { it.name == name && it.parameterCount == args.size }
        if (m != null) {
            return try {
                m.isAccessible = true
                m.invoke(target, *args)
            } catch (t: Throwable) {
                logWarn("callOrNull($name) failed: ${t.javaClass.simpleName}: ${t.message}")
                null
            }
        }
        c = c.superclass
    }
    logWarn("callOrNull($name) not found")
    return null
}

fun Class<*>?.callStaticOrNull(name: String, vararg args: Any?): Any? {
    val target = this ?: return null
    val m = target.declaredMethods.firstOrNull { it.name == name && it.parameterCount == args.size }
        ?: return null
    return try {
        m.isAccessible = true
        m.invoke(null, *args)
    } catch (t: Throwable) {
        logWarn("callStaticOrNull($name) failed: ${t.javaClass.simpleName}: ${t.message}")
        null
    }
}
