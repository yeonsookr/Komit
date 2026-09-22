package com.a501.service.domain.room.service;

import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.project.service.ProjectQueryService;
import com.a501.service.domain.room.dto.RoomCountView;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.entity.Room;
import com.a501.service.domain.room.repository.RoomRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 방을 물어볼 때 쓰는 조회 창구. 엔티티 없이 집계와 식별자만 내줍니다.
 *
 * <p>방은 개발자만 직접 들고 있고 의뢰인은 프로젝트에 있습니다. 그 한 단계를 여기서 넘어 두어야
 * 묻는 쪽이 프로젝트 도메인까지 알 필요가 없습니다. (BACKEND_CONVENTIONS 7.1)
 *
 * <p>방이 들고 있는 값은 {@code developers.id} 라 사람이 아닙니다. 묻는 쪽은 로그인한 사용자와
 * 견주려 하므로 여기서 사용자 식별자로 바꿔 내보냅니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomQueryService {
    private final RoomRepository roomRepository;
    private final ProjectQueryService projectQueryService;
    private final DeveloperLookupService developerLookupService;

    public Map<Integer, Long> countByProjects(Collection<Integer> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        return roomRepository.countByProjectIds(projectIds).stream()
                .collect(Collectors.toMap(RoomCountView::getProjectId, RoomCountView::getRoomCount));
    }

    public List<Integer> findIdsByDeveloper(Integer developerId) {
        if (developerId == null) {
            return List.of();
        }
        return roomRepository.findByDeveloperIdAndDeletedFalse(developerId).stream()
                .map(Room::getId)
                .toList();
    }

    /** 여러 개발자의 살아 있는 방. 방 식별자별로 그 방의 개발자 식별자를 돌려줍니다. 개발자별 마친 계약 수를 셀 때 씁니다. */
    public Map<Integer, Integer> findRoomDevelopers(Collection<Integer> developerIds) {
        if (developerIds == null || developerIds.isEmpty()) {
            return Map.of();
        }
        return roomRepository.findByDeveloperIdInAndDeletedFalse(developerIds).stream()
                .collect(Collectors.toMap(Room::getId, Room::getDeveloperId));
    }

    /**
     * 이 방의 두 당사자.
     *
     * <p>방이 없거나, 그 프로젝트나 개발자를 찾을 수 없으면 비어 있습니다.
     *
     * @param roomId 방 id
     * @return 의뢰인과 개발자의 사용자 id
     */
    public Optional<RoomParties> findParties(Integer roomId) {
        return roomRepository.findByIdAndDeletedFalse(roomId).flatMap(this::toParties);
    }

    private Optional<RoomParties> toParties(Room room) {
        return projectQueryService
                .findClientId(room.getProjectId())
                .flatMap(
                        clientId ->
                                developerLookupService
                                        .findUserId(room.getDeveloperId())
                                        .map(developerUserId -> new RoomParties(clientId, developerUserId)));
    }
}
