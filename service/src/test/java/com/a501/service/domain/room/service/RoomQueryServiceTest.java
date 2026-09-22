package com.a501.service.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.domain.project.service.ProjectQueryService;
import com.a501.service.domain.room.entity.Room;
import com.a501.service.domain.room.repository.RoomRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class RoomQueryServiceTest {
    private static final Integer ROOM_ID = 92;
    private static final Integer PROJECT_ID = 5;
    private static final Integer CLIENT_ID = 7;
    private static final Integer DEVELOPER_ID = 9;

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final ProjectQueryService projectQueryService = mock(ProjectQueryService.class);

    private final RoomQueryService service = new RoomQueryService(roomRepository, projectQueryService);

    @BeforeEach
    void setUp() {
        Room room = BeanUtils.instantiateClass(Room.class);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        ReflectionTestUtils.setField(room, "projectId", PROJECT_ID);
        ReflectionTestUtils.setField(room, "developerId", DEVELOPER_ID);
        when(roomRepository.findByIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.of(room));
        when(projectQueryService.findClientId(PROJECT_ID)).thenReturn(Optional.of(CLIENT_ID));
    }

    /** 개발자는 방이 들고 있고 의뢰인은 프로젝트에 있다. 그 한 단계를 여기서 넘는다. */
    @Test
    void 개발자는_방에서_의뢰인은_프로젝트에서_찾는다() {
        var parties = service.findParties(ROOM_ID);

        assertThat(parties).isPresent();
        assertThat(parties.get().developerId()).isEqualTo(DEVELOPER_ID);
        assertThat(parties.get().clientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void 방이_없으면_비어_있다() {
        when(roomRepository.findByIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.empty());

        assertThat(service.findParties(ROOM_ID)).isEmpty();
    }

    @Test
    void 프로젝트를_찾지_못하면_비어_있다() {
        when(projectQueryService.findClientId(PROJECT_ID)).thenReturn(Optional.empty());

        assertThat(service.findParties(ROOM_ID)).isEmpty();
    }
}
