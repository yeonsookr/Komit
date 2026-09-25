package com.a501.service.domain.mypage.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.time.SeoulTime;
import com.a501.service.domain.auth.service.AuthService;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.ContractProgressView;
import com.a501.service.domain.contract.service.ContractDepositService;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.mypage.MyPageErrorCode;
import com.a501.service.domain.mypage.dto.response.WithdrawalBlockerResponse;
import com.a501.service.domain.mypage.dto.response.WithdrawalBlockerResponse.Type;
import com.a501.service.domain.mypage.dto.response.WithdrawalCheckResponse;
import com.a501.service.domain.mypage.dto.response.WithdrawalResultResponse;
import com.a501.service.domain.project.service.ProjectQueryService;
import com.a501.service.domain.room.dto.MyRoomView;
import com.a501.service.domain.room.service.RoomQueryService;
import com.a501.service.domain.user.service.UserService;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴와 탈퇴 전 확인. 두 API가 같은 조건을 봅니다. (R-ACC-05)
 *
 * <p>막는 것은 셋입니다. 내가 의뢰인이나 개발자로 든 계약 중 마치지 않은 것(협의 중 포함), 내가 의뢰인인 계약의 에스크로 잔액(남은 예치금),
 * 내가 개발자인 계약의 에스크로 잔액(아직 받지 못한 소득)입니다. 잔액은 Escrow 서버에만 있어 계약마다 물어봅니다.
 *
 * <p>목 명세의 403은 두 API 모두 명세 스스로 「나오지 않는다」고 밝힌 대로 실서버에서도 나오지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WithdrawalService {
    private static final String CONTRACT_TITLE = "%s · 진행 중인 의뢰";
    private static final String DEPOSIT_TITLE = "예치금 %s원";
    private static final String INCOME_TITLE = "못 받은 소득 %s원";

    private final UserService userService;
    private final RoomQueryService roomQueryService;
    private final ContractQueryService contractQueryService;
    private final ContractDepositService contractDepositService;
    private final ProjectQueryService projectQueryService;
    private final AuthService authService;

    /**
     * 지금 탈퇴할 수 있는지와 막고 있는 것.
     *
     * <p>잔액을 계약마다 Escrow 서버에 묻는 동안 DB 연결을 붙잡지 않도록 트랜잭션 밖에서 돕니다. 조회는 각 서비스의 읽기 트랜잭션에서 합니다.
     * (BACKEND_CONVENTIONS 16)
     *
     * @throws BusinessException {@code UNAUTHORIZED} 탈퇴했거나 없는 사용자일 때. {@code INTERNAL_ERROR} Escrow 서버에서 잔액을 받지 못했을 때
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WithdrawalCheckResponse check(Integer userId) {
        userService.getActiveUser(userId);
        return WithdrawalCheckResponse.of(findBlockers(userId));
    }

    /**
     * 탈퇴합니다. 탈퇴 전 확인과 같은 조건을 여기서 다시 봅니다. 화면이 버튼을 잠갔더라도 서버가 한 번 더 막습니다.
     *
     * <p><b>트랜잭션 밖에서 시작합니다.</b> 잔액을 Escrow 서버에 묻는 동안 DB 트랜잭션을 붙잡지 않고(BACKEND_CONVENTIONS 16),
     * 읽기 전용 트랜잭션 안에서 쓰기가 읽기 연결로 가지 않게 하기 위해서입니다(6.2). 조회는 각 서비스의 읽기 트랜잭션에서,
     * 삭제 표시는 {@link UserService#withdraw}의 쓰기 트랜잭션에서 합니다.
     *
     * <p>리프레시 토큰은 삭제 표시보다 먼저 지웁니다. 표시를 켠 뒤 토큰 저장소가 실패해도 이미 끝난 탈퇴를 500으로 답하지 않기 위해서입니다.
     * 지운 뒤 표시가 실패하면 다시 로그인하면 되고, 표시가 켜진 계정은 재발급도 막힙니다.
     *
     * <p>조건을 본 뒤 삭제 표시를 켜기까지의 짧은 사이에 상대가 새 계약서를 쓰는 경우는 막지 않습니다. 계약은 계약 도메인이
     * 따로 저장하므로 사용자 행 잠금으로 함께 묶을 수 없습니다. 같은 사용자의 탈퇴 요청끼리는 {@link UserService#withdraw}의 행 잠금이 줄을 세웁니다.
     *
     * @throws BusinessException {@link MyPageErrorCode#WITHDRAWAL_BLOCKED} 진행 중인 의뢰나 남은 돈이 있을 때.
     *                           {@code UNAUTHORIZED} 탈퇴했거나 없는 사용자일 때. {@code INTERNAL_ERROR} 잔액을 받지 못했을 때
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WithdrawalResultResponse withdraw(Integer userId) {
        userService.getActiveUser(userId);
        if (!findBlockers(userId).isEmpty()) {
            throw new BusinessException(MyPageErrorCode.WITHDRAWAL_BLOCKED);
        }
        authService.logout(userId);
        LocalDateTime deletedAt = userService.withdraw(userId);
        return new WithdrawalResultResponse(true, SeoulTime.of(deletedAt));
    }

    private List<WithdrawalBlockerResponse> findBlockers(Integer userId) {
        Map<Integer, MyRoomView> rooms = roomQueryService.findMine(userId).stream()
                .collect(Collectors.toMap(MyRoomView::roomId, Function.identity()));
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<ContractProgressView> contracts = contractQueryService.findProgressByRooms(rooms.keySet());
        Map<Integer, String> projectNames = projectQueryService.findNames(
                rooms.values().stream().map(MyRoomView::projectId).distinct().toList());

        List<WithdrawalBlockerResponse> blockers = new ArrayList<>();
        for (ContractProgressView contract : contracts) {
            MyRoomView room = rooms.get(contract.roomId());
            if (!contract.finished()) {
                blockers.add(new WithdrawalBlockerResponse(Type.CONTRACT,
                        CONTRACT_TITLE.formatted(projectName(projectNames, room)), null,
                        contract.contractId(), contract.roomId()));
            }
            if (contract.concluded()) {
                addMoney(blockers, contract, room);
            }
        }
        return blockers;
    }

    /** 체결된 계약의 잔액. 내 자리에 따라 예치금이나 소득이 되며, 0이면 줄을 만들지 않습니다. */
    private void addMoney(List<WithdrawalBlockerResponse> blockers, ContractProgressView contract, MyRoomView room) {
        long balance = remainingBalance(contract.contractId());
        if (balance <= 0) {
            return;
        }
        if (room.asClient()) {
            blockers.add(money(Type.DEPOSIT, DEPOSIT_TITLE, balance, contract));
        }
        if (room.asDeveloper()) {
            blockers.add(money(Type.INCOME, INCOME_TITLE, balance, contract));
        }
    }

    private static WithdrawalBlockerResponse money(Type type, String title, long amount, ContractProgressView contract) {
        return new WithdrawalBlockerResponse(type, title.formatted(String.format(Locale.KOREA, "%,d", amount)), amount,
                contract.contractId(), contract.roomId());
    }

    /** 계약 도메인의 Escrow 실패 문구는 예치 화면용이라, 명세가 정한 이 API의 500 문구로 바꿉니다. */
    private long remainingBalance(Integer contractId) {
        BigInteger balance;
        try {
            balance = contractDepositService.findRemainingBalance(contractId);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ContractErrorCode.ESCROW_UNAVAILABLE) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
            }
            throw exception;
        }
        return balance.longValueExact();
    }

    /** 방은 늘 살아 있는 프로젝트에 달려 있습니다. 이름이 없으면 확인하지 못한 것으로 보고 탈퇴를 통과시키지 않습니다. */
    private static String projectName(Map<Integer, String> projectNames, MyRoomView room) {
        String name = projectNames.get(room.projectId());
        if (name == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        return name;
    }
}
