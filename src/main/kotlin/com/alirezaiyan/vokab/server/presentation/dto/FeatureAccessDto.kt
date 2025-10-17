package com.alirezaiyan.vokab.server.presentation.dto

import com.alirezaiyan.vokab.server.service.ClientFeatureFlags
import com.alirezaiyan.vokab.server.service.UserFeatureAccess

/**
 * Combined response with feature flags and user's access status
 */
data class FeatureAccessResponse(
    val featureFlags: ClientFeatureFlags,
    val userAccess: UserFeatureAccess
)


