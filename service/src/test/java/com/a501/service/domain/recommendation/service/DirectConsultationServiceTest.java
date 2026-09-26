package com.a501.service.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.project.dto.ProjectSummary;
import com.a501.service.domain.project.service.ProjectService;
import com.a501.service.domain.recommendation.RecommendationErrorCode;
import com.a501.service.domain.recommendation.dto.request.CreateDirectConsultationRequest;
import com.a501.service.domain.room.dto.InquiryTarget;
import com.a501.service.domain.room.dto.response.RoomListResponse;
import com.a501.service.domain.room.service.RoomService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectConsultationServiceTest {
    private static final Integer PROJECT_ID = 8;
    private static final Integer USER_ID = 9001;

    private final ProjectService projectService = mock(ProjectService.class);
    private final DeveloperLookupService developerLookupService = mock(DeveloperLookupService.class);
    private final RoomService roomService = mock(RoomService.class);
    private final DirectConsultationService service =
            new DirectConsultationService(projectService, developerLookupService, roomService);

    @BeforeEach
    void setUp() {
        when(projectService.findOwned(PROJECT_ID, USER_ID)).thenReturn(Optional.of(mock(ProjectSummary.class)));
        when(roomService.openInquiries(any(), any(), anyList())).thenReturn(new RoomListResponse(List.of()));
    }

    @Test
    void 고른_개발자마다_추천_항목_없이_방을_연다() {
        when(developerLookupService.findUserIds(List.of(5, 3))).thenReturn(Map.of(5, 105, 3, 103));

        service.request(PROJECT_ID, USER_ID, new CreateDirectConsultationRequest(List.of(5, 3, 5)));

        verify(roomService).openInquiries(
                USER_ID, PROJECT_ID, List.of(new InquiryTarget(null, 5), new InquiryTarget(null, 3)));
    }

    @Test
    void 남의_프로젝트면_404() {
        when(projectService.findOwned(PROJECT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(PROJECT_ID, USER_ID, new CreateDirectConsultationRequest(List.of(5))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RecommendationErrorCode.PROJECT_NOT_FOUND);
        verify(roomService, never()).openInquiries(any(), any(), anyList());
    }

    @Test
    void 없는_개발자가_섞이면_422() {
        when(developerLookupService.findUserIds(List.of(5, 404))).thenReturn(Map.of(5, 105));

        assertThatThrownBy(() -> service.request(PROJECT_ID, USER_ID, new CreateDirectConsultationRequest(List.of(5, 404))))
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_REQUEST);
        verify(roomService, never()).openInquiries(any(), any(), anyList());
    }

    @Test
    void 자기_자신에게는_요청하지_못한다() {
        when(developerLookupService.findUserIds(List.of(5))).thenReturn(Map.of(5, USER_ID));

        assertThatThrownBy(() -> service.request(PROJECT_ID, USER_ID, new CreateDirectConsultationRequest(List.of(5))))
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_REQUEST);
        verify(roomService, never()).openInquiries(any(), any(), anyList());
    }
}
