package com.example.nextlist.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nextlist.core.designsystem.NextListTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginScreenShowsPrimaryAccountActions() {
        composeRule.setContent {
            NextListTheme {
                LoginScreen(
                    state = LoginUiState(),
                    firebaseAvailable = true,
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onTogglePasswordVisibility = {},
                    onSubmit = {},
                    onRegister = {},
                    onForgotPassword = {},
                )
            }
        }

        composeRule.onNodeWithText("邮箱").assertIsDisplayed()
        composeRule.onNodeWithText("密码").assertIsDisplayed()
        composeRule.onNodeWithText("忘记密码").assertIsDisplayed()
        composeRule.onNodeWithText("注册账号").assertIsDisplayed()
    }

    @Test
    fun unavailableFirebaseConfigurationDisablesLogin() {
        var submitCount = 0
        composeRule.setContent {
            NextListTheme {
                LoginScreen(
                    state = LoginUiState(),
                    firebaseAvailable = false,
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onTogglePasswordVisibility = {},
                    onSubmit = { submitCount += 1 },
                    onRegister = {},
                    onForgotPassword = {},
                )
            }
        }

        composeRule.onNodeWithText("登录").assertIsNotEnabled().performClick()
        assertEquals(0, submitCount)
        composeRule.onNodeWithText(
            "当前构建没有 Firebase 配置。请使用本地示例配置连接 Emulator。",
        ).assertIsDisplayed()
    }
}
