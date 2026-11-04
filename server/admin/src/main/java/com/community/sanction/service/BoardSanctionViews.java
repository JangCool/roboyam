package com.community.sanction.service;

import com.community.sanction.dto.response.AppealBoardSanctionBriefView;
import com.community.sanction.dto.response.BoardSanctionBoardRefView;
import com.community.sanction.dto.response.BoardSanctionIssuerView;
import com.community.sanction.dto.response.BoardSanctionLiftActorView;
import com.community.sanction.dto.response.BoardSanctionLineView;
import com.community.sanction.dto.response.BoardSanctionTriggerView;
import com.community.sanction.dto.response.MemberBoardSanctionBoardView;
import com.community.sanction.dto.response.MemberBoardSanctionSummaryView;
import com.community.sanction.dto.response.SanctionAppealRefView;
import com.community.sanction.dto.response.SanctionUserRefView;
import com.community.sanction.repository.master.AdminBoardSanctionStore;
import com.community.sanction.repository.master.BoardSanctionStore;
import com.community.sanction.repository.master.BoardSanctionStore.BoardSanctionRow;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 제재 줄·회원 요약의 응답 모양(admin-web {@code useBoardSanctionApi.types.ts}) — 게시판 제재 화면·반복 검토·이의제기 화면·회원 상세가 같이 쓴다. 읽기만(지연 만료를 쓰지 않고
 * {@link BoardSanctionRow#effectiveStatus} 로 지금 상태를 보인다). 처리자(매니저·스텝)는 관리자 응답이라 싣는다(결정 7).
 */
@Component
@Transactional(readOnly = true)
public class BoardSanctionViews {

    /** 회원 상세 최근 이력 수 */
    static final int MEMBER_RECENT = 5;

    private final BoardSanctionService sanctions;
    private final BoardSanctionStore store;
    private final AdminBoardSanctionStore adminStore;

    public BoardSanctionViews(BoardSanctionService sanctions, BoardSanctionStore store, AdminBoardSanctionStore adminStore) {
        this.sanctions = sanctions;
        this.store = store;
        this.adminStore = adminStore;
    }

    /** 그 회원의 게시판 제재 이력(모든 게시판, 최근 것이 위, 최대 30개) */
    public List<BoardSanctionLineView> history(long userId) {
        Instant now = sanctions.now();
        return sanctions.ofUser(userId, MemberSanctionService.HISTORY_MAX).stream().map(r -> line(r, now)).toList();
    }

    /** 회원 상세의 {@code boardSanctions} — 게시판별 수 + 최근 5개 + 열린 반복 검토 수 */
    public MemberBoardSanctionSummaryView memberSummary(long userId) {
        Instant now = sanctions.now();
        List<MemberBoardSanctionBoardView> boards = adminStore.memberBoards(userId, now).stream()
                .map(b -> new MemberBoardSanctionBoardView(board(b.board().id(), b.board().slug(), b.board().name(), b.board().nameEn()), b.warnings(), b.restrictions(),
                        b.activeUntil()))
                .toList();
        long total = boards.stream().mapToLong(b -> b.warningCount() + b.restrictCount()).sum();
        long active = boards.stream().filter(b -> b.activeUntil() != null).count();
        List<BoardSanctionLineView> recent = sanctions.ofUser(userId, MEMBER_RECENT).stream().map(r -> line(r, now)).toList();
        return new MemberBoardSanctionSummaryView(total, active, store.openReviewsOf(userId), boards, recent);
    }

    public static BoardSanctionLineView line(BoardSanctionRow r, Instant now) {
        SanctionAppealRefView appeal = r.appealId() == null ? null : new SanctionAppealRefView(Long.toString(r.appealId()), r.appealNo(), r.appealStatus().name());
        BoardSanctionTriggerView trigger = r.postId() == null ? null
                : new BoardSanctionTriggerView(r.commentId() == null ? "POST" : "COMMENT", Long.toString(r.postId()),
                r.commentId() == null ? null : Long.toString(r.commentId()), r.sourceTitle());
        BoardSanctionLiftActorView liftedBy = null;
        if (r.liftedAt() != null && r.liftedByType() != null) {
            Long id = "MANAGER".equals(r.liftedByType()) ? r.liftedByUser() : r.liftedByOperator();
            String name = "MANAGER".equals(r.liftedByType()) ? r.liftedByUserNickname() : r.liftedByOperatorName();
            liftedBy = id == null ? null : new BoardSanctionLiftActorView(r.liftedByType(), Long.toString(id), name);
        }
        return new BoardSanctionLineView(Long.toString(r.id()), r.no(), r.kind().name(), r.effectiveStatus(now).name(), board(r), user(r), r.reasonCode(), r.reasonText(),
                issuer(r), r.startsAt(), r.endsAt(), r.createdAt(), trigger, liftedBy, r.liftedAt(), r.liftReason(), r.voidedAt(),
                SanctionViews.actor(r.voidedBy(), r.voidedByName()), r.voidSource(), r.voidReason(), r.effectiveEndedAt(now), r.acknowledgedAt(), appeal,
                BoardSanctionService.appealDeadline(r));
    }

    /** 이의제기 줄의 게시판 제재 요약 */
    public static AppealBoardSanctionBriefView brief(BoardSanctionRow r, Instant now) {
        return new AppealBoardSanctionBriefView(Long.toString(r.id()), r.no(), r.kind().name(), r.effectiveStatus(now).name(), board(r), issuer(r), r.startsAt(),
                r.endsAt());
    }

    public static BoardSanctionIssuerView issuer(BoardSanctionRow r) {
        return r.issuedBy() == null ? null : new BoardSanctionIssuerView(Long.toString(r.issuedBy()), r.issuedByNickname(), r.issuedRole());
    }

    public static BoardSanctionBoardRefView board(BoardSanctionRow r) {
        return board(r.boardId(), r.boardSlug(), r.boardName(), r.boardNameEn());
    }

    public static BoardSanctionBoardRefView board(long id, String slug, String name, String nameEn) {
        return new BoardSanctionBoardRefView(Long.toString(id), slug, name, nameEn);
    }

    public static SanctionUserRefView user(BoardSanctionRow r) {
        return new SanctionUserRefView(Long.toString(r.userId()), r.nickname() == null ? "" : r.nickname(), r.gradeKey(), r.userStatus());
    }
}
