package com.a501.service.domain.mypage.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** 목 명세 {@code WithdrawalResultResponse}와 같습니다. 계정을 지우지 않고 삭제 표시를 켰다는 것을 돌려줍니다. */
@Schema(description = "탈퇴 처리 결과.\n\n계정을 지우는 것이 아니라 삭제 표시를 켜는 것이므로, 무엇이 바뀌었는지를\n돌려준다.")
public record WithdrawalResultResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "삭제 표시. 탈퇴로 켜진 값이며, 사용자 정보는 지우지 않고 이 표시만 바꾼다.", example = "true")
                @JsonProperty("isDeleted") boolean isDeleted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "삭제 표시를 켠 시각.",
                example = "2026-09-14T10:05:00+09:00")
                @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") OffsetDateTime deletedAt) {
}
