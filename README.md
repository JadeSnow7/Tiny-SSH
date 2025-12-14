# Tiny-SSH / 掌上终端

[中文](#中文) | [English](#english)

## 中文

### 项目简介

Tiny-SSH（掌上终端）是一款轻量、现代的 Android SSH/SFTP 客户端，基于 Jetpack Compose 构建，用于在移动端便捷管理远程服务器。

### 功能

- SSH 终端
  - 使用 IP/用户名/密码连接标准 SSH 服务器。
  - 提供可交互的 Shell 终端，支持实时执行命令与查看输出。
- SFTP 文件管理器
  - 图形化浏览远程文件系统，支持目录导航。
  - 文件下载到设备公共“下载”目录；从设备上传文件到远端当前目录。
  - 创建目录、删除（带确认）、重命名等常用管理能力。
- 远程文件编辑
  - 在应用内预览文本文件。
  - 在线编辑并保存回服务器。

### 技术栈与架构

- UI：Jetpack Compose
- 架构：MVVM（Model-View-ViewModel）
  - View：`MainActivity.kt`（UI 渲染与事件派发）
  - ViewModel：`SshViewModel.kt`（`StateFlow` 管理 UI 状态与业务逻辑）
  - Repository：`SshRepository.kt`（封装 SSH/SFTP 数据操作；协程/Flow 异步）
- 异步：Kotlin Coroutines + Flow（网络操作放在 IO 线程，避免阻塞 UI）
- 协议库：JSch（Java Secure Channel）
- 兼容性：`minSdk 24`，`targetSdk 36`

### 构建与运行

1. 克隆仓库：
   ```sh
   git clone https://github.com/JadeSnow7/Tiny-SSH.git
   ```
2. 使用 Android Studio 打开项目并等待 Gradle Sync 完成。
3. 运行到模拟器或真机，或使用命令行构建：
   ```sh
   ./gradlew assembleDebug
   ```

### 文档

- 产品报告（Markdown）：`产品报告.md`
- 产品报告（PDF）：`产品报告.pdf`
- 架构图（SVG）：`产品报告-架构图.svg`（对应 Mermaid 源码：`产品报告-架构图.mmd`）

### 说明

本项目为课程作业的一部分。

---

## English

### Overview

Tiny-SSH is a lightweight, modern SSH and SFTP client for Android built with Jetpack Compose, designed for managing remote servers on the go.

### Features

- SSH Terminal
  - Connect to any standard SSH server using IP/username/password.
  - Fully interactive shell terminal for executing commands in real time.
- SFTP File Manager
  - Browse remote file systems with a clean graphical interface and seamless directory navigation.
  - Download remote files to the device “Download” folder, and upload local files to the current remote directory.
  - Create directories, delete with confirmation, and rename files/folders.
- File Editor
  - Preview text-based files within the app.
  - Edit file content and save changes back to the server.

### Tech Stack & Architecture

- UI: Jetpack Compose
- Architecture: MVVM (Model-View-ViewModel)
  - View: `MainActivity.kt` (UI rendering and event forwarding)
  - ViewModel: `SshViewModel.kt` (UI state via `StateFlow` + business logic)
  - Repository: `SshRepository.kt` (SSH/SFTP data access; Coroutines/Flow)
- Async: Kotlin Coroutines + Flow for all network operations (non-blocking UI)
- SSH/SFTP: JSch (Java Secure Channel)
- Compatibility: `minSdk 24`, `targetSdk 36`

### Setup & Build

1. Clone the repository:
   ```sh
   git clone https://github.com/JadeSnow7/Tiny-SSH.git
   ```
2. Open the project in Android Studio and wait for Gradle sync.
3. Build and run on an emulator or a physical device, or build from CLI:
   ```sh
   ./gradlew assembleDebug
   ```

### Docs

- Product report (Markdown): `产品报告.md`
- Product report (PDF): `产品报告.pdf`
- Architecture diagram (SVG): `产品报告-架构图.svg` (Mermaid source: `产品报告-架构图.mmd`)

### Note

This project was developed as part of a course assignment.
