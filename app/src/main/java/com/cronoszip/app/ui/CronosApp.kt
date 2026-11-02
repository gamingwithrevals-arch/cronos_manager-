package com.cronoszip.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cronoszip.app.data.BrowserMode
import com.cronoszip.app.data.CheckData
import com.cronoszip.app.data.UiEffect
import com.cronoszip.app.ui.background.LiquidBackground
import com.cronoszip.app.ui.components.CheckingOverlay
import com.cronoszip.app.ui.components.PlanBar
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.components.SolidDialogCard
import com.cronoszip.app.ui.screens.ArchiveDetailsScreen
import com.cronoszip.app.ui.screens.ArchiveViewerScreen
import com.cronoszip.app.ui.screens.ExtractSetupScreen
import com.cronoszip.app.ui.screens.ExtractingScreen
import com.cronoszip.app.ui.screens.FileBrowserScreen
import com.cronoszip.app.ui.screens.FileManagerScreen
import com.cronoszip.app.ui.screens.HomeScreen
import com.cronoszip.app.ui.screens.SettingsScreen
import com.cronoszip.app.ui.screens.UpdaterScreen
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.Danger
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.util.formatBytes
import com.cronoszip.app.viewmodel.CronosViewModel
import kotlinx.coroutines.launch

private object Routes {
    const val Home = "home"
    const val Setup = "setup"
    const val Extract = "extract"
    const val Details = "details"
    const val Settings = "settings"
    const val Files = "files"
    const val Update = "update"
    const val ArchivePattern = "archive/{path}"
    fun browser(mode: BrowserMode) = "browser/${mode.name}"
    fun archiveView(path: String) = "archive/${Uri.encode(path)}"
}

@Composable
fun CronosApp(viewModel: CronosViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var passwordDialog by remember { mutableStateOf(false) }
    var wrongPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var noSpaceCheck by remember { mutableStateOf<CheckData?>(null) }

    // The archive screen runs its own check and extraction, so those must not jump to the old screens.
    val onArchiveScreen = { nav.currentBackStackEntry?.destination?.route?.startsWith("archive/") == true }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.OpenSetup -> if (!onArchiveScreen()) {
                    nav.navigate(Routes.Setup) { launchSingleTop = true }
                }
                is UiEffect.NoSpace -> {
                    noSpaceCheck = effect.check
                }
                is UiEffect.PasswordNeeded -> {
                    wrongPassword = effect.wrong
                    password = ""
                    passwordDialog = true
                }
                is UiEffect.Message -> {
                    scope.launch { snackbar.showSnackbar(effect.text) }
                }
                is UiEffect.ExtractionStarted -> if (!onArchiveScreen()) {
                    nav.navigate(Routes.Extract) { launchSingleTop = true }
                }
                is UiEffect.RestoreCompleted -> {
                    viewModel.resetExtraction()
                    scope.launch { snackbar.showSnackbar(effect.message) }
                    nav.navigate(Routes.Files) {
                        popUpTo(Routes.Files) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LiquidBackground(animate = viewModel.settings.animations)

        NavHost(
            navController = nav,
            startDestination = Routes.Files,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.Files) {
                FileManagerScreen(
                    viewModel = viewModel,
                    onBack = null,
                    onOpenArchive = { nav.navigate(Routes.archiveView(it)) },
                    onExtract = { nav.navigate(Routes.browser(BrowserMode.Archive)) },
                    onSettings = { nav.navigate(Routes.Settings) },
                )
            }

            composable(
                route = Routes.ArchivePattern,
                arguments = listOf(navArgument("path") { type = NavType.StringType }),
            ) { entry ->
                ArchiveViewerScreen(
                    viewModel = viewModel,
                    path = entry.arguments?.getString("path").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onChooseFolder = { nav.navigate(Routes.browser(BrowserMode.Folder)) },
                    onDone = {
                        nav.navigate(Routes.Files) {
                            popUpTo(Routes.Files) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.Home) {
                HomeScreen(
                    viewModel = viewModel,
                    onGetStarted = {
                        viewModel.enterBrowser(BrowserMode.Archive)
                        nav.navigate(Routes.browser(BrowserMode.Archive))
                    },
                    onOpenHistory = { path -> viewModel.checkArchive(path) },
                    onSettings = { nav.navigate(Routes.Settings) },
                    onOpenFiles = { nav.navigate(Routes.Files) },
                )
            }

            composable(Routes.browser(BrowserMode.Archive)) {
                FileBrowserScreen(
                    viewModel = viewModel,
                    mode = BrowserMode.Archive,
                    onBack = { nav.popBackStack() },
                    onFolderChosen = { },
                )
            }

            composable(Routes.browser(BrowserMode.Folder)) {
                FileBrowserScreen(
                    viewModel = viewModel,
                    mode = BrowserMode.Folder,
                    onBack = { nav.popBackStack() },
                    onFolderChosen = { path ->
                        viewModel.applyOutputFolder(path)
                        nav.popBackStack()
                    },
                )
            }

            composable(Routes.Setup) {
                ExtractSetupScreen(
                    viewModel = viewModel,
                    onBack = { nav.popBackStack() },
                    onChooseFolder = { nav.navigate(Routes.browser(BrowserMode.Folder)) },
                    onViewContents = { nav.navigate(Routes.Details) },
                )
            }

            composable(Routes.Extract) {
                ExtractingScreen(
                    viewModel = viewModel,
                    onDone = {
                        nav.navigate(Routes.Files) {
                            popUpTo(Routes.Files) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(Routes.Details) {
                ArchiveDetailsScreen(
                    viewModel = viewModel,
                    onBack = { nav.popBackStack() },
                )
            }

            composable(Routes.Settings) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { nav.popBackStack() },
                    onOpenFiles = { nav.navigate(Routes.Files) },
                    onOpenUpdate = { nav.navigate(Routes.Update) },
                )
            }

            composable(Routes.Update) {
                UpdaterScreen(
                    viewModel = viewModel,
                    onBack = { nav.popBackStack() },
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp),
        )

        if (viewModel.isChecking) {
            CheckingOverlay()
        }
    }

    if (passwordDialog) {
        Dialog(
            onDismissRequest = {
                passwordDialog = false
                viewModel.cancelPassword()
            },
        ) {
            SolidDialogCard(glow = CronosBlue) {
                Text(
                    text = "Archive password",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (wrongPassword) {
                        "That password did not unlock the archive. Try again."
                    } else {
                        "This archive is encrypted. The password stays on this device."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (wrongPassword) Danger else TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Password", color = TextSecondary) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Cancel",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            passwordDialog = false
                            viewModel.cancelPassword()
                        },
                    )
                    PrimaryButton(
                        text = "Unlock",
                        modifier = Modifier.weight(1f),
                        enabled = password.isNotEmpty(),
                        onClick = {
                            val attempt = password
                            passwordDialog = false
                            password = ""
                            viewModel.retryPassword(attempt)
                        },
                    )
                }
            }
        }
    }

    noSpaceCheck?.let { check ->
        Dialog(onDismissRequest = { noSpaceCheck = null }) {
            SolidDialogCard(glow = Danger) {
                Text(
                    text = "Not enough room",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The engine proved the plan before changing anything. " +
                        "Chronos Manager will not start this extraction yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                PlanBar(
                    free = check.plan.free,
                    needed = check.plan.needed,
                    keepFree = check.plan.keepFree,
                    fits = false,
                )
                Spacer(Modifier.height(14.dp))
                SpaceRow("Free now", formatBytes(check.plan.free))
                SpaceRow("Required at peak", formatBytes(check.plan.required))
                SpaceRow("Kept free", formatBytes(check.plan.keepFree))
                SpaceRow(
                    label = "Still needed",
                    value = formatBytes((check.plan.required - check.plan.free).coerceAtLeast(0L)),
                    danger = true,
                )
                if (check.plan.inPlaceNeeds > check.plan.normalNeeds) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Keeping the archive uses more room. Shrinking in place needs less.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CronosCyan,
                    )
                }
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    text = "Check again",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        noSpaceCheck = null
                        viewModel.recheckCurrent()
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Open plan",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            noSpaceCheck = null
                            nav.navigate(Routes.Setup) { launchSingleTop = true }
                        },
                    )
                    SecondaryButton(
                        text = "Pick another",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            noSpaceCheck = null
                            nav.navigate(Routes.browser(BrowserMode.Archive))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpaceRow(label: String, value: String, danger: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (danger) Danger else TextPrimary,
        )
    }
}
