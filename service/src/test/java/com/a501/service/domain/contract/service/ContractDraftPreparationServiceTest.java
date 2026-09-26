package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.chat.dto.ChatMessageView;
import com.a501.service.domain.chat.service.ChatMessageService;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.ContractDraftMessage;
import com.a501.service.domain.diagnosis.dto.DraftRequirements;
import com.a501.service.domain.diagnosis.service.ContractRequirementService;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.ContractDraftRoomView;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractDraftPreparationServiceTest {
    private static final Integer ROOM_ID = 92;
    private static final Integer CLIENT_ID = 7;
    private static final Integer DEVELOPER_ID = 41;
    private static final Integer STRANGER_ID = 99;
    private static final String INQUIRY_CHAT_REF_ID = "room-91-chat";
    private static final Instant BASE = Instant.parse("2026-09-24T04:00:00Z");

    private final RoomQueryService roomQueryService = mock(RoomQueryService.class);
    private final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    private final ContractRequirementService contractRequirementService = mock(ContractRequirementService.class);
    private final ContractDraftPreparationService service =
            new ContractDraftPreparationService(roomQueryService, chatMessageService, contractRequirementService);

    @BeforeEach
    void setUp() {
        when(roomQueryService.findContractDraftRoom(ROOM_ID))
                .thenReturn(Optional.of(room(INQUIRY_CHAT_REF_ID)));
    }

    @Test
    void 원래_무료_상담의_전체_메시지를_AI_입력으로_바꾼다() {
        when(chatMessageService.findAllChronological(INQUIRY_CHAT_REF_ID))
                .thenReturn(List.of(
                        new ChatMessageView(CLIENT_ID, "결제가 실패합니다", BASE),
                        new ChatMessageView(DEVELOPER_ID, "재현 과정을 보겠습니다", BASE.plusSeconds(60))));

        var input = service.prepare(ROOM_ID, DEVELOPER_ID);

        assertThat(input.targetType()).isEqualTo("CONSULTING");
        assertThat(input.messages()).extracting(ContractDraftMessage::chatRefId)
                .containsOnly(INQUIRY_CHAT_REF_ID);
        assertThat(input.messages()).extracting(ContractDraftMessage::authorRole)
                .containsExactly(
                        ContractDraftMessage.AuthorRole.CLIENT,
                        ContractDraftMessage.AuthorRole.DEVELOPER);
        assertThat(input.messages()).extracting(ContractDraftMessage::content)
                .containsExactly("결제가 실패합니다", "재현 과정을 보겠습니다");
    }

    @Test
    void 개발자가_아니면_전체_채팅을_조회하지_않는다() {
        assertThatThrownBy(() -> service.prepare(ROOM_ID, STRANGER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.FORBIDDEN);

        verify(chatMessageService, never()).findAllChronological(INQUIRY_CHAT_REF_ID);
    }

    @Test
    void 없는_방이면_전체_채팅을_조회하지_않는다() {
        when(roomQueryService.findContractDraftRoom(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare(ROOM_ID, DEVELOPER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_NOT_FOUND);

        verify(chatMessageService, never()).findAllChronological(INQUIRY_CHAT_REF_ID);
    }

    @Test
    void 전환_출처가_없으면_전체_채팅을_조회하지_않는다() {
        when(roomQueryService.findContractDraftRoom(ROOM_ID))
                .thenReturn(Optional.of(room(null)));

        assertThatThrownBy(() -> service.prepare(ROOM_ID, DEVELOPER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.ROOM_NOT_CONVERTED);

        verify(chatMessageService, never()).findAllChronological(INQUIRY_CHAT_REF_ID);
    }

    @Test
    void 방_당사자가_아닌_발신자가_섞이면_입력을_만들지_않는다() {
        when(chatMessageService.findAllChronological(INQUIRY_CHAT_REF_ID))
                .thenReturn(List.of(new ChatMessageView(STRANGER_ID, "섞인 메시지", BASE)));

        assertThatThrownBy(() -> service.prepare(ROOM_ID, DEVELOPER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INTERNAL_ERROR);
    }

    private static ContractDraftRoomView room(String inquiryChatRefId) {
        return room("CONSULTING", inquiryChatRefId);
    }

    private static ContractDraftRoomView room(String targetType, String inquiryChatRefId) {
        return new ContractDraftRoomView(
                ROOM_ID,
                targetType,
                inquiryChatRefId,
                new RoomParties(CLIENT_ID, DEVELOPER_ID),
                8);
    }

    @Test
    void 개발방은_요청서와_고르지_않은_요구사항을_함께_담는다() {
        when(roomQueryService.findContractDraftRoom(ROOM_ID))
                .thenReturn(Optional.of(room("DEVELOPMENT", INQUIRY_CHAT_REF_ID)));
        when(chatMessageService.findAllChronological(INQUIRY_CHAT_REF_ID))
                .thenReturn(List.of(new ChatMessageView(CLIENT_ID, "관리자 화면이 열립니다", BASE)));
        var requested = new DraftRequirements.Requested(
                312, "관리자 화면에 인증이 없다", null, List.of("로그인 화면이 뜬다"));
        var leftOut = new DraftRequirements.LeftOut(313, "결제가 가끔 실패한다");
        when(contractRequirementService.findDraftRequirements(8))
                .thenReturn(new DraftRequirements(List.of(requested), List.of(leftOut)));

        var input = service.prepare(ROOM_ID, DEVELOPER_ID);

        assertThat(input.targetType()).isEqualTo("DEVELOPMENT");
        assertThat(input.issues()).containsExactly(requested);
        assertThat(input.rejectedIssues()).containsExactly(leftOut);
    }

    @Test
    void 컨설팅방은_요청서를_읽지_않는다() {
        when(chatMessageService.findAllChronological(INQUIRY_CHAT_REF_ID))
                .thenReturn(List.of(new ChatMessageView(CLIENT_ID, "결제가 실패합니다", BASE)));

        var input = service.prepare(ROOM_ID, DEVELOPER_ID);

        assertThat(input.issues()).isEmpty();
        assertThat(input.rejectedIssues()).isEmpty();
        verifyNoInteractions(contractRequirementService);
    }
}
