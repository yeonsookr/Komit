package com.a501.service.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.repository.DiagnosisRepository;
import com.a501.service.domain.project.dto.ProjectSummary;
import com.a501.service.domain.project.service.ProjectService;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 진단을 누가 만질 수 있는지 가리는 규칙을 검증합니다.
 *
 * <p>고치는 것과 읽는 것이 갈립니다. 요구사항을 더하거나 다시 검사하는 일은 의뢰인만 하지만, 상담 요청을
 * 받은 개발자는 무엇을 고쳐야 하는지 읽을 수 있어야 합니다. (`R-REQ-03`)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiagnosisOwnershipTest {
    private static final Integer DIAGNOSIS_ID = 31;
    private static final Integer PROJECT_ID = 8;
    private static final Integer CLIENT_ID = 9001;
    private static final Integer DEVELOPER_USER_ID = 9101;
    private static final Integer STRANGER_ID = 9999;

    @Mock
    private DiagnosisRepository diagnosisRepository;
    @Mock
    private ProjectService projectService;
    @Mock
    private RoomQueryService roomQueryService;

    @InjectMocks
    private DiagnosisOwnership diagnosisOwnership;

    private Diagnosis diagnosis;

    @BeforeEach
    void setUp() {
        diagnosis = Diagnosis.of(PROJECT_ID);
        ReflectionTestUtils.setField(diagnosis, "id", DIAGNOSIS_ID);
        ReflectionTestUtils.setField(diagnosis, "version", (short) 1);

        when(diagnosisRepository.findByIdAndDeletedFalse(DIAGNOSIS_ID))
                .thenReturn(Optional.of(diagnosis));
        when(projectService.findOwned(PROJECT_ID, CLIENT_ID))
                .thenReturn(Optional.of(new ProjectSummary(
                        PROJECT_ID, "수제 비누 가게", "동네 가게", "커머스", List.of())));
        when(roomQueryService.hasRoomAsDeveloper(PROJECT_ID, DEVELOPER_USER_ID)).thenReturn(true);
    }

    @Test
    @DisplayName("고칠 수 있는 사람은 의뢰인뿐이다")
    void onlyClientCanEdit() {
        assertThat(diagnosisOwnership.findOwned(DIAGNOSIS_ID, CLIENT_ID)).isPresent();
        assertThat(diagnosisOwnership.findOwned(DIAGNOSIS_ID, DEVELOPER_USER_ID)).isEmpty();
        assertThat(diagnosisOwnership.findOwned(DIAGNOSIS_ID, STRANGER_ID)).isEmpty();
    }

    @Test
    @DisplayName("요청을 받은 개발자는 읽을 수 있다")
    void invitedDeveloperCanRead() {
        assertThat(diagnosisOwnership.findReadable(DIAGNOSIS_ID, DEVELOPER_USER_ID)).isPresent();
    }

    @Test
    @DisplayName("의뢰인도 읽을 수 있다")
    void clientCanRead() {
        assertThat(diagnosisOwnership.findReadable(DIAGNOSIS_ID, CLIENT_ID)).isPresent();
    }

    @Test
    @DisplayName("방이 없는 개발자는 읽을 수 없다")
    void developerWithoutRoomCannotRead() {
        // 상담 요청을 받으면 방이 열린다. 방이 없다는 것은 이 요청서를 받지 않았다는 뜻이다.
        assertThat(diagnosisOwnership.findReadable(DIAGNOSIS_ID, STRANGER_ID)).isEmpty();
    }

    @Test
    @DisplayName("읽기만 하는 사람인지 가린다")
    void tellsReaderFromOwner() {
        assertThat(diagnosisOwnership.isReaderOnly(diagnosis, CLIENT_ID)).isFalse();
        assertThat(diagnosisOwnership.isReaderOnly(diagnosis, DEVELOPER_USER_ID)).isTrue();
    }

    @Test
    @DisplayName("없는 진단은 둘 다 빈 값이다")
    void missingDiagnosis() {
        when(diagnosisRepository.findByIdAndDeletedFalse(404)).thenReturn(Optional.empty());

        assertThat(diagnosisOwnership.findOwned(404, CLIENT_ID)).isEmpty();
        assertThat(diagnosisOwnership.findReadable(404, CLIENT_ID)).isEmpty();
    }
}
