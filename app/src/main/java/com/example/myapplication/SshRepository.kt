package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

// Data class for SFTP files/directories
data class SftpFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedTime: String
)

class SshRepository {

    private var session: Session? = null

    // Shell related properties
    private var shellChannel: ChannelShell? = null
    private var shellOutputStream: OutputStream? = null

    fun connect(
        user: String,
        host: String,
        port: Int = 22,
        password: String,
        onResult: (Result<String>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsch = JSch()
                session = jsch.getSession(user, host, port)
                session?.setPassword(password)

                // Important: This is for testing purposes only.
                // In a real app, you should use a KnownHosts file.
                session?.setConfig("StrictHostKeyChecking", "no")

                session?.connect(30000) // 30 second timeout

                if (session?.isConnected == true) {
                    withContext(Dispatchers.Main) {
                        onResult(Result.success("Connection successful"))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(Result.failure(Exception("Connection failed")))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(e))
                }
            }
        }
    }

    // --- Shell (Terminal Emulator) Functions ---

    fun startShell(): Flow<String> = flow {
        if (session?.isConnected != true) {
            throw IllegalStateException("Not connected")
        }
        shellChannel = (session?.openChannel("shell") as? ChannelShell)?.also {
            val inputStream = it.inputStream
            shellOutputStream = it.outputStream
            it.connect(15000)

            try {
                val buffer = ByteArray(1024)
                while (it.isConnected) {
                    val readBytes = inputStream.read(buffer)
                    if (readBytes == -1) break
                    emit(String(buffer, 0, readBytes, Charsets.UTF_8))
                }
            } finally {
                closeShell()
            }
        }
    }.flowOn(Dispatchers.IO)

    fun sendCommandToShell(command: String) {
        val fullCommand = "$command\n"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                shellOutputStream?.write(fullCommand.toByteArray(Charsets.UTF_8))
                shellOutputStream?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun closeShell() {
        shellChannel?.disconnect()
        shellOutputStream = null
        shellChannel = null
    }

    // --- SFTP (File Manager) Functions ---

    private fun openSftpChannel(): ChannelSftp? {
        if (session?.isConnected != true) return null
        return (session?.openChannel("sftp") as? ChannelSftp)?.apply { connect() }
    }

    fun listRemoteFiles(remotePath: String): Flow<List<SftpFile>> = flow {
        var sftpChannel: ChannelSftp? = null
        try {
            sftpChannel = openSftpChannel() ?: throw IllegalStateException("Cannot open SFTP channel")

            @Suppress("UNCHECKED_CAST")
            val vector = sftpChannel.ls(remotePath) as java.util.Vector<ChannelSftp.LsEntry>

            val files = vector.mapNotNull { entry ->
                if (entry.filename == "." || entry.filename == "..") {
                    null
                } else {
                    SftpFile(
                        name = entry.filename,
                        path = if (remotePath.endsWith("/")) "${remotePath}${entry.filename}" else "${remotePath}/${entry.filename}",
                        isDirectory = entry.attrs.isDir,
                        size = entry.attrs.size,
                        modifiedTime = entry.attrs.mtimeString
                    )
                }
            }
            emit(files)
        } finally {
            sftpChannel?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    fun downloadFile(context: Context, remotePath: String, fileName: String, onResult: (Result<String>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var sftpChannel: ChannelSftp? = null
            try {
                sftpChannel = openSftpChannel() ?: throw IllegalStateException("SFTP channel not available")

                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream") // Generic mime type
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
                    }
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        sftpChannel.get(remotePath, outputStream)
                    }
                    withContext(Dispatchers.Main) {
                        onResult(Result.success("File '$fileName' downloaded to Downloads folder"))
                    }
                } ?: throw Exception("Failed to create file in Downloads folder")

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(e))
                }
            } finally {
                sftpChannel?.disconnect()
            }
        }
    }

    fun uploadFile(context: Context, localFileUri: Uri, remotePath: String, onResult: (Result<Unit>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var sftpChannel: ChannelSftp? = null
            try {
                sftpChannel = openSftpChannel() ?: throw IllegalStateException("SFTP channel not available")

                val resolver = context.contentResolver
                val fileName = resolver.query(localFileUri, null, null, null, null)?.use {
                    it.moveToFirst()
                    it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                } ?: "upload.tmp"

                resolver.openInputStream(localFileUri)?.use {
                    sftpChannel.put(it, "$remotePath/$fileName")
                }

                withContext(Dispatchers.Main) {
                    onResult(Result.success(Unit))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(e))
                }
            } finally {
                sftpChannel?.disconnect()
            }
        }
    }
    
    fun createDirectory(remotePath: String, dirName: String, onResult: (Result<Unit>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var sftpChannel: ChannelSftp? = null
            try {
                sftpChannel = openSftpChannel() ?: throw IllegalStateException("SFTP channel not available")
                val newDirPath = if (remotePath.endsWith("/")) "$remotePath$dirName" else "$remotePath/$dirName"
                sftpChannel.mkdir(newDirPath)
                withContext(Dispatchers.Main) {
                    onResult(Result.success(Unit))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(e))
                }
            } finally {
                sftpChannel?.disconnect()
            }
        }
    }

    fun deleteRemotePath(file: SftpFile, onResult: (Result<Unit>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var sftpChannel: ChannelSftp? = null
            try {
                sftpChannel = openSftpChannel() ?: throw IllegalStateException("SFTP channel not available")
                if (file.isDirectory) {
                    sftpChannel.rmdir(file.path)
                } else {
                    sftpChannel.rm(file.path)
                }
                withContext(Dispatchers.Main) {
                    onResult(Result.success(Unit))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(e))
                }
            } finally {
                sftpChannel?.disconnect()
            }
        }
    }

    fun renameRemotePath(file: SftpFile, newName: String, onResult: (Result<Unit>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var sftpChannel: ChannelSftp? = null
            try {
                sftpChannel = openSftpChannel() ?: throw IllegalStateException("SFTP channel not available")
                val newPath = file.path.substringBeforeLast('/') + "/" + newName
                sftpChannel.rename(file.path, newPath)
                withContext(Dispatchers.Main) {
                    onResult(Result.success(Unit))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(e))
                }
            } finally {
                sftpChannel?.disconnect()
            }
        }
    }
    
    fun readFileContent(remotePath: String): Flow<String> = flow {
        var sftpChannel: ChannelSftp? = null
        try {
            sftpChannel = openSftpChannel() ?: throw IllegalStateException("SFTP channel not available")
            val inputStream = sftpChannel.get(remotePath)
            val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            emit(content)
        } finally {
            sftpChannel?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    fun writeFileContent(remotePath: String, content: String, onResult: (Result<Unit>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var sftpChannel: ChannelSftp? = null
            try {
                sftpChannel = openSftpChannel() ?: throw IllegalStateException("SFTP channel not available")
                sftpChannel.put(content.byteInputStream(Charsets.UTF_8), remotePath)
                withContext(Dispatchers.Main) {
                    onResult(Result.success(Unit))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(e))
                }
            } finally {
                sftpChannel?.disconnect()
            }
        }
    }

    fun disconnect() {
        closeShell()
        session?.disconnect()
        session = null
    }
}
