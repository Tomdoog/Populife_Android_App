# Android 16 / API 36 适配

更新日期：2026-09-10。

Google Play 的新应用及更新从 2026-08-31 起要求 targetSdk 36；这不要求提高最低安装版本。本次 compileSdk / targetSdk 升至 36，minSdk 保持 21。

## 改动

- 所有 Android 模块统一 compileSdk / targetSdk 36。
- Android Gradle Plugin 升至 8.10.1，Gradle Wrapper 升至 8.11.1，满足 API 36 构建要求。
- 在 BaseActivity 中为 Android 13 及以上注册 OnBackInvokedCallback，将系统返回交给各页面现有的 onBackPressed 实现，保留设置页返回结果、首页二次返回退出等逻辑；销毁时解除注册。
- Manifest 显式启用新返回分发。此桥接保留旧返回行为，不提供跨页面预测返回预览动画。
- 沿用现有系统栏 inset 处理，无需设置已在 targetSdk 36 失效的 edge-to-edge 退出选项。

## 验证

执行并通过：

```sh
./gradlew :app:assembleDomesticDebug :app:assembleOverseasDebug :app:bundleOverseasRelease
```

- 国内、海外 Debug APK 及海外 Release AAB 构建成功，Release lintVital 通过。
- 海外 AAB 签名校验通过；产物 Manifest 确认为 minSdk 21、targetSdk 36。
- 海外 Debug APK 通过 `zipalign -c -P 16 4`；海外 AAB 中全部 8 个 ARM64 原生库的 ELF LOAD 段对齐至少 16 KB。
- 国内 Debug APK 已覆盖安装至 OnePlus PLQ110（Android 16 / API 36），保留现有应用数据。
- 真机验证：添加设备页系统栏布局正常；详情返回到首页、添加页边缘返回、首页首次返回保持页面均正常；新建空间输入框首次返回收起键盘并保留页面，第二次返回退出页面。测试未创建空间。

验证边界：真机为 4 KB 页大小，16 KB 仅完成产物静态检查；未完成平板/折叠屏、旧 Android 系统、蓝牙开锁及所有业务流程回归。详情请求曾出现网络超时，需网络恢复后补测。版本已递增为 3.5.6 / 356，本次未执行商店发布。

## 官方依据

- [Google Play 目标 API 要求](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Android 16：以 API 36 为目标的行为变更](https://developer.android.com/about/versions/16/behavior-changes-16)
- [系统返回手势迁移](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
- [AGP 8.10 兼容要求](https://developer.android.com/build/releases/agp-8-10-0-release-notes)
