package com.a501.service.domain.consulting.repository;

import com.a501.service.domain.consulting.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Integer> {}
