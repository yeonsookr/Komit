package com.a501.service.common.realtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.relay.RealtimeControlPublisher;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

class RealtimeDuplicateConnectionsTest {
    private RealtimePresenceService presence;
    private RealtimeControlPublisher controlPublisher;
    private RealtimeDuplicateConnections duplicates;

    @BeforeEach
    void setUp() {
        presence = mock(RealtimePresenceService.class);
        controlPublisher = mock(RealtimeControlPublisher.class);
        duplicates =
                new RealtimeDuplicateConnections(
                        presence, controlPublisher, mock(RealtimeConnectionRegistry.class));
    }

    @Test
    void 자리를_내준_연결을_접는다() {
        RealtimePrincipal joined = RealtimeTestFixtures.principal("conn-2");
        when(presence.claimOwnership(joined)).thenReturn(Optional.of("conn-1"));

        duplicates.closeOlderOf(joined);

        verify(controlPublisher).closeDuplicate("conn-1");
    }

    @Test
    void 앞선_연결이_없으면_아무것도_접지_않는다() {
        RealtimePrincipal joined = RealtimeTestFixtures.principal("conn-1");
        when(presence.claimOwnership(joined)).thenReturn(Optional.empty());

        duplicates.closeOlderOf(joined);

        verifyNoInteractions(controlPublisher);
    }

    @Test
    void 자리를_정하는_일과_접을_대상을_고르는_일이_같은_판단이다() {
        // 들어온 시각을 견주지 않는다. 시각은 pod 마다 다르고 살아 있다고 알릴 때마다 덮어써져,
        // 늦게 온 쪽이 앞선 것처럼 보이면 두 탭이 서로를 접거나 아무도 접지 못한다.
        RealtimePrincipal joined = RealtimeTestFixtures.principal("conn-2");
        when(presence.claimOwnership(joined)).thenReturn(Optional.of("conn-1"));

        duplicates.closeOlderOf(joined);

        verify(presence).claimOwnership(joined);
        verify(presence, never()).inChannel(joined.channel(), joined.channelId());
        verify(controlPublisher).closeDuplicate("conn-1");
    }

    @Test
    void Redis에서_대표를_정하지_못하면_입장을_실패시킨다() {
        RealtimePrincipal joined = RealtimeTestFixtures.principal("conn-2");
        RealtimePresenceService.OwnershipClaimException failure =
                new RealtimePresenceService.OwnershipClaimException(
                        joined, new RedisConnectionFailureException("down"));
        when(presence.claimOwnership(joined)).thenThrow(failure);

        assertThatThrownBy(() -> duplicates.closeOlderOf(joined)).isSameAs(failure);
        verifyNoInteractions(controlPublisher);
    }
}
