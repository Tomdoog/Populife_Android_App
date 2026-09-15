# CLAUDE.md（populife-android-old）

This file provides guidance to Claude Code when working in this legacy repository.

## 定位

旧 Populife Android 历史仓，默认只读。Codeup 是 `origin`，GitHub 是备用 `github` remote。本仓不是 Populife Pro Android，不参与新产品构建、契约或发布。

## 规则

- 普通 Pro 任务不得修改本仓；只在需要核对历史业务、API 或失败恢复行为时读取。
- 不把旧账号、Redis 直连、旧设备、旧 API、固定密钥、日志方式或兼容逻辑复制到 Pro。
- 仓内历史文档可能含过时配置或敏感值，不读取、复述、提交或传播这些值。
- 若用户明确要求维护旧 APP，先重新读取当前分支、README、Gradle/JDK 基线和发布要求，并把它作为独立任务处理。
- 未经明确授权，不 commit、不 push、不构建发布包、不操作商店。
