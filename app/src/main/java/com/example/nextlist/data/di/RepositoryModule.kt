package com.example.nextlist.data.di

import com.example.nextlist.data.auth.FirebaseAuthRepository
import com.example.nextlist.data.firestore.FirestoreUserProfileRepository
import com.example.nextlist.data.storage.FirebaseAvatarRepository
import com.example.nextlist.domain.repository.AuthRepository
import com.example.nextlist.domain.repository.AvatarRepository
import com.example.nextlist.domain.repository.UserProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(
        implementation: FirestoreUserProfileRepository,
    ): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindAvatarRepository(
        implementation: FirebaseAvatarRepository,
    ): AvatarRepository
}
