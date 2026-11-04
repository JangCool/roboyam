package com.community.withdrawal.dto;

import com.community.withdrawal.entity.WithdrawalStatus;
import java.time.Instant;
import java.util.List;

/**
 * 탈퇴 신청 처리({@code WithdrawalRequestWriter#apply})의 결과 — api 는 {@code WithdrawalResult}(회원 화면), admin 은 {@code ForceWithdrawResult} 로 바꾼다.
 *
 * @param status            {@link WithdrawalStatus#COMPLETED}(같은 트랜잭션에서 익명화까지 끝남) · {@link WithdrawalStatus#SCHEDULED}(남은 일이 있어 진행 중)
 * @param pending           남은 일(완료면 빈 목록)
 * @param expectedCompleteAt 예상 완료 시각(모르면 null)
 * @param rejoinUntil       재가입 가능 시각(완료면 확정값, 진행 중이면 지금 기준 예상값)
 * @param hasPaymentHistory 결제 이력이 있어 연락용 이메일을 분리 보관하는가(했는가)
 * @param retentionYears    연락용 이메일 보관 기간(년)
 * @param supportEmail      고객센터 메일
 * @param completedAt       완료 시각(진행 중이면 null)
 */
public record WithdrawalOutcome(long withdrawalId, WithdrawalStatus status, List<WithdrawalPending> pending, Instant expectedCompleteAt, Instant rejoinUntil,
                                boolean hasPaymentHistory, int retentionYears, String supportEmail, Instant completedAt) {

    public WithdrawalOutcome {
        pending = pending == null ? List.of() : List.copyOf(pending);
    }

    public boolean completed() {
        return status == WithdrawalStatus.COMPLETED;
    }
}
