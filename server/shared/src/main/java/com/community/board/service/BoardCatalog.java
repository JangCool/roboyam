package com.community.board.service;

import com.community.cache.definition.CacheNames;
import org.springframework.cache.CacheManager;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardCatalogSnapshot.GradeInfo;
import com.community.board.dto.BoardDefaultsValues;
import com.community.board.dto.BoardPolicyValues;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.BoardReportValues;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardDefaultsEntity;
import com.community.board.entity.BoardEntity;
import com.community.board.entity.BoardPrefixEntity;
import com.community.board.entity.BoardReportSettingEntity;
import com.community.board.entity.BoardSecuritySettingEntity;
import com.community.board.repository.master.BoardDefaultsRepository;
import com.community.board.repository.master.BoardPrefixRepository;
import com.community.board.repository.master.BoardReportSettingRepository;
import com.community.board.repository.master.BoardRepository;
import com.community.board.repository.master.BoardSecuritySettingRepository;
import com.community.grade.entity.GradeEntity;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 게시판·유효 정책·기본 설정 하위 화면(보안·신고)·등급 목록의 <b>짧은 메모리 사본</b>({@link BoardCatalogSnapshot}) — 공개 게시판 API 가 요청마다 DB 를 읽지 않게 한다.
 *
 * <ul>
 *   <li>캐시 {@code board.catalog}(기본 15초 — docs/features/cache-admin-contract.md)에 둔다. 관리자 저장은 세대 번호로 모든 서버에 몇 초 안에 닿는다.
 *       같은 서버 안에서는 {@link #invalidate()} 로 바로 비울 수 있다.</li>
 *   <li>읽다 실패하면 담지 않고 그 요청이 실패한다(다음 요청이 다시 읽는다).</li>
 *   <li>유효값 = 게시판 값 ?? 기본값. 게시판별로 덮어쓸 수 없는 칸은 {@link BoardCatalogSnapshot#defaults()} 에서 읽는다.</li>
 * </ul>
 */
@Component
public class BoardCatalog {


    private final BoardRepository boards;
    private final BoardPrefixRepository prefixes;
    private final BoardDefaultsRepository defaults;
    private final BoardSecuritySettingRepository security;
    private final BoardReportSettingRepository report;
    private final GradeRepositoryQDSL grades;
    private final CacheManager caches;

    public BoardCatalog(BoardRepository boards, BoardPrefixRepository prefixes, BoardDefaultsRepository defaults, BoardSecuritySettingRepository security,
                        BoardReportSettingRepository report, GradeRepositoryQDSL grades, CacheManager caches) {
        this.caches = caches;
        this.boards = boards;
        this.prefixes = prefixes;
        this.defaults = defaults;
        this.security = security;
        this.report = report;
        this.grades = grades;
    }

    /** 지금 쓸 수 있는 사본 — 캐시 {@code board.catalog} */
    public BoardCatalogSnapshot snapshot() {
        return caches.getCache(CacheNames.BOARD_CATALOG).get(CacheNames.SINGLE, this::load);
    }

    /** 이 서버의 사본을 버린다 — 다음 {@link #snapshot()} 이 다시 읽는다(다른 서버까지 비우려면 관리자 저장이 {@code CacheRefresher} 로 세대 번호를 올린다) */
    public void invalidate() {
        caches.getCache(CacheNames.BOARD_CATALOG).clear();
    }

    private BoardCatalogSnapshot load() {
        BoardDefaultsValues d = defaults.findById(BoardDefaultsEntity.ROW_ID).map(BoardDefaultsEntity::values)
                .orElseThrow(() -> new IllegalStateException("board_defaults 행이 없다"));
        BoardSecurityValues sec = security.findById(BoardSecuritySettingEntity.ROW_ID).map(BoardSecuritySettingEntity::values)
                .orElseThrow(() -> new IllegalStateException("board_security_settings 행이 없다"));
        BoardReportValues rep = report.findById(BoardReportSettingEntity.ROW_ID).map(BoardReportSettingEntity::values)
                .orElseThrow(() -> new IllegalStateException("board_report_settings 행이 없다"));

        List<GradeEntity> gradeRows = new ArrayList<>(grades.findAllOrdered());
        gradeRows.sort(Comparator.comparingInt(GradeEntity::getLevel));
        Map<String, GradeInfo> gradeMap = new LinkedHashMap<>();
        for (GradeEntity g : gradeRows) {
            gradeMap.put(g.getGradeKey(), new GradeInfo(g.getGradeKey(), g.getLevel(), g.getLabel()));
        }
        String firstGrade = gradeRows.isEmpty() ? null : gradeRows.get(0).getGradeKey();

        Map<Long, List<BoardPrefixInfo>> prefixMap = new HashMap<>();
        for (BoardPrefixEntity p : prefixes.findAll()) {
            prefixMap.computeIfAbsent(p.getBoardId(), k -> new ArrayList<>())
                    .add(new BoardPrefixInfo(p.getId(), p.getName(), p.isStaffOnly(), p.isAdultOnly(), p.getCommonKey(), p.getColor(), p.isActive(), p.getSortOrder()));
        }
        prefixMap.values().forEach(list -> list.sort(Comparator.comparingInt(BoardPrefixInfo::sortOrder).thenComparingLong(BoardPrefixInfo::id)));

        List<EffectiveBoard> list = new ArrayList<>();
        Map<Long, EffectiveBoard> byId = new HashMap<>();
        Map<String, EffectiveBoard> bySlug = new HashMap<>();
        for (BoardEntity b : boards.findAllByOrderBySortOrderAscIdAsc()) {
            EffectiveBoard e = effective(b, d, List.copyOf(prefixMap.getOrDefault(b.getId(), List.of())));
            list.add(e);
            byId.put(e.id(), e);
            bySlug.put(e.slug(), e);
        }
        return new BoardCatalogSnapshot(Collections.unmodifiableList(list), Collections.unmodifiableMap(byId), Collections.unmodifiableMap(bySlug), d, sec, rep,
                Collections.unmodifiableMap(gradeMap), firstGrade);
    }

    /** 유효값 = 게시판 값 ?? 기본값. 이미지 올리기는 고급 에디터에서만 참일 수 있다(간단·평문은 이미지를 받지 않는다) */
    static EffectiveBoard effective(BoardEntity b, BoardDefaultsValues d, List<BoardPrefixInfo> prefixes) {
        BoardPolicyValues p = b.policy();
        String postEditor = or(p.postEditor(), d.postEditor());
        boolean imageSetting = or(p.imageUploadAllowed(), d.imageUploadAllowed());
        String description = b.getDescription() == null || b.getDescription().isBlank() ? null : b.getDescription();
        return new EffectiveBoard(b.getId(), b.getSlug(), b.getName(), b.getNameEn(), description, b.getKind(), b.getLayout(), b.isViewToggle(), b.getColor(),
                b.isAllowAdult() && !BoardFields.KIND_DEAL.equals(b.getKind()) /* 핫딜 게시판은 19금 끔 고정(hotdeal 서버 규칙 8) */, b.isInvestmentNotice(), b.isActive(), b.getSortOrder(), b.getWriteLock(), b.getIntroText(), b.getWriteGuideText(), b.getWriteTemplate(),
                or(p.writeMin(), d.writeMin()), or(p.commentMin(), d.commentMin()), postEditor,
                BoardFields.EDITOR_ADVANCED.equals(postEditor) && imageSetting, imageSetting, or(p.commentEditor(), d.commentEditor()),
                or(p.listSize(), d.listSize()), or(p.pinnedMax(), d.pinnedMax()), or(p.commentProtectMin(), d.commentProtectMin()),
                or(p.commentProtectEdit(), d.commentProtectEdit()), or(p.commentProtectDelete(), d.commentProtectDelete()),
                or(p.commentProtectDelayMinutes(), d.commentProtectDelayMinutes()), or(p.commentGuestDelayMinutes(), d.commentGuestDelayMinutes()),
                or(p.staffHiddenNotice(), d.staffHiddenNotice()), or(p.commentDislikeEnabled(), d.commentDislikeEnabled()),
                or(p.commentLikeLabel(), d.commentLikeLabel()), or(p.commentDislikeLabel(), d.commentDislikeLabel()),
                or(p.pinRequestReviewers(), d.pinRequestReviewers()), or(p.debateEnabled(), d.debateEnabled()), or(p.pollEnabled(), d.pollEnabled()),
                "ON".equals(or(p.pointMode(), d.pointMode())), or(p.pointMinLengthApplied(), d.pointMinLengthApplied()), prefixes,
                Set.copyOf(b.getGadgetExclusions()), b.isAdsHidden(),
                // 운영 게시판 설정(V166·V168) — 일반·소모임 게시판만 받는다(관리자 저장이 막지만, 핫딜·직접홍보는 늘 꺼진 값으로 본다)
                BoardFields.opsAllowed(b.getKind()) ? b.getSecretMode() : BoardFields.SECRET_OFF,
                BoardFields.opsAllowed(b.getKind()) && b.isStatusEnabled(),
                // 글 목록 칩 위치(V169) — 게시판 값 ?? 사이트 기본
                or(b.getChipPosition(), d.listChipPosition()));
    }

    private static <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }

}
