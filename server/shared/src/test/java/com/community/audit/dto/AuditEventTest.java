package com.community.audit.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.entity.AuditResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 감사 기록의 덧붙임 값({@link AuditEvent#with}) — null 값도 받는다(관리자 QA 2026-11-17 F-5: 비어 있던 오픈소스 안내 문구를 처음 저장하면
 * {@code with("from", null)} 이 {@code Map.copyOf} 의 NullPointerException 으로 500 이 됐다).
 */
class AuditEventTest {

    @Test
    void null_값도_남기고_넣은_차례를_지킨다() {
        AuditEvent event = AuditEvent.byOperator(1L, "127.0.0.1", "OSS_NOTICE_INTRO_UPDATE", AuditResult.SUCCESS)
                .target("OSS_NOTICE", "intro").with("from", null).with("to", "새 문구").with("count", 3);
        assertThat(event.detail()).containsEntry("from", null).containsEntry("to", "새 문구");
        assertThat(List.copyOf(event.detail().keySet())).containsExactly("from", "to", "count");
    }

    @Test
    void 덧붙임은_바꿀_수_없다() {
        AuditEvent event = AuditEvent.byOperator(1L, null, "X", AuditResult.SUCCESS).with("a", 1);
        assertThatThrownBy(() -> event.detail().put("b", 2)).isInstanceOf(UnsupportedOperationException.class);
    }
}
