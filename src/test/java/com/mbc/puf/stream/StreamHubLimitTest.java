package com.mbc.puf.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mbc.puf.member.repository.ManagerRegionRepository;

class StreamHubLimitTest {

    @Test
    void anonymousConnectionsAreCappedAndOldestIsClosedFirst() {
        var hub = new StreamHub(mock(ManagerRegionRepository.class));
        try {
            for (int i = 0; i < StreamHub.MAX_ANONYMOUS_CONNECTIONS + 20; i++) {
                hub.connectCustomer(null);
            }
            Map<String, Object> stats = hub.stats();
            assertEquals(StreamHub.MAX_ANONYMOUS_CONNECTIONS, ((Number) stats.get("anonymousOrOther")).intValue());
        } finally {
            hub.shutdown();
        }
    }

    @Test
    void loggedInCustomersAndManagersAreNotCountedAgainstTheAnonymousCap() {
        var regions = mock(ManagerRegionRepository.class);
        when(regions.findRegionIds(1L)).thenReturn(List.of(4L));
        var hub = new StreamHub(regions);
        try {
            for (int i = 0; i < StreamHub.MAX_ANONYMOUS_CONNECTIONS; i++) {
                hub.connectCustomer(null);
            }
            hub.connectCustomer(7L);
            hub.connectManager(1L);
            Map<String, Object> stats = hub.stats();
            assertEquals(StreamHub.MAX_ANONYMOUS_CONNECTIONS, ((Number) stats.get("anonymousOrOther")).intValue());
            assertEquals(1, ((Number) stats.get("customerLoggedIn")).intValue());
            assertEquals(1, ((Number) stats.get("admin")).intValue());
        } finally {
            hub.shutdown();
        }
    }

    @Test
    void reportsWhetherAnyAdminIsConnected() {
        var regions = mock(ManagerRegionRepository.class);
        when(regions.findRegionIds(1L)).thenReturn(List.of(4L));
        var hub = new StreamHub(regions);
        try {
            assertFalse(hub.hasAdminConnections());
            hub.connectCustomer(null);
            assertFalse(hub.hasAdminConnections());
            hub.connectManager(1L);
            assertTrue(hub.hasAdminConnections());
        } finally {
            hub.shutdown();
        }
    }
}
