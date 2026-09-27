package com.a501.service.domain.contract.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.contract.service.DeliverableDownloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 인계 파일 내려받기. 권한은 파일마다 따로 본다. (R-QA-06) */
@Tag(name = "계약", description = "개발 계약의 인계 자료")
@RestController
@RequiredArgsConstructor
public class DeliverableDownloadController {
    private final DeliverableDownloadService deliverableDownloadService;

    @Operation(
            summary = "인계 자료 파일 내려받기",
            description =
                    """
                    계약 당사자가 파일 자료 하나를 받는다. TEST 파일은 양쪽이 언제든 받는다. PAID 파일은 올린 개발자는 \
                    언제든, 의뢰인은 대금 지급이 끝나 개발 계약이 COMPLETED 가 된 뒤에만 받는다. 같은 산출물에 URL 이나 \
                    TEST 파일이 있어도 PAID 를 열지 않는다. (R-QA-06)

                    - 200: 본문이 파일 내용이다. 형식은 application/octet-stream, 이름은 Content-Disposition 에 담긴다
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 DELIVERABLE_NOT_FOUND: 없는 자료이거나, 이 계약의 당사자가 아니거나, 받을 파일이 없는 URL 자료일 때
                    - 409 DELIVERABLE_ACCESS_LOCKED: 지급이 끝나기 전 의뢰인이 PAID 파일을 받으려 할 때
                    - 422 INVALID_REQUEST: deliverableMaterialId 가 정수가 아닐 때""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인계 파일. 본문이 파일 내용이다.",
                    content = @Content(mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "자료 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "지급 전 해금 파일", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/api/deliverable-materials/{deliverableMaterialId}/file")
    ResponseEntity<InputStreamResource> downloadMaterial(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "인계 자료 식별자.", example = "501") @PathVariable Integer deliverableMaterialId) {
        return FileDownloads.attachment(deliverableDownloadService.downloadMaterial(user.userId(), deliverableMaterialId));
    }

    @Operation(
            summary = "기존 인계물 파일 내려받기",
            description =
                    """
                    기존 deliverables.file_id 단일 파일만 받는 호환 경로다. 새 TEST·PAID 파일은 \
                    GET /api/deliverable-materials/{deliverableMaterialId}/file 로 받는다. 기존 단일 파일은 TEST·PAID \
                    구분이 없어 지급 뒤 해금 파일로 다룬다. 개발자는 언제든, 의뢰인은 지급이 끝난 뒤에 받는다. (R-QA-06)

                    - 200: 본문이 파일 내용이다
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 DELIVERABLE_NOT_FOUND: 없는 인계물이거나 이 계약의 당사자가 아닐 때
                    - 409 DELIVERABLE_NOT_UPLOADED: 올라온 파일이 없을 때
                    - 409 DELIVERABLE_ACCESS_LOCKED: 지급이 끝나기 전 의뢰인일 때
                    - 422 INVALID_REQUEST: deliverableId 가 정수가 아닐 때""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인계 파일. 본문이 파일 내용이다.",
                    content = @Content(mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "인계물 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "파일 없음이거나 지급 전", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/api/deliverables/{deliverableId}/file")
    ResponseEntity<InputStreamResource> downloadLegacy(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "인계물 식별자.", example = "401") @PathVariable Integer deliverableId) {
        return FileDownloads.attachment(deliverableDownloadService.downloadLegacy(user.userId(), deliverableId));
    }
}
