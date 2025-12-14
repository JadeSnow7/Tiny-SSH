package com.example.myapplication

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- UI State Data Classes ---
data class LoginUiState(
    val host: String = "106.54.188.236",
    val username: String = "snow",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class TerminalUiState(
    val output: List<String> = emptyList(),
    val command: String = ""
)

data class SftpUiState(
    val currentPath: String = ".",
    val files: List<SftpFile> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

data class EditorUiState(
    val content: String = "",
    val isLoading: Boolean = true
)

class SshViewModel : ViewModel() {

    private val sshRepository = SshRepository()

    // --- Global State ---
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // --- Login Screen State ---
    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    // --- Terminal Screen State ---
    private val _terminalUiState = MutableStateFlow(TerminalUiState())
    val terminalUiState: StateFlow<TerminalUiState> = _terminalUiState.asStateFlow()

    // --- SFTP Screen State ---
    private val _sftpUiState = MutableStateFlow(SftpUiState())
    val sftpUiState: StateFlow<SftpUiState> = _sftpUiState.asStateFlow()

    // --- Editor Screen State ---
    private val _editorUiState = MutableStateFlow(EditorUiState())
    val editorUiState: StateFlow<EditorUiState> = _editorUiState.asStateFlow()

    // --- Snackbar messages ---
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        sshRepository.disconnect()
    }

    fun onSnackbarShown() {
        _snackbarMessage.value = null
    }

    // --- Login Actions ---
    fun onLoginInfoChange(host: String, username: String, pass: String) {
        _loginUiState.update { it.copy(host = host, username = username, password = pass) }
    }

    fun connect() {
        _loginUiState.update { it.copy(isLoading = true, errorMessage = null) }
        val state = _loginUiState.value
        sshRepository.connect(state.username, state.host, password = state.password) { result ->
            _loginUiState.update { it.copy(isLoading = false) }
            result.onSuccess {
                _isConnected.value = true
                startShellSession()
                loadSftpFiles(".")
            }
            result.onFailure {
                _loginUiState.update { loginState -> loginState.copy(errorMessage = it.message) }
            }
        }
    }

    fun disconnect() {
        sshRepository.disconnect()
        _isConnected.value = false
    }

    // --- Terminal Actions ---
    private fun startShellSession() {
        viewModelScope.launch {
            sshRepository.startShell()
                .catch { e ->
                    _terminalUiState.update { it.copy(output = it.output + "\nError: ${e.message}") }
                }
                .collect { output ->
                    _terminalUiState.update { it.copy(output = it.output + output) }
                }
        }
    }

    fun onCommandChange(newCommand: String) {
        _terminalUiState.update { it.copy(command = newCommand) }
    }

    fun sendCommandToShell() {
        val command = _terminalUiState.value.command
        sshRepository.sendCommandToShell(command)
        _terminalUiState.update { it.copy(command = "") } // Clear input after sending
    }

    // --- SFTP Actions ---
    fun loadSftpFiles(path: String) {
        _sftpUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            sshRepository.listRemoteFiles(path)
                .catch { e ->
                    _sftpUiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
                .collect { files ->
                     _sftpUiState.update { 
                        it.copy(
                            isLoading = false, 
                            files = files.sortedWith(compareBy({ !it.isDirectory }, { it.name })),
                            currentPath = path
                        )
                     }
                }
        }
    }

    fun downloadFile(context: Context, file: SftpFile) {
        _snackbarMessage.value = "Downloading ${file.name}..."
        sshRepository.downloadFile(context, file.path, file.name) { result ->
            result.onSuccess { _snackbarMessage.value = it }
            result.onFailure { _snackbarMessage.value = "Download failed: ${it.message}" }
        }
    }

    fun uploadFile(context: Context, uri: Uri) {
        val path = _sftpUiState.value.currentPath
        _snackbarMessage.value = "Uploading..."
        sshRepository.uploadFile(context, uri, path) { result ->
            result.onSuccess {
                _snackbarMessage.value = "File uploaded successfully"
                loadSftpFiles(path)
            }
            result.onFailure {
                _snackbarMessage.value = "Upload failed: ${it.message}"
            }
        }
    }

    fun createDirectory(dirName: String) {
        val path = _sftpUiState.value.currentPath
        sshRepository.createDirectory(path, dirName) { result ->
            result.onSuccess {
                _snackbarMessage.value = "Directory '$dirName' created"
                loadSftpFiles(path)
            }
            result.onFailure {
                _snackbarMessage.value = "Error: ${it.message}"
            }
        }
    }

    fun deletePath(file: SftpFile) {
        sshRepository.deleteRemotePath(file) { result ->
            result.onSuccess {
                _snackbarMessage.value = "Deleted: ${file.name}"
                loadSftpFiles(_sftpUiState.value.currentPath)
            }
            result.onFailure {
                _snackbarMessage.value = "Error deleting ${file.name}: ${it.message}"
            }
        }
    }

    fun renamePath(file: SftpFile, newName: String) {
        sshRepository.renameRemotePath(file, newName) { result ->
            result.onSuccess {
                _snackbarMessage.value = "Renamed to '$newName'"
                loadSftpFiles(_sftpUiState.value.currentPath)
            }
            result.onFailure {
                _snackbarMessage.value = "Error renaming: ${it.message}"
            }
        }
    }
    
    // --- Editor Actions ---
    fun readFileContent(filePath: String) {
        _editorUiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            sshRepository.readFileContent(filePath)
                .catch { e -> 
                    _editorUiState.update { it.copy(isLoading = false, content = "Error: ${e.message}") }
                }
                .collect { content ->
                    _editorUiState.update { it.copy(isLoading = false, content = content) }
                }
        }
    }

    fun onFileContentChange(newContent: String) {
        _editorUiState.update { it.copy(content = newContent) }
    }

    fun saveFileContent(file: SftpFile) {
        val content = _editorUiState.value.content
        _snackbarMessage.value = "Saving..."
        sshRepository.writeFileContent(file.path, content) { result ->
            result.onSuccess { 
                _snackbarMessage.value = "File saved successfully"
            }
            result.onFailure {
                _snackbarMessage.value = "Save failed: ${it.message}"
            }
        }
    }
}
