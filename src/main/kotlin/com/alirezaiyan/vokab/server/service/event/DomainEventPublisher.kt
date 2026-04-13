package com.alirezaiyan.vokab.server.service.event

import com.alirezaiyan.vokab.server.domain.event.DomainEvent

/**
 * Abstraction over event publishing. Decouples domain services from Spring.
 * Swap implementation for tests or to migrate to a message broker.
 */
interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}
