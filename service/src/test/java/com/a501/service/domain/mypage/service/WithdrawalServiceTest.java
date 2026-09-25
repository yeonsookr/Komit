package com.a501.service.domain.mypage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** R-ACC-05 탈퇴 조건과 목 명세 {@code GET·POST /api/users/me/withdrawal}의 줄 모양을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {
    private static final Integer USER_ID = 12;

    @Mock
    private UserService userService;
    @Mock
    private RoomQueryService roomQueryService;
    @Mock
    private ContractQueryService contractQueryService;
    @Mock
    private ContractDepositService contractDepositService;
    @Mock
    private ProjectQueryService projectQueryService;
    @Mock
    private AuthService authService;
    @InjectMocks
    private WithdrawalService withdrawalService;

    @Test
    @DisplayName("의뢰인 계약은 예치금, 개발자 계약은 소득 줄을 명세의 제목 모양으로 계약 순으로 만든다")
    void buildsBlockersLikeMockExample() {
        givenRooms(new MyRoomView(93, 8, true, false), new MyRoomView(108, 21, false, true));
        when(contractQueryService.findProgressByRooms(Set.of(93, 108))).thenReturn(List.of(
                new ContractProgressView(130, 93, true, false),
                new ContractProgressView(134, 108, true, false)));
        when(projectQueryService.findNames(any())).thenReturn(Map.of(8, "수제 비누 가게", 21, "미미헤어샵 예약"));
        when(contractDepositService.findRemainingBalance(130)).thenReturn(BigInteger.valueOf(650_000));
        when(contractDepositService.findRemainingBalance(134)).thenReturn(BigInteger.valueOf(650_000));

        WithdrawalCheckResponse response = withdrawalService.check(USER_ID);

        assertThat(response.canWithdraw()).isFalse();
        assertThat(response.blockers()).containsExactly(
                new WithdrawalBlockerResponse(Type.CONTRACT, "수제 비누 가게 · 진행 중인 의뢰", null, 130, 93),
                new WithdrawalBlockerResponse(Type.DEPOSIT, "예치금 650,000원", 650_000L, 130, 93),
                new WithdrawalBlockerResponse(Type.CONTRACT, "미미헤어샵 예약 · 진행 중인 의뢰", null, 134, 108),
                new WithdrawalBlockerResponse(Type.INCOME, "못 받은 소득 650,000원", 650_000L, 134, 108));
    }

    @Test
    @DisplayName("협의 중 계약은 막되 Escrow에 묻지 않고, 마친 계약은 남은 잔액만 막는다")
    void negotiatingBlocksWithoutEscrowAndFinishedBlocksOnlyByBalance() {
        givenRooms(new MyRoomView(91, 8, true, false), new MyRoomView(104, 8, true, false),
                new MyRoomView(106, 8, true, false));
        when(contractQueryService.findProgressByRooms(Set.of(91, 104, 106))).thenReturn(List.of(
                new ContractProgressView(132, 91, false, false),
                new ContractProgressView(140, 104, true, true),
                new ContractProgressView(141, 106, true, true)));
        when(projectQueryService.findNames(any())).thenReturn(Map.of(8, "수제 비누 가게"));
        when(contractDepositService.findRemainingBalance(140)).thenReturn(BigInteger.ZERO);
        when(contractDepositService.findRemainingBalance(141)).thenReturn(BigInteger.valueOf(1_200));

        assertThat(withdrawalService.check(USER_ID).blockers()).containsExactly(
                new WithdrawalBlockerResponse(Type.CONTRACT, "수제 비누 가게 · 진행 중인 의뢰", null, 132, 91),
                new WithdrawalBlockerResponse(Type.DEPOSIT, "예치금 1,200원", 1_200L, 141, 106));
        verify(contractDepositService, never()).findRemainingBalance(132);
    }

    @Test
    @DisplayName("R-CON-20·21: 의뢰인만 서명한 계약의 예치금, 취소 뒤 환불 전 잔액도 막고, 잔액 없는 취소 계약은 막지 않는다")
    void depositBeforeConclusionAndCancelledContracts() {
        givenRooms(new MyRoomView(91, 8, true, false), new MyRoomView(104, 8, true, false),
                new MyRoomView(106, 8, true, false));
        when(contractQueryService.findProgressByRooms(Set.of(91, 104, 106))).thenReturn(List.of(
                new ContractProgressView(150, 91, true, false),
                new ContractProgressView(151, 104, true, true),
                new ContractProgressView(152, 106, false, true)));
        when(projectQueryService.findNames(any())).thenReturn(Map.of(8, "수제 비누 가게"));
        when(contractDepositService.findRemainingBalance(150)).thenReturn(BigInteger.valueOf(650_000));
        when(contractDepositService.findRemainingBalance(151)).thenReturn(BigInteger.valueOf(650_000));

        assertThat(withdrawalService.check(USER_ID).blockers()).containsExactly(
                new WithdrawalBlockerResponse(Type.CONTRACT, "수제 비누 가게 · 진행 중인 의뢰", null, 150, 91),
                new WithdrawalBlockerResponse(Type.DEPOSIT, "예치금 650,000원", 650_000L, 150, 91),
                new WithdrawalBlockerResponse(Type.DEPOSIT, "예치금 650,000원", 650_000L, 151, 104));
        verify(contractDepositService, never()).findRemainingBalance(152);
    }

    @Test
    @DisplayName("든 방이 없으면 탈퇴할 수 있고 계약을 묻지 않는다")
    void noRoomsMeansCanWithdraw() {
        when(roomQueryService.findMine(USER_ID)).thenReturn(List.of());

        WithdrawalCheckResponse response = withdrawalService.check(USER_ID);

        assertThat(response.canWithdraw()).isTrue();
        assertThat(response.blockers()).isEmpty();
        verify(contractQueryService, never()).findProgressByRooms(any());
    }

    @Test
    @DisplayName("Escrow에 닿지 못하면 예치 화면 문구가 아니라 명세의 500 INTERNAL_ERROR다")
    void escrowFailureIsInternalError() {
        givenRooms(new MyRoomView(93, 8, true, false));
        when(contractQueryService.findProgressByRooms(Set.of(93)))
                .thenReturn(List.of(new ContractProgressView(130, 93, true, true)));
        when(projectQueryService.findNames(any())).thenReturn(Map.of(8, "수제 비누 가게"));
        when(contractDepositService.findRemainingBalance(130))
                .thenThrow(new BusinessException(ContractErrorCode.ESCROW_UNAVAILABLE));

        assertThatThrownBy(() -> withdrawalService.check(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR));
    }

    @Test
    @DisplayName("탈퇴했거나 없는 사용자면 401이고 조건을 보지 않는다")
    void withdrawnUserCannotCheck() {
        when(userService.getActiveUser(USER_ID)).thenThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED));

        assertThatThrownBy(() -> withdrawalService.check(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
        verify(roomQueryService, never()).findMine(any());
    }

    @Test
    @DisplayName("막는 것이 있으면 409 WITHDRAWAL_BLOCKED이고 토큰도 계정도 건드리지 않는다")
    void blockedWithdrawalChangesNothing() {
        givenRooms(new MyRoomView(91, 8, true, false));
        when(contractQueryService.findProgressByRooms(Set.of(91)))
                .thenReturn(List.of(new ContractProgressView(132, 91, false, false)));
        when(projectQueryService.findNames(any())).thenReturn(Map.of(8, "수제 비누 가게"));

        assertThatThrownBy(() -> withdrawalService.withdraw(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(MyPageErrorCode.WITHDRAWAL_BLOCKED));
        verify(authService, never()).logout(any());
        verify(userService, never()).withdraw(any());
    }

    @Test
    @DisplayName("막는 것이 없으면 토큰을 먼저 지우고 삭제 표시를 켜며, 시각은 한국 시각으로 돌려준다")
    void withdrawsAfterLoggingOut() {
        when(roomQueryService.findMine(USER_ID)).thenReturn(List.of());
        when(userService.withdraw(USER_ID)).thenReturn(LocalDateTime.of(2026, 9, 14, 1, 5));

        WithdrawalResultResponse response = withdrawalService.withdraw(USER_ID);

        assertThat(response).isEqualTo(
                new WithdrawalResultResponse(true, OffsetDateTime.parse("2026-09-14T10:05:00+09:00")));
        InOrder order = inOrder(authService, userService);
        order.verify(authService).logout(USER_ID);
        order.verify(userService).withdraw(USER_ID);
    }

    private void givenRooms(MyRoomView... rooms) {
        when(roomQueryService.findMine(USER_ID)).thenReturn(List.of(rooms));
    }
}
