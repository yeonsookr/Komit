package com.a501.service.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.diagnosis.dto.RequestSummary;
import com.a501.service.domain.diagnosis.service.DiagnosisQueryService;
import com.a501.service.domain.project.service.ProjectQueryService;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.dto.request.ConvertRoomRequest;
import com.a501.service.domain.room.dto.request.ConvertRoomRequest.ConversionTarget;
import com.a501.service.domain.room.dto.request.RoomStatus;
import com.a501.service.domain.room.dto.request.RoomType;
import com.a501.service.domain.room.entity.Room;
import com.a501.service.domain.room.repository.RoomRepository;
import com.a501.service.domain.user.service.UserQueryService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class RoomServiceTest {
    private static final Integer ROOM_ID = 92;
    private static final Integer CONVERTED_ROOM_ID = 104;
    private static final Integer OPENED_ROOM_ID = 120;
    private static final Integer PROJECT_ID = 5;
    private static final Integer CLIENT_ID = 7;
    private static final Integer DEVELOPER_ID = 3;
    private static final Integer DEVELOPER_USER_ID = 9;
    private static final Integer OUTSIDER_ID = 99;
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 8, 18, 7, 40);

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final RoomQueryService roomQueryService = mock(RoomQueryService.class);
    private final ProjectQueryService projectQueryService = mock(ProjectQueryService.class);
    private final UserQueryService userQueryService = mock(UserQueryService.class);
    private final ContractQueryService contractQueryService = mock(ContractQueryService.class);
    private final DiagnosisQueryService diagnosisQueryService = mock(DiagnosisQueryService.class);
    private final DeveloperLookupService developerLookupService = mock(DeveloperLookupService.class);

    /** 갈래를 정할 때 새로 열린 방. 저장 흉내가 채워 넣는다. */
    private Room opened;

    private final RoomService service =
            new RoomService(
                    roomRepository,
                    roomQueryService,
                    projectQueryService,
                    userQueryService,
                    contractQueryService,
                    diagnosisQueryService,
                    developerLookupService);

    @BeforeEach
    void setUp() {
        givenRoom("INQUIRY", null);
        when(roomQueryService.findParties(ROOM_ID))
                .thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_USER_ID)));
        when(projectQueryService.findName(PROJECT_ID)).thenReturn(Optional.of("수제 비누 가게"));
        when(userQueryService.findName(CLIENT_ID)).thenReturn(Optional.of("김의뢰"));
        when(userQueryService.findName(DEVELOPER_USER_ID)).thenReturn(Optional.of("이준"));
        when(contractQueryService.findContractIdByRoomId(ROOM_ID)).thenReturn(Optional.empty());
        when(diagnosisQueryService.findRequestSummary(PROJECT_ID)).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------ 상세

    @Test
    void 의뢰인에게는_개발자가_상대로_보인다() {
        assertThat(service.getRoom(CLIENT_ID, ROOM_ID).counterpartName()).isEqualTo("이준");
    }

    @Test
    void 개발자에게는_의뢰인이_상대로_보인다() {
        assertThat(service.getRoom(DEVELOPER_USER_ID, ROOM_ID).counterpartName()).isEqualTo("김의뢰");
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

    /** 응답의 developerId 는 사용자 식별자가 아니라 방이 들고 있는 개발자 식별자다. */
    @Test
    void 개발자_식별자는_방이_들고_있는_값_그대로_나간다() {
        assertThat(service.getRoom(CLIENT_ID, ROOM_ID).developerId()).isEqualTo(DEVELOPER_ID);
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
        givenRoom("INQUIRY", CONVERTED_ROOM_ID);
        givenConvertedRoom(true);

        var response = service.getRoom(CLIENT_ID, ROOM_ID);

        assertThat(response.convertedRoomId()).isEqualTo(CONVERTED_ROOM_ID);
        assertThat(response.convertedRoomType()).isEqualTo("CONSULTING");
    }

    /** 화면은 둘을 짝으로 보고 버튼을 그린다. 한쪽만 차 있으면 없는 방으로 보낸다. */
    @Test
    void 전환된_방이_지워졌으면_둘_다_비운다() {
        givenRoom("INQUIRY", CONVERTED_ROOM_ID);
        givenConvertedRoom(false);

        var response = service.getRoom(CLIENT_ID, ROOM_ID);

        assertThat(response.convertedRoomId()).isNull();
        assertThat(response.convertedRoomType()).isNull();
    }

    // ------------------------------------------------------------------ 목록

    @Test
    void 개발자도_아니고_맡긴_프로젝트도_없으면_빈_목록이다() {
        givenNoRoles();

        assertThat(service.getRooms(CLIENT_ID, null, null).content()).isEmpty();
        verify(roomRepository, never()).findMine(any(), anyCollection(), any(), any());
    }

    @Test
    void 의뢰인이_보면_그_방의_개발자가_상대다() {
        givenClientWithRoom();

        var content = service.getRooms(CLIENT_ID, null, null).content();

        assertThat(content).hasSize(1);
        assertThat(content.getFirst().counterpartName()).isEqualTo("이준");
        assertThat(content.getFirst().projectName()).isEqualTo("수제 비누 가게");
    }

    @Test
    void 개발자가_보면_그_프로젝트의_의뢰인이_상대다() {
        givenDeveloperWithRoom();

        var content = service.getRooms(DEVELOPER_USER_ID, null, null).content();

        assertThat(content).hasSize(1);
        assertThat(content.getFirst().counterpartName()).isEqualTo("김의뢰");
    }

    @Test
    void 거르는_값은_이름_그대로_내려간다() {
        givenClientWithRoom();

        service.getRooms(CLIENT_ID, RoomType.INQUIRY, RoomStatus.OPEN);

        verify(roomRepository)
                .findMine(isNull(), eq(List.of(PROJECT_ID)), eq("INQUIRY"), eq("OPEN"));
    }

    @Test
    void 거르지_않으면_용도와_개폐를_비워_넘긴다() {
        givenClientWithRoom();

        service.getRooms(CLIENT_ID, null, null);

        verify(roomRepository).findMine(isNull(), eq(List.of(PROJECT_ID)), isNull(), isNull());
    }

    // ------------------------------------------------------------------ 갈래 정하기

    @Test
    void 갈래를_정하면_상담방이_닫히고_새_방을_가리킨다() {
        givenSavedRoom();

        var response = service.convert(CLIENT_ID, ROOM_ID, consultingRequest());

        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(response.convertedRoomId()).isEqualTo(OPENED_ROOM_ID);
        assertThat(response.convertedRoomType()).isEqualTo("CONSULTING");
    }

    /** 새 방은 상담방과 같은 프로젝트·개발자를 물려받고, 식별자가 정해진 뒤 채팅 키를 얻는다. */
    @Test
    void 새_방은_상담방을_물려받고_채팅_키를_얻는다() {
        givenSavedRoom();

        service.convert(CLIENT_ID, ROOM_ID, consultingRequest());

        assertThat(opened.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(opened.getDeveloperId()).isEqualTo(DEVELOPER_ID);
        assertThat(opened.getType()).isEqualTo("CONSULTING");
        assertThat(opened.getStatus()).isEqualTo("OPEN");
        assertThat(opened.getChatRefId()).isEqualTo("room-" + OPENED_ROOM_ID + "-chat");
    }

    @Test
    void 개발_갈래를_고르면_개발방이_열린다() {
        givenSavedRoom();

        var response =
                service.convert(
                        CLIENT_ID, ROOM_ID, new ConvertRoomRequest(ConversionTarget.DEVELOPMENT));

        assertThat(response.convertedRoomType()).isEqualTo("DEVELOPMENT");
    }

    /** 개발자는 그 방의 당사자라 방이 있다는 것을 이미 안다. 감추지 않고 권한이 없다고 답한다. */
    @Test
    void 개발자는_갈래를_정할_수_없다() {
        assertThatThrownBy(() -> service.convert(DEVELOPER_USER_ID, ROOM_ID, consultingRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.FORBIDDEN);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void 남의_방은_갈래_정하기에서도_존재를_감춘다() {
        assertThatThrownBy(() -> service.convert(OUTSIDER_ID, ROOM_ID, consultingRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void 이미_갈래가_정해졌으면_다시_정할_수_없다() {
        givenRoom("INQUIRY", CONVERTED_ROOM_ID);

        assertThatThrownBy(() -> service.convert(CLIENT_ID, ROOM_ID, consultingRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_ALREADY_CONVERTED);
        verify(roomRepository, never()).save(any());
    }

    /** 갈래를 정해 열린 방에는 더 고를 것이 없다. 상담방이 아니면 같게 막는다. */
    @Test
    void 상담방이_아니면_갈래를_정할_수_없다() {
        givenRoom("CONSULTING", null);

        assertThatThrownBy(() -> service.convert(CLIENT_ID, ROOM_ID, consultingRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_ALREADY_CONVERTED);
    }

    // ------------------------------------------------------------------ 준비

    private ConvertRoomRequest consultingRequest() {
        return new ConvertRoomRequest(ConversionTarget.CONSULTING);
    }

    private void givenRoom(String type, Integer convertedRoomId) {
        Room room = BeanUtils.instantiateClass(Room.class);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        ReflectionTestUtils.setField(room, "projectId", PROJECT_ID);
        ReflectionTestUtils.setField(room, "developerId", DEVELOPER_ID);
        ReflectionTestUtils.setField(room, "chatRefId", "room-92-chat");
        ReflectionTestUtils.setField(room, "type", type);
        ReflectionTestUtils.setField(room, "typeGroup", "ROOM_TYPE");
        ReflectionTestUtils.setField(room, "status", "OPEN");
        ReflectionTestUtils.setField(room, "statusGroup", "ROOM_STATUS");
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
        when(roomRepository.findByIdAndDeletedFalse(CONVERTED_ROOM_ID))
                .thenReturn(Optional.of(convertedRoom(CONVERTED_ROOM_ID, "CONSULTING")));
    }

    private Room convertedRoom(Integer id, String type) {
        Room room = BeanUtils.instantiateClass(Room.class);
        ReflectionTestUtils.setField(room, "id", id);
        ReflectionTestUtils.setField(room, "type", type);
        return room;
    }

    /** 저장이 식별자를 달아 돌려주는 것을 흉내 낸다. 채팅 키는 그 뒤에 지어진다. */
    private void givenSavedRoom() {
        when(roomRepository.save(any(Room.class)))
                .thenAnswer(
                        invocation -> {
                            opened = invocation.getArgument(0);
                            ReflectionTestUtils.setField(opened, "id", OPENED_ROOM_ID);
                            return opened;
                        });
        when(roomRepository.findByIdAndDeletedFalse(OPENED_ROOM_ID))
                .thenAnswer(invocation -> Optional.ofNullable(opened));
    }

    private void givenNoRoles() {
        when(developerLookupService.findDeveloperId(CLIENT_ID)).thenReturn(null);
        when(projectQueryService.findProjectIds(CLIENT_ID)).thenReturn(List.of());
    }

    private void givenClientWithRoom() {
        when(developerLookupService.findDeveloperId(CLIENT_ID)).thenReturn(null);
        when(projectQueryService.findProjectIds(CLIENT_ID)).thenReturn(List.of(PROJECT_ID));
        givenListed();
        when(developerLookupService.findUserIds(anyCollection()))
                .thenReturn(Map.of(DEVELOPER_ID, DEVELOPER_USER_ID));
        when(userQueryService.findNames(anyCollection()))
                .thenReturn(Map.of(DEVELOPER_USER_ID, "이준"));
    }

    private void givenDeveloperWithRoom() {
        when(developerLookupService.findDeveloperId(DEVELOPER_USER_ID)).thenReturn(DEVELOPER_ID);
        when(projectQueryService.findProjectIds(DEVELOPER_USER_ID)).thenReturn(List.of());
        givenListed();
        when(developerLookupService.findUserIds(anyCollection())).thenReturn(Map.of());
        when(userQueryService.findNames(anyCollection())).thenReturn(Map.of(CLIENT_ID, "김의뢰"));
    }

    private void givenListed() {
        Room listed = BeanUtils.instantiateClass(Room.class);
        ReflectionTestUtils.setField(listed, "id", ROOM_ID);
        ReflectionTestUtils.setField(listed, "projectId", PROJECT_ID);
        ReflectionTestUtils.setField(listed, "developerId", DEVELOPER_ID);
        ReflectionTestUtils.setField(listed, "chatRefId", "room-92-chat");
        ReflectionTestUtils.setField(listed, "type", "INQUIRY");
        ReflectionTestUtils.setField(listed, "status", "OPEN");
        ReflectionTestUtils.setField(listed, "openedAt", OPENED_AT);
        when(roomRepository.findMine(any(), anyCollection(), any(), any()))
                .thenReturn(List.of(listed));
        when(projectQueryService.findNames(anyCollection()))
                .thenReturn(Map.of(PROJECT_ID, "수제 비누 가게"));
        when(projectQueryService.findClientIds(anyCollection()))
                .thenReturn(Map.of(PROJECT_ID, CLIENT_ID));
    }
}
