package com.example.nextlist.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nextlist.data.firebase.FirebaseRuntimeStatus

private object AuthRoute {
    const val LOGIN = "auth/login"
    const val REGISTER = "auth/register"
    const val FORGOT_PASSWORD = "auth/forgot-password"
}

@Composable
fun AuthNavHost(
    firebaseStatus: FirebaseRuntimeStatus,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AuthRoute.LOGIN,
        modifier = modifier,
    ) {
        composable(AuthRoute.LOGIN) {
            val viewModel: LoginViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LoginScreen(
                state = state,
                firebaseAvailable = firebaseStatus != FirebaseRuntimeStatus.NOT_CONFIGURED,
                onEmailChanged = viewModel::onEmailChanged,
                onPasswordChanged = viewModel::onPasswordChanged,
                onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                onSubmit = viewModel::submit,
                onRegister = { navController.navigate(AuthRoute.REGISTER) },
                onForgotPassword = { navController.navigate(AuthRoute.FORGOT_PASSWORD) },
            )
        }
        composable(AuthRoute.REGISTER) {
            val viewModel: RegisterViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            RegisterScreen(
                state = state,
                firebaseAvailable = firebaseStatus != FirebaseRuntimeStatus.NOT_CONFIGURED,
                onNicknameChanged = viewModel::onNicknameChanged,
                onEmailChanged = viewModel::onEmailChanged,
                onPasswordChanged = viewModel::onPasswordChanged,
                onPasswordConfirmationChanged = viewModel::onPasswordConfirmationChanged,
                onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                onSubmit = viewModel::submit,
                onBackToLogin = { navController.popBackStack() },
            )
        }
        composable(AuthRoute.FORGOT_PASSWORD) {
            val viewModel: ForgotPasswordViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ForgotPasswordScreen(
                state = state,
                firebaseAvailable = firebaseStatus != FirebaseRuntimeStatus.NOT_CONFIGURED,
                onEmailChanged = viewModel::onEmailChanged,
                onSubmit = viewModel::submit,
                onBackToLogin = { navController.popBackStack() },
            )
        }
    }
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    firebaseAvailable: Boolean,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthPage(
        eyebrow = "下次 · NextList",
        title = "把下次想做的事，留在这里",
        description = "登录后继续和身边的人收集灵感。",
        modifier = modifier,
    ) {
        FirebaseAvailabilityNotice(firebaseAvailable)
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("邮箱") },
            singleLine = true,
            isError = state.emailError != null,
            supportingText = state.emailError?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )
        PasswordField(
            value = state.password,
            onValueChanged = onPasswordChanged,
            label = "密码",
            error = state.passwordError,
            visible = state.passwordVisible,
            onToggleVisibility = onTogglePasswordVisibility,
            imeAction = ImeAction.Done,
            onDone = onSubmit,
        )
        FeedbackText(state.message)
        SubmitButton(
            text = "登录",
            loadingText = "正在登录…",
            isLoading = state.isSubmitting,
            enabled = firebaseAvailable,
            onClick = onSubmit,
        )
        TextButton(
            onClick = onForgotPassword,
            enabled = !state.isSubmitting,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("忘记密码")
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("还没有账号？", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRegister, enabled = !state.isSubmitting) {
                Text("注册账号")
            }
        }
    }
}

@Composable
fun RegisterScreen(
    state: RegisterUiState,
    firebaseAvailable: Boolean,
    onNicknameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordConfirmationChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthPage(
        eyebrow = "创建账号",
        title = "先认识一下",
        description = "昵称会展示给之后与你同组的成员，注册后仍可修改。",
        modifier = modifier,
    ) {
        FirebaseAvailabilityNotice(firebaseAvailable)
        OutlinedTextField(
            value = state.nickname,
            onValueChange = onNicknameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("昵称") },
            singleLine = true,
            isError = state.nicknameError != null,
            supportingText = {
                Text(state.nicknameError ?: "2～20 个字符")
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("邮箱") },
            singleLine = true,
            isError = state.emailError != null,
            supportingText = state.emailError?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )
        PasswordField(
            value = state.password,
            onValueChanged = onPasswordChanged,
            label = "密码",
            error = state.passwordError,
            visible = state.passwordVisible,
            onToggleVisibility = onTogglePasswordVisibility,
            imeAction = ImeAction.Next,
            supportingText = "至少 6 位",
        )
        PasswordField(
            value = state.passwordConfirmation,
            onValueChanged = onPasswordConfirmationChanged,
            label = "确认密码",
            error = state.passwordConfirmationError,
            visible = state.passwordVisible,
            onToggleVisibility = onTogglePasswordVisibility,
            imeAction = ImeAction.Done,
            onDone = onSubmit,
        )
        FeedbackText(state.message)
        SubmitButton(
            text = "注册",
            loadingText = "正在创建账号…",
            isLoading = state.isSubmitting,
            enabled = firebaseAvailable,
            onClick = onSubmit,
        )
        TextButton(
            onClick = onBackToLogin,
            enabled = !state.isSubmitting,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("已有账号，返回登录")
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordUiState,
    firebaseAvailable: Boolean,
    onEmailChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthPage(
        eyebrow = "找回账号",
        title = "重置密码",
        description = "填写注册邮箱，我们会发送一封密码重置邮件。",
        modifier = modifier,
    ) {
        FirebaseAvailabilityNotice(firebaseAvailable)
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("邮箱") },
            singleLine = true,
            isError = state.emailError != null,
            supportingText = state.emailError?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        FeedbackText(state.message, positive = state.emailSent)
        SubmitButton(
            text = if (state.emailSent) "重新发送" else "发送重置邮件",
            loadingText = "正在发送…",
            isLoading = state.isSubmitting,
            enabled = firebaseAvailable,
            onClick = onSubmit,
        )
        TextButton(
            onClick = onBackToLogin,
            enabled = !state.isSubmitting,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("返回登录")
        }
    }
}

@Composable
private fun AuthPage(
    eyebrow: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = eyebrow,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChanged: (String) -> Unit,
    label: String,
    error: String?,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    imeAction: ImeAction,
    supportingText: String? = null,
    onDone: () -> Unit = {},
) {
    val supportMessage = error ?: supportingText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(onClick = onToggleVisibility) {
                Text(if (visible) "隐藏" else "显示")
            }
        },
        supportingText = supportMessage?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
private fun SubmitButton(
    text: String,
    loadingText: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp),
                strokeWidth = 2.dp,
            )
            Text(loadingText)
        } else {
            Text(text)
        }
    }
}

@Composable
private fun FirebaseAvailabilityNotice(firebaseAvailable: Boolean) {
    if (firebaseAvailable) return
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "当前构建没有 Firebase 配置。请使用本地示例配置连接 Emulator。",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FeedbackText(message: String?, positive: Boolean = false) {
    if (message == null) return
    Text(
        text = message,
        color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}
