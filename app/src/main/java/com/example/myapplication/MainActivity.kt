package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                SshClientApp()
            }
        }
    }
}

@Composable
fun SshClientApp(sshViewModel: SshViewModel = viewModel()) {
    val isConnected by sshViewModel.isConnected.collectAsState()
    var fileToEdit by remember { mutableStateOf<SftpFile?>(null) }

    if (fileToEdit != null) {
        FileEditorScreen(sshViewModel, fileToEdit!!) { fileToEdit = null }
    } else if (!isConnected) {
        LoginScreen(sshViewModel)
    } else {
        MainScaffold(sshViewModel) { fileToEdit = it }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(sshViewModel: SshViewModel, onEditFile: (SftpFile) -> Unit) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.TERMINAL) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Observe snackbar messages from ViewModel
    val snackbarMessage by sshViewModel.snackbarMessage.collectAsState()
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            sshViewModel.onSnackbarShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("掌上终端") }, actions = {
                IconButton(onClick = { sshViewModel.disconnect() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Logout")
                }
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Computer, contentDescription = "Terminal") },
                    label = { Text("Terminal") },
                    selected = currentScreen == Screen.TERMINAL,
                    onClick = { currentScreen = Screen.TERMINAL }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = "SFTP") },
                    label = { Text("SFTP") },
                    selected = currentScreen == Screen.SFTP,
                    onClick = { currentScreen = Screen.SFTP }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.TERMINAL -> TerminalScreen(sshViewModel)
                Screen.SFTP -> SftpScreen(sshViewModel, onEditFile)
                else -> {}
            }
        }
    }
}

@Composable
fun LoginScreen(sshViewModel: SshViewModel) {
    val loginState by sshViewModel.loginUiState.collectAsState()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SSH/SFTP Client", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = loginState.host,
            onValueChange = { sshViewModel.onLoginInfoChange(it, loginState.username, loginState.password) },
            label = { Text("Host IP") },
            modifier = Modifier.fillMaxWidth(),
            isError = loginState.errorMessage != null
        )
        OutlinedTextField(
            value = loginState.username,
            onValueChange = { sshViewModel.onLoginInfoChange(loginState.host, it, loginState.password) },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
             isError = loginState.errorMessage != null
        )
        OutlinedTextField(
            value = loginState.password,
            onValueChange = { sshViewModel.onLoginInfoChange(loginState.host, loginState.username, it) },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
             isError = loginState.errorMessage != null
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (loginState.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { sshViewModel.connect() }, modifier = Modifier.fillMaxWidth()) {
                Text("Connect")
            }
        }

        loginState.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun TerminalScreen(sshViewModel: SshViewModel) {
    val terminalState by sshViewModel.terminalUiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(terminalState.output.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)) {
        Text(
            text = terminalState.output.joinToString(""),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
             fun send() = sshViewModel.sendCommandToShell()
            OutlinedTextField(
                value = terminalState.command,
                onValueChange = { sshViewModel.onCommandChange(it) },
                modifier = Modifier.weight(1f),
                label = { Text("Enter command") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            Button(onClick = { send() }) {
                Text("Send")
            }
        }
    }
}

@Composable
fun SftpScreen(sshViewModel: SshViewModel, onEditFile: (SftpFile) -> Unit) {
    val sftpState by sshViewModel.sftpUiState.collectAsState()
    var showCreateDirDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<SftpFile?>(null) }
    var fileToRename by remember { mutableStateOf<SftpFile?>(null) }
    val context = LocalContext.current
    
    val filePickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sshViewModel.uploadFile(context, it) }
    }

    if (showCreateDirDialog) {
        CreateOrRenameDialog(
            onConfirm = { dirName ->
                showCreateDirDialog = false
                sshViewModel.createDirectory(dirName)
            },
            onDismiss = { showCreateDirDialog = false }
        )
    }

    fileToDelete?.let { file ->
        DeleteConfirmationDialog(
            file = file,
            onConfirm = {
                fileToDelete = null
                sshViewModel.deletePath(file)
            },
            onDismiss = { fileToDelete = null }
        )
    }
    
    fileToRename?.let { file ->
        CreateOrRenameDialog(
            file = file,
            onConfirm = { newName ->
                fileToRename = null
                sshViewModel.renamePath(file, newName)
            },
            onDismiss = { fileToRename = null }
        )
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(onClick = { showCreateDirDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Directory")
                }
                Spacer(modifier = Modifier.height(8.dp))
                FloatingActionButton(onClick = { filePickerLauncher.launch("*/*") }) {
                    Icon(Icons.Default.Upload, contentDescription = "Upload File")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(text = "Path: ${sftpState.currentPath}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)

            when {
                sftpState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                sftpState.errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Error: ${sftpState.errorMessage}", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { sshViewModel.loadSftpFiles(sftpState.currentPath) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (sftpState.currentPath != "." && sftpState.currentPath != "/") {
                            item {
                                ListItem(
                                    headlineContent = { Text("../") },
                                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Parent Directory") },
                                    modifier = Modifier.clickable { 
                                        val parentPath = sftpState.currentPath.substringBeforeLast('/', ".")
                                        sshViewModel.loadSftpFiles(parentPath)
                                     }
                                )
                            }
                        }
                        items(sftpState.files) { file ->
                            ListItem(
                                headlineContent = { Text(file.name) },
                                leadingContent = {
                                    Icon(
                                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                        contentDescription = if (file.isDirectory) "Directory" else "File"
                                    )
                                },
                                supportingContent = { Text("Size: ${file.size} bytes - ${file.modifiedTime}") },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { fileToRename = file }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename")
                                        }
                                        IconButton(onClick = { fileToDelete = file }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                                        }
                                    }
                                },
                                modifier = Modifier.clickable {
                                    if (file.isDirectory) {
                                        sshViewModel.loadSftpFiles(file.path)
                                    } else {
                                       onEditFile(file)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(sshViewModel: SshViewModel, file: SftpFile, onExit: () -> Unit) {
    val editorState by sshViewModel.editorUiState.collectAsState()

    LaunchedEffect(file) {
        sshViewModel.readFileContent(file.path)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { sshViewModel.saveFileContent(file) }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (editorState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                TextField(
                    value = editorState.content,
                    onValueChange = { sshViewModel.onFileContentChange(it) },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                )
            }
        }
    }
}

@Composable
fun CreateOrRenameDialog(file: SftpFile? = null, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(file?.name ?: "") }
    val title = if (file == null) "Create New Directory" else "Rename"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (file == null) "Directory Name" else "New Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { 
                if (name.isNotBlank()) {
                    onConfirm(name)
                } 
            }) {
                Text(if (file == null) "Create" else "Rename")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(file: SftpFile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Deletion") },
        text = { Text("Are you sure you want to delete '${file.name}'? This action cannot be undone.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class Screen {
    LOGIN, TERMINAL, SFTP
}
