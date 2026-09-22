package com.a501.service.domain.diagnosis.dto.response;

import com.a501.service.domain.code.dto.CodeResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "검사에서 나온 결함 하나. 진단서와 요구사항 선택에서 함께 쓴다.")
public record IssueResponse(
        @Schema(description = "결함 식별자.", example = "311") Integer issueId,
        @Schema(description = "결함 내용. 요구사항 선택에서는 이 문장이 요구사항이 된다.", example = "관리자 API에 인증 추가")
        String title,
        @Schema(description = "자세한 설명. 없으면 null 이다.") String detail,
        @Schema(description = "문제가 있는 위치. 코드에서 찾지 못했으면 null 이다.", example = "app/api/checkout/route.ts:47")
        String location,
        @Schema(description = "사용자가 적은 증상과 관련된 결함인지. 진단서는 참인 것을 위에 따로 보여준다.", example = "false")
        Boolean isInput,
        @Schema(description = "결함 분류. 보안·위법·결제·기타처럼 화면에 칩으로 붙는다.") CodeResponse category,
        @Schema(description = "결함 처리. UNRESOLVED 미해결, RESOLVED 해결, SUGGESTED 작업 제안됨, CONTRACTED 계약 포함, DISABLED 제안 만료.", example = "UNRESOLVED")
        String status,
        @Schema(description = "위험도. LOW 낮음, MEDIUM 보통, HIGH 높음.", example = "HIGH") String severity,
        @Schema(description = "문제가 사라진 것을 확인한 진단 회차. 아직 남아 있으면 null 이다.") Integer fixedVersion,
        @Schema(description = "지난 회차에서 이어진 결함이면 그 최초 결함 식별자. 이번에 처음 나왔으면 null 이다.")
        Integer originIssueId,
        @Schema(description = "이 요구사항의 확인 문장. 검사 직후에는 비어 있고 요청서를 다듬으며 의뢰인이 채운다.")
        List<QaCriterionResponse> qaCriteria) {}
