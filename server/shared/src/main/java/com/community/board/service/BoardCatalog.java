package com.community.board.service;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 게시판·유효 정책·기본 설정 하위 화면(보안·신고)·등급 목록의 <b>짧은 메모리 사본</b>({@link BoardCatalogSnapshot}) — 공개 게시판 API 가 요청마다 DB 를 읽지 않게 한다.
 *
 * <ul>
 *   <li>{@value #TTL_SECONDS}초마다 다시 읽는다. 관리자 앱은 다른 서버라 게시판·기본 설정을 저장해도 <b>이 서버에는 최대 그만큼 늦게</b> 닿는다(계약에 적은 반영 지연).
 *       같은 서버 안에서 바꾼 경우는 {@link #invalidate()} 로 바로 비운다.</li>
 *   <li>다시 읽다 실패하면 이전 사본을 쓰고 5초 뒤 다시 시도한다(사이트 설정 사본 {@code PublicSiteService} 와 같은 방식).</li>
 *   <li>유효값 = 게시판 값 ?? 기본값. 게시판별로 덮어쓸 수 없는 칸은 {@link BoardCatalogSnapshot#defaults()} 에서 읽는다.</li>
 * </ul>
 */
@Component
public class BoardCatalog {

    private static final Logger log = LoggerFactory.getLogger(BoardCatalog.class);

    static final int TTL_SECONDS = 15;
    private static final long TTL_NANOS = Duration.ofSeconds(TTL_SECONDS).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    private final BoardRepository boards;
    private final BoardPrefixRepository prefixes;
    private final BoardDefaultsRepository defaults;
    private final BoardSecuritySettingRepository security;
    private final BoardReportSettingRepository report;
    private final GradeRepositoryQDSL grades;
    private volatile Cached cached;

    public BoardCatalog(BoardRepository boards, BoardPrefixRepository prefixes, BoardDefaultsRepository defaults, BoardSecuritySettingRepository security,
                        BoardReportSettingRepository report, GradeRepositoryQDSL grades) {
        this.boards = boards;
        this.prefixes = prefixes;
        this.defaults = defaults;
        this.security = security;
        this.report = report;
        this.grades = grades;
    }

    /** 지금 쓸 수 있는 사본 */
    public BoardCatalogSnapshot snapshot() {
        Cached current = cached;
        if (current != null && System.nanoTime() - current.loadedAt < TTL_NANOS) {
            return current.snapshot;
        }
        synchronized (this) {
            current = cached;
            long now = System.nanoTime();
            if (current != null && now - current.loadedAt < TTL_NANOS) {
                return current.snapshot;
            }
            try {
                BoardCatalogSnapshot loaded = load();
                cached = new Cached(loaded, now);
                return loaded;
            } catch (RuntimeException e) {
                if (current == null) {
                    throw e;
                }
                log.warn("게시판 사본을 다시 읽지 못해 이전 값을 쓴다: {}", e.toString());
                cached = new Cached(current.snapshot, now - TTL_NANOS + RETRY_NANOS);
                return current.snapshot;
            }
        }
    }

    /** 사본을 버린다 — 다음 {@link #snapshot()} 이 다시 읽는다 */
    public void invalidate() {
        cached = null;
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
                    .add(new BoardPrefixInfo(p.getId(), p.getName(), p.isStaffOnly(), p.getCommonKey(), p.getColor(), p.isActive(), p.getSortOrder()));
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

    /** 유효값 = 게시판 값 ?? 기본값 */
    static EffectiveBoard effective(BoardEntity b, BoardDefaultsValues d, List<BoardPrefixInfo> prefixes) {
        BoardPolicyValues p = b.policy();
        String description = b.getDescription() == null || b.getDescription().isBlank() ? null : b.getDescription();
        return new EffectiveBoard(b.getId(), b.getSlug(), b.getName(), b.getNameEn(), description, b.getKind(), b.getLayout(), b.isViewToggle(), b.getColor(),
                b.isAllowAdult(), b.isInvestmentNotice(), b.isActive(), b.getSortOrder(), b.getWriteLock(), b.getIntroText(), b.getWriteGuideText(),
                or(p.writeMin(), d.writeMin()), or(p.commentMin(), d.commentMin()), or(p.imageUploadAllowed(), d.imageUploadAllowed()),
                or(p.listSize(), d.listSize()), or(p.pinnedMax(), d.pinnedMax()), or(p.commentProtectMin(), d.commentProtectMin()),
                or(p.commentProtectEdit(), d.commentProtectEdit()), or(p.commentProtectDelete(), d.commentProtectDelete()),
                or(p.commentProtectDelayMinutes(), d.commentProtectDelayMinutes()), or(p.commentGuestDelayMinutes(), d.commentGuestDelayMinutes()), prefixes);
    }

    private static <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private record Cached(BoardCatalogSnapshot snapshot, long loadedAt) {
    }
}
