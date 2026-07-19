package com.jihyeon.coffeeorder.ranking.event;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ProcessedOrderEventStore {

    private final Set<UUID> processedEventIds = ConcurrentHashMap.newKeySet();

    public boolean markIfNew(UUID eventId) {
        return processedEventIds.add(eventId);
    }

    public void remove(UUID eventId) {
        processedEventIds.remove(eventId);
    }
}
