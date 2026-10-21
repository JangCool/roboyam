package com.community.installation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Locale;

/**
 * 설치 때 고르는 <b>기본 게시판 구성(프리셋)</b> 하나 — {@code install-presets/*.json} 데이터의 모양이다. 프리셋은 코드에 박지 않고 데이터로 둔다.
 *
 * <p>JSON 에는 게시판을 실제로 만들 때 쓸 칸(주소·목록 모양·표시색·종류 …)도 들어 있다. 지금은 게시판 표가 없어 설치는 <b>선택(아이디)만 기록</b>하므로
 * 이 레코드는 목록에 보이는 칸과 검사에 필요한 칸만 읽고 나머지는 무시한다({@code ignoreUnknown}) — 게시판을 만드는 적용을 구현할 때 칸을 더한다.
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
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BoardPreset(String id, String name, String nameEn, String description, String descriptionEn, boolean defaultPreset, int order,
                          String siteTagline, List<Board> boards) {

    /** 요청 언어가 {@code en} 이고 영문이 비어 있지 않으면 영문, 아니면 기본값 */
    public static String localized(String name, String nameEn, Locale locale) {
        return "en".equals(locale.getLanguage()) && nameEn != null && !nameEn.isBlank() ? nameEn : name;
    }

    /**
     * 프리셋 안의 게시판 하나.
     *
     * @param nameEn    영문 이름
     * @param slug      게시판 주소 — 프리셋 안에서 겹치지 않는다
     * @param kind      {@code GENERAL}(비우면 이것) · {@code GROUP}(소모임 게시판 — 메뉴에 없는 그릇이라 설치 화면 목록에 싣지 않는다)
     * @param sortOrder 게시판 순서(10 단위)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Board(String slug, String name, String nameEn, String kind, int sortOrder, List<Prefix> prefixes) {

        /** 소모임 게시판인가 */
        public boolean isGroupBoard() {
            return "GROUP".equals(kind);
        }
    }

    /** 프리셋 안의 말머리 하나. {@code nameEn} 은 영문 이름 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prefix(String name, String nameEn, int sortOrder) {
    }
}
