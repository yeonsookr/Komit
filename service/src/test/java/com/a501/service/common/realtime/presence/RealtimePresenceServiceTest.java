package com.a501.service.common.realtime.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.realtime.RealtimePrincipal;
import com.a501.service.common.realtime.RealtimeTestFixtures;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RealtimePresenceServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-20T09:00:00Z");
    private static final String PRESENCE_KEY = "realtime:presence:CONSULTING_SESSION:55";
    private static final String OWNER_KEY = "realtime:owner:CONSULTING_SESSION:55:12";

    private StringRedisTemplate redisTemplate;
    private ZSetOperations<String, String> zSet;
    private RealtimePresenceService presence;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        zSet = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        presence =
                new RealtimePresenceService(
                        redisTemplate,
                        RealtimeTestFixtures.properties(),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RealtimePrincipal principal(int userId, String connectionId) {
        return RealtimeTestFixtures.principal(userId, connectionId, "55");
    }

    @SuppressWarnings("unchecked")
    private List<Object> capturedArgs() {
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), args.capture());
        return List.of(args.getAllValues().toArray());
    }

    @Test
    void 기록과_수명_연장을_한_번에_보낸다() {
        // 나눠 보내면 사이에서 끊겼을 때 수명 없는 키가 남아 아무도 치우지 못한다.
        presence.touch(principal(12, "conn-1"));

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate)
                .execute(any(RedisScript.class), keys.capture(), args.capture(), args.capture(), args.capture());
        assertThat(keys.getValue()).containsExactly(PRESENCE_KEY, OWNER_KEY);
        assertThat(args.getAllValues().get(0)).isEqualTo("12:conn-1");
        assertThat(args.getAllValues().get(1))
                .isEqualTo(String.valueOf(NOW.plus(RealtimeTestFixtures.HEARTBEAT_TIMEOUT).toEpochMilli()));
    }

    @Test
    void 여러_연결을_한_번에_알린다() {
        presence.touchAll(List.of(principal(12, "conn-1"), principal(34, "conn-2")));

        verify(redisTemplate, org.mockito.Mockito.times(2))
                .execute(any(RedisScript.class), anyList(), any(), any(), any());
    }

    @Test
    void 아무도_없으면_Redis_를_건드리지_않는다() {
        presence.touchAll(List.of());

        org.mockito.Mockito.verifyNoInteractions(redisTemplate);
    }

    @Test
    void 대표_자리를_가져가고_직전_대표를_돌려준다() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn("conn-old");

        assertThat(presence.claimOwnership(principal(12, "conn-new"))).contains("conn-old");
    }

    @Test
    void 직전_대표가_없으면_접을_것도_없다() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn(null);

        assertThat(presence.claimOwnership(principal(12, "conn-1"))).isEmpty();
    }

    @Test
    void 직전_대표가_나_자신이면_나를_접지_않는다() {
        // 같은 연결이 두 번 알리는 경우다. 여기서 걸러 내지 않으면 스스로를 끊는다.
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn("conn-1");

        assertThat(presence.claimOwnership(principal(12, "conn-1"))).isEmpty();
    }

    @Test
    void 대표를_못_정해도_새_연결을_막지_않는다() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(presence.claimOwnership(principal(12, "conn-1"))).isEmpty();
    }

    @Test
    void 나갈_때_내_자리와_대표_자리를_함께_본다() {
        presence.leave(principal(12, "conn-1"));

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), any(), any());
        assertThat(keys.getValue()).containsExactly(PRESENCE_KEY, OWNER_KEY);
    }

    @Test
    void 읽을_때_만료된_항목을_먼저_치운다() {
        // pod 가 갑자기 죽으면 정리하지 못한 항목이 남는다. 계속 들어와 있는 사람으로 보이면 안 된다.
        when(zSet.range(PRESENCE_KEY, 0, -1)).thenReturn(new LinkedHashSet<>(List.of("12:conn-1")));

        presence.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55");

        verify(zSet).removeRangeByScore(PRESENCE_KEY, 0, (double) NOW.toEpochMilli());
    }

    @Test
    void 들어와_있는_사람과_연결을_돌려준다() {
        when(zSet.range(PRESENCE_KEY, 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("12:conn-1", "34:conn-2")));

        assertThat(presence.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55"))
                .containsExactlyInAnyOrder(
                        new RealtimePresence(12, "conn-1"), new RealtimePresence(34, "conn-2"));
    }

    @Test
    void 아무도_없으면_빈_목록을_준다() {
        when(zSet.range(PRESENCE_KEY, 0, -1)).thenReturn(null);

        assertThat(presence.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55")).isEmpty();
    }

    @Test
    void 형식이_다른_항목은_건너뛴다() {
        // 배포가 겹쳐 형식이 다른 pod 가 적은 것이다. 한 줄 때문에 조회 전체가 실패하면 안 된다.
        when(zSet.range(PRESENCE_KEY, 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("conn-1", "12:conn-2", "12:", ":conn-3")));

        assertThat(presence.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55"))
                .containsExactly(new RealtimePresence(12, "conn-2"));
    }

    @Test
    void Redis_가_죽어도_연결을_끊지_않는다() {
        // presence 는 장부일 뿐 메시지가 오가는 길이 아니다. 여기서 던지면 입장 자체가 막힌다.
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));
        when(zSet.range(anyString(), any(Long.class), any(Long.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> presence.touch(principal(12, "conn-1"))).doesNotThrowAnyException();
        assertThatCode(() -> presence.leave(principal(12, "conn-1"))).doesNotThrowAnyException();
        assertThat(presence.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55")).isEmpty();
    }
}
