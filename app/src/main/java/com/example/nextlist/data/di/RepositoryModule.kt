package com.example.nextlist.data.di

import com.example.nextlist.data.auth.FirebaseAuthRepository
import com.example.nextlist.data.firestore.FirestoreUserProfileRepository
import com.example.nextlist.data.firestore.FirebaseGroupRepository
import com.example.nextlist.data.firestore.FirebaseIdeaRepository
import com.example.nextlist.data.preferences.DataStorePendingInviteRepository
import com.example.nextlist.data.storage.FirebaseAvatarRepository
import com.example.nextlist.data.storage.FirebaseIdeaImageRepository
import com.example.nextlist.domain.repository.AuthRepository
import com.example.nextlist.domain.repository.AvatarRepository
import com.example.nextlist.domain.repository.GroupRepository
import com.example.nextlist.domain.repository.IdeaImageRepository
import com.example.nextlist.domain.repository.IdeaRepository
import com.example.nextlist.domain.repository.PendingInviteRepository
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

    @Binds
    @Singleton
    abstract fun bindGroupRepository(
        implementation: FirebaseGroupRepository,
    ): GroupRepository

    @Binds
    @Singleton
    abstract fun bindIdeaRepository(
        implementation: FirebaseIdeaRepository,
    ): IdeaRepository

    @Binds
    @Singleton
    abstract fun bindIdeaImageRepository(
        implementation: FirebaseIdeaImageRepository,
    ): IdeaImageRepository

    @Binds
    @Singleton
    abstract fun bindPendingInviteRepository(
        implementation: DataStorePendingInviteRepository,
    ): PendingInviteRepository
}
