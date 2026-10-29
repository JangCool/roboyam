package com.community.home.service;

import com.community.home.dto.HomeGadgetSpec;
import com.community.home.dto.HomeGridLayout;
import com.community.home.dto.HomeLayoutItem;
import com.community.site.service.SiteThemes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 테마별 홈 기본 배치(시드) — 서버의 원천이다. 운영자가 그 테마의 홈을 저장한 적이 없으면 관리자 편집기·공개 홈 모두 이것을 쓰고, 「테마 기본 배치로」도 이것을 준다.
 * 네 테마 모두 로보얌 구성(같은 가젯·차례·칸 배분)이고 꾸미기만 테마 모양을 따른다(아래 주석). 제목은 설치 프리셋처럼
 * 운영자가 고칠 수 있는 처음 데이터다(화면 문구가 아니다). 가젯 ID 는 {@code seed-<n>}(테마 안에서 1부터) — 저장 요청에 이 ID 가 오면 그 시드의 설정 그대로 새 가젯을 만든다.
 * 게시판을 slug 로 가리키는 설정({@code boardIds})은 ROBOYAM 설치 프리셋의 게시판이다 — 없는 게시판은 공개 홈이 건너뛴다.
 */
public final class HomeLayoutDefaults {

    /** 시드 가젯 ID 앞머리 */
    public static final String SEED_PREFIX = "seed-";

    private HomeLayoutDefaults() {
    }

    /** 테마의 기본 배치와 가젯(모르는 테마는 기본 테마의 것) */
    public record Home(HomeGridLayout layout, List<HomeGadgetSpec> gadgets) {

        public Optional<HomeGadgetSpec> gadget(String id) {
            return gadgets.stream().filter(g -> g.id().equals(id)).findFirst();
        }
    }

    public static Home of(String theme) {
        return switch (SiteThemes.normalize(theme)) {
            case "daum" -> daum();
            case "naver" -> naver();
            case "roboyam" -> roboyam();
            default -> classic();
        };
    }

    /*
     * 네 테마의 기본 배치는 로보얌 구성을 기준으로 같은 가젯·같은 차례·같은 칸 배분이다(사용자 결정 2026-10-29 「로보얌 가젯 기준으로 해당 테마도 구성 맞추기」):
     *   본문 넓은 열(하이라이트 · 인기글 두 열 · [모바일 전용 공구] · 영상·짤 · 방금 올라온 글 두 열) + 오른쪽 열(공구 — 데스크톱 전용 · 인기 소모임).
     *   24칸 테마는 17 + 7, 다음식은 12칸 8 + 4(같은 비율). 높이(최소)도 같다 — 영상·짤만 한 줄 4장(로보얌 템플릿 `galleryColumns: 4`)이 아닌 테마는 3장 두 줄이라 더 높다.
     * 테마마다 다른 것은 꾸미기(머리 모양·제목 색·부제)와 하이라이트 모양뿐이다 — 다음식은 막대 머리 + 제목 색, 네이버식은 막대 머리 + 하이라이트 사진 카드 한 줄, 기본형은 구역 이름 줄(본문은 카드 없음 · 오른쪽 열은 카드).
     */

    /** 기본형 — 24칸: 본문 17칸(하이라이트 · 인기글 · [모바일 전용 공구] · 영상·짤 · 방금 올라온 글) + 오른쪽 7칸(공구 · 인기 소모임) */
    private static Home classic() {
        Builder b = new Builder("default", 24);
        b.add("HIGHLIGHT", "하이라이트", true, true, style().put("headerStyle", "HIDDEN").set("empty", noKeep()), cfg().put("lead", "HERO").put("thumbs", "ALL").put("count", 4),
                null, null, 0, 0, 17, 12);
        b.add("POST_LIST", "인기글", true, true, null,
                cfg().put("sort", "POPULAR").put("rowStyle", "RANK").put("columns", 2).put("count", 6).put("periodTabs", true).set("meta", meta("BOARD", "COMMENTS", "TIME")),
                "인기글 더보기", "/popular", 0, 12, 17, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", false, true, style().put("cardLook", "DEFAULT").set("empty", noKeep()), null, null, null, 0, 20, 17, 10);
        b.add("GALLERY", "영상·짤", true, true, null, cfg().put("count", 6).set("boardIds", meta("video")), "영상·짤 더보기", "/boards/video", 0, 30, 17, 11);
        b.add("POST_LIST", "방금 올라온 글", true, true, null,
                cfg().put("sort", "LATEST").put("rowStyle", "HEADLINE").put("thumbnail", "NONE").put("titleLines", 1).put("columns", 2).put("count", 8)
                        .set("meta", meta("BOARD", "COMMENTS", "TIME")),
                "최신글 더보기", "/latest", 0, 41, 17, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", true, false, style().put("cardLook", "DEFAULT").set("empty", noKeep()), null, null, null, 17, 0, 7, 12);
        b.add("GROUPS", "인기 소모임", true, true, style().put("cardLook", "DEFAULT"), cfg().put("count", 3), "소모임 더 보기", "/groups", 17, 12, 7, 9);
        return b.build();
    }

    /** 다음식 — 12칸: 본문 8칸(하이라이트 · 지금 뜨는 글 · [모바일 전용 공구] · 영상·짤 · 방금 올라온 글) + 오른쪽 4칸(공구 · 인기 소모임) */
    private static Home daum() {
        Builder b = new Builder("daum", 12);
        b.add("HIGHLIGHT", "하이라이트", true, true, style().put("subtitle", "운영자가 고른 글").set("empty", noKeep()), cfg().put("lead", "HERO").put("thumbs", "ALL").put("count", 4),
                null, null, 0, 0, 8, 12);
        b.add("POST_LIST", "지금 뜨는 글", true, true, null,
                cfg().put("sort", "POPULAR").put("rowStyle", "RANK").put("columns", 2).put("count", 6).put("periodTabs", true).set("meta", meta("BOARD", "COMMENTS", "TIME")),
                null, "/popular", 0, 12, 8, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", false, true, style().put("titleColor", "RED").set("empty", noKeep()), null, null, null, 0, 20, 8, 10);
        b.add("GALLERY", "영상·짤", true, true, style().put("titleColor", "ORANGE"), cfg().put("count", 6).set("boardIds", meta("video")), "영상·짤 게시판", "/boards/video",
                0, 30, 8, 11);
        b.add("POST_LIST", "방금 올라온 글", true, true, style().put("titleColor", "TEAL").put("subtitle", "전체 게시판 최신순"),
                cfg().put("sort", "LATEST").put("rowStyle", "HEADLINE").put("thumbnail", "NONE").put("titleLines", 1).put("columns", 2).put("count", 8)
                        .put("newBadgeHours", 1).set("meta", meta("BOARD", "COMMENTS", "TIME")),
                "최신글 더보기", "/latest", 0, 41, 8, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", true, false, style().put("titleColor", "RED").set("empty", noKeep()), null, null, null, 8, 0, 4, 12);
        b.add("GROUPS", "인기 소모임", true, true, style().put("titleColor", "PURPLE"), cfg().put("count", 3), "소모임 전체", "/groups", 8, 12, 4, 9);
        return b.build();
    }

    /** 네이버형 — 24칸: 본문 17칸(하이라이트 사진 카드 한 줄 · 인기글 · [모바일 전용 공구] · 영상·짤 · 방금 올라온 글) + 오른쪽 7칸(공구 · 인기 소모임) */
    private static Home naver() {
        Builder b = new Builder("naver", 24);
        b.add("HIGHLIGHT", "하이라이트", true, true, style().set("empty", noKeep()), cfg().put("lead", "NONE").put("thumbs", "ALL").put("count", 4), null, null, 0, 0, 17, 8);
        b.add("POST_LIST", "인기글", true, true, null,
                cfg().put("sort", "POPULAR").put("rowStyle", "RANK").put("columns", 2).put("count", 6).put("periodTabs", true).set("meta", meta("BOARD", "COMMENTS", "TIME")),
                null, "/popular", 0, 8, 17, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", false, true, style().set("empty", noKeep()), null, null, null, 0, 16, 17, 10);
        b.add("GALLERY", "영상·짤", true, true, null, cfg().put("count", 6).set("boardIds", meta("video")), "영상·짤 게시판", "/boards/video", 0, 26, 17, 11);
        b.add("POST_LIST", "방금 올라온 글", true, true, null,
                cfg().put("sort", "LATEST").put("rowStyle", "HEADLINE").put("thumbnail", "NONE").put("titleLines", 1).put("columns", 2).put("count", 8)
                        .set("meta", meta("BOARD", "COMMENTS", "TIME")),
                null, "/latest", 0, 37, 17, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", true, false, style().set("empty", noKeep()), null, null, null, 17, 0, 7, 12);
        b.add("GROUPS", "인기 소모임", true, true, null, cfg().put("count", 3), "소모임 전체", "/groups", 17, 12, 7, 9);
        return b.build();
    }

    /**
     * 로보얌형 — 24칸: 본문 17칸(하이라이트 · 인기글 두 열 · [모바일 전용 공구] · 영상·짤 · 방금 올라온 글 두 열) + 오른쪽 7칸(공구 · 인기 소모임).
     * 머리는 아이콘 + 제목({@code PLAIN}), 게시판 이름은 회색({@code NEUTRAL}), 구분선은 끈다.
     */
    private static Home roboyam() {
        Builder b = new Builder("roboyam", 24);
        ObjectNode neutral = HomeLayoutJson.object().put("mode", "NEUTRAL");
        neutral.putNull("color");
        b.add("HIGHLIGHT", "하이라이트", true, true, plain("pi-sparkles").set("empty", noKeep()), cfg().put("lead", "HERO").put("thumbs", "ALL").put("count", 4),
                null, null, 0, 0, 17, 12);
        b.add("POST_LIST", "인기글", true, true, plain("pi-chart-line"),
                cfg().put("sort", "POPULAR").put("rowStyle", "RANK").put("columns", 2).put("count", 6).put("dividers", false).put("periodTabs", true)
                        .set("meta", meta("BOARD", "COMMENTS", "TIME")).set("badge", neutral.deepCopy()),
                null, "/popular", 0, 12, 17, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", false, true, plain("pi-shopping-bag").set("empty", noKeep()), null, null, null, 0, 20, 17, 10);
        b.add("GALLERY", "영상·짤", true, true, plain("pi-images"), cfg().put("count", 4).set("boardIds", meta("video")), null, "/boards/video", 0, 30, 17, 7);
        b.add("POST_LIST", "방금 올라온 글", true, true, plain("pi-clock"),
                cfg().put("sort", "LATEST").put("rowStyle", "HEADLINE").put("thumbnail", "NONE").put("titleLines", 1).put("columns", 2).put("count", 8)
                        .put("dividers", false).set("meta", meta("BOARD", "COMMENTS", "TIME")).set("badge", neutral.deepCopy()),
                null, "/latest", 0, 37, 17, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", true, false, plain("pi-shopping-bag").set("empty", noKeep()), null, null, null, 17, 0, 7, 12);
        b.add("GROUPS", "인기 소모임", true, true, plain("pi-users"), cfg().put("count", 3), null, "/groups", 17, 12, 7, 9);
        return b.build();
    }

    private static ObjectNode style() {
        return HomeLayoutJson.object();
    }

    private static ObjectNode plain(String icon) {
        return HomeLayoutJson.object().put("headerStyle", "PLAIN").put("cardLook", "DEFAULT").put("icon", icon);
    }

    private static ObjectNode cfg() {
        return HomeLayoutJson.object();
    }

    private static ObjectNode noKeep() {
        return HomeLayoutJson.object().put("keep", false).put("message", true).put("text", "");
    }

    private static ArrayNode meta(String... values) {
        ArrayNode out = HomeLayoutJson.array();
        for (String v : values) {
            out.add(v);
        }
        return out;
    }

    /** 시드를 차례로 쌓는다 — 꾸미기는 테마 기본 꾸미기에 덮어쓴다 */
    private static final class Builder {

        private final String theme;
        private final int columns;
        private final List<HomeGadgetSpec> gadgets = new ArrayList<>();
        private final List<HomeLayoutItem> items = new ArrayList<>();

        Builder(String theme, int columns) {
            this.theme = theme;
            this.columns = columns;
        }

        void add(String type, String title, boolean desktop, boolean mobile, JsonNode styleOverride, ObjectNode config, String moreLabel, String moreTo,
                 int x, int y, int w, int h) {
            String id = SEED_PREFIX + (gadgets.size() + 1);
            ObjectNode style = HomeGadgetKinds.baseStyle(theme);
            if (styleOverride != null) {
                styleOverride.properties().forEach(e -> style.set(e.getKey(), e.getValue()));
            }
            ObjectNode more = null;
            if (moreTo != null) {
                more = HomeLayoutJson.object().put("label", moreLabel).put("to", moreTo);
            }
            gadgets.add(new HomeGadgetSpec(id, type, title, desktop, mobile, style, config == null ? HomeLayoutJson.object() : config, more));
            items.add(new HomeLayoutItem(id, x, y, w, h));
        }

        Home build() {
            return new Home(new HomeGridLayout(columns, items), List.copyOf(gadgets));
        }
    }
}
