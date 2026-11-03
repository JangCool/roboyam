package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/**
 * {@code GROUPBUY_QNA} — 답변 대기 질문 수(OPEN · 숨기지 않음 · 지우지 않음 — 관리자 Q&A 「답변 대기」 탭과 같다) + 오래된 차례 limit 건.
 *
 * @param oldest {@code question} = 공개 질문은 본문 앞부분, 비밀글은 서버 문구(요청 언어)만 — 비밀글 본문은 개인정보 열람 감사가 남는 Q&A 화면에서만 본다
 */
public record DashboardGroupBuyQnaView(long pending, List<Oldest> oldest) implements DashboardGadgetData {

    public record Oldest(String id, String groupBuyTitle, String question, Instant askedAt) {
    }
}
