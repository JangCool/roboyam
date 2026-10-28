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
 * 가젯·자리·꾸미기·설정은 화면 확정 때의 목업(admin {@code home-layout-mock.ts}·user-web {@code home-gadget-mock.ts})과 같다. 제목·운영 원칙 문구는 설치 프리셋처럼
 * 운영자가 고칠 수 있는 처음 데이터다(화면 문구가 아니다). 가젯 ID 는 {@code seed-<n>}(테마 안에서 1부터) — 저장 요청에 이 ID 가 오면 그 시드의 설정 그대로 새 가젯을 만든다.
 * 게시판을 slug 로 가리키는 설정({@code boardIds})은 ROBOYAM 설치 프리셋의 게시판이다 — 없는 게시판은 공개 홈이 건너뛴다.
 */
public final class HomeLayoutDefaults {

    /** 시드 가젯 ID 앞머리 */
    public static final String SEED_PREFIX = "seed-";

    private static final String RULES = String.join("\n", "주장에는 출처를 붙여요", "비방·인신공격은 금지예요", "허위정보는 신고할 수 있어요", "산업·주식 글은 투자 권유가 아니에요");

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

    /** 기본형 — 24칸: 본문 17칸(모바일 전용 공구 · 하이라이트 · 인기글 · 최신글 · 영상·짤) + 오른쪽 7칸(공구 · 소모임 · 운영 원칙 — 데스크톱 전용) */
    private static Home classic() {
        Builder b = new Builder("default", 24);
        b.add("GROUP_BUY", "공동구매", false, true, style().put("headerStyle", "HIDDEN").put("cardLook", "DEFAULT").set("empty", noKeep()), null, null, null, 0, 0, 17, 10);
        b.add("HIGHLIGHT", "하이라이트", true, true, style().put("headerStyle", "HIDDEN").set("empty", noKeep()), cfg().put("lead", "HERO").put("thumbs", "ALL"),
                null, null, 0, 10, 17, 13);
        b.add("POST_LIST", "인기글", true, true, null, cfg().put("sort", "POPULAR").put("rowStyle", "RANK").put("periodTabs", true).set("meta", meta("BOARD", "COMMENTS", "TIME")),
                "인기글 더보기", "/popular", 0, 23, 17, 15);
        b.add("POST_LIST", "최신글", true, true, null, cfg().put("sort", "LATEST").put("rowStyle", "HEADLINE").set("meta", meta("BOARD", "COMMENTS", "TIME")),
                "최신글 더보기", "/latest", 0, 38, 17, 20);
        b.add("GALLERY", "영상·짤", true, true, null, cfg().put("count", 6).set("boardIds", meta("video")), null, "/boards/video", 0, 58, 17, 11);
        b.add("GROUP_BUY", "진행 중 공동구매", true, false, style().put("cardLook", "DEFAULT").set("empty", noKeep()), null, null, null, 17, 0, 7, 12);
        b.add("GROUPS", "인기 소모임", true, false, style().put("cardLook", "DEFAULT"), cfg().put("count", 3), "소모임 더 보기", "/groups", 17, 12, 7, 9);
        b.add("NOTICE_TEXT", "운영 원칙", true, false, style().put("cardLook", "DEFAULT").set("empty", noKeep()), cfg().put("format", "BULLETS").put("text", RULES),
                "공지 보기", "/boards/notice", 17, 21, 7, 6);
        return b.build();
    }

    /** 다음식 — 12칸: 본문 8칸(하이라이트 · 지금 뜨는 글 · 갤러리 · 방금 올라온 글) + 오른쪽 4칸(공구 · 로봇 인기 · 소모임 · 운영 원칙) */
    private static Home daum() {
        Builder b = new Builder("daum", 12);
        b.add("HIGHLIGHT", "하이라이트", true, true, style().put("subtitle", "운영자가 고른 글").set("empty", noKeep()), cfg().put("lead", "HERO").put("thumbs", "ALL"),
                null, null, 0, 0, 8, 13);
        b.add("POST_LIST", "지금 뜨는 글", true, true, null,
                cfg().put("sort", "POPULAR").put("rowStyle", "RANK").put("columns", 2).put("periodTabs", true).set("meta", meta("BOARD", "COMMENTS", "TIME")),
                null, "/popular", 0, 13, 8, 10);
        b.add("GALLERY", "갤러리", true, true, style().put("titleColor", "ORANGE"), cfg().put("count", 6).set("boardIds", meta("video")), "영상·짤 게시판", "/boards/video",
                0, 23, 8, 11);
        b.add("POST_LIST", "방금 올라온 글", true, true, style().put("titleColor", "TEAL").put("subtitle", "전체 게시판 최신순"),
                cfg().put("sort", "LATEST").put("rowStyle", "HEADLINE").put("count", 8).put("newBadgeHours", 1).set("meta", meta("BOARD", "PREFIX", "COMMENTS", "TIME")),
                "최신글 더보기", "/latest", 0, 34, 8, 18);
        b.add("GROUP_BUY", "진행 중 공동구매", true, true, style().put("titleColor", "RED").set("empty", noKeep()), null, null, null, 8, 0, 4, 12);
        b.add("POST_LIST", "로봇 인기", true, true, style().put("titleColor", "PURPLE"),
                cfg().put("sort", "POPULAR").put("rowStyle", "RANK").put("count", 5).set("meta", meta("COMMENTS", "TIME")).set("boardIds", meta("robot")),
                "로봇 게시판", "/boards/robot", 8, 12, 4, 9);
        b.add("GROUPS", "인기 소모임", true, false, style().put("titleColor", "PURPLE"), cfg().put("count", 3), "소모임 전체", "/groups", 8, 21, 4, 9);
        b.add("NOTICE_TEXT", "운영 원칙", true, false, style().put("headerStyle", "PLAIN").set("empty", noKeep()), cfg().put("format", "BULLETS").put("text", RULES),
                "공지 보기", "/boards/notice", 8, 30, 4, 6);
        return b.build();
    }

    /** 네이버형 — 24칸: 본문 17칸(하이라이트 행 → 게시판 카드 격자 · 갤러리) + 오른쪽 7칸(많이 본 글 · 공구 · 소모임). 바로가기 줄은 기본 배치에 두지 않는다 */
    private static Home naver() {
        Builder b = new Builder("naver", 24);
        b.add("HIGHLIGHT", "하이라이트", true, true, style().set("empty", noKeep()), cfg().put("lead", "NONE").put("thumbs", "ALL").put("count", 4), null, null, 0, 0, 17, 8);
        b.add("BOARD_CARDS", "게시판", true, true, style().put("headerStyle", "HIDDEN").put("cardLook", "FLAT"),
                cfg().put("columns", "AUTO").put("perBoard", 4).set("boardIds", meta("ai", "robot", "stock", "game", "sports", "free")), null, null, 0, 8, 17, 30);
        b.add("GALLERY", "갤러리", true, true, style().put("titleColor", "ORANGE"), cfg().put("count", 6).set("boardIds", meta("video")), "영상·짤 게시판", "/boards/video",
                0, 38, 17, 11);
        b.add("POST_LIST", "많이 본 글", true, true, null, cfg().put("sort", "VIEWS").put("rowStyle", "RANK").put("count", 5).set("meta", meta("BOARD", "TIME")),
                null, null, 17, 0, 7, 9);
        b.add("GROUP_BUY", "진행 중 공동구매", true, true, style().set("empty", noKeep()), null, null, null, 17, 9, 7, 12);
        b.add("GROUPS", "인기 소모임", true, false, null, cfg().put("count", 3), "소모임 전체", "/groups", 17, 21, 7, 9);
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
