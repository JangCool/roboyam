package com.community.trial.service;

import com.community.permission.dto.AdminActor;
import com.community.report.entity.ReportCaseEntity;
import com.community.report.service.ReportCaseActionService;
import com.community.sanction.dto.request.SanctionWithReportRequest;
import com.community.sanction.dto.response.SanctionLineView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협찬 숨김 확인할 일 처리(점검 D2 — {@code POST /api/admin/review-queue/sponsor-hidden/{postId}/confirm|dismiss}) — 한 트랜잭션에서:
 *
 * <ol>
 *   <li>그 글의 열린 신고 사건을 먼저 잠근다(신고 처리와 같은 잠금 차례 — 사건 → 확인 줄 → 글). 사건의 신고가 모두 협찬 숨김일 때만 함께 닫는다.</li>
 *   <li>확인 = 글을 협찬(SPONSORED)으로 고정 · 무시 = 그대로({@link SponsorReviewService}).</li>
 *   <li>확인에 곁들인 {@code sanction}(신고 처리와 같은 모양) → 글쓴이 회원 제재(제재 권한 검사·오류면 모두 롤백) · 사건 닫기(확인 = 처리 완료 {@code SPONSOR_LABEL}, 무시 = 문제 없음).</li>
 * </ol>
 */
@Service
public class SponsorHiddenActionService {

    /** 확인·무시 본문 — {@code note} 처리 글(300자) · {@code sanction} 확인에 곁들인 회원 제재(선택, 무시에는 쓰지 않는다) */
    public record Request(String note, SanctionWithReportRequest sanction) {
    }

    private final SponsorReviewService sponsors;
    private final ReportCaseActionService reportActions;

    public SponsorHiddenActionService(SponsorReviewService sponsors, ReportCaseActionService reportActions) {
        this.sponsors = sponsors;
        this.reportActions = reportActions;
    }

    /** 확인 — 제재를 곁들였으면 그 줄(없으면 null) */
    @Transactional
    public SanctionLineView confirm(AdminActor actor, String postIdText, Request body) {
        long postId = SponsorReviewService.postId(postIdText);
        ReportCaseEntity c = reportActions.lockSponsorCase(postId);
        String note = body == null ? null : body.note();
        sponsors.confirm(actor.operatorId(), actor.ip(), postIdText, note);
        return reportActions.finishSponsorReview(actor, c, postId, true, clean(note), body == null ? null : body.sanction());
    }

    @Transactional
    public void dismiss(AdminActor actor, String postIdText, Request body) {
        long postId = SponsorReviewService.postId(postIdText);
        ReportCaseEntity c = reportActions.lockSponsorCase(postId);
        String note = body == null ? null : body.note();
        sponsors.dismiss(actor.operatorId(), actor.ip(), postIdText, note);
        reportActions.finishSponsorReview(actor, c, postId, false, clean(note), null);
    }

    private static String clean(String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }
}
