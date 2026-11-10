package com.community.sanction.service;

import com.community.permission.dto.AdminActor;
import com.community.sanction.dto.request.BoardSanctionReasonRequest;
import com.community.sanction.dto.response.BoardSanctionDetailView;
import com.community.sanction.dto.response.BoardSanctionLineView;
import com.community.sanction.dto.response.BoardSanctionListView;
import com.community.sanction.dto.response.SanctionCountsView;
import com.community.sanction.entity.BoardSanctionKind;
import com.community.sanction.exception.BoardSanctionException;
import com.community.sanction.repository.master.AdminBoardSanctionStore;
import com.community.sanction.repository.master.BoardSanctionStore.BoardSanctionRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 게시판 제재(docs/features/board-sanction-contract.md · admin-web {@code useBoardSanctionApi.ts}) — 목록 · 하나 · 해제 · 무효. 규칙은 shared {@link BoardSanctionService}.
 *
 * <ul>
 *   <li><b>목록</b>: {@code scope} ALL(기본) · ACTIVE(진행 중 쓰기 제한만 — 결정 11) · CLOSED(기간 끝·해제·무효) · {@code boardId} · {@code kind} · {@code searchField}
 *       NICKNAME(기본 — 2자 이상 부분 일치, 아니면 400 {@code fields.q}) · SANCTION_NO(숫자 정확히 — 게시판 제재 번호) · {@code from}·{@code to}(부여 시각 UTC ISO, 양 끝 포함 —
 *       형식 오류는 그 칸, 시작 &gt; 끝은 {@code fields.from}) · {@code userId} · {@code sort} newest(기본)·oldest · {@code page} 0부터 · {@code size} 기본 20 최대 50.
 *       모르는 값은 기본값(사이트 제재 목록과 같은 규칙).</li>
 *   <li><b>권한</b>: 조회 {@code sanction.read} · 해제·무효 {@code sanction.manage}(컨트롤러 선언).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AdminBoardSanctionService {

    private final BoardSanctionService sanctions;
    private final AdminBoardSanctionStore store;
    private final BoardSanctionViews views;

    public AdminBoardSanctionService(BoardSanctionService sanctions, AdminBoardSanctionStore store, BoardSanctionViews views) {
        this.sanctions = sanctions;
        this.store = store;
        this.views = views;
    }

    public BoardSanctionListView list(String scopeRaw, String boardIdRaw, String kindRaw, String searchField, String qRaw, String fromRaw, String toRaw, String userIdRaw,
                                      String sort, String pageRaw, String sizeRaw) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String scope = scopeRaw == null ? "ALL" : scopeRaw.strip().toUpperCase(Locale.ROOT);
        BoardSanctionKind kind = BoardSanctionKind.of(kindRaw).orElse(null);
        String q = AdminSanctionService.trim(qRaw);
        String nicknameLike = null;
        Long sanctionNo = null;
        if (q != null) {
            if ("SANCTION_NO".equalsIgnoreCase(AdminSanctionService.trim(searchField))) {
                sanctionNo = AdminSanctionService.parseId(q.startsWith("#") ? q.substring(1) : q);
                if (sanctionNo == null) {
                    invalid.put("q", new MessageRef("sanction.query.number"));
                }
            } else if (q.codePointCount(0, q.length()) < AdminSanctionService.QUERY_MIN) {
                invalid.put("q", new MessageRef("sanction.query.tooShort", AdminSanctionService.QUERY_MIN));
            } else {
                nicknameLike = "%" + AdminSanctionService.likeEscape(AdminSanctionService.cut(q, AdminSanctionService.QUERY_MAX).toLowerCase(Locale.ROOT)) + "%";
            }
        }
        Instant from = AdminSanctionService.instant(fromRaw, "from", invalid);
        Instant to = AdminSanctionService.instant(toRaw, "to", invalid);
        if (from != null && to != null && from.isAfter(to)) {
            invalid.put("from", new MessageRef("sanction.range.invalid"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        Long boardId = AdminSanctionService.trim(boardIdRaw) == null ? null : orNone(AdminSanctionService.parseId(boardIdRaw));
        Long userId = AdminSanctionService.trim(userIdRaw) == null ? null : orNone(AdminSanctionService.parseId(userIdRaw));
        int page = Math.max(0, AdminSanctionService.number(pageRaw, 0));
        int size = Math.min(AdminSanctionService.SIZE_MAX, Math.max(1, AdminSanctionService.number(sizeRaw, AdminSanctionService.SIZE_DEFAULT)));
        AdminBoardSanctionStore.Filter filter = new AdminBoardSanctionStore.Filter(scope, boardId, kind, nicknameLike, sanctionNo, from, to, userId,
                "oldest".equalsIgnoreCase(AdminSanctionService.trim(sort)));
        Instant now = sanctions.now();
        long[] counts = store.counts(now);
        List<BoardSanctionLineView> items = store.page(filter, now, page, size).stream().map(r -> BoardSanctionViews.line(r, now)).toList();
        return new BoardSanctionListView(new SanctionCountsView(counts[0], counts[1], counts[2]), store.total(filter, now), page, size, items,
                store.boards().stream().map(b -> BoardSanctionViews.board(b.id(), b.slug(), b.name(), b.nameEn())).toList());
    }

    /** 제재 하나 + 그 회원의 게시판 제재 이력 — 없으면 404 {@code BOARD_SANCTION_NOT_FOUND} */
    public BoardSanctionDetailView get(String idText) {
        return detail(require(idText));
    }

    @Transactional
    public BoardSanctionDetailView lift(AdminActor actor, String idText, BoardSanctionReasonRequest body) {
        BoardSanctionRow row = sanctions.liftByOperator(idOf(idText), body == null ? null : body.reason(), actor.operatorId(), actor.ip());
        return detail(row);
    }

    @Transactional
    public BoardSanctionDetailView voidSanction(AdminActor actor, String idText, BoardSanctionReasonRequest body) {
        BoardSanctionRow row = sanctions.voidByOperator(idOf(idText), body == null ? null : body.reason(), actor.operatorId(), actor.ip());
        return detail(row);
    }

    private BoardSanctionDetailView detail(BoardSanctionRow row) {
        // 봉인 제재(V184)는 회원 이력을 싣지 않는다(이력 줄의 닉네임으로 신원이 이어지지 않게), 봉인 아닌 제재의 회원 이력에서는 봉인 줄이 빠진다(BoardSanctionViews.history)
        return new BoardSanctionDetailView(BoardSanctionViews.line(row, sanctions.now()), row.sealed() ? List.of() : views.history(row.userId()));
    }

    private BoardSanctionRow require(String idText) {
        return sanctions.find(idOf(idText)).orElseThrow(BoardSanctionException::notFound);
    }

    private static long idOf(String idText) {
        Long id = AdminSanctionService.parseId(idText);
        if (id == null) {
            throw BoardSanctionException.notFound();
        }
        return id;
    }

    /** 숫자가 아닌 ID 거르기 — 아무것도 맞지 않게 */
    private static Long orNone(Long id) {
        return id == null ? -1L : id;
    }
}
