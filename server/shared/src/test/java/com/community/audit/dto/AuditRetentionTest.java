package com.community.audit.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditLogEntity;
import com.community.audit.entity.AuditResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 감사 보관 분류(docs/domain/commerce.md 13-4, commerce-foundation-contract 결정 13 · 「0-5」) — 행위마다 분류, 분류별 기간·하한 */
class AuditRetentionTest {

    @Test
    void 모든_감사_행위가_분류표에_있다_새_행위를_더하면_분류를_정해야_한다() {
        List<String> missing = AuditActions.all().stream().filter(action -> AuditRetention.declared(action).isEmpty()).toList();
        assertThat(missing).as("AuditRetention 표에 없는 행위 — 일반·개인정보 열람·돈 관련 중 하나로 정한다").isEmpty();
    }

    @Test
    void 돈_관련은_5년_분류_포인트_수동_조정과_감사_보관_기간_변경() {
        assertThat(AuditRetention.classify(AuditActions.POINT_ADJUST)).isEqualTo(AuditRetentionClass.MONEY);
        assertThat(AuditRetention.classify(AuditActions.AUDIT_RETENTION_UPDATE)).isEqualTo(AuditRetentionClass.MONEY);
    }

    @Test
    void 개인정보_열람은_2년_분류_회원_상세_열람() {
        assertThat(AuditRetention.classify(AuditActions.MEMBER_VIEW)).isEqualTo(AuditRetentionClass.PERSONAL_DATA);
    }

    @Test
    void 설정_변경_게시판_메뉴_관리_로그인은_일반_운영() {
        for (String action : List.of(AuditActions.POINT_SETTINGS_UPDATE, AuditActions.BOARD_UPDATE, AuditActions.MENU_TREE_UPDATE, AuditActions.LOGIN_SUCCESS,
                AuditActions.COMMERCE_REQUEST_LIMIT_UPDATE, AuditActions.MFA_SETTINGS_UPDATE,
                // 세션 정리 결정(2026-10-26): 글 삭제·복구는 포인트를 회수·되돌려도 콘텐츠 관리 — 돈 관련이 아니다
                AuditActions.POST_DELETE, AuditActions.POST_RESTORE, AuditActions.REPORT_RESOLVE, AuditActions.TERMS_PUBLISH)) {
            assertThat(AuditRetention.classify(action)).as(action).isEqualTo(AuditRetentionClass.GENERAL);
        }
        assertThat(AuditRetention.classify("OLD_UNKNOWN_ACTION")).as("표에 없는 옛 이름").isEqualTo(AuditRetentionClass.GENERAL);
        assertThat(AuditRetention.classify(null)).isEqualTo(AuditRetentionClass.GENERAL);
    }

    @Test
    void 감사_줄을_남길_때_행위로_분류가_붙는다() {
        AuditLogEntity money = AuditLogEntity.create(1L, Instant.now(), AuditActorType.OPERATOR, 1L, AuditActions.POINT_ADJUST, AuditResult.SUCCESS, "MEMBER", "1", null, null, null);
        AuditLogEntity pii = AuditLogEntity.create(2L, Instant.now(), AuditActorType.OPERATOR, 1L, AuditActions.MEMBER_VIEW, AuditResult.SUCCESS, "MEMBER", "1", null, null, null);
        AuditLogEntity general = AuditLogEntity.create(3L, Instant.now(), AuditActorType.OPERATOR, 1L, AuditActions.LOGIN_SUCCESS, AuditResult.SUCCESS, null, null, null, null, null);
        assertThat(money.getRetentionClass()).isEqualTo("MONEY");
        assertThat(pii.getRetentionClass()).isEqualTo("PERSONAL_DATA");
        assertThat(general.getRetentionClass()).isEqualTo("GENERAL");
    }

    @Test
    void 기본_기간과_하한은_일반_1년_개인정보_열람_2년_하한_1년_돈_관련_5년_하한_5년() {
        assertThat(AuditRetentionClass.GENERAL.defaultDays()).isEqualTo(365);
        assertThat(AuditRetentionClass.GENERAL.minDays()).isEqualTo(1);
        assertThat(AuditRetentionClass.PERSONAL_DATA.defaultDays()).isEqualTo(730);
        assertThat(AuditRetentionClass.PERSONAL_DATA.minDays()).isEqualTo(365);
        assertThat(AuditRetentionClass.MONEY.defaultDays()).isEqualTo(1825);
        assertThat(AuditRetentionClass.MONEY.minDays()).isEqualTo(1825);
        assertThat(AuditRetentionClass.MAX_DAYS).isEqualTo(3650);
        assertThat(AuditRetentionClass.PERSONAL_DATA.allows(364)).isFalse();
        assertThat(AuditRetentionClass.PERSONAL_DATA.allows(365)).isTrue();
        assertThat(AuditRetentionClass.MONEY.allows(1824)).isFalse();
        assertThat(AuditRetentionClass.MONEY.allows(3651)).isFalse();
        assertThat(AuditRetentionClass.GENERAL.paramKey()).as("옛 단일 기간 칸을 일반 운영이 이어 쓴다").isEqualTo("retention_days");
    }
}
