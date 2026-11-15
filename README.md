# Octopus App

> 为 [Octopus](https://github.com/bestruirui/octopus)（LLM API 聚合网关）打造的**原生 Android 壳子**：
> 多站点管理、每站点多账号、登录态独立持久、沉浸式全屏体验。

[English](#english) | 简体中文

---

## 🎯 项目简介

Octopus 是一个「一个 Hub 聚合所有 LLM API」的网关，通常以网页形式部署使用。本 App 把网页面板封装成 Android 原生应用：

- 打开 App 即进入你设置的**默认站点**（不写死任何地址）
- 每个站点可以管理**多个账号**，每个账号有独立的登录快照
- 切换站点/账号互不干扰，登录态长期有效
- 原生沉浸式体验：无地址栏、返回键/手势、启动页、错误页、上传下载

## ✨ 功能特性

- **多站点管理**：添加 / 编辑 / 删除站点，任意设置默认站点，首启自动引导
- **多账号管理**：每个站点可添加多个账号（名称 / 用户名 / 密码），账号间独立登录
- **登录态持久化**：每个账号保存独立的登录快照（localStorage + Cookie），切换账号一键恢复
- **密码同步**：修改账号密码后，旧登录态自动作废，站点强制用新密码重新登录
- **沉浸式 UI**：edge-to-edge 全屏（适配 Android 15/16/17）、系统返回键/手势、启动页、加载进度、断网错误页
- **安全链接处理**：外站链接交给系统浏览器，站点内链接在 App 中打开
- **文件上传/下载**：网页文件选择器与系统下载管理器均已接入
- **无写死地址**：代码与资源中不包含任何预置站点
- **Android 17**：`compileSdk/targetSdk 37`

## 📸 截图

*（本仓库暂未上传真机截图，欢迎补充）*

应用图标（来自 Octopus 项目 Logo，自适应 + 深浅色 + 圆形）：

![App icon](docs/app-icon.png)

## 📦 安装

1. 在 **Releases** 页面下载 `Octopus-v1.2.apk`
2. 拷贝到手机后安装（首次安装需开启"允许未知来源"）
3. 首次打开 → 进入"站点管理" → 添加站点 → 设为默认站点 → 保存

> ⚠️ 升级安装请使用**同一签名**的 APK，否则无法覆盖安装。

## 🛠 构建

### 环境要求

- JDK 17
- Android SDK（Platform 37 / Build-Tools 37）
- Gradle 8.13（项目自带 wrapper）

### 步骤

```bash
# 1. 配置签名（可选，用于生成可安装的 release APK）
cp keystore.properties.example keystore.properties
# 编辑 keystore.properties 填入你的 keystore 信息

# 2. 构建
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

> 没有 `keystore.properties` 时，release 构建不签名（用于 CI 验证）。

## 🧩 核心实现

### 多站点
- `SiteStore`：SharedPreferences + JSON 持久化，字段 `id / name / url / isDefault`
- 站点列表可增删改；默认站点每次冷启动读取，修改后立即生效

### 多账号与登录态隔离
- `AccountStore`：按站点保存账号及登录快照
- 每个账号保存 `localStorageJson + cookies` 两份快照
- 切换账号时：先保存当前账号快照 → 清空站点 Cookie → 写入目标账号快照 → 页面加载完成后注入 localStorage 并重载
- 所有站点共用一个 WebView 实例，切换站点/账号不会清空任何站点数据

### 密码修改同步
- 在账号编辑页修改密码后：清空该账号登录快照
- 若为当前激活账号：标记 `pendingLogout`，回到主界面自动清空站点登录态并重载，强制用新密码登录

### 沉浸式
- `setDecorFitsSystemWindows(false)` + `OnApplyWindowInsetsListener`：状态栏/导航栏/输入法/刘海统一内边距
- `enableOnBackInvokedCallback` + 自定义返回逻辑：先网页后退，回到根页面"再按一次退出"

## ❓ 常见问题

**Q：切换账号时页面会刷新吗？**
A：会。切换账号需要注入目标账号的登录快照，因此页面会重新加载一次。

**Q：站点地址写死在代码里吗？**
A：没有。代码与资源中不包含任何预置站点，全部由你在应用中添加。

**Q：账号密码存储在哪里？安全吗？**
A：保存在 Android 应用沙盒的 SharedPreferences 中，仅本 App 可访问。若担心安全，请勿在本设备安装其他来源的同名应用。

**Q：支持哪些 Android 版本？**
A：Android 7.0（API 24）及以上，并针对 Android 17（API 37）做了适配。

## ⚠️ 免责声明

本项目为**第三方壳子应用**，非 Octopus 官方项目，仅供学习与个人使用。Octopus 的版权归原作者所有。

## 📄 License

[MIT](LICENSE)

## 🙏 致谢

- [Octopus](https://github.com/bestruirui/octopus) —— 一个 Hub 聚合所有 LLM API
- Android 开发社区

---

## English

**Octopus App** is a native Android shell for the [Octopus](https://github.com/bestruirui/octopus) gateway.

**Features**
- Multi-site management (add / edit / delete, set any site as default)
- Multi-account per site, each with an independent login snapshot (localStorage + cookies)
- Password change sync: old login is revoked, site forces re-login with the new password
- Immersive edge-to-edge UI (Android 15/16/17), no address bar, back gesture support
- First-run setup wizard; no site URL is hard-coded

**Build**

```bash
cp keystore.properties.example keystore.properties
./gradlew :app:assembleRelease
```

**License**: MIT
