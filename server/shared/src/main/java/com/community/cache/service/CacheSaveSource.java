package com.community.cache.service;

import com.community.cache.definition.CacheCatalog;
import com.community.cache.definition.CacheNames;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 「자동(저장)」 갱신의 계기 — 관리자 화면에서 무엇을 저장했는가와 그때 비울 캐시. 기록 글은 메시지 {@code cache.source.<이름>}(+ 대상 이름이 있으면 「— 이름」).
 * 관리자 저장 서비스가 같은 트랜잭션에서 {@link CacheRefresher#onSave} 로 부른다(cache-admin-contract 「정책」). 회원이 새 글을 쓰는 것은 TTL 에 맡긴다.
 */
public enum CacheSaveSource {

    /** 홈 구성(가젯 배치) 저장 — 가젯 데이터는 설정 지문이 키라 바뀐 설정은 새 항목이 된다 */
    HOME_LAYOUT_SAVE(CacheNames.HOME_LAYOUT),
    /** 가젯 페이지 저장(배치·머리) — 공개 메뉴의 가젯 페이지 항목 이름도 바뀐다 */
    GADGET_PAGE_SAVE(CacheNames.PAGES_LAYOUT, CacheNames.MENU_PUBLIC),
    /** 게시판 레이아웃 저장 */
    BOARD_LAYOUT_SAVE(CacheNames.BOARDS_LAYOUT),
    /** 사이트 테마 저장 — 사이트 정보의 테마(배치 캐시는 테마가 키다) */
    THEME_SAVE(CacheNames.SITE_INFO),
    /** 공개 메뉴 저장 — 가젯 페이지의 기준 폴더(메뉴 폴더) 게시판도 메뉴에서 온다 */
    MENU_SAVE(CacheNames.MENU_PUBLIC, CacheNames.PAGES_LAYOUT),
    /** 사이트 기본 정보(이름·소개·로고·아이콘·표시 기본값·공개 주소) 저장 — 알림 메일의 사이트 이름·로고도 같은 설정이다 */
    SITE_SETTINGS_SAVE(CacheNames.SITE_INFO, CacheNames.NOTIFICATION_TEMPLATES),
    /** 운영 주체 단계·연락처 저장 */
    SITE_OPERATOR_SAVE(CacheNames.SITE_INFO),
    /** 공지 띠 저장 */
    NOTICE_BAR_SAVE(CacheNames.SITE_NOTICE_BAR),
    /** 사이트 팝업 저장 */
    POPUP_SAVE(CacheNames.SITE_POPUPS),
    /** 약관 게시·옵션 저장 */
    TERMS_SAVE(CacheNames.TERMS_PUBLIC),
    /** 오픈소스 라이선스 안내 문구·직접 추가 항목 저장 */
    OSS_NOTICE_SAVE(CacheNames.OSS_NOTICE),
    /** 공지 관리(전체 공지·공지 글) 저장 — 공지 띠의 「공지 게시판」 줄도 같은 글이다 */
    NOTICE_SAVE(CacheNames.NOTICE_GLOBAL, CacheNames.SITE_NOTICE_BAR),
    /** 게시판 설정 저장 — 게시판 사본과, 게시판 이름·색·노출 규칙을 담은 메뉴·글 목록 */
    BOARD_SAVE(boardAndLists()),
    /** 게시판 기본 설정 저장 — 게시판 사본 · 사이트 정보의 목록 표시 기본값 · 글 목록 */
    BOARD_DEFAULTS_SAVE(withSiteInfo(boardAndLists())),
    /** 등급 저장 — 게시판 사본의 등급표 · 메뉴의 등급 조건 */
    GRADE_SAVE(CacheNames.BOARD_CATALOG, CacheNames.MENU_PUBLIC),
    /** 통계 수집 설정 저장 — 사이트 정보에 수집 스위치가 실린다 */
    STATS_SAVE(CacheNames.STATS_COLLECTION, CacheNames.SITE_INFO),
    /** 커머스 요청 한도 저장 */
    RATE_LIMITS_SAVE(CacheNames.COMMERCE_RATE_LIMITS),
    /** 핫딜 링크 규칙·제휴 몰 저장 */
    HOTDEAL_RULES_SAVE(CacheNames.HOTDEAL_RULES),
    /** 핫딜 설정 저장 — 딜 표시·자동 종료 일수를 쓰는 목록 */
    HOTDEAL_SETTINGS_SAVE(CacheNames.HOTDEAL_SETTINGS, CacheNames.GADGET_HOT_DEALS, CacheNames.HOME_POPULAR),
    /** 알림 문구·템플릿 저장 */
    NOTIFICATION_TEMPLATE_SAVE(CacheNames.NOTIFICATION_TEMPLATES),
    /** 포인트 정책 저장 */
    POINT_POLICY_SAVE(CacheNames.POINT_POLICY),
    /** 하이라이트 저장 */
    HIGHLIGHT_SAVE(CacheNames.GADGET_HIGHLIGHT),
    /** 인기글 설정·제외 저장 */
    POPULAR_SAVE(CacheNames.HOME_POPULAR, CacheNames.GADGET_POST_LIST),
    /** 운영자가 글을 숨기거나 지웠다(게시글 관리·신고 처리 등) — 글 목록 계열 */
    POST_MODERATION(postLists());

    private final List<String> keys;

    CacheSaveSource(String... keys) {
        Set<String> out = new LinkedHashSet<>(List.of(keys));
        for (String key : out) {
            if (CacheCatalog.find(key).isEmpty()) {
                throw new IllegalStateException("카탈로그에 없는 캐시: " + key);
            }
        }
        this.keys = List.copyOf(out);
    }

    /** 비울 캐시 키 */
    public List<String> keys() {
        return keys;
    }

    public String messageKey() {
        return "cache.source." + name();
    }

    /** 글 목록 계열 — 최신글 · 인기글 · 가젯 데이터 전부 */
    private static String[] postLists() {
        List<String> out = new ArrayList<>(List.of(CacheNames.HOME_LATEST, CacheNames.HOME_POPULAR));
        out.addAll(CacheCatalog.gadgetKeys());
        return out.toArray(String[]::new);
    }

    private static String[] boardAndLists() {
        List<String> out = new ArrayList<>(List.of(CacheNames.BOARD_CATALOG, CacheNames.MENU_PUBLIC, CacheNames.NOTICE_GLOBAL));
        out.addAll(List.of(postLists()));
        return out.toArray(String[]::new);
    }

    private static String[] withSiteInfo(String[] keys) {
        List<String> out = new ArrayList<>(List.of(keys));
        out.add(CacheNames.SITE_INFO);
        return out.toArray(String[]::new);
    }
}
