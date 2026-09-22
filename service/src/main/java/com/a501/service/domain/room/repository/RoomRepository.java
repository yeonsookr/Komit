package com.a501.service.domain.room.repository;

import com.a501.service.domain.room.dto.RoomCountView;
import com.a501.service.domain.room.entity.Room;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Integer> {
    Optional<Room> findByIdAndDeletedFalse(Integer id);

    @Query("""
            select room.projectId as projectId, count(room.id) as roomCount
            from Room room
            where room.projectId in :projectIds and room.deleted = false
            group by room.projectId
            """)
    List<RoomCountView> countByProjectIds(@Param("projectIds") Collection<Integer> projectIds);

    List<Room> findByDeveloperIdAndDeletedFalse(Integer developerId);

    List<Room> findByDeveloperIdInAndDeletedFalse(Collection<Integer> developerIds);
}
