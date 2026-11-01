package com.community.bizapplication.service;

import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.hotdeal.service.DealHosts;
import com.community.hotdeal.service.DealLinkJudge.ChainVerdict;
import com.community.hotdeal.service.DealRuleBook;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 사업자 핫딜 신청의 2차 칸(biz-hotdeal-contract 규칙 8) — 받는 핫딜 게시판·말머리, 회원 핫딜과 같은 링크 규칙({@code deal_domains}), 재고 값. 소재 내용(JSON)의 칸 이름:
 * {@code boardId}·{@code boardSlug}·{@code boardName}·{@code prefixId}·{@code prefixName}(신청 때 스냅샷)·{@code imageFileId}·{@code imageUrl}.
 */
@Component
public class BizDealContent {

    /** 새 신청이 받는 재고 값(회원 핫딜과 같은 이름 — 품절은 신청에서 받지 않는다) */
    public static final Set<String> STOCKS = Set.of("IN_STOCK", "LOW", "UNKNOWN");

    /** 받는 핫딜 게시판 하나 — 고를 수 있는 말머리(켜짐 · 운영진 전용 아님) */
    public record Board(long id, String slug, String name, List<BoardPrefixInfo> prefixes) {
    }

    private final BoardCatalog catalog;
    private final DealRuleBook rules;

    public BizDealContent(BoardCatalog catalog, DealRuleBook rules) {
        this.catalog = catalog;
        this.rules = rules;
    }

    /** 받는 핫딜 게시판(켜진 DEAL 게시판, 게시판 차례) */
    public List<Board> boards() {
        return catalog.snapshot().boards().stream().filter(BizDealContent::open).map(BizDealContent::board).toList();
    }

    /** 지금 링크 규칙으로 판정 — 주소가 아니면 비어 있다 */
    public Optional<ChainVerdict> judge(String url) {
        String host = url == null ? null : DealHosts.hostOf(url.strip());
        return host == null ? Optional.empty() : Optional.of(rules.judge(List.of(host)));
    }

    /**
     * 게시판·말머리 검사 → 소재 내용에 넣는다. 게시판 필수(켜진 DEAL), 게시판에 고를 말머리가 있으면 말머리 필수, 말머리 상단({@code CATEGORY_TOP})이면 말머리 필수.
     */
    public void board(BizFields f, String boardIdText, String prefixIdText, String placement, Map<String, Object> c) {
        Long boardId = BizFields.id(boardIdText);
        EffectiveBoard b = boardId == null ? null : catalog.snapshot().board(boardId).orElse(null);
        if (BizFields.strip(boardIdText) == null) {
            f.add("deal.boardId", "bizapp.field.required");
            return;
        }
        if (b == null || !open(b)) {
            f.add("deal.boardId", "bizapp.deal.boardClosed");
            return;
        }
        Board board = board(b);
        c.put("boardId", Long.toString(b.id()));
        c.put("boardSlug", b.slug());
        c.put("boardName", b.name());
        Long prefixId = BizFields.id(prefixIdText);
        if (BizFields.strip(prefixIdText) == null) {
            if (!board.prefixes().isEmpty() || "CATEGORY_TOP".equals(placement)) {
                f.add("deal.prefixId", "bizapp.deal.prefixRequired");
            }
            return;
        }
        BoardPrefixInfo prefix = prefixId == null ? null : board.prefixes().stream().filter(p -> p.id() == prefixId).findFirst().orElse(null);
        if (prefix == null) {
            f.add("deal.prefixId", "bizapp.deal.prefixInvalid");
            return;
        }
        c.put("prefixId", Long.toString(prefix.id()));
        c.put("prefixName", prefix.name());
    }

    private static boolean open(EffectiveBoard b) {
        return b.active() && BoardFields.KIND_DEAL.equals(b.kind());
    }

    private static Board board(EffectiveBoard b) {
        return new Board(b.id(), b.slug(), b.name(), b.activePrefixes().stream().filter(p -> !p.staffOnly()).toList());
    }

    // ── 소재 내용 읽기 ──────────────────────────────────────────────────────

    /** 옛 재고 값(V112 — PLENTY·LIMITED)을 글의 재고로 — 넉넉 → 있음, 한정 → 적음, 그 밖 모름 */
    public static String postStock(String stock) {
        if (stock == null) {
            return "UNKNOWN";
        }
        return switch (stock) {
            case "IN_STOCK", "PLENTY" -> "IN_STOCK";
            case "LOW", "LIMITED" -> "LOW";
            default -> "UNKNOWN";
        };
    }

    public static Long id(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return value == null ? null : BizFields.id(value.toString());
    }

    public static String text(Object value) {
        return value == null ? null : BizFields.strip(value.toString());
    }
}
