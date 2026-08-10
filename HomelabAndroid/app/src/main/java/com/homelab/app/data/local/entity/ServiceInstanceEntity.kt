package com.homelab.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_instances")
data class ServiceInstanceEntity(
    @PrimaryKey val id: String,
    val type: String,
    val label: String,
    val url: String,
    val credentialRef: String? = null,
    val username: String?,
    val piholeAuthMode: String?,
    val fallbackUrl: String?,
    @ColumnInfo(defaultValue = "'SYSTEM'")
    val tlsMode: String,
    val certificatePin: String? = null
)
