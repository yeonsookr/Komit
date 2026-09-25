package com.a501.service.domain.mypage.service;

import com.a501.service.domain.file.service.FileLookupService;
import com.a501.service.domain.mypage.dto.response.MyAccountResponse;
import com.a501.service.domain.user.dto.MyPageUserView;
import com.a501.service.domain.user.service.UserQueryService;
import com.a501.service.domain.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지의 계정 칸. 조회와 사진 바꾸기가 같은 모양({@link MyAccountResponse})을 돌려줍니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyAccountService {
    private final UserService userService;
    private final UserQueryService userQueryService;
    private final FileLookupService fileLookupService;

    /**
     * 내 계정 칸.
     *
     * @throws com.a501.service.common.exception.BusinessException {@code UNAUTHORIZED} 탈퇴했거나 없는 사용자일 때
     */
    public MyAccountResponse getAccount(Integer userId) {
        MyPageUserView user = userService.getMyPageUser(userId);
        return new MyAccountResponse(user.name(), user.email(), userQueryService.findProfileImageUrl(userId));
    }

    /**
     * 올려 둔 사진을 내 프로필 사진으로 겁니다. 대상 계정은 토큰의 주인이며 요청에 담지 않습니다.
     *
     * <p>목 명세의 403({@code sub}가 올린 파일이 아니거나 남의 계정에 걸려 할 때)은 실서버에서 나오지 않습니다.
     * 남의 계정을 가리킬 자리가 요청에 없고, 남이 올린 파일은 없는 파일·완료 전 파일과 가르지 않고 400으로 막습니다.
     * 파일의 존재를 드러내지 않기 위해서이며 다른 첨부 API와 같습니다.
     *
     * @throws com.a501.service.common.exception.BusinessException {@code FILE_NOT_ATTACHABLE} 파일이 없거나, 올리기를 마치지 않았거나,
     *                                                             남이 올렸을 때. {@code UNAUTHORIZED} 탈퇴했거나 없는 사용자일 때
     */
    @Transactional
    public MyAccountResponse changeProfileImage(Integer userId, Integer fileId) {
        fileLookupService.assertAttachable(userId, List.of(fileId));
        userService.changeProfileImage(userId, fileId);
        return getAccount(userId);
    }
}
