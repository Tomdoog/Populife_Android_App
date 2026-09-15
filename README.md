# Populife Android（旧版）

旧 Populife Android 历史仓库，仅用于维护仍在使用的旧产品或核对历史业务。它不是 Populife Pro Android，新产品不得从本仓复制账号、设备兼容、Redis 直连、API、安全配置或部署方式。

## 仓库状态

- Codeup 为 `origin`，GitHub 为备用 `github` remote。
- 默认只读；旧产品修复和发布必须由用户单独授权。
- Populife Pro Android 位于兄弟仓 `populife-android-pro`。
- 跨仓产品与契约文档位于 `populife-shared`。

## 安全

签名文件、密码、服务端凭据和生产配置必须在仓库外安全管理，不在 README、源码、提交记录或聊天中保存。历史内容即使仍可运行，也不能视为当前安全基线。

## AI 与开发入口

AI 任务先读 [CLAUDE.md](CLAUDE.md)。如确需维护旧 APP，再基于当前分支核对实际 Gradle、JDK、签名和发布环境；不要把旧构建成功描述成 Pro 验收。
