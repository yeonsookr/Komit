package com.a501.service.domain.file.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.file.dto.request.CreateUploadRequest;
import com.a501.service.domain.file.dto.response.UploadTargetResponse;
import com.a501.service.domain.file.dto.response.UploadedFileResponse;
import com.a501.service.domain.file.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "file", description = "파일 업로드와 저장소 검증")
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class FileController {
    private final FileUploadService fileUploadService;

    @Operation(
            summary = "파일 올리기 시작",
            description = "파일 자체를 보내기 전에 이름·형식·크기·용도를 알리고 저장소에 직접 PUT할 임시 주소를 받는다.\n\n"
                    + "- **201**: 보낼 주소를 내줬을 때\n"
                    + "- **400 / FILE_TOO_LARGE**: R-FILE-01의 크기 제한을 넘었을 때\n"
                    + "- **400 / UNSUPPORTED_FILE_TYPE**: R-FILE-02에서 받지 않는 형식일 때\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **422 / INVALID_REQUEST**: 필수 값이 없거나 형식 제약을 어겼을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 주소를 만들지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "업로드 주소 발급"),
            @ApiResponse(responseCode = "400", description = "크기 또는 형식 거절", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UploadTargetResponse start(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateUploadRequest request) {
        return fileUploadService.start(user.userId(), request);
    }

    @Operation(
            summary = "파일 올리기 완료",
            description = "저장소의 객체가 시작 때 알린 크기·형식과 같은지 확인하고 첨부 가능한 파일로 확정한다. 같은 요청은 멱등하다.\n\n"
                    + "- **200**: 파일을 확인했거나 이미 완료된 건일 때\n"
                    + "- **400 / UPLOAD_NOT_COMPLETED**: 저장소에 파일이 없을 때\n"
                    + "- **400 / UPLOAD_SIZE_MISMATCH**: 실제 크기가 다를 때\n"
                    + "- **400 / UPLOAD_TYPE_MISMATCH**: 실제 형식이 다를 때\n"
                    + "- **400 / UPLOAD_EXPIRED**: R-FILE-03의 업로드 기한이 지났을 때\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **403 / FORBIDDEN**: 남이 시작한 업로드 건일 때\n"
                    + "- **404 / UPLOAD_NOT_FOUND**: 업로드 건이 없을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 저장소를 확인하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 완료"),
            @ApiResponse(responseCode = "400", description = "파일 미완료·불일치·만료", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "소유권 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "업로드 건 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{uploadId}/complete")
    UploadedFileResponse complete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer uploadId) {
        return fileUploadService.complete(user.userId(), uploadId);
    }
}
