package com.community.title.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.entity.BoardStaffEntity;
import com.community.board.entity.BoardStaffId;
import com.community.board.repository.master.BoardStaffRepository;
import com.community.title.entity.TitleApplicationEntity;
import com.community.title.entity.TitleApplicationStatus;
import com.community.title.entity.TitleEntity;
import com.community.title.entity.TitleReviewer;
import com.community.title.entity.TitleSource;
import com.community.title.exception.TitleException;
import com.community.title.repository.master.TitleApplicationRepository;
import com.community.title.repository.master.TitleQueries;
import com.community.title.repository.master.TitleRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 칭호 신청 심사(member-title-contract 「규칙」·「구현 결정」 5) — 운영자(관리자 앱, {@code title.review})와 게시판 매니저(공개 사이트, 그 게시판에 연결된 칭호만)가 같은 길을 쓴다.
 *
 * <ul>
 *   <li>신청 행을 잠그고 대기가 아니면 409 {@code TITLE_APPLICATION_CLOSED}.</li>
 *   <li>게시판 매니저: 칭호의 심사자가 「운영자 + 연결 게시판 매니저」이고 그 연결 게시판의 매니저({@code MANAGER} — 스텝 아님)여야 한다(아니면 403 {@code TITLE_REVIEW_FORBIDDEN}),
 *       자기 신청은 403 {@code TITLE_REVIEW_SELF}.</li>
 *   <li>승인: 신청자가 지금도 받을 수 있어야 하고(404·409) 칭호를 넣는다(신청 승인, 단계 없음 — 리뷰어 단계는 체험단 기능 때). 거절: 사유 필수, 다시 신청할 수 있는 날 = 지금 + 설정 일수(기본 30일).</li>
 *   <li>승인·거절 모두 감사 기록 — 운영자는 운영자 행위자, 매니저는 회원 행위자({@code TITLE_APPLICATION_APPROVE}·{@code TITLE_APPLICATION_REJECT}, 대상 MEMBER).</li>
 * </ul>
 */
@Service
public class TitleReviewService {

    /** 심사한 사람 — 운영자 또는 게시판 매니저(회원) 중 하나 */
    public record Reviewer(Long operatorId, Long memberId, String ip) {

        public static Reviewer operator(long operatorId, String ip) {
            return new Reviewer(operatorId, null, ip);
        }

        public static Reviewer manager(long memberId, String ip) {
            return new Reviewer(null, memberId, ip);
        }
    }

    private final TitleApplicationRepository applications;
    private final TitleRepository titles;
    private final TitleQueries queries;
    private final TitleGrantService grants;
    private final TitleEligibility eligibility;
    private final TitleSettingsReader settings;
    private final BoardStaffRepository boardStaff;
    private final AuditLogService audit;

    public TitleReviewService(TitleApplicationRepository applications, TitleRepository titles, TitleQueries queries, TitleGrantService grants, TitleEligibility eligibility,
                              TitleSettingsReader settings, BoardStaffRepository boardStaff, AuditLogService audit) {
        this.applications = applications;
        this.titles = titles;
        this.queries = queries;
        this.grants = grants;
        this.eligibility = eligibility;
        this.settings = settings;
        this.boardStaff = boardStaff;
        this.audit = audit;
    }

    @Transactional
    public TitleApplicationEntity approve(long applicationId, Reviewer reviewer, String note) {
        TitleApplicationEntity app = applications.findWithLockById(applicationId).orElseThrow(TitleException::applicationNotFound);
        TitleEntity title = titles.findWithLockById(app.getTitleId()).orElseThrow(TitleException::notFound);
        authorize(title, app, reviewer);
        if (!app.isPending()) {
            throw TitleException.applicationClosed();
        }
        grants.requireReceivable(app.getUserId());
        Instant now = grants.now();
        String cleaned = clean(note);
        grants.insert(title, app.getUserId(), null, "", TitleSource.APPLICATION, app.getId(), reviewer.operatorId(), reviewer.memberId(), cleaned, now);
        app.approve(reviewer.operatorId(), reviewer.memberId(), cleaned, now);
        audit.record(event(reviewer, AuditActions.TITLE_APPLICATION_APPROVE, app, title, cleaned));
        return app;
    }

    @Transactional
    public TitleApplicationEntity reject(long applicationId, Reviewer reviewer, String reason) {
        String why = clean(reason);
        if (why == null) {
            throw new ValidationException(Map.of("reason", new MessageRef("title.reason.required")));
        }
        TitleApplicationEntity app = applications.findWithLockById(applicationId).orElseThrow(TitleException::applicationNotFound);
        TitleEntity title = titles.findById(app.getTitleId()).orElseThrow(TitleException::notFound);
        authorize(title, app, reviewer);
        if (!app.isPending()) {
            throw TitleException.applicationClosed();
        }
        Instant now = grants.now();
        app.reject(reviewer.operatorId(), reviewer.memberId(), why, now.plus(Duration.ofDays(settings.current().reapplyDays())), now);
        audit.record(event(reviewer, AuditActions.TITLE_APPLICATION_REJECT, app, title, why).with("reapplyAt", app.getReapplyAt().toString()));
        return app;
    }

    /** 매니저 심사 권한 — 운영자는 늘 된다(입구에서 {@code title.review} 를 본다) */
    private void authorize(TitleEntity title, TitleApplicationEntity app, Reviewer reviewer) {
        if (reviewer.memberId() == null) {
            return;
        }
        if (!managesTitle(reviewer.memberId(), title)) {
            throw TitleException.reviewForbidden();
        }
        if (app.getUserId().equals(reviewer.memberId())) {
            throw TitleException.reviewSelf();
        }
    }

    /** 그 회원이 이 칭호를 심사할 수 있는 게시판 매니저인가 */
    public boolean managesTitle(long memberId, TitleEntity title) {
        if (title.getReviewer() != TitleReviewer.OPERATOR_AND_BOARD_MANAGER) {
            return false;
        }
        Long board = eligibility.linkedBoard(title);
        return board != null && isManager(memberId, board);
    }

    /** 그 게시판의 매니저인가(스텝 아님) */
    public boolean isManager(long memberId, long boardId) {
        return boardStaff.findById(new BoardStaffId(boardId, memberId)).map(BoardStaffEntity::getRole).filter(BoardStaffEntity.MANAGER::equals).isPresent();
    }

    /** 그 게시판에 연결돼 매니저가 심사하는 칭호들 */
    public List<TitleEntity> managedTitles(long boardId) {
        return titles.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(t -> t.getReviewer() == TitleReviewer.OPERATOR_AND_BOARD_MANAGER && Long.valueOf(boardId).equals(eligibility.linkedBoard(t))).toList();
    }

    /** 신청 목록(최신 순 200개) — {@code titleIds} 가 null 이면 모든 칭호 */
    @Transactional(readOnly = true)
    public List<TitleApplicationEntity> list(TitleApplicationStatus status, List<Long> titleIds) {
        return queries.applications(status, titleIds, 200);
    }

    private static AuditEvent event(Reviewer reviewer, String action, TitleApplicationEntity app, TitleEntity title, String reason) {
        AuditEvent event = reviewer.operatorId() != null ? AuditEvent.byOperator(reviewer.operatorId(), reviewer.ip(), action, AuditResult.SUCCESS)
                : AuditEvent.byMember(reviewer.memberId(), reviewer.ip(), action, AuditResult.SUCCESS);
        event = event.target("MEMBER", Long.toString(app.getUserId())).with("titleKey", title.getTitleKey()).with("applicationId", Long.toString(app.getId()))
                .with("reviewer", reviewer.operatorId() != null ? "OPERATOR" : "BOARD_MANAGER");
        return reason == null ? event : event.with("reason", reason);
    }

    static String clean(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String s = text.strip();
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
