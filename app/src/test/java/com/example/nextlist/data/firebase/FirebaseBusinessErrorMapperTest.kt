package com.example.nextlist.data.firebase

import com.example.nextlist.core.result.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirebaseBusinessErrorMapperTest {
    @Test
    fun `maps stable callable codes without exposing Firebase errors`() {
        assertEquals(AppError.GROUP_FULL, businessCodeToAppError("GROUP_FULL"))
        assertEquals(
            AppError.EMAIL_NOT_VERIFIED,
            businessCodeToAppError("EMAIL_NOT_VERIFIED"),
        )
        assertEquals(AppError.NOT_ADMIN, businessCodeToAppError("NOT_ADMIN"))
        assertEquals(
            AppError.ADMIN_CANNOT_LEAVE,
            businessCodeToAppError("ADMIN_CANNOT_LEAVE"),
        )
        assertNull(businessCodeToAppError("INTERNAL_DATABASE_PATH"))
    }
}
