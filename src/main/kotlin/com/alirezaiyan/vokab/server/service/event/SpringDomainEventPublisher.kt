package com.alirezaiyan.vokab.server.service.event

import com.alirezaiyan.vokab.server.domain.event.DomainEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class SpringDomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) : DomainEventPublisher {

    override fun publish(event: DomainEvent) {
        applicationEventPublisher.publishEvent(event)
    }
}
