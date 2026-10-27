package com.community.installation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Locale;

/**
 * 설치 때 고르는 <b>기본 게시판 구성(프리셋)</b> 하나 — {@code install-presets/*.json} 데이터의 모양이다. 프리셋은 코드에 박지 않고 데이터로 둔다.
 *
 * <p>JSON 에는 게시판을 실제로 만들 때 쓸 칸(주소·목록 모양·표시색·종류·소모임 분류 이름 …)과 공개 메뉴 {@code main} 트리({@code menu})도 들어 있다.
 * 설치({@code complete})는 이 칸으로 일반 게시판 → 소모임 게시판·분류 → 메뉴 트리를 만들고({@code BoardPresetApplier}), 이미 설치된 곳의 한 번 적용은 일반 게시판만 만든다.
 * 모르는 칸은 무시한다({@code ignoreUnknown}). 구조의 정본은 docs/features/install-contract.md 「프리셋 구조 (2026-10-27)」.
 *
 * @param id            {@code NEUTRAL} · {@code ROBOYAM} … 설치 요청의 {@code boardPreset} 값
 * <p>이름·설명은 기본값(한국어, {@code name}·{@code description})과 영문({@code nameEn}·{@code descriptionEn}) 짝으로 둔다 — 프로젝트의 "이름은 {@code name} + 선택 {@code nameEn}" 규칙과 같다.
 * 응답은 요청 언어가 {@code en} 이고 영문이 있으면 영문, 아니면 기본값이다({@link #localized}). 영문이 없는 프리셋도 {@code ko} 로 나가므로 깨지지 않지만, 이 저장소의 프리셋은 모두 영문을 갖추도록 시작 때 검사한다.
 *
 * @param name          설치 화면에 보이는 이름(한국어)
 * @param nameEn        영문 이름
 * @param description   설치 화면에 보이는 한 줄 설명(한국어)
 * @param descriptionEn 영문 설명
 * @param defaultPreset 설치 요청이 고르지 않았을 때 쓰는 것 — 프리셋 전체에서 정확히 하나
 * @param order         설치 화면의 순서(작을수록 앞)
 * @param siteTagline   이 구성을 고르고 설치 입력의 슬로건이 비어 있을 때 사이트 설정에 넣을 슬로건 — 없으면(null) 넣지 않는다. 입력한 값이 있으면 그것이 이긴다
 * @param menu          설치 때 공개 메뉴 {@code main} 그룹의 트리를 바꿀 정의(최상위 노드 차례대로). 없으면(null) 메뉴를 바꾸지 않는다(V43 시드 + 게시판 자동 추가 그대로)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BoardPreset(String id, String name, String nameEn, String description, String descriptionEn, boolean defaultPreset, int order,
                          String siteTagline, List<Board> boards, List<MenuEntry> menu) {

    /** 요청 언어가 {@code en} 이고 영문이 비어 있지 않으면 영문, 아니면 기본값 */
    public static String localized(String name, String nameEn, Locale locale) {
        return "en".equals(locale.getLanguage()) && nameEn != null && !nameEn.isBlank() ? nameEn : name;
    }

    /**
     * 프리셋 안의 게시판 하나.
     *
     * @param nameEn    영문 이름
     * @param slug      게시판 주소 — 프리셋 안에서 겹치지 않는다
     * @param kind           {@code GENERAL}(비우면 이것) · {@code GROUP}(소모임 게시판 — 메뉴에 두지 않는 그릇이라 설치 화면 목록·메뉴에 싣지 않는다. 설치 때만 만든다)
     * @param sortOrder      게시판 순서(10 단위)
     * @param description    설명(없으면 빈 글자)
     * @param layout         LIST · CARD · GALLERY(비우면 LIST)
     * @param color          표시색 이름(없으면 자동)
     * @param staffOnlyWrite 운영진만 글쓰기 — 게시판 정책 writeMin = STAFF 로 만든다
     * @param groupCategoryName 소모임 게시판({@code GROUP})만 — 이 이름의 소모임 분류(없으면 만든다)에 게시판을 잇는다. 일반 게시판은 비운다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Board(String slug, String name, String nameEn, String kind, int sortOrder, List<Prefix> prefixes, String description, String layout,
                        boolean viewToggle, String color, boolean investmentNotice, boolean staffOnlyWrite, String groupCategoryName) {

        /** 소모임 게시판인가 */
        public boolean isGroupBoard() {
            return "GROUP".equals(kind);
        }
    }

    /**
     * 프리셋 안의 말머리 하나. {@code nameEn} 은 영문 이름. {@code common} 이면 같은 이름의 공통 말머리(질문·후기·잡담·공지)를 켠다는 뜻이고,
     * 아니면 고유 말머리({@code staffOnly} = 운영진 전용)다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prefix(String name, String nameEn, int sortOrder, boolean common, boolean staffOnly) {
    }

    /**
     * 공개 메뉴 {@code main} 트리의 노드 하나 — 메뉴 표({@code menu_nodes})의 노드가 된다. {@code type} 에 따라 쓰는 칸이 다르다.
     *
     * <ul>
     *   <li>{@code FOLDER}: {@code label}(필수)·{@code nameEn}·{@code autoAdd}(새 게시판이 끝에 들어가는 폴더 — 트리에 하나까지)·{@code collapsible}·{@code collapsed}·{@code children}.</li>
     *   <li>{@code BUILTIN}: {@code key} = 앱 화면 키({@code MenuBuiltin}). 로그인 필요 화면은 {@code audience} 가 {@code MEMBER}·{@code STAFF} 여야 한다.</li>
     *   <li>{@code BOARD}: {@code slug} = 이 프리셋의 일반 게시판 주소(소모임 게시판 불가). 설치 때 만들지 못한 게시판이면 그 항목은 빠진다.</li>
     *   <li>항목 공통: {@code label}·{@code nameEn}(비우면 대상 이름) · {@code mobileTab} · {@code audience}(비우면 {@code ALL} · {@code GUEST} · {@code MEMBER} · {@code STAFF}
     *       — 등급 키는 설치마다 달라 {@code GRADE_MIN} 은 받지 않는다).</li>
     *   <li>공통: {@code visible}(비우면 보임).</li>
     * </ul>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MenuEntry(String type, String key, String slug, String label, String nameEn, String audience, Boolean visible, Boolean mobileTab,
                            Boolean autoAdd, Boolean collapsible, Boolean collapsed, List<MenuEntry> children) {

        public static final String FOLDER = "FOLDER";
        public static final String BUILTIN = "BUILTIN";
        public static final String BOARD = "BOARD";

        public MenuEntry {
            children = children == null ? List.of() : List.copyOf(children);
        }

        public boolean isFolder() {
            return FOLDER.equals(type);
        }

        /** 보이는 대상(비우면 {@code ALL}) */
        public String audienceOrAll() {
            return audience == null ? "ALL" : audience;
        }

        /** 보임(비우면 true) */
        public boolean visibleOrDefault() {
            return visible == null || visible;
        }

        // 아래 켜짐 칸은 JSON 에서 생략할 수 있게 Boolean 으로 받는다(Jackson 3 은 빠진 primitive 칸을 null → 실패로 본다). 비우면 꺼짐

        public boolean tabOn() {
            return Boolean.TRUE.equals(mobileTab);
        }

        public boolean autoAddOn() {
            return Boolean.TRUE.equals(autoAdd);
        }

        public boolean collapsibleOn() {
            return Boolean.TRUE.equals(collapsible);
        }

        public boolean collapsedOn() {
            return Boolean.TRUE.equals(collapsed);
        }
    }
}
