package com.a501.service.domain.room.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.diagnosis.service.DiagnosisQueryService;
import com.a501.service.domain.project.service.ProjectQueryService;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.InquiryTarget;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.dto.request.ConvertRoomRequest;
import com.a501.service.domain.room.dto.request.RoomStatus;
import com.a501.service.domain.room.dto.request.RoomType;
import com.a501.service.domain.room.dto.response.RoomDetailResponse;
import com.a501.service.domain.room.dto.response.RoomListResponse;
import com.a501.service.domain.room.dto.response.RoomRequestSummaryResponse;
import com.a501.service.domain.room.dto.response.RoomSummaryResponse;
import com.a501.service.domain.room.entity.Room;
import com.a501.service.domain.room.repository.RoomRepository;
import com.a501.service.domain.user.service.UserQueryService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방 유스케이스.
 *
 * <p><b>404 에 대해.</b> 방은 존재 자체가 민감한 리소스라, 들어가 있지 않은 사람에게는 403 이
 * 아니라 404 로 답해 있다는 사실도 알리지 않습니다. (BACKEND_CONVENTIONS 12.1) 갈래 정하기만
 * 다릅니다 — 개발자는 그 방의 당사자라 방이 있다는 것을 이미 알고 있으므로, 감추지 않고 403 으로
 * 권한이 없다고 답합니다.
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
    private final DeveloperLookupService developerLookupService;

    /**
     * 내가 들어가 있는 방을 모두 가져옵니다.
     *
     * <p>의뢰인이 보면 보낸 의뢰, 개발자가 보면 받은 의뢰가 됩니다. 한 사람이 양쪽일 수 있으므로
     * 역할로 나누지 않고 둘 다 내려주며, 가르는 일은 화면이 {@code developerId} 로 합니다.
     *
     * <p><b>줄 세우는 기준.</b> 마지막으로 움직인 시각으로 세워야 하지만 방에 그 값이 없어 지금은
     * 개설 시각을 씁니다. 메시지가 오거나 계약이 바뀔 때 갱신되는 칸을 두기 전까지는 사이드바가
     * 움직인 순서대로 서지 않습니다.
     *
     * @param userId 인증된 사용자
     * @param type 방 용도로 거를 값. {@code null} 이면 전부
     * @param status 방 개폐로 거를 값. {@code null} 이면 전부
     * @return 조건에 맞는 방. 들어가 있는 방이 없으면 빈 목록
     */
    public RoomListResponse getRooms(Integer userId, RoomType type, RoomStatus status) {
        Integer myDeveloperId = developerLookupService.findDeveloperId(userId);
        List<Integer> myProjectIds = projectQueryService.findProjectIds(userId);
        if (myDeveloperId == null && myProjectIds.isEmpty()) {
            return new RoomListResponse(List.of());
        }

        List<Room> rooms = roomRepository.findMine(myDeveloperId, myProjectIds, name(type), name(status));
        return new RoomListResponse(toSummaries(rooms, myDeveloperId));
    }

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
        Room room = room(roomId);
        RoomParties parties = parties(roomId, userId);
        return detail(room, counterpartName(parties, userId));
    }

    /**
     * 상담을 마치고 컨설팅과 개발 중 하나로 이어갑니다. 상담방은 닫히고 고른 갈래의 방이 새로
     * 열립니다. 나눈 이야기를 계속 볼 수 있도록 닫힌 상담방은 지우지 않습니다.
     *
     * <p><b>갈래는 의뢰인이 정합니다.</b> 상담을 어디로 이어갈지는 돈을 내는 쪽이 고릅니다.
     *
     * @param userId 인증된 사용자
     * @param roomId 상담방 id
     * @param request 이어갈 갈래
     * @return 닫힌 상담방. 새로 열린 방이 {@code convertedRoomId} 에 담긴다
     * @throws BusinessException {@link RoomErrorCode#ROOM_NOT_FOUND} — 없거나 들어가 있지 않은
     *     방, {@link CommonErrorCode#FORBIDDEN} — 이 방의 의뢰인이 아닌 경우,
     *     {@link RoomErrorCode#ROOM_ALREADY_CONVERTED} — 갈래를 정할 수 없는 방인 경우
     */
    @Transactional
    public RoomDetailResponse convert(Integer userId, Integer roomId, ConvertRoomRequest request) {
        Room inquiry = room(roomId);
        RoomParties parties = parties(roomId, userId);
        if (!Objects.equals(parties.clientId(), userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (!inquiry.isConvertible()) {
            throw new BusinessException(RoomErrorCode.ROOM_ALREADY_CONVERTED);
        }

        Room opened = roomRepository.save(Room.openFrom(inquiry, request.targetType().name()));
        opened.assignChatRef();
        inquiry.convertTo(opened.getId());

        return detail(inquiry, counterpartName(parties, userId));
    }

    /**
     * 고른 개발자들에게 무료 상담방을 하나씩 엽니다. 개발자의 수락을 기다리지 않고 그 자리에서 열립니다.
     *
     * <p><b>몇 번을 보내도 방은 개발자마다 하나입니다.</b> 이미 방이 있는 개발자는 그 방을 그대로 담고 새로
     * 만들지 않습니다. 같은 개발자를 두 번 담아 보내도, 새로고침해도 결과가 같습니다. 이미 요청했다는 이유로
     * 막지 않으므로 여러 명 중 한 명이 걸려 나머지가 못 나가는 일도 없습니다.
     *
     * @param targets 방을 열 개발자. 보낸 순서를 지켜 돌려줍니다
     * @return 이번에 연 방과 이미 있던 방
     */
    @Transactional
    public RoomListResponse openInquiries(
            Integer userId, Integer projectId, List<InquiryTarget> targets) {
        if (targets.isEmpty()) {
            return new RoomListResponse(List.of());
        }

        List<Integer> developerIds = targets.stream().map(InquiryTarget::developerId).toList();
        Map<Integer, Room> existing = roomRepository
                .findByProjectIdAndDeveloperIdInAndTypeAndDeletedFalse(
                        projectId, developerIds, RoomType.INQUIRY.name())
                .stream()
                // 같은 개발자에 방이 둘 이상 남아 있으면 먼저 열린 방을 씁니다. 나눈 이야기가 그쪽에 있습니다.
                .collect(Collectors.toMap(
                        Room::getDeveloperId, room -> room, (first, later) -> first));

        List<Room> rooms = new ArrayList<>();
        for (InquiryTarget target : targets) {
            Room room = existing.get(target.developerId());
            if (room == null) {
                room = roomRepository.save(Room.openInquiry(
                        projectId, target.developerId(), target.recommendationItemId()));
                room.assignChatRef();
                existing.put(target.developerId(), room);
            }
            rooms.add(room);
        }

        return new RoomListResponse(
                toSummaries(rooms, developerLookupService.findDeveloperId(userId)));
    }

    private Room room(Integer roomId) {
        return roomRepository
                .findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
    }

    private RoomParties parties(Integer roomId, Integer userId) {
        return roomQueryService
                .findParties(roomId)
                .filter(found -> found.includes(userId))
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
    }

    private RoomDetailResponse detail(Room room, String counterpartName) {
        return RoomDetailResponse.of(
                room,
                projectName(room.getProjectId()),
                counterpartName,
                request(room.getProjectId()),
                contractQueryService.findContractIdByRoomId(room.getId()).orElse(null),
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
                        ? parties.developerUserId()
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

    /**
     * 방마다 프로젝트 이름과 상대 이름을 붙입니다.
     *
     * <p>방 수만큼 묻지 않도록 필요한 식별자를 모아 한 번에 가져옵니다. 상대는 방마다 다릅니다 —
     * 내가 개발자로 들어간 방이면 그 프로젝트의 의뢰인이, 아니면 그 방의 개발자가 상대입니다.
     */
    private List<RoomSummaryResponse> toSummaries(List<Room> rooms, Integer myDeveloperId) {
        Set<Integer> projectIds = new HashSet<>();
        Set<Integer> developerIds = new HashSet<>();
        for (Room room : rooms) {
            projectIds.add(room.getProjectId());
            if (!mineAsDeveloper(room, myDeveloperId)) {
                developerIds.add(room.getDeveloperId());
            }
        }

        Map<Integer, String> projectNames = projectQueryService.findNames(projectIds);
        Map<Integer, Integer> clientIds = projectQueryService.findClientIds(projectIds);
        Map<Integer, Integer> developerUserIds = developerLookupService.findUserIds(developerIds);

        Set<Integer> counterpartIds = new HashSet<>(developerUserIds.values());
        for (Room room : rooms) {
            if (mineAsDeveloper(room, myDeveloperId)) {
                counterpartIds.add(clientIds.get(room.getProjectId()));
            }
        }
        counterpartIds.remove(null);
        Map<Integer, String> names = userQueryService.findNames(counterpartIds);

        List<RoomSummaryResponse> summaries = new ArrayList<>(rooms.size());
        for (Room room : rooms) {
            Integer counterpartId =
                    mineAsDeveloper(room, myDeveloperId)
                            ? clientIds.get(room.getProjectId())
                            : developerUserIds.get(room.getDeveloperId());
            summaries.add(
                    RoomSummaryResponse.of(
                            room, projectNames.get(room.getProjectId()), names.get(counterpartId)));
        }
        return summaries;
    }

    /** 내가 개발자로 들어간 방인지. 개발자가 아니면 늘 아닙니다. */
    private boolean mineAsDeveloper(Room room, Integer myDeveloperId) {
        return myDeveloperId != null && myDeveloperId.equals(room.getDeveloperId());
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
