package com.community.report.dto.response;

import com.community.sanction.dto.response.SanctionPreviewView;
import java.time.Instant;

/**
 * 작성자 카드 — 비회원이면 id·grade·joinedAt null, 닫힌 사건 수 0. {@code grade} 는 등급 키. {@code sanction} 은 작성자의 제재 현황(제재 API {@code preview} 와 같은 모양,
 * member-sanction-contract) — 비회원·탈퇴(진행 중 포함) 회원이면 null
 */
public record ReportAuthorCardView(String id, String name, String grade, Instant joinedAt, ReportClosedCasesView closedCases, SanctionPreviewView sanction,
                                   boolean sealed) {

    /** 비회원·회원 카드(봉인 아님) */
    public ReportAuthorCardView(String id, String name, String grade, Instant joinedAt, ReportClosedCasesView closedCases, SanctionPreviewView sanction) {
        this(id, name, grade, joinedAt, closedCases, sanction, false);
    }

    /**
     * 익명 글·댓글(V184)의 작성자 카드 — ID·이름·등급·가입일 null, 닫힌 사건 수 0, 제재 현황은 봉인된 모양({@code user.sealed true} — 「이 글 작성자 제재」 대화창)
     */
    public static ReportAuthorCardView sealed(SanctionPreviewView sanction) {
        return new ReportAuthorCardView(null, null, null, null, new ReportClosedCasesView(0, 0), sanction, true);
    }
}
