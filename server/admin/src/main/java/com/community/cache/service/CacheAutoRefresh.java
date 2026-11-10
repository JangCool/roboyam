package com.community.cache.service;

import com.community.audit.dto.AuditEvent;
import com.community.audit.dto.AuditRecorded;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.board.entity.BoardEntity;
import com.community.board.repository.master.BoardRepository;
import com.community.home.entity.GadgetPageEntity;
import com.community.home.repository.master.GadgetPageRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 관리자 화면에서 공개 화면 데이터를 저장하면 관련 캐시를 비운다(기록 「자동(저장)」, docs/features/cache-admin-contract.md 「정책」).
 *
 * <ul>
 *   <li>저장마다 남는 감사 기록({@link AuditRecorded} — {@code AuditLogService#record} 가 같은 스레드·같은 트랜잭션에서 알린다)의 작업 이름으로 계기({@link CacheSaveSource})를 고른다.
 *       그래서 세대 번호도 저장과 같은 트랜잭션에서 오른다(롤백되면 함께 사라진다). 성공한 기록만 본다.</li>
 *   <li>한 저장에서 같은 계기는 한 번만 올린다({@link CacheRefresher#onSave}). 대상 이름은 게시판·가젯 페이지만 붙인다.</li>
 *   <li>설정 사본·내부 규칙(포인트 정책 · 요청 한도 · 통계 수집 · 핫딜 규칙·설정 · 알림 문구)은 예전 {@code invalidate()} 자리에서 서비스가 직접 올린다 — 여기서 겹쳐 올리지 않는다.</li>
 *   <li>관리자가 글을 숨기거나 지우면(게시글 관리·신고 처리) 글 목록 계열({@code home.latest}·{@code home.popular}·{@code gadget.*})을 비운다. 회원이 새 글을 쓰는 것은 TTL 에 맡긴다.</li>
 * </ul>
 */
@Component
public class CacheAutoRefresh {

    /** 감사 작업 이름 → 계기 */
    static final Map<String, CacheSaveSource> BY_ACTION;

    static {
        Map<String, CacheSaveSource> map = new HashMap<>();
        put(map, CacheSaveSource.HOME_LAYOUT_SAVE, "HOME_LAYOUT_UPDATE");
        put(map, CacheSaveSource.GADGET_PAGE_SAVE, "GADGET_PAGE_CREATE", "GADGET_PAGE_UPDATE", "GADGET_PAGE_DELETE", "GADGET_PAGE_LAYOUT_UPDATE");
        put(map, CacheSaveSource.BOARD_LAYOUT_SAVE, "BOARD_LAYOUT_UPDATE");
        put(map, CacheSaveSource.THEME_SAVE, "SITE_THEME_UPDATE", "SITE_THEME_MENU_UPDATE");
        put(map, CacheSaveSource.MENU_SAVE, "MENU_SET_CREATE", "MENU_SET_UPDATE", "MENU_SET_DELETE", "MENU_TREE_UPDATE");
        put(map, CacheSaveSource.SITE_SETTINGS_SAVE, "SITE_BASIC_UPDATE", "SITE_DISPLAY_UPDATE", "SITE_LOGO_CHANGE", "SITE_LOGO_REMOVE", "SITE_ICON_CHANGE",
                "SITE_ICON_RESET", "SITE_OG_CHANGE", "SITE_OG_RESET");
        put(map, CacheSaveSource.SITE_OPERATOR_SAVE, "SITE_OPERATOR_UPDATE", "SITE_OPERATOR_STAGE_CHANGE");
        put(map, CacheSaveSource.NOTICE_BAR_SAVE, "SITE_NOTICE_BAR_UPDATE");
        put(map, CacheSaveSource.POPUP_SAVE, "SITE_POPUP_CREATE", "SITE_POPUP_UPDATE", "SITE_POPUP_DELETE", "SITE_POPUP_ORDER", "SITE_POPUP_VISIBLE",
                "SITE_POPUP_LAYOUT");
        put(map, CacheSaveSource.TERMS_SAVE, "TERMS_PUBLISH", "TERMS_LINKS_UPDATE");
        put(map, CacheSaveSource.OSS_NOTICE_SAVE, "OSS_NOTICE_INTRO_UPDATE", "OSS_NOTICE_ENTRY_CREATE", "OSS_NOTICE_ENTRY_UPDATE", "OSS_NOTICE_ENTRY_HIDDEN",
                "OSS_NOTICE_ENTRY_DELETE");
        put(map, CacheSaveSource.NOTICE_SAVE, "NOTICE_CREATE", "NOTICE_UPDATE", "NOTICE_DELETE", "NOTICE_PIN", "NOTICE_UNPIN");
        put(map, CacheSaveSource.BOARD_SAVE, "BOARD_CREATE", "BOARD_UPDATE", "BOARD_ACTIVE", "BOARD_ORDER", "BOARD_DELETE");
        put(map, CacheSaveSource.BOARD_DEFAULTS_SAVE, "BOARD_DEFAULTS_UPDATE");
        put(map, CacheSaveSource.GRADE_SAVE, "GRADE_UPDATE");
        put(map, CacheSaveSource.HIGHLIGHT_SAVE, "HIGHLIGHT_CREATE", "HIGHLIGHT_UPDATE", "HIGHLIGHT_DELETE", "HIGHLIGHT_ORDER", "HIGHLIGHT_VISIBLE");
        put(map, CacheSaveSource.POPULAR_SAVE, "POPULAR_SETTINGS_UPDATE", "POPULAR_EXCLUDE", "POPULAR_RESTORE");
        put(map, CacheSaveSource.POST_MODERATION, "POST_HIDE", "POST_UNHIDE", "POST_DELETE", "POST_RESTORE", "POST_MOVE", "POST_NOTICE", "POST_PIN", "POST_UNPIN",
                "POST_BULK", "POST_POLL_DELETE", "POST_PROCESS_STATUS", "REPORT_RESOLVE", "REPORT_REOPEN", "BOARD_STAFF_ACTION_RESTORE", "SITE_ACTION_RESTORE");
        BY_ACTION = Map.copyOf(map);
    }

    private final CacheRefresher refresher;
    private final BoardRepository boards;
    private final GadgetPageRepository pages;

    public CacheAutoRefresh(CacheRefresher refresher, BoardRepository boards, GadgetPageRepository pages) {
        this.refresher = refresher;
        this.boards = boards;
        this.pages = pages;
    }

    @EventListener
    public void on(AuditRecorded recorded) {
        AuditEvent e = recorded.event();
        if (e.result() != AuditResult.SUCCESS) {
            return;
        }
        CacheSaveSource source = BY_ACTION.get(e.action());
        if (source == null) {
            return;
        }
        refresher.onSave(source, detail(e), e.actorType() == AuditActorType.OPERATOR ? e.actorId() : null);
    }

    /** 대상 이름 — 게시판 · 가젯 페이지만(지워졌으면 없음) */
    private String detail(AuditEvent e) {
        Long id = parse(e.targetId());
        if (id == null) {
            return null;
        }
        if ("BOARD".equals(e.targetType())) {
            return boards.findById(id).map(BoardEntity::getName).orElse(null);
        }
        if ("GADGET_PAGE".equals(e.targetType())) {
            return pages.findById(id).map(GadgetPageEntity::getName).orElse(null);
        }
        return null;
    }

    private static Long parse(String id) {
        if (id == null) {
            return null;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void put(Map<String, CacheSaveSource> map, CacheSaveSource source, String... actions) {
        for (String action : List.of(actions)) {
            if (map.put(action, source) != null) {
                throw new IllegalStateException("감사 작업이 두 계기에 걸렸다: " + action);
            }
        }
    }
}
