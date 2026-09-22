package com.a501.service.domain.room.service;

import com.a501.service.domain.room.dto.RoomCountView;
import com.a501.service.domain.room.entity.Room;
import com.a501.service.domain.room.repository.RoomRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인이 방 엔티티 없이 집계와 식별자만 가져가는 창구입니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomQueryService {
    private final RoomRepository roomRepository;

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
}
