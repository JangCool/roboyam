package com.community.boardstaff.service;

import com.community.boardstaff.dto.request.BoardSanctionIssueRequest;
import com.community.boardstaff.dto.request.BoardSanctionLiftRequest;
import com.community.boardstaff.dto.response.BoardSanctionActiveView;
import com.community.boardstaff.dto.response.BoardSanctionCountsView;
import com.community.boardstaff.dto.response.BoardSanctionIssuedView;
import com.community.boardstaff.dto.response.BoardSanctionIssuerView;
import com.community.boardstaff.dto.response.BoardSanctionLiftInfoView;
import com.community.boardstaff.dto.response.BoardSanctionPreviewView;
import com.community.boardstaff.dto.response.BoardSanctionSourceView;
import com.community.boardstaff.dto.response.BoardSanctionTargetView;
import com.community.boardstaff.dto.response.BoardStaffQuotaView;
import com.community.boardstaff.dto.response.BoardStaffSanctionPageView;
import com.community.boardstaff.dto.response.BoardStaffSanctionRowView;
import com.community.boardstaff.dto.request.BoardStaffActionRestoreRequest;
import com.community.boardstaff.dto.request.BoardStaffCommentDeleteRequest;
import com.community.boardstaff.dto.request.BoardStaffPostActionRequest;
import com.community.boardstaff.dto.response.BoardStaffActionDoneView;
import com.community.boardstaff.dto.response.BoardStaffActionPageView;
import com.community.boardstaff.dto.response.BoardStaffActionRowView;
import com.community.sanction.exception.BoardSanctionException;
import com.community.sanction.exception.BoardStaffActionException;
import com.community.sanction.repository.master.BoardSanctionStore.BoardSanctionRow;
import com.community.sanction.repository.master.BoardStaffActionStore.ActionRow;
import com.community.sanction.service.BoardSanctionService;
import com.community.sanction.service.BoardStaffActionService;
import com.community.post.service.AnonymousAuthorLookup;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 매니저·스텝 도구(공개 사이트 — docs/features/board-sanction-contract.md 「API 후보 > 공개」, 모양은 user-web {@code useBoardStaffApi.types.ts}).
 * 규칙·판정은 shared {@link BoardSanctionService}. 처리할 때마다 그 게시판 자리·역할 권한·계정 상태를 다시 본다.
 *
 * <p><b>처리자 신원</b>(닉네임·자리)은 그 게시판 매니저가 보는 내역({@code manager = true})에만 싣는다 — 스텝 내역·현황·부여 응답에는 없다(결정 7).
 * 해제한 사람도 싣지 않는다(사유·시각만).
 */
@Service
public class BoardStaffService {

    private final BoardSanctionService sanctions;
    private final BoardStaffActionService staffActions;
    private final AnonymousAuthorLookup anonymousAuthors;

    public BoardStaffService(BoardSanctionService sanctions, BoardStaffActionService staffActions, AnonymousAuthorLookup anonymousAuthors) {
        this.sanctions = sanctions;
        this.staffActions = staffActions;
        this.anonymousAuthors = anonymousAuthors;
    }

    /**
     * @param anonymousPostId    「이 글 작성자」(익명 글 — V184) — {@code userId} 대신. 대상 칸은 봉인({@code sealed true}, 닉네임·회원 ID 없음)
     * @param anonymousCommentId 「이 댓글 작성자」(익명 댓글)
     */
    @Transactional(readOnly = true)
    public BoardSanctionPreviewView preview(long actorId, String boardIdText, String userIdText, String anonymousPostId, String anonymousCommentId) {
        Long boardId = parseId(boardIdText);
        if (boardId == null) {
            throw BoardSanctionException.forbidden();
        }
        boolean sealed = AnonymousAuthorLookup.given(anonymousPostId, anonymousCommentId);
        Long userId = sealed ? anonymousTarget(userIdText, anonymousPostId, anonymousCommentId).userId() : parseId(userIdText);
        if (userId == null) {
            throw BoardSanctionException.targetProtected();
        }
        BoardSanctionService.Preview p = sanctions.preview(actorId, boardId, userId, sealed); // 봉인 대상의 보호 409 는 까닭을 가린 ANONYMOUS_TARGET_UNAVAILABLE
        return new BoardSanctionPreviewView(sealed ? BoardSanctionTargetView.SEALED : new BoardSanctionTargetView(Long.toString(p.target().id()), nz(p.target().nickname()), false),
                new BoardSanctionCountsView(p.counts().warnings(), p.counts().restrictions()),
                p.active() == null ? null : new BoardSanctionActiveView(Long.toString(p.active().boardSanctionId()), p.active().until()), p.recommended().name(),
                BoardStaffQuotaView.of(p.quota()), BoardSanctionService.nextResetAfter(sanctions.now()));
    }

    @Transactional
    public BoardSanctionIssuedView issue(long actorId, BoardSanctionIssueRequest body, String ip) {
        Long boardId = body == null ? null : parseId(body.boardId());
        if (boardId == null) {
            throw BoardSanctionException.forbidden();
        }
        // 「이 글/댓글 작성자」(익명 — V184, anonymous-board-contract 정책 9): 대상 회원은 서버가 그 글·댓글에서 찾고, 계기도 그 글·댓글이다(봉인 제재)
        if (AnonymousAuthorLookup.given(body.anonymousPostId(), body.anonymousCommentId())) {
            AnonymousAuthorLookup.Found found = anonymousTarget(body.userId(), body.anonymousPostId(), body.anonymousCommentId());
            BoardSanctionService.Issued issued = sanctions.issue(new BoardSanctionService.Issue(actorId, boardId, found.userId(), body.kind(), body.reasonCode(),
                    body.reasonText(), found.postId(), found.commentId(), ip, true));
            BoardSanctionRow r = issued.row();
            return new BoardSanctionIssuedView(Long.toString(r.id()), r.no(), r.kind().name(), r.startsAt(), r.endsAt(), BoardStaffQuotaView.of(issued.quota()),
                    BoardSanctionService.nextResetAfter(sanctions.now()));
        }
        Long userId = parseId(body.userId());
        if (userId == null) {
            throw BoardSanctionException.targetProtected();
        }
        Long postId = optionalId(body.postId(), "postId");
        Long commentId = optionalId(body.commentId(), "commentId");
        BoardSanctionService.Issued issued = sanctions.issue(new BoardSanctionService.Issue(actorId, boardId, userId, body.kind(), body.reasonCode(), body.reasonText(),
                postId, commentId, ip));
        BoardSanctionRow r = issued.row();
        return new BoardSanctionIssuedView(Long.toString(r.id()), r.no(), r.kind().name(), r.startsAt(), r.endsAt(), BoardStaffQuotaView.of(issued.quota()),
                BoardSanctionService.nextResetAfter(sanctions.now()));
    }

    @Transactional(readOnly = true)
    public BoardStaffSanctionPageView list(long actorId, String boardIdText, String scopeRaw, String pageRaw) {
        Long boardId = parseId(boardIdText);
        if (boardId == null) {
            throw BoardSanctionException.staffNotFound();
        }
        String scope = scopeRaw == null ? "ALL" : scopeRaw.strip().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(scope) && !"ENDED".equals(scope)) {
            scope = "ALL";
        }
        BoardSanctionService.StaffPage page = sanctions.staffPage(actorId, boardId, scope, number(pageRaw, 1));
        Instant now = sanctions.now();
        List<BoardStaffSanctionRowView> items = page.rows().stream().map(r -> row(r, page.manager(), page.canLift(r, now), now)).toList();
        int totalPages = (int) Math.max(1, (page.total() + page.size() - 1) / page.size());
        return new BoardStaffSanctionPageView(page.manager(), items, page.page(), totalPages, page.total());
    }

    @Transactional
    public BoardStaffSanctionRowView lift(long actorId, String idText, BoardSanctionLiftRequest body, String ip) {
        Long id = parseId(idText);
        if (id == null) {
            throw BoardSanctionException.notFound();
        }
        BoardSanctionRow row = sanctions.liftByManager(actorId, id, body == null ? null : body.reason(), ip);
        return row(row, true, false, sanctions.now()); // 방금 풀었다 — 더 풀 것이 없다
    }

    // ── 스텝 처리(글 숨기기 · 글 삭제 · 댓글 삭제 — docs/features/board-staff-action-contract.md) ──

    @Transactional
    public BoardStaffActionDoneView hidePost(long actorId, String postIdText, BoardStaffPostActionRequest body, String ip) {
        Long postId = parseId(postIdText);
        if (postId == null) {
            throw BoardStaffActionException.postNotFound();
        }
        return done(staffActions.hidePost(actorId, postId, body == null ? null : body.reasonCode(), ip));
    }

    @Transactional
    public BoardStaffActionDoneView deletePost(long actorId, String postIdText, BoardStaffPostActionRequest body, String ip) {
        Long postId = parseId(postIdText);
        if (postId == null) {
            throw BoardStaffActionException.postNotFound();
        }
        return done(staffActions.deletePost(actorId, postId, body == null ? null : body.reasonCode(), ip));
    }

    @Transactional
    public BoardStaffActionDoneView deleteComment(long actorId, String commentIdText, BoardStaffCommentDeleteRequest body, String ip) {
        Long commentId = parseId(commentIdText);
        if (commentId == null) {
            throw BoardStaffActionException.commentNotFound();
        }
        return done(staffActions.deleteComment(actorId, body == null ? null : parseId(body.postId()), commentId, body == null ? null : body.reasonCode(), ip));
    }

    @Transactional(readOnly = true)
    public BoardStaffActionPageView actions(long actorId, String boardIdText, String kindRaw, String pageRaw) {
        Long boardId = parseId(boardIdText);
        if (boardId == null) {
            throw BoardSanctionException.staffNotFound();
        }
        String kind = kindRaw == null ? "ALL" : kindRaw.strip().toUpperCase(Locale.ROOT);
        if (!"POST".equals(kind) && !"COMMENT".equals(kind)) {
            kind = "ALL";
        }
        BoardStaffActionService.Page page = staffActions.page(actorId, boardId, kind, number(pageRaw, 1));
        List<BoardStaffActionRowView> items = page.rows().stream().map(r -> actionRow(r, page.manager(), page.canRestore(r))).toList();
        int totalPages = (int) Math.max(1, (page.total() + page.size() - 1) / page.size());
        return new BoardStaffActionPageView(page.manager(), items, page.page(), totalPages, page.total());
    }

    @Transactional
    public BoardStaffActionRowView restore(long actorId, String idText, BoardStaffActionRestoreRequest body, String ip) {
        Long id = parseId(idText);
        if (id == null) {
            throw BoardStaffActionException.actionNotFound();
        }
        ActionRow row = staffActions.restoreByManager(actorId, id, body == null ? null : body.reason(), ip);
        return actionRow(row, true, false); // 방금 되돌렸다 — 더 되돌릴 것이 없다
    }

    private BoardStaffActionDoneView done(BoardStaffActionService.Done done) {
        return new BoardStaffActionDoneView(BoardStaffQuotaView.of(done.quota()),
                done.quota() == null ? BoardSanctionService.nextResetAfter(staffActions.now()) : done.quota().resetsAt());
    }

    /** 익명 대상 — 회원 ID 와 함께 보내면 400 {@code fields.userId}(셋 중 하나만), 찾기 규칙은 {@link AnonymousAuthorLookup#resolve} */
    private AnonymousAuthorLookup.Found anonymousTarget(String userIdText, String anonymousPostId, String anonymousCommentId) {
        if (userIdText != null && !userIdText.isBlank()) {
            throw ValidationException.of("userId", new MessageRef("anonymous.target.one"));
        }
        return anonymousAuthors.resolve(anonymousPostId, anonymousCommentId, "anonymousPostId", "anonymousCommentId");
    }

    /** 처리 내역 한 줄 — 처리자는 매니저(·운영자 회원 계정)에게만, 되돌린 사람·사유는 싣지 않는다(정책 8). {@code canRestore} 는 줄마다 판정한 값 */
    private static BoardStaffActionRowView actionRow(ActionRow r, boolean manager, boolean canRestore) {
        BoardStaffActionRowView.Actor actor = manager && r.actorRole() != null ? new BoardStaffActionRowView.Actor(nz(r.actorNickname()), r.actorRole()) : null;
        return new BoardStaffActionRowView(Long.toString(r.id()), r.apiKind(), r.reasonCode(),
                new BoardStaffActionRowView.Target(Long.toString(r.postId()), r.commentId() == null ? null : Long.toString(r.commentId()), nz(r.targetTitle()),
                        r.targetExcerpt()),
                new BoardStaffActionRowView.Author(r.anonymousTarget() ? "" : nz(r.authorName()), r.anonymousTarget()), actor, r.createdAt(),
                r.restoredAt() == null ? null : new BoardStaffActionRowView.Restored(r.restoredAt()), canRestore);
    }

    /** 내역 한 줄 — 처리자는 매니저에게만, 해제한 사람은 싣지 않는다 */
    private static BoardStaffSanctionRowView row(BoardSanctionRow r, boolean manager, boolean canLift, Instant now) {
        BoardSanctionIssuerView issuer = manager && r.issuedRole() != null ? new BoardSanctionIssuerView(nz(r.issuedByNickname()), r.issuedRole()) : null;
        BoardSanctionLiftInfoView lift = r.liftedAt() == null ? null : new BoardSanctionLiftInfoView(r.liftReason(), r.liftedAt());
        BoardSanctionSourceView source = r.postId() == null ? null
                : new BoardSanctionSourceView(Long.toString(r.postId()), r.commentId() == null ? null : Long.toString(r.commentId()), r.sourceTitle());
        return new BoardStaffSanctionRowView(Long.toString(r.id()), r.no(),
                r.sealed() ? BoardSanctionTargetView.SEALED : new BoardSanctionTargetView(Long.toString(r.userId()), nz(r.nickname()), false), r.kind().name(),
                r.reasonCode(), r.reasonText(), r.effectiveStatus(now).name(), r.startsAt(), r.endsAt(), r.createdAt(), issuer, lift, source,
                canLift);
    }

    /** 선택 ID — 비면 null, 숫자가 아니면 400 그 칸 */
    private static Long optionalId(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Long id = parseId(raw);
        if (id == null) {
            throw com.community.zconfiguration.exception.ValidationException.of(field, new com.community.zconfiguration.message.MessageRef("boardSanction.source.invalid"));
        }
        return id;
    }

    static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int number(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String nz(String text) {
        return text == null ? "" : text;
    }
}
