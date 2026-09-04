package com.homelab.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.homelab.app.domain.model.Tenant

@Entity(
    tableName = "service_instances",
    indices = [Index(value = ["tenantRef"], name = "index_service_instances_tenantRef")]
)
data class ServiceInstanceEntity(
    @PrimaryKey val id: String,
    val type: String,
    val label: String,
    val url: String,
    @ColumnInfo(defaultValue = "'default'")
    val tenantRef: String = Tenant.DEFAULT_ID,
    val siteRef: String? = null,
    val credentialRef: String? = null,
    val username: String?,
    val piholeAuthMode: String?,
    val fallbackUrl: String?,
    @ColumnInfo(defaultValue = "'SYSTEM'")
    val tlsMode: String,
    val certificatePin: String? = null
)
