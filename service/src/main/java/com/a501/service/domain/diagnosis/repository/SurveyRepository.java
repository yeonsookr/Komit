package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Survey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyRepository extends JpaRepository<Survey, Integer> {}
