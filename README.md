# FPLimit · 指纹上限解锁

独立 LSPosed 模块（包名 `com.hanson.fplimit`），用于解除 ColorOS / OPlus 机型「最多 5 枚指纹」的限制，
可与 OShin 共存，不需要修改或跟随 OShin 的更新。

## 原理

| 层 | 位置 | 原始逻辑 | 本模块 |
|---|---|---|---|
| UI | `com.oplus.settings.feature.fingerprint.OplusFingerprintSettings$FingerprintSettingsFragment#updateAddPreference()` | `count >= config_fingerprintMaxTemplatesPerUser(5)` 时把「添加指纹」置灰并提示上限 | 执行后强制 `setEnabled(true)`、清空上限提示 |
| system_server | `com.android.server.biometrics.sensors.fingerprint.aidl.FingerprintEnrollClient#hasReachedEnrollmentLimit()` | 已录入数 `>= mMaxTemplatesPerUser` 时整场录入直接失败 | 恒返回 `false`，把上限判断交给 HAL/TEE |

> 说明：Android 16 / ColorOS 16 中并不存在 `FingerprintService#getMaxFingerprintsForUser`，
> 真正的 Java 侧闸门是上面的 `hasReachedEnrollmentLimit()`。

## 使用

1. 安装 APK（release 已用 debug key 签名，可直接安装）。
2. LSPosed 中启用模块，作用域勾选 **系统框架（android）** 与 **设置（com.android.settings）**。
3. 重启手机（system_server 需要重启才能生效）。
4. 设置 → 指纹：按钮应保持可用，尝试录入第 6 枚。

## 实测结论（2026-11-01，一加 Ace 5 竞速版 / PLF110_16.0.10.501 / Goodix 光学 UFF）

模块生效后，UI 与 system_server 两道软件闸门都被放行（按钮可用、录入可以真正开始），
但录入到最后一帧时 **TEE（Trustonic TA）拒绝保存模板**：

```
I/[FP_HAL][IFingerprintCore] [enrollImage] cmd.result.remaining =0, fid = 1904359103
E/[FP_HAL][TrustonicCa]     [sendCommand] ta send value.b = 259
E/[FP_HAL][IFingerprintCore] enrollSaveTemplate:256: result:259
E/[FP_HAL][IFingerprintCore] [enrollSaveTemplate] exit. errno=259
I/[FP_HAL][OpticalCore]      [doTouchDownEvent] not woking mode
```

即：**5 枚上限不是软件限制，而是 TA 里写死的模板槽位数**（采集阶段的 rem 能从 9 数到 0，
说明算法层面接受第 6 枚，但落盘时 TA 返回 259，模板最终没有写入，`dumpsys fingerprint` 仍是 `count:5`）。

所以本模块能做到「把门打开」，但挡在最后一步的是 TEE，无法从 Android 侧绕过。

## 日志

```bash
adb logcat -s FPLimit
```

## 构建

GitHub Actions：`.github/workflows/build.yml`，push 到 `main` 或手动 `workflow_dispatch` 即可，
产物为 `fplimit-apk` artifact（`app-release.apk`）。
