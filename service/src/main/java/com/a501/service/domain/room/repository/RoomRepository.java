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

    /** 이 프로젝트에 이 개발자의 방이 있는지. 요청서를 받은 개발자인지 가릴 때 씁니다. */
    boolean existsByProjectIdAndDeveloperIdAndDeletedFalse(Integer projectId, Integer developerId);

    /**
     * 이 프로젝트에서 이 개발자들과 열려 있는 상담방. 몇 번을 요청해도 방은 개발자마다 하나이므로,
     * 새로 열기 전에 이미 있는 방을 찾습니다.
     */
    List<Room> findByProjectIdAndDeveloperIdInAndTypeAndDeletedFalse(
            Integer projectId, Collection<Integer> developerIds, String type);

    List<Room> findByDeveloperIdInAndDeletedFalse(Collection<Integer> developerIds);

    /**
     * 내가 들어가 있는 방. 개발자로 들어간 방과 내가 맡긴 프로젝트의 방을 함께 가져옵니다.
     *
     * <p>개발자가 아니면 {@code developerId} 가 {@code null} 이고, 맡긴 프로젝트가 없으면
     * {@code projectIds} 가 비어 있습니다. 어느 쪽도 아니면 결과가 빕니다.
     *
     * <p>닫힌 방도 함께 옵니다. 갈래가 정해져 닫힌 상담방은 나눈 이야기를 계속 볼 수 있어야
     * 하므로 목록에 남습니다.
     *
     * @param developerId 내 개발자 id. 개발자가 아니면 {@code null}
     * @param projectIds 내가 맡긴 프로젝트 id 목록
     * @param type 방 용도로 거를 값. 비우면 전부
     * @param status 방 개폐로 거를 값. 비우면 전부
     */
    @Query(
            """
            select r
            from Room r
            where r.deleted = false
              and (r.developerId = :developerId or r.projectId in :projectIds)
              and (:type is null or r.type = :type)
              and (:status is null or r.status = :status)
            order by r.openedAt desc, r.id desc
            """)
    List<Room> findMine(
            @Param("developerId") Integer developerId,
            @Param("projectIds") Collection<Integer> projectIds,
            @Param("type") String type,
            @Param("status") String status);
}
