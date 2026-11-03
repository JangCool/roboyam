package com.community.withdrawal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.withdrawal.* — 회원 탈퇴(docs/domain/commerce.md 17절, docs/features/member-withdrawal-contract.md).
 *
 * @param rejoinBlockDays             같은 이메일·소셜 계정·본인인증 값이 탈퇴 뒤 다시 가입·인증할 수 없는 기간(일, 기본 30). 탈퇴 때 {@code identity_withdrawals.expires_at} 으로 박는다 —
 *                                    나중에 바꿔도 이미 탈퇴한 사람의 기간은 그대로다. 정지·기능 제한 중 탈퇴면 제재 끝나는 날과 비교해 늦은 쪽(D3)
 * @param rejoinBlockMaxDays          재가입 제한의 상한(일, 기본 365) — 무기한 제한 중 탈퇴하면 이 값
 * @param contactRetentionYears       탈퇴 회원 연락용 이메일을 보존 기준일부터 보관하는 기간(년, 기본 5 — 전자상거래법 시행령 제6조)
 * @param contactRetentionBasisMonth  보존 기준일의 달(기본 6) — 기준일 = 마지막 거래일이 속한 해의 다음 해 이 달·이 날(D5: 종합소득세 신고기한 다음날 6월 1일)
 * @param contactRetentionBasisDay    보존 기준일의 날(기본 1)
 * @param progressNoticeDays          탈퇴 신청 뒤 이 일수 안에 완료되지 않으면 진행 상태 메일(개인정보보호법 시행령 제44조 — 기본 10)
 * @param reauthMinutes               소셜 전용 회원의 본인 확인 — 최근 이 분 안에 로그인했어야 한다(기본 10)
 * @param supportEmail                완료 메일·탈퇴 화면의 고객센터 메일(사이트 설정에 연락 메일이 없어 설정값으로 둔다). 비어 있으면 메일 서버의 보내는 주소를 쓴다
 */
@ConfigurationProperties(prefix = "roboyam.withdrawal")
public record WithdrawalProperties(@DefaultValue("30") int rejoinBlockDays, @DefaultValue("365") int rejoinBlockMaxDays, @DefaultValue("5") int contactRetentionYears,
                                   @DefaultValue("6") int contactRetentionBasisMonth, @DefaultValue("1") int contactRetentionBasisDay,
                                   @DefaultValue("10") int progressNoticeDays, @DefaultValue("10") int reauthMinutes, @DefaultValue("") String supportEmail) {

    public WithdrawalProperties {
        if (rejoinBlockDays < 1 || rejoinBlockDays > 3650) {
            throw new IllegalArgumentException("roboyam.withdrawal.rejoin-block-days 는 1~3650 이어야 합니다: " + rejoinBlockDays);
        }
        if (rejoinBlockMaxDays < rejoinBlockDays || rejoinBlockMaxDays > 3650) {
            throw new IllegalArgumentException("roboyam.withdrawal.rejoin-block-max-days 는 rejoin-block-days 이상 3650 이하여야 합니다: " + rejoinBlockMaxDays);
        }
        if (contactRetentionYears < 1 || contactRetentionYears > 50) {
            throw new IllegalArgumentException("roboyam.withdrawal.contact-retention-years 는 1~50 이어야 합니다: " + contactRetentionYears);
        }
        if (contactRetentionBasisMonth < 1 || contactRetentionBasisMonth > 12 || contactRetentionBasisDay < 1 || contactRetentionBasisDay > 28) {
            throw new IllegalArgumentException("roboyam.withdrawal.contact-retention-basis-month/day 는 1~12 · 1~28 이어야 합니다");
        }
        if (progressNoticeDays < 1 || progressNoticeDays > 30) {
            throw new IllegalArgumentException("roboyam.withdrawal.progress-notice-days 는 1~30 이어야 합니다: " + progressNoticeDays);
        }
        if (reauthMinutes < 1 || reauthMinutes > 1440) {
            throw new IllegalArgumentException("roboyam.withdrawal.reauth-minutes 는 1~1440 이어야 합니다: " + reauthMinutes);
        }
        supportEmail = supportEmail == null ? "" : supportEmail.strip();
    }
}
