package com.community.title.dto.response;

import com.community.title.dto.response.MyTitlesView.ConditionView;
import java.time.Instant;
import java.util.List;

/**
 * 게시판 매니저의 칭호 신청 한 줄({@code GET /api/boards/{slug}/staff/title-applications} — 계약 「구현 결정」 5, user-web {@code BoardTitleApplication}).
 *
 * @param title      칭호 표시({@code key·label·labelEn·icon·tier·chip})
 * @param member     신청자(ID·닉네임·아바타)
 * @param conditions 신청자의 조건별 채움(대기 중인 신청만 지금 기준, 처리된 신청은 빈 목록)
 * @param reason     거절 사유·승인 메모(대기면 null)
 * @param canReview  내가 심사할 수 있나 — 대기 중이고 내 신청이 아닐 때만 true
 */
public record BoardTitleApplicationView(String id, TitleBadgeView title, Applicant member, String status, List<ConditionView> conditions, String reason, Instant reapplyAt,
                                        Instant createdAt, Instant reviewedAt, boolean canReview) {

    public record Applicant(String id, String nickname, String avatarUrl) {
    }
}
