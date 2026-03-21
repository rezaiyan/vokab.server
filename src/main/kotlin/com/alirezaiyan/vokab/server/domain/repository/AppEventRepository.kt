package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.AppEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AppEventRepository : JpaRepository<AppEvent, Long>
