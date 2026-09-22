package com.a501.service.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.diagnosis.dto.RequestSummary;
import com.a501.service.domain.diagnosis.service.DiagnosisQueryService;
import com.a501.service.domain.project.service.ProjectQueryService;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.entity.Room;
import com.a501.service.domain.room.repository.RoomRepository;
import com.a501.service.domain.user.service.UserQueryService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class RoomServiceTest {
    private static final Integer ROOM_ID = 92;
    private static final Integer CONVERTED_ROOM_ID = 104;
    private static final Integer PROJECT_ID = 5;
    private static final Integer CLIENT_ID = 7;
    private static final Integer DEVELOPER_ID = 9;
    private static final Integer OUTSIDER_ID = 99;
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 8, 18, 7, 40);

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final RoomQueryService roomQueryService = mock(RoomQueryService.class);
    private final ProjectQueryService projectQueryService = mock(ProjectQueryService.class);
    private final UserQueryService userQueryService = mock(UserQueryService.class);
    private final ContractQueryService contractQueryService = mock(ContractQueryService.class);
    private final DiagnosisQueryService diagnosisQueryService = mock(DiagnosisQueryService.class);

    private final RoomService service =
            new RoomService(
                    roomRepository,
                    roomQueryService,
                    projectQueryService,
                    userQueryService,
                    contractQueryService,
                    diagnosisQueryService);

    @BeforeEach
    void setUp() {
        givenRoom(null);
        when(roomQueryService.findParties(ROOM_ID))
                .thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_ID)));
        when(projectQueryService.findName(PROJECT_ID)).thenReturn(Optional.of("수제 비누 가게"));
        when(userQueryService.findName(CLIENT_ID)).thenReturn(Optional.of("김의뢰"));
        when(userQueryService.findName(DEVELOPER_ID)).thenReturn(Optional.of("이준"));
        when(contractQueryService.findContractIdByRoomId(ROOM_ID)).thenReturn(Optional.empty());
        when(diagnosisQueryService.findRequestSummary(PROJECT_ID)).thenReturn(Optional.empty());
    }

    @Test
    void 의뢰인에게는_개발자가_상대로_보인다() {
        assertThat(service.getRoom(CLIENT_ID, ROOM_ID).counterpartName()).isEqualTo("이준");
    }

    @Test
    void 개발자에게는_의뢰인이_상대로_보인다() {
        assertThat(service.getRoom(DEVELOPER_ID, ROOM_ID).counterpartName()).isEqualTo("김의뢰");
    }

    @Test
    void 들어가_있지_않으면_방이_있다는_것도_알리지_않는다() {
        assertThatThrownBy(() -> service.getRoom(OUTSIDER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void 개설_시각은_offset_을_달고_나간다() {
        assertThat(service.getRoom(CLIENT_ID, ROOM_ID).openedAt())
                .isEqualTo(OPENED_AT.atOffset(ZoneOffset.UTC));
    }

    @Test
    void 요청서가_있으면_요약이_함께_온다() {
        when(diagnosisQueryService.findRequestSummary(PROJECT_ID))
                .thenReturn(Optional.of(new RequestSummary(31, 3L, OPENED_AT)));

        var request = service.getRoom(CLIENT_ID, ROOM_ID).request();

        assertThat(request).isNotNull();
        assertThat(request.diagnosisId()).isEqualTo(31);
        assertThat(request.issueCount()).isEqualTo(3L);
    }

    @Test
    void 전환된_방이_있으면_식별자와_용도가_함께_온다() {
        givenRoom(CONVERTED_ROOM_ID);
        givenConvertedRoom(true);

        var response = service.getRoom(CLIENT_ID, ROOM_ID);

        assertThat(response.convertedRoomId()).isEqualTo(CONVERTED_ROOM_ID);
        assertThat(response.convertedRoomType()).isEqualTo("CONSULTING");
    }

    /** 화면은 둘을 짝으로 보고 버튼을 그린다. 한쪽만 차 있으면 없는 방으로 보낸다. */
    @Test
    void 전환된_방이_지워졌으면_둘_다_비운다() {
        givenRoom(CONVERTED_ROOM_ID);
        givenConvertedRoom(false);

        var response = service.getRoom(CLIENT_ID, ROOM_ID);

        assertThat(response.convertedRoomId()).isNull();
        assertThat(response.convertedRoomType()).isNull();
    }

    private void givenRoom(Integer convertedRoomId) {
        Room room = BeanUtils.instantiateClass(Room.class);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        ReflectionTestUtils.setField(room, "projectId", PROJECT_ID);
        ReflectionTestUtils.setField(room, "developerId", DEVELOPER_ID);
        ReflectionTestUtils.setField(room, "chatRefId", "room-92-chat");
        ReflectionTestUtils.setField(room, "type", "INQUIRY");
        ReflectionTestUtils.setField(room, "status", "OPEN");
        ReflectionTestUtils.setField(room, "openedAt", OPENED_AT);
        ReflectionTestUtils.setField(room, "convertedRoomId", convertedRoomId);
        when(roomRepository.findByIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.of(room));
    }

    private void givenConvertedRoom(boolean alive) {
        if (!alive) {
            when(roomRepository.findByIdAndDeletedFalse(CONVERTED_ROOM_ID))
                    .thenReturn(Optional.empty());
            return;
        }
        Room converted = BeanUtils.instantiateClass(Room.class);
        ReflectionTestUtils.setField(converted, "id", CONVERTED_ROOM_ID);
        ReflectionTestUtils.setField(converted, "type", "CONSULTING");
        when(roomRepository.findByIdAndDeletedFalse(CONVERTED_ROOM_ID))
                .thenReturn(Optional.of(converted));
    }
}
