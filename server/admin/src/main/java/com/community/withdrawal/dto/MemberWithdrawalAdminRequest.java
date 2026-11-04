package com.community.withdrawal.dto;

/**
 * 회원 탈퇴 관리 요청 본문(admin-web {@code useMemberWithdrawalApi.types.ts}). 검사는 서비스가 한다(400 {@code fields.<칸>}) — 값이 어떤 모양으로 와도 스프링 변환 오류가 나가지 않게
 * 글자·{@code Boolean} 으로 받는다.
 */
public final class MemberWithdrawalAdminRequest {

    private MemberWithdrawalAdminRequest() {
    }

    /** 강제 탈퇴 — 사유 종류 {@code UNDER_14 · POLICY_VIOLATION · ON_BEHALF · OTHER} + 운영 메모 5~500자 */
    public record Force(String reasonKind, String memo) {
    }

    /** 운영자 대리 취소 — 요청 경로 {@code CS_INQUIRY · PHONE · EMAIL · OTHER} + 운영 메모 5~500자 + 「회원 본인이 요청했나요」(늘 true) */
    public record Cancel(String requestChannel, String memo, Boolean memberConfirmed) {
    }
}
