package com.a501.service.domain.code.controller;

import com.a501.service.domain.code.dto.CodeListResponse;
import com.a501.service.domain.code.service.CodeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Codes", description = "공통 선택 코드")
@RestController
@RequestMapping("/api/codes")
@RequiredArgsConstructor
public class CodeController {
    private final CodeQueryService codeQueryService;

    @Operation(summary = "코드 목록 조회")
    @GetMapping("/{groupKey}")
    ResponseEntity<CodeListResponse> getCodes(@PathVariable String groupKey) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(codeQueryService.getCodes(groupKey));
    }
}
