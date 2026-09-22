package com.a501.service.domain.room.service;

import com.a501.service.domain.project.service.ProjectQueryService;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.entity.Room;
import com.a501.service.domain.room.repository.RoomRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 방을 물어볼 때 쓰는 조회 창구.
 *
 * <p>방은 개발자만 직접 들고 있고 의뢰인은 프로젝트에 있습니다. 그 한 단계를 여기서 넘어 두어야
 * 묻는 쪽이 프로젝트 도메인까지 알 필요가 없습니다. (BACKEND_CONVENTIONS 7.1)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomQueryService {
    private final RoomRepository roomRepository;
    private final ProjectQueryService projectQueryService;

    /**
     * 이 방의 두 당사자.
     *
     * <p>방이 없거나 그 프로젝트를 찾을 수 없으면 비어 있습니다.
     *
     * @param roomId 방 id
     * @return 의뢰인과 개발자
     */
    public Optional<RoomParties> findParties(Integer roomId) {
        return roomRepository
                .findByIdAndDeletedFalse(roomId)
                .flatMap(this::toParties);
    }

    private Optional<RoomParties> toParties(Room room) {
        return projectQueryService
                .findClientId(room.getProjectId())
                .map(clientId -> new RoomParties(clientId, room.getDeveloperId()));
    }
}
