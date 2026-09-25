package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.chat.dto.ChatMessageView;
import com.a501.service.domain.chat.service.ChatMessageService;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.ContractDraftInput;
import com.a501.service.domain.contract.dto.ContractDraftMessage;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.ContractDraftRoomView;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 전환 전 무료 상담 대화를 AI 계약 초안 요청 모양으로 준비합니다. */
@Service
@RequiredArgsConstructor
public class ContractDraftPreparationService {
    private final RoomQueryService roomQueryService;
    private final ChatMessageService chatMessageService;

    /**
     * 새 방의 개발자인지와 정상 전환 출처가 있는지 확인한 뒤 무료 상담 전체 메시지를 변환합니다.
     */
    public ContractDraftInput prepare(Integer roomId, Integer userId) {
        ContractDraftRoomView room = roomQueryService
                .findContractDraftRoom(roomId)
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));

        if (!Objects.equals(room.parties().developerUserId(), userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (room.inquiryChatRefId() == null) {
            throw new BusinessException(ContractErrorCode.ROOM_NOT_CONVERTED);
        }

        List<ContractDraftMessage> messages = chatMessageService
                .findAllChronological(room.inquiryChatRefId())
                .stream()
                .map(message -> toDraftMessage(room, message))
                .toList();
        return new ContractDraftInput(room.targetType(), messages);
    }

    private ContractDraftMessage toDraftMessage(
            ContractDraftRoomView room, ChatMessageView message) {
        ContractDraftMessage.AuthorRole role;
        if (Objects.equals(room.parties().clientId(), message.senderUserId())) {
            role = ContractDraftMessage.AuthorRole.CLIENT;
        } else if (Objects.equals(room.parties().developerUserId(), message.senderUserId())) {
            role = ContractDraftMessage.AuthorRole.DEVELOPER;
        } else {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        return new ContractDraftMessage(
                room.inquiryChatRefId(), role, message.sentAt(), message.content());
    }
}
