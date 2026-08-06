package com.example.feature.modelsetup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class ModelSetupScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLoadingState() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ModelSetupScreenContent(
                    uiState = ModelSetupUiState.Loading,
                    onAction = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun testReadyToDownloadState() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ModelSetupScreenContent(
                    uiState = ModelSetupUiState.ReadyToDownload(modelSizeMB = 45),
                    onAction = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun testDownloadingState() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ModelSetupScreenContent(
                    uiState = ModelSetupUiState.Downloading(progress = 0.5f, statusMessage = "Downloading neural voice weights... 50%"),
                    onAction = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun testPausedState() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ModelSetupScreenContent(
                    uiState = ModelSetupUiState.Paused(progress = 0.5f),
                    onAction = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun testFailedState() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ModelSetupScreenContent(
                    uiState = ModelSetupUiState.Failed(message = "Connection lost during download."),
                    onAction = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun testOfflineState() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ModelSetupScreenContent(
                    uiState = ModelSetupUiState.Offline(),
                    onAction = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun testInsufficientStorageState() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ModelSetupScreenContent(
                    uiState = ModelSetupUiState.InsufficientStorage(requiredSizeMB = 45, availableSizeMB = 12),
                    onAction = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun testReadyState() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ModelSetupScreenContent(
                    uiState = ModelSetupUiState.Ready,
                    onAction = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
