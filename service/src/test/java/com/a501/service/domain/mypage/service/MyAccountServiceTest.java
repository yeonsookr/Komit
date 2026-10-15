package com.a501.service.domain.mypage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.file.FileErrorCode;
import com.a501.service.domain.file.service.FileLookupService;
import com.a501.service.domain.mypage.dto.response.MyAccountResponse;
import com.a501.service.domain.user.dto.MyPageUserView;
import com.a501.service.domain.user.service.UserQueryService;
import com.a501.service.domain.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MyAccountServiceTest {
    private static final String UPLOADED_URL = "https://storage.komit.site/profile-image/512?sig=abc";

    @Mock
    private UserService userService;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private FileLookupService fileLookupService;
    @InjectMocks
    private MyAccountService myAccountService;

    @Test
    @DisplayName("내 파일인지 확인한 뒤 걸고, 새 사진 주소가 담긴 계정 칸을 돌려준다")
    void checksOwnershipThenChangesAndReturnsNewUrl() {
        when(userService.getMyPageUser(12)).thenReturn(new MyPageUserView("김미미", "mimi@kakao.com", 512, null));
        when(userQueryService.findProfileImageUrl(12)).thenReturn(UPLOADED_URL);

        MyAccountResponse response = myAccountService.changeProfileImage(12, 512);

        assertThat(response).isEqualTo(new MyAccountResponse("김미미", "mimi@kakao.com", UPLOADED_URL));
        InOrder order = inOrder(fileLookupService, userService, userQueryService);
        order.verify(fileLookupService).assertAttachable(12, List.of(512));
        order.verify(userService).changeProfileImage(12, 512);
        order.verify(userQueryService).findProfileImageUrl(12);
    }

    @Test
    @DisplayName("이름을 바꾼 뒤 바뀐 이름이 담긴 계정 칸을 돌려준다")
    void changesNameThenReturnsAccount() {
        when(userService.getMyPageUser(12)).thenReturn(new MyPageUserView("새이름", "mimi@kakao.com", null, null));
        when(userQueryService.findProfileImageUrl(12)).thenReturn(null);

        MyAccountResponse response = myAccountService.changeName(12, "새이름");

        assertThat(response).isEqualTo(new MyAccountResponse("새이름", "mimi@kakao.com", null));
        InOrder order = inOrder(userService);
        order.verify(userService).rename(12, "새이름");
        order.verify(userService).getMyPageUser(12);
    }

    @Test
    @DisplayName("걸 수 없는 파일이면 FILE_NOT_ATTACHABLE이고 사진을 바꾸지 않는다")
    void unattachableFileIsNotApplied() {
        doThrow(new BusinessException(FileErrorCode.FILE_NOT_ATTACHABLE))
                .when(fileLookupService).assertAttachable(12, List.of(777));

        assertThatThrownBy(() -> myAccountService.changeProfileImage(12, 777))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(FileErrorCode.FILE_NOT_ATTACHABLE));
        verify(userService, never()).changeProfileImage(any(), any());
    }
}
