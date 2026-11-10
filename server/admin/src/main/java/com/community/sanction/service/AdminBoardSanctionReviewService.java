package com.community.sanction.service;

import com.community.permission.dto.AdminActor;
import com.community.sanction.dto.request.BoardSanctionReviewCloseRequest;
import com.community.sanction.dto.response.BoardSanctionLineView;
import com.community.sanction.dto.response.BoardSanctionOtherBoardView;
import com.community.sanction.dto.response.BoardSanctionReviewCountsView;
import com.community.sanction.dto.response.BoardSanctionReviewDetailView;
import com.community.sanction.dto.response.BoardSanctionReviewItemView;
import com.community.sanction.dto.response.BoardSanctionReviewListView;
import com.community.sanction.dto.response.BoardSanctionReviewSealedSourceView;
import com.community.sanction.dto.response.BoardSanctionReviewSiteSanctionView;
import com.community.sanction.dto.response.BoardSanctionReviewSummaryView;
import com.community.sanction.dto.response.SanctionUserRefView;
import com.community.sanction.exception.BoardSanctionException;
import com.community.sanction.repository.master.AdminBoardSanctionStore;
import com.community.sanction.repository.master.AdminBoardSanctionStore.ReviewLine;
import com.community.sanction.repository.master.BoardSanctionStore;
import com.community.sanction.repository.master.BoardSanctionStore.BoardSanctionRow;
import com.community.sanction.repository.master.BoardSanctionStore.ReviewRow;
import com.community.sanction.repository.master.BoardSanctionStore.SealedSource;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
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
        List<BoardSanctionReviewItemView> items = adminStore.reviewPage(open, now, page, size).stream()
                .map(line -> item(line, sanctions.sealedSource(line.row()).map(BoardSanctionReviewSealedSourceView::of).orElse(null))).toList();
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
        List<BoardSanctionRow> reviewRows = sanctions.reviewSanctions(r);
        // 봉인 검토(V184) — 센 제재 중 하나라도 봉인이면 줄 전체를 봉인: 가장 최근 봉인 제재(받은 차례의 마지막)의 계기가 사이트 제재 대상
        BoardSanctionRow lastSealed = null;
        for (BoardSanctionRow s : reviewRows) {
            if (s.sealed()) {
                lastSealed = s;
            }
        }
        boolean sealed = lastSealed != null;
        // 봉인 검토는 봉인 제재만 싣는다 — 봉인 아닌 제재의 번호·계기 글·다른 게시판 이력으로 회원이 이어지지 않게(수는 그대로 센다)
        // 봉인 아닌 검토는 회원이 드러난 화면이라 거꾸로 봉인 제재 줄을 뺀다(센 범위 밖의 옛 봉인 줄로 익명 글과 이어지지 않게 — 2026-11-11)
        List<BoardSanctionLineView> recent = store.ofUserBoard(r.userId(), r.boardId(), SANCTIONS_MAX).stream().filter(s -> sealed == s.sealed())
                .map(s -> BoardSanctionViews.line(s, now)).toList();
        List<String> counted = reviewRows.stream().filter(s -> s.valid() && (!sealed || s.sealed())).map(s -> Long.toString(s.id())).toList();
        Set<Long> sealedIds = reviewRows.stream().filter(BoardSanctionRow::sealed).map(BoardSanctionRow::id).collect(Collectors.toSet());
        List<String> voided = r.voidedSanctionIds().stream().filter(id -> !sealed || sealedIds.contains(id)).map(String::valueOf).toList();
        List<BoardSanctionOtherBoardView> others = sealed ? List.of() : store.boardSummaries(r.userId(), now.minus(BoardSanctionStore.REVIEW_WINDOW), now, true).stream()
                .filter(b -> b.boardId() != r.boardId())
                .map(b -> new BoardSanctionOtherBoardView(BoardSanctionViews.board(b.boardId(), b.slug(), b.name(), b.nameEn()), b.recentCount(), b.total(), b.activeUntil()))
                .toList();
        BoardSanctionReviewSiteSanctionView site = r.siteSanctionId() == null ? null
                : new BoardSanctionReviewSiteSanctionView(Long.toString(r.siteSanctionId()), r.siteSanctionNo() == null ? 0 : r.siteSanctionNo(), r.siteSanctionKind());
        BoardSanctionReviewSealedSourceView source = sealed
                ? BoardSanctionReviewSealedSourceView.of(new SealedSource(lastSealed.postId(), lastSealed.commentId())) : null;
        return new BoardSanctionReviewDetailView(Long.toString(r.id()), r.no(), r.status(), user(r, sealed), BoardSanctionViews.board(r.boardId(), r.boardSlug(),
                r.boardName(), r.boardNameEn()), line.recentCount(), line.recentBoardCount(), r.openedAt(), r.closedAt(), r.result(), recent, counted, others,
                SanctionViews.actor(r.closedBy(), r.closedByName()), r.memo(), site, voided, source);
    }

    private static BoardSanctionReviewItemView item(ReviewLine line, BoardSanctionReviewSealedSourceView sealedSource) {
        ReviewRow r = line.row();
        return new BoardSanctionReviewItemView(Long.toString(r.id()), r.no(), r.status(), user(r, sealedSource != null), BoardSanctionViews.board(r.boardId(),
                r.boardSlug(), r.boardName(), r.boardNameEn()), line.recentCount(), line.recentBoardCount(), r.openedAt(), r.closedAt(), r.result(), sealedSource);
    }

    /** 검토 대상 회원 — 봉인 검토면 {@link SanctionUserRefView#SEALED}(관리자 제재 응답과 같은 모양) */
    private static SanctionUserRefView user(ReviewRow r, boolean sealed) {
        if (sealed) {
            return SanctionUserRefView.SEALED;
        }
        return new SanctionUserRefView(Long.toString(r.userId()), r.nickname() == null ? "" : r.nickname(), r.gradeKey(), r.userStatus());
    }
}
