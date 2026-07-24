package com.example.nextlist.data.auth

import com.example.nextlist.BuildConfig
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.data.firebase.awaitTask
import com.example.nextlist.data.firebase.toAppError
import com.example.nextlist.domain.model.AuthUser
import com.example.nextlist.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class FirebaseAuthRepository @Inject constructor() : AuthRepository {
    private val auth: FirebaseAuth? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAuth.getInstance() else null
    }
    private val listening = AtomicBoolean(false)
    private val currentUser = MutableStateFlow<AuthUser?>(null)
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    override fun observeCurrentUser(): Flow<AuthUser?> {
        ensureListening()
        return currentUser.asStateFlow()
    }

    override suspend fun signIn(email: String, password: String): AppResult<Unit> =
        runFirebase {
            requireAuth().signInWithEmailAndPassword(email, password).awaitTask()
            publishCurrentUser()
        }

    override suspend fun register(
        email: String,
        password: String,
        nickname: String,
    ): AppResult<Unit> {
        val firebaseAuth = auth ?: return AppResult.Failure(AppError.UNKNOWN)
        return try {
            val user = firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .awaitTask()
                .user
                ?: return AppResult.Failure(AppError.UNKNOWN)

            publish(user)

            // The account already exists at this point. These follow-up calls are
            // recoverable from the profile completion screen, so they must not
            // turn a successful account creation into a misleading failure.
            runCatching {
                user.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(nickname)
                        .build(),
                ).awaitTask()
                publish(user)
            }
            runCatching { user.sendEmailVerification().awaitTask() }
            AppResult.Success(Unit)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): AppResult<Unit> =
        runFirebase {
            requireAuth().sendPasswordResetEmail(email).awaitTask()
        }

    override suspend fun sendEmailVerification(): AppResult<Unit> =
        runFirebase {
            val user = requireAuth().currentUser
                ?: throw UnauthenticatedException()
            if (!user.isEmailVerified) {
                user.sendEmailVerification().awaitTask()
            }
        }

    override suspend fun refreshCurrentUser(): AppResult<AuthUser> {
        val firebaseAuth = auth ?: return AppResult.Failure(AppError.UNKNOWN)
        val user = firebaseAuth.currentUser
            ?: return AppResult.Failure(AppError.UNAUTHENTICATED)
        return try {
            user.reload().awaitTask()
            user.getIdToken(true).awaitTask()
            val refreshed = firebaseAuth.currentUser
                ?: return AppResult.Failure(AppError.UNAUTHENTICATED)
            val domainUser = refreshed.toDomain()
            currentUser.value = domainUser
            AppResult.Success(domainUser)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun signOut(): AppResult<Unit> {
        val firebaseAuth = auth ?: return AppResult.Failure(AppError.UNKNOWN)
        return try {
            firebaseAuth.signOut()
            currentUser.value = null
            AppResult.Success(Unit)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    private fun ensureListening() {
        if (!listening.compareAndSet(false, true)) return
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            currentUser.value = null
            return
        }
        val listener = FirebaseAuth.AuthStateListener { updatedAuth ->
            currentUser.value = updatedAuth.currentUser?.toDomain()
        }
        authStateListener = listener
        currentUser.value = firebaseAuth.currentUser?.toDomain()
        firebaseAuth.addAuthStateListener(listener)
    }

    private fun publishCurrentUser() {
        publish(requireAuth().currentUser)
    }

    private fun publish(user: FirebaseUser?) {
        currentUser.value = user?.toDomain()
    }

    private fun requireAuth(): FirebaseAuth = auth ?: throw FirebaseNotConfiguredException()

    private suspend fun runFirebase(block: suspend () -> Unit): AppResult<Unit> = try {
        block()
        AppResult.Success(Unit)
    } catch (_: FirebaseNotConfiguredException) {
        AppResult.Failure(AppError.UNKNOWN)
    } catch (_: UnauthenticatedException) {
        AppResult.Failure(AppError.UNAUTHENTICATED)
    } catch (error: Exception) {
        AppResult.Failure(error.toAppError())
    }
}

private class FirebaseNotConfiguredException : IllegalStateException()

private class UnauthenticatedException : IllegalStateException()

private fun FirebaseUser.toDomain(): AuthUser = AuthUser(
    id = uid,
    email = email.orEmpty(),
    suggestedNickname = displayName?.trim()?.takeIf(String::isNotEmpty),
    emailVerified = isEmailVerified,
)
