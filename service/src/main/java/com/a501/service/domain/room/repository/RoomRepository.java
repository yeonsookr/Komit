package com.a501.service.domain.room.repository;

import com.a501.service.domain.room.entity.Room;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Integer> {
    Optional<Room> findByIdAndDeletedFalse(Integer id);
}
