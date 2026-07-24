package com.example.nextlist.data.firebase

import com.example.nextlist.core.result.AppError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException
import java.io.IOException

internal fun Throwable.toAppError(): AppError = when (this) {
    is FirebaseAuthInvalidCredentialsException,
    is FirebaseAuthInvalidUserException,
    -> AppError.UNAUTHENTICATED
    is FirebaseAuthUserCollisionException -> AppError.ALREADY_EXISTS
    is FirebaseAuthWeakPasswordException -> AppError.VALIDATION
    is FirebaseTooManyRequestsException -> AppError.RATE_LIMITED
    is FirebaseNetworkException -> AppError.NETWORK_UNAVAILABLE
    is FirebaseFunctionsException -> {
        val businessCode = (details as? Map<*, *>)?.get("code") as? String
        businessCodeToAppError(businessCode) ?: when (code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    AppError.UNAUTHENTICATED
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    AppError.PERMISSION_DENIED
                FirebaseFunctionsException.Code.NOT_FOUND ->
                    AppError.NOT_FOUND
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                    AppError.RATE_LIMITED
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                FirebaseFunctionsException.Code.CANCELLED,
                -> AppError.NETWORK_UNAVAILABLE
                else -> AppError.UNKNOWN
            }
    }
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppError.UNAUTHENTICATED
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PERMISSION_DENIED
        FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NOT_FOUND
        FirebaseFirestoreException.Code.ALREADY_EXISTS -> AppError.ALREADY_EXISTS
        FirebaseFirestoreException.Code.ABORTED -> AppError.CONFLICT
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> AppError.RATE_LIMITED
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.CANCELLED,
        -> AppError.NETWORK_UNAVAILABLE
        else -> AppError.UNKNOWN
    }
    is StorageException -> when (errorCode) {
        StorageException.ERROR_NOT_AUTHENTICATED -> AppError.UNAUTHENTICATED
        StorageException.ERROR_NOT_AUTHORIZED -> AppError.PERMISSION_DENIED
        StorageException.ERROR_OBJECT_NOT_FOUND -> AppError.NOT_FOUND
        StorageException.ERROR_QUOTA_EXCEEDED -> AppError.RATE_LIMITED
        StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> AppError.NETWORK_UNAVAILABLE
        else -> AppError.UNKNOWN
    }
    is IOException -> AppError.NETWORK_UNAVAILABLE
    else -> AppError.UNKNOWN
}

internal fun businessCodeToAppError(code: String?): AppError? = when (code) {
    "UNAUTHENTICATED" -> AppError.UNAUTHENTICATED
    "EMAIL_NOT_VERIFIED" -> AppError.EMAIL_NOT_VERIFIED
    "VALIDATION" -> AppError.VALIDATION
    "NOT_FOUND" -> AppError.NOT_FOUND
    "NOT_ADMIN", "PERMISSION_DENIED" -> AppError.NOT_ADMIN
    "GROUP_DISSOLVED" -> AppError.GROUP_DISSOLVED
    "GROUP_FULL" -> AppError.GROUP_FULL
    "INVITE_INVALID" -> AppError.INVITE_INVALID
    "INVITE_EXPIRED" -> AppError.INVITE_EXPIRED
    "ADMIN_CANNOT_LEAVE" -> AppError.ADMIN_CANNOT_LEAVE
    "TARGET_NOT_MEMBER" -> AppError.TARGET_NOT_MEMBER
    "CONFLICT" -> AppError.CONFLICT
    "RATE_LIMITED" -> AppError.RATE_LIMITED
    else -> null
}
