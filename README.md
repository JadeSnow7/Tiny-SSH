# Tiny-SSH

A lightweight, modern SSH and SFTP client for Android, built entirely with Jetpack Compose. This project serves as a comprehensive mobile solution for managing remote servers on the go.

## ✨ Features

-   **SSH Terminal**:
    -   Connect to any standard SSH server using username and password.
    -   Fully interactive shell terminal for executing commands in real-time.
-   **SFTP File Manager**:
    -   Browse remote file systems with a clean, intuitive graphical interface.
    -   Navigate through directories seamlessly.
    -   **Download** any remote file directly to the device's "Download" folder.
    -   **Upload** files from the device to the current remote directory.
    -   **Create** new directories.
    -   **Delete** files and folders with a confirmation dialog.
    -   **Rename** files and folders.
-   **File Editor**:
    -   **Preview** text-based files directly within the app.
    -   **Edit** file content and save changes back to the server.

## 🚀 Tech Stack & Architecture

-   **UI**: 100% built with **Jetpack Compose**, following modern Android UI development practices.
-   **Architecture**: Implemented the **MVVM (Model-View-ViewModel)** architecture for a clear separation of concerns, improved scalability, and testability.
    -   **View (`MainActivity.kt`)**: Renders the UI and delegates user actions to the ViewModel.
    -   **ViewModel (`SshViewModel.kt`)**: Manages all UI state using `StateFlow` and handles all business logic.
    -   **Repository (`SshRepository.kt`)**: Encapsulates data operations, providing a clean API for the ViewModel. It uses Kotlin Coroutines and Flow for asynchronous tasks.
-   **Asynchronous Operations**: Uses **Kotlin Coroutines** and **Flow** to handle all network operations, ensuring a smooth, non-blocking user experience.
-   **SSH/SFTP Protocol**: Leverages the robust and mature **JSch (Java Secure Channel)** library for all backend connections.

## 🛠️ Setup & Build

1.  Clone the repository:
    ```sh
    git clone https://github.com/JadeSnow7/Tiny-SSH.git
    ```
2.  Open the project in Android Studio.
3.  Let Gradle sync the dependencies.
4.  Build and run the application on an emulator or a physical device.

---

*This project was developed as part of a course assignment.*
