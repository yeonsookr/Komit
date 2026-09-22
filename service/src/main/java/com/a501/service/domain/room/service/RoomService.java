package com.a501.service.domain.room.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.diagnosis.service.DiagnosisQueryService;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.dto.response.RoomDetailResponse;
import com.a501.service.domain.room.dto.response.RoomRequestSummaryResponse;
import com.a501.service.domain.room.entity.Room;
import com.a501.service.domain.room.repository.RoomRepository;
import com.a501.service.domain.project.service.ProjectQueryService;
import com.a501.service.domain.user.service.UserQueryService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방 유스케이스.
 *
 * <p><b>404 에 대해.</b> 방은 존재 자체가 민감한 리소스라, 들어가 있지 않은 사람에게는 403 이
 * 아니라 404 로 답해 있다는 사실도 알리지 않습니다. (BACKEND_CONVENTIONS 12.1)
 *
 * <p>머리에 붙는 값들은 다른 도메인에 있습니다. 프로젝트 이름·상대 이름·요청서 요약·계약
 * 식별자를 각 도메인의 Service 에 묻습니다. (BACKEND_CONVENTIONS 7.1)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {
    private final RoomRepository roomRepository;
    private final RoomQueryService roomQueryService;
    private final ProjectQueryService projectQueryService;
    private final UserQueryService userQueryService;
    private final ContractQueryService contractQueryService;
    private final DiagnosisQueryService diagnosisQueryService;

    /**
     * 방 한 건을 조회합니다.
     *
     * @param userId 인증된 사용자
     * @param roomId 방 id
     * @return 방 상세
     * @throws BusinessException {@link RoomErrorCode#ROOM_NOT_FOUND} — 없거나 지워진 방, 또는
     *     요청자가 그 방에 들어가 있지 않은 경우
     */
    public RoomDetailResponse getRoom(Integer userId, Integer roomId) {
        Room room =
                roomRepository
                        .findByIdAndDeletedFalse(roomId)
                        .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));

        RoomParties parties =
                roomQueryService
                        .findParties(roomId)
                        .filter(found -> found.includes(userId))
                        .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));

        return RoomDetailResponse.of(
                room,
                projectName(room.getProjectId()),
                counterpartName(parties, userId),
                request(room.getProjectId()),
                contractQueryService.findContractIdByRoomId(roomId).orElse(null),
                convertedRoom(room));
    }

    private String projectName(Integer projectId) {
        return projectQueryService
                .findName(projectId)
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
    }

    /** 요청한 사람이 의뢰인이면 개발자를, 개발자면 의뢰인을 보여 줍니다. */
    private String counterpartName(RoomParties parties, Integer userId) {
        Integer counterpartId =
                Objects.equals(parties.clientId(), userId)
                        ? parties.developerId()
                        : parties.clientId();
        return userQueryService
                .findName(counterpartId)
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
    }

    private RoomRequestSummaryResponse request(Integer projectId) {
        return diagnosisQueryService
                .findRequestSummary(projectId)
                .map(RoomRequestSummaryResponse::from)
                .orElse(null);
    }

    /** 가리키는 방이 지워졌으면 없는 것으로 다룹니다. 식별자와 용도가 함께 비어야 합니다. */
    private Room convertedRoom(Room room) {
        return room.getConvertedRoomId() == null
                ? null
                : roomRepository.findByIdAndDeletedFalse(room.getConvertedRoomId()).orElse(null);
    }
}
