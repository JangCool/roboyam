package com.community.sanction.service;

import com.community.permission.dto.AdminActor;
import com.community.sanction.dto.request.BoardSanctionReviewCloseRequest;
import com.community.sanction.dto.response.BoardSanctionLineView;
import com.community.sanction.dto.response.BoardSanctionOtherBoardView;
import com.community.sanction.dto.response.BoardSanctionReviewCountsView;
import com.community.sanction.dto.response.BoardSanctionReviewDetailView;
import com.community.sanction.dto.response.BoardSanctionReviewItemView;
import com.community.sanction.dto.response.BoardSanctionReviewListView;
import com.community.sanction.dto.response.BoardSanctionReviewSiteSanctionView;
import com.community.sanction.dto.response.BoardSanctionReviewSummaryView;
import com.community.sanction.dto.response.SanctionUserRefView;
import com.community.sanction.exception.BoardSanctionException;
import com.community.sanction.repository.master.AdminBoardSanctionStore;
import com.community.sanction.repository.master.AdminBoardSanctionStore.ReviewLine;
import com.community.sanction.repository.master.BoardSanctionStore;
import com.community.sanction.repository.master.BoardSanctionStore.ReviewRow;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 게시판 제재 반복 검토(docs/features/board-sanction-contract.md 결정 3·9 · admin-web {@code useBoardSanctionReviewApi.ts}) — 목록({@code status} OPEN(기본)·CLOSED,
 * {@code page} 0부터 · {@code size} 기본 20 최대 50) · 요약 · 상세 · 닫기(바뀐 상세). 닫기 규칙은 shared {@link BoardSanctionService#closeReview}.
 * 권한: 조회 {@code sanction.read} · 닫기 {@code sanction.manage}(컨트롤러 선언).
 */
@Service
@Transactional(readOnly = true)
public class AdminBoardSanctionReviewService {

    /** 상세의 「이 게시판 최근 제재」 최대 */
    static final int SANCTIONS_MAX = 20;

    private final BoardSanctionService sanctions;
    private final BoardSanctionStore store;
    private final AdminBoardSanctionStore adminStore;

    public AdminBoardSanctionReviewService(BoardSanctionService sanctions, BoardSanctionStore store, AdminBoardSanctionStore adminStore) {
        this.sanctions = sanctions;
        this.store = store;
        this.adminStore = adminStore;
    }

    public BoardSanctionReviewListView list(String statusRaw, String pageRaw, String sizeRaw) {
        boolean open = !"CLOSED".equals(statusRaw == null ? null : statusRaw.strip().toUpperCase(Locale.ROOT));
        int page = Math.max(0, AdminSanctionService.number(pageRaw, 0));
        int size = Math.min(AdminSanctionService.SIZE_MAX, Math.max(1, AdminSanctionService.number(sizeRaw, AdminSanctionService.SIZE_DEFAULT)));
        long[] counts = adminStore.reviewCounts();
        Instant now = sanctions.now();
        List<BoardSanctionReviewItemView> items = adminStore.reviewPage(open, now, page, size).stream().map(AdminBoardSanctionReviewService::item).toList();
        return new BoardSanctionReviewListView(new BoardSanctionReviewCountsView(counts[0], counts[1]), open ? counts[0] : counts[1], page, size, items);
    }

    public BoardSanctionReviewSummaryView summary() {
        return new BoardSanctionReviewSummaryView(adminStore.openReviews());
    }

    /** 상세 — 없으면 404 {@code BOARD_SANCTION_REVIEW_NOT_FOUND} */
    public BoardSanctionReviewDetailView detail(String idText) {
        Long id = AdminSanctionService.parseId(idText);
        ReviewRow review = (id == null ? java.util.Optional.<ReviewRow>empty() : sanctions.findReview(id)).orElseThrow(BoardSanctionException::reviewNotFound);
        return detail(review);
    }

    @Transactional
    public BoardSanctionReviewDetailView close(AdminActor actor, String idText, BoardSanctionReviewCloseRequest body) {
        Long id = AdminSanctionService.parseId(idText);
        if (id == null) {
            throw BoardSanctionException.reviewNotFound();
        }
        ReviewRow closed = sanctions.closeReview(id, body == null ? null
                : new BoardSanctionService.ReviewClose(body.result(), body.memo(), body.siteSanctionId(), body.voidSanctionIds()), actor.operatorId(), actor.ip());
        return detail(closed);
    }

    private BoardSanctionReviewDetailView detail(ReviewRow r) {
        Instant now = sanctions.now();
        ReviewLine line = adminStore.line(r, now.minus(BoardSanctionStore.REVIEW_WINDOW));
        List<BoardSanctionLineView> recent = store.ofUserBoard(r.userId(), r.boardId(), SANCTIONS_MAX).stream().map(s -> BoardSanctionViews.line(s, now)).toList();
        List<String> counted = sanctions.reviewSanctions(r).stream().filter(s -> s.valid()).map(s -> Long.toString(s.id())).toList();
        List<BoardSanctionOtherBoardView> others = store.boardSummaries(r.userId(), now.minus(BoardSanctionStore.REVIEW_WINDOW), now).stream()
                .filter(b -> b.boardId() != r.boardId())
                .map(b -> new BoardSanctionOtherBoardView(BoardSanctionViews.board(b.boardId(), b.slug(), b.name(), b.nameEn()), b.recentCount(), b.total(), b.activeUntil()))
                .toList();
        BoardSanctionReviewSiteSanctionView site = r.siteSanctionId() == null ? null
                : new BoardSanctionReviewSiteSanctionView(Long.toString(r.siteSanctionId()), r.siteSanctionNo() == null ? 0 : r.siteSanctionNo(), r.siteSanctionKind());
        return new BoardSanctionReviewDetailView(Long.toString(r.id()), r.no(), r.status(), user(r), BoardSanctionViews.board(r.boardId(), r.boardSlug(), r.boardName(),
                r.boardNameEn()), line.recentCount(), line.recentBoardCount(), r.openedAt(), r.closedAt(), r.result(), recent, counted, others,
                SanctionViews.actor(r.closedBy(), r.closedByName()), r.memo(), site, r.voidedSanctionIds().stream().map(String::valueOf).toList());
    }

    private static BoardSanctionReviewItemView item(ReviewLine line) {
        ReviewRow r = line.row();
        return new BoardSanctionReviewItemView(Long.toString(r.id()), r.no(), r.status(), user(r), BoardSanctionViews.board(r.boardId(), r.boardSlug(), r.boardName(),
                r.boardNameEn()), line.recentCount(), line.recentBoardCount(), r.openedAt(), r.closedAt(), r.result());
    }

    private static SanctionUserRefView user(ReviewRow r) {
        return new SanctionUserRefView(Long.toString(r.userId()), r.nickname() == null ? "" : r.nickname(), r.gradeKey(), r.userStatus());
    }
}
