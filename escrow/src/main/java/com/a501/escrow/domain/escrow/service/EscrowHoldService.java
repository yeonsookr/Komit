package com.a501.escrow.domain.escrow.service;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.crypto.RequestFingerprint;
import com.a501.escrow.common.exception.EscrowApiException;
import com.a501.escrow.common.security.ApiRequirements;
import com.a501.escrow.common.security.AuthenticatedCaller;
import com.a501.escrow.common.security.InternalApiSecurity;
import com.a501.escrow.domain.escrow.dto.EscrowHoldDtos;
import com.a501.escrow.domain.escrow.repository.EscrowHoldRepository;
import com.a501.escrow.domain.escrow.repository.EscrowHoldRepository.StateCommand;
import com.a501.escrow.domain.escrow.repository.EscrowRepository;
import com.a501.escrow.domain.escrow.repository.LockedEscrow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscrowHoldService {
    private static final String HOLD_API = "holdEscrow";
    private static final String UNHOLD_API = "unholdEscrow";

    private final InternalApiSecurity security;
    private final RequestFingerprint fingerprints;
    private final EscrowRepository escrows;
    private final EscrowHoldRepository holds;

    public EscrowHoldService(InternalApiSecurity security, RequestFingerprint fingerprints,
            EscrowRepository escrows, EscrowHoldRepository holds) {
        this.security = security;
        this.fingerprints = fingerprints;
        this.escrows = escrows;
        this.holds = holds;
    }

    @Transactional
    public EscrowHoldDtos.Record hold(EscrowHoldDtos.Request request) {
        AuthenticatedCaller.Client caller = client(request.header(), HOLD_API);
        String fingerprint = fingerprints.create(request);
        LockedEscrow escrow = lock(caller.serviceClientId(), request.escrowId(), request.header());
        StateCommand replay = holds.findCommand(caller.serviceClientId(), HOLD_API,
                request.header().idempotencyKey()).orElse(null);
        if (replay != null) return replay(replay, request, fingerprint, true);
        requireActive(escrow, request.header());
        if ("HELD".equals(escrow.holdStatus())) {
            requireSameReport(escrow, request.reportId(), request.header());
            holds.recordExistingHold(caller.serviceClientId(), escrow.id(), request.reportId(), HOLD_API,
                    request.header().idempotencyKey(), fingerprint);
        } else {
            if ("RELEASED".equals(escrow.holdStatus())
                    && request.reportId().equals(escrow.externalReportId())) {
                throw conflict("종결된 신고로 다시 동결할 수 없습니다.", request.header());
            }
            holds.hold(caller.serviceClientId(), escrow.id(), request.reportId(), HOLD_API,
                    request.header().idempotencyKey(), fingerprint);
        }
        return new EscrowHoldDtos.Record(escrow.id(), true);
    }

    @Transactional
    public EscrowHoldDtos.Record unhold(EscrowHoldDtos.Request request) {
        AuthenticatedCaller.Client caller = client(request.header(), UNHOLD_API);
        String fingerprint = fingerprints.create(request);
        LockedEscrow escrow = lock(caller.serviceClientId(), request.escrowId(), request.header());
        StateCommand replay = holds.findCommand(caller.serviceClientId(), UNHOLD_API,
                request.header().idempotencyKey()).orElse(null);
        if (replay != null) return replay(replay, request, fingerprint, false);
        requireActive(escrow, request.header());
        if (!"HELD".equals(escrow.holdStatus())) {
            throw conflict("해제할 활성 동결이 없습니다.", request.header());
        }
        requireSameReport(escrow, request.reportId(), request.header());
        holds.release(caller.serviceClientId(), escrow.id(), request.reportId(), UNHOLD_API,
                request.header().idempotencyKey(), fingerprint);
        return new EscrowHoldDtos.Record(escrow.id(), false);
    }

    private AuthenticatedCaller.Client client(EscrowRequestHeader header, String apiName) {
        return (AuthenticatedCaller.Client) security.authenticate(header, apiName, ApiRequirements.write(false));
    }

    private LockedEscrow lock(long clientId, long escrowId, EscrowRequestHeader header) {
        return escrows.findOwnedByIdForUpdate(clientId, escrowId)
                .orElseThrow(() -> error("E5004", "에스크로를 찾을 수 없습니다.", header));
    }

    private static void requireActive(LockedEscrow escrow, EscrowRequestHeader header) {
        if (!"ACTIVE".equals(escrow.status())) {
            throw error("E5007", "동결 상태를 변경할 수 없는 계약입니다.", header);
        }
    }

    private static void requireSameReport(LockedEscrow escrow, String reportId, EscrowRequestHeader header) {
        if (!reportId.equals(escrow.externalReportId())) {
            throw conflict("현재 동결의 신고 식별자와 다릅니다.", header);
        }
    }

    private static EscrowHoldDtos.Record replay(StateCommand command, EscrowHoldDtos.Request request,
            String fingerprint, boolean held) {
        if (command.escrowId() != request.escrowId() || !command.reportId().equals(request.reportId())
                || !command.fingerprint().equals(fingerprint)) {
            throw error("E5003", "같은 멱등키로 다른 요청이 접수되었습니다.", request.header());
        }
        return new EscrowHoldDtos.Record(command.escrowId(), held);
    }

    private static EscrowApiException conflict(String message, EscrowRequestHeader header) {
        return error("E5014", message, header);
    }

    private static EscrowApiException error(String code, String message, EscrowRequestHeader header) {
        return new EscrowApiException(HttpStatus.BAD_REQUEST, code, message, header);
    }
}
