package com.a501.service.domain.mypage.service;

import com.a501.service.common.time.SeoulTime;
import com.a501.service.domain.account.service.AccountQueryService;
import com.a501.service.domain.consulting.service.ConsultingScheduleQueryService;
import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.diagnosis.dto.DiagnosisSummary;
import com.a501.service.domain.diagnosis.service.DiagnosisQueryService;
import com.a501.service.domain.file.service.FileLookupService;
import com.a501.service.domain.mypage.dto.response.MyAccountResponse;
import com.a501.service.domain.mypage.dto.response.MyDeveloperResponse;
import com.a501.service.domain.mypage.dto.response.MyPageResponse;
import com.a501.service.domain.mypage.dto.response.MyProjectResponse;
import com.a501.service.domain.mypage.dto.response.NotificationSettingResponse;
import com.a501.service.domain.project.dto.MyProjectView;
import com.a501.service.domain.project.service.ProjectQueryService;
import com.a501.service.domain.review.service.ReviewQueryService;
import com.a501.service.domain.room.service.RoomQueryService;
import com.a501.service.domain.setting.service.SettingService;
import com.a501.service.domain.user.dto.MyPageUserView;
import com.a501.service.domain.user.service.UserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지 화면 한 판을 각 도메인의 조회 서비스 결과로 조립합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {
    private final UserService userService;
    private final FileLookupService fileLookupService;
    private final AccountQueryService accountQueryService;
    private final ProjectQueryService projectQueryService;
    private final DiagnosisQueryService diagnosisQueryService;
    private final RoomQueryService roomQueryService;
    private final DeveloperLookupService developerLookupService;
    private final ReviewQueryService reviewQueryService;
    private final ConsultingScheduleQueryService consultingScheduleQueryService;
    private final SettingService settingService;

    public MyPageResponse getMyPage(Integer userId) {
        MyPageUserView user = userService.getMyPageUser(userId);
        List<MyProjectView> projectViews = projectQueryService.findMine(userId);
        List<Integer> projectIds = projectViews.stream().map(MyProjectView::projectId).toList();
        Map<Integer, DiagnosisSummary> diagnoses = diagnosisQueryService.findLatestByProjects(projectIds);
        Map<Integer, Long> roomCounts = roomQueryService.countByProjects(projectIds);
        Integer developerId = developerLookupService.findDeveloperId(userId);

        return new MyPageResponse(
                account(user),
                accountQueryService.findSettlementAccount(userId),
                projectViews.stream()
                        .map(project -> project(project, diagnoses.get(project.projectId()), roomCounts))
                        .toList(),
                developer(developerId),
                new NotificationSettingResponse(settingService.isNotificationEnabled(userId)));
    }

    private MyAccountResponse account(MyPageUserView user) {
        String profileImageUrl = user.kakaoProfileImageUrl();
        if (user.profileImageFileId() != null) {
            profileImageUrl = fileLookupService.findReadUrls(List.of(user.profileImageFileId()))
                    .getOrDefault(user.profileImageFileId(), profileImageUrl);
        }
        return new MyAccountResponse(user.name(), user.email(), profileImageUrl);
    }

    private static MyProjectResponse project(
            MyProjectView project,
            DiagnosisSummary diagnosis,
            Map<Integer, Long> roomCounts) {
        return new MyProjectResponse(
                project.projectId(),
                project.name(),
                diagnosis == null ? null : diagnosis.diagnosisId(),
                diagnosis == null ? null : SeoulTime.of(diagnosis.diagnosedAt()),
                Math.toIntExact(roomCounts.getOrDefault(project.projectId(), 0L)));
    }

    private MyDeveloperResponse developer(Integer developerId) {
        if (developerId == null) {
            return null;
        }
        return new MyDeveloperResponse(
                developerId,
                reviewQueryService.countByDeveloper(developerId),
                consultingScheduleQueryService.countUpcomingByDeveloper(developerId));
    }
}
