package com.community.home.service;

import com.community.board.service.BoardFields;
import com.community.home.dto.GadgetSpec;
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
 * 네 테마 모두 아이로빗 구성(같은 가젯·차례·칸 배분)이고 꾸미기만 테마 모양을 따른다(아래 주석). 제목은 설치 프리셋처럼
 * 운영자가 고칠 수 있는 처음 데이터다(화면 문구가 아니다). 가젯 ID 는 {@code seed-<n>}(테마 안에서 1부터) — 저장 요청에 이 ID 가 오면 그 시드의 설정 그대로 새 가젯을 만든다.
 * 게시판을 slug 로 가리키는 설정({@code boardIds})은 IROBIT 설치 프리셋의 게시판이다 — 없는 게시판은 공개 홈이 건너뛴다.
 */
public final class HomeLayoutDefaults {

    /** 시드 가젯 ID 앞머리 */
    public static final String SEED_PREFIX = "seed-";

    private HomeLayoutDefaults() {
    }

    /** 테마의 기본 배치와 가젯(모르는 테마는 기본 테마의 것) */
    public record Home(HomeGridLayout layout, List<GadgetSpec> gadgets) {

        public Optional<GadgetSpec> gadget(String id) {
            return gadgets.stream().filter(g -> g.id().equals(id)).findFirst();
        }
    }

    public static Home of(String theme) {
        return switch (SiteThemes.normalize(theme)) {
            case "daum" -> daum();
            case "naver" -> naver();
            case "irobit" -> irobit();
            default -> classic();
        };
    }

    /*
     * 네 테마의 기본 배치는 아이로빗 구성을 기준으로 같은 가젯·같은 차례·같은 칸 배분이다(사용자 결정 2026-10-29 「아이로빗 가젯 기준으로 해당 테마도 구성 맞추기」, 방금 올라온 글을 영상·짤 위로 2026-11-05):
     *   본문 넓은 열(하이라이트 · 인기글 두 열 · [모바일 전용 공구] · 방금 올라온 글 두 열 · 영상·짤) + 오른쪽 열(공구 — 데스크톱 전용 · 인기 소모임).
     *   24칸 테마는 17 + 7, 다음식은 12칸 8 + 4(같은 비율). 높이(최소)도 같다 — 영상·짤만 한 줄 4장(아이로빗 템플릿 `galleryColumns: 4`)이 아닌 테마는 3장 두 줄이라 더 높다.
     * 테마마다 다른 것은 꾸미기(머리 모양·제목 색·부제)와 하이라이트 모양이다 — 다음식은 막대 머리 + 제목 색, 네이버식은 막대 머리 + 하이라이트 사진 카드 한 줄, 기본형은 구역 이름 줄(본문은 카드 없음 · 오른쪽 열은 카드).
     * 네이버식만 가젯을 더한다 — 본문 게시판 카드 격자 · 오른쪽 많이 본 글(언론사 홈 모양, 아래 {@link #naver()}).
     */

    /** 기본형 — 24칸: 본문 17칸(하이라이트 · 인기글 · [모바일 전용 공구] · 방금 올라온 글 · 영상·짤) + 오른쪽 7칸(공구 · 인기 소모임) */
    private static Home classic() {
        Builder b = new Builder("default", 24);
        b.add("HIGHLIGHT", "하이라이트", true, true, style().put("headerStyle", "HIDDEN").set("empty", noKeep()), cfg().put("lead", "HERO").put("thumbs", "ALL").put("count", 4),
                null, null, 0, 0, 17, 12);
        b.add("POST_LIST", "인기글", true, true, null,
                cfg().put("sort", "POPULAR").put("rowStyle", "RANK").put("columns", 2).put("count", 6).put("periodTabs", true).set("meta", meta("BOARD", "COMMENTS", "TIME")),
                "인기글 더보기", "/popular", 0, 12, 17, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", false, true, style().put("cardLook", "DEFAULT").set("empty", noKeep()), null, null, null, 0, 20, 17, 10);
        b.add("GALLERY", "영상·짤", true, true, null, cfg().put("count", 6).set("boardIds", meta("video")), "영상·짤 더보기", "/boards/video", 0, 38, 17, 11);
        b.add("POST_LIST", "최신글", true, true, null,
                cfg().put("sort", "LATEST").put("rowStyle", "HEADLINE").put("thumbnail", "NONE").put("titleLines", 1).put("columns", 2).put("count", 8)
                        .set("meta", meta("BOARD", "COMMENTS", "TIME")),
                "최신글 더보기", "/latest", 0, 30, 17, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", true, false, style().put("cardLook", "DEFAULT").set("empty", noKeep()), null, null, null, 17, 0, 7, 12);
        b.add("GROUPS", "인기 소모임", true, true, style().put("cardLook", "DEFAULT"), cfg().put("count", 3), "소모임 더 보기", "/groups", 17, 12, 7, 9);
        return b.build();
    }

    /** 다음식 — 12칸: 본문 8칸(하이라이트 · 지금 뜨는 글 · [모바일 전용 공구] · 방금 올라온 글 · 영상·짤) + 오른쪽 4칸(공구 · 인기 소모임) */
    private static Home daum() {
        Builder b = new Builder("daum", 12);
        b.add("HIGHLIGHT", "하이라이트", true, true, style().put("subtitle", "운영자가 고른 글").set("empty", noKeep()), cfg().put("lead", "HERO").put("thumbs", "ALL").put("count", 4),
                null, null, 0, 0, 8, 12);
        b.add("POST_LIST", "지금 뜨는 글", true, true, null,
                cfg().put("sort", "POPULAR").put("rowStyle", "RANK").put("columns", 2).put("count", 6).put("periodTabs", true).set("meta", meta("BOARD", "COMMENTS", "TIME")),
                null, "/popular", 0, 12, 8, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", false, true, style().put("titleColor", "RED").set("empty", noKeep()), null, null, null, 0, 20, 8, 10);
        b.add("GALLERY", "영상·짤", true, true, style().put("titleColor", "ORANGE"), cfg().put("count", 6).set("boardIds", meta("video")), "영상·짤 게시판", "/boards/video",
                0, 38, 8, 11);
        b.add("POST_LIST", "최신글", true, true, style().put("titleColor", "TEAL").put("subtitle", "전체 게시판 최신순"),
                cfg().put("sort", "LATEST").put("rowStyle", "HEADLINE").put("thumbnail", "NONE").put("titleLines", 1).put("columns", 2).put("count", 8)
                        .put("newBadgeHours", 1).set("meta", meta("BOARD", "COMMENTS", "TIME")),
                "최신글 더보기", "/latest", 0, 30, 8, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", true, false, style().put("titleColor", "RED").set("empty", noKeep()), null, null, null, 8, 0, 4, 12);
        b.add("GROUPS", "인기 소모임", true, true, style().put("titleColor", "PURPLE"), cfg().put("count", 3), "소모임 전체", "/groups", 8, 12, 4, 9);
        return b.build();
    }

    /**
     * 네이버형 — 24칸: 본문 17칸(하이라이트 사진 카드 한 줄 · 인기글 · [모바일 전용 공구] · 게시판 카드 격자 · 방금 올라온 글 · 영상·짤) + 오른쪽 7칸(공구 · 많이 본 글 · 인기 소모임).
     * 아이로빗 구성에 언론사 홈 같은 게시판별 새 글 격자(게시판 카드 — 카드마다 게시판 이름 · 대표 글 · 새 글 4개)를 더해 본문을 채우고(사용자 요청 2026-10-29 「네이버식 본문 내용이 부족해」),
     * 본문이 길어진 만큼 오른쪽 열에 많이 본 글 순위를 더한다.
     */
    private static Home naver() {
        Builder b = new Builder("naver", 24);
        b.add("HIGHLIGHT", "하이라이트", true, true, style().set("empty", noKeep()), cfg().put("lead", "NONE").put("thumbs", "ALL").put("count", 4), null, null, 0, 0, 17, 8);
        b.add("POST_LIST", "인기글", true, true, null,
                cfg().put("sort", "POPULAR").put("rowStyle", "RANK").put("columns", 2).put("count", 6).put("periodTabs", true).set("meta", meta("BOARD", "COMMENTS", "TIME")),
                null, "/popular", 0, 8, 17, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", false, true, style().set("empty", noKeep()), null, null, null, 0, 16, 17, 10);
        b.add("BOARD_CARDS", "게시판별 새 글", true, true, style().put("headerStyle", "HIDDEN").put("cardLook", "FLAT"),
                cfg().put("columns", "AUTO").put("perBoard", 4).put("boardScope", GadgetConfigs.PICK).set("boardIds", meta("ai", "robot", "stock", "game", "sports", "free")),
                null, null, 0, 26, 17, 30);
        b.add("GALLERY", "영상·짤", true, true, null, cfg().put("count", 6).set("boardIds", meta("video")), "영상·짤 게시판", "/boards/video", 0, 64, 17, 11);
        b.add("POST_LIST", "최신글", true, true, null,
                cfg().put("sort", "LATEST").put("rowStyle", "HEADLINE").put("thumbnail", "NONE").put("titleLines", 1).put("columns", 2).put("count", 8)
                        .set("meta", meta("BOARD", "COMMENTS", "TIME")),
                null, "/latest", 0, 56, 17, 8);
        b.add("GROUP_BUY", "진행 중 공동구매", true, false, style().set("empty", noKeep()), null, null, null, 17, 0, 7, 12);
        b.add("POST_LIST", "많이 본 글", true, false, null, cfg().put("sort", "VIEWS").put("rowStyle", "RANK").put("count", 5).set("meta", meta("BOARD", "TIME")),
                null, null, 17, 12, 7, 9);
        b.add("GROUPS", "인기 소모임", true, true, null, cfg().put("count", 3), "소모임 전체", "/groups", 17, 21, 7, 9);
        return b.build();
    }

    /**
     * 아이로빗형 — 24칸: 본문 18칸 + 오른쪽 6칸(아이로빗 설치 프리셋 {@code irobit.structure.json} 의 홈과 같은 구성 — home-news-contract 「화면」·「홈 배치 데이터」, shared V211).
     * <pre>
     *   PC   [새소식(뉴스 게시판 · LINE · 두 열 · 첫 글 작은 사진)            ] [인기글     ]
     *        [핫 아이로 픽(PC 전용 · 좁은 칸 LIST)] [최신글(PC 전용 · COMPACT)] [광고       ]
     *        [핫 아이로 픽(모바일 전용)] [최신글(모바일 전용)] — PC 에서는 숨어 자리가 없다          [인기 소모임]
     *        [상단 띠 배너                                                    ]
     *        [팁과강좌] [사용기] → [영상·짤] → [띠 배너]                         [공동구매(PC 전용)]
     *   모바일(템플릿 한 열 순서 beside) 새소식 → 인기글 → 핫 아이로 픽(모바일 전용) → 최신글(모바일 전용) → 광고 → 인기 소모임 → 상단 띠 배너
     *        → 팁과강좌 → 사용기 → 영상·짤 → 띠 배너
     * </pre>
     * 자리는 겹치지 않고 위로 당겨 정리된 모양이다(관리자 저장이 겹침을 400 으로 막고 저장 때 위로 당긴다 — 저장해도 차례가 그대로). 모바일 전용 두 가젯을 PC 두 가젯 바로 아래 같은 행(y20)에
     * 나란히 두어, 한 열 순서(옆 열 가젯은 본문 열에서 「위 끝 ≤ 그 가젯 아래 끝」인 마지막 가젯 뒤)가 인기글 → 핫 아이로 픽 → 최신글 → 광고가 되게 했다(이미 설치된 곳은 V211 이 넣고 V212 가 이 자리로 바로잡는다).
     * 머리는 아이콘 + 제목({@code PLAIN}), 구분선은 끈다. 게시판 이름은 기본(자동 — 게시판 표시색이 있으면 그 색, 2026-11-05 사용자 결정으로 회색에서 바꿈).
     */
    private static Home irobit() {
        Builder b = new Builder("irobit", 24);
        b.add("POST_LIST", "새소식", true, true, plain("pi-megaphone"),
                cfg().put("boardScope", GadgetConfigs.PICK).<ObjectNode>set("boardIds", meta(BoardFields.NEWS_SLUG)).put("sort", "LATEST").put("rowStyle", "LINE")
                        .put("lead", GadgetConfigs.LEAD_THUMB).put("thumbnail", "NONE").put("titleLines", 1).put("columns", 2).put("count", 17).put("dividers", false)
                        .put("mobilePages", 3).put("mobilePageSize", 5).set("meta", meta("PREFIX", "COMMENTS", "TIME")),
                null, "/boards/" + BoardFields.NEWS_SLUG, 0, 0, 18, 12);
        b.add("POST_LIST", "인기글", true, true, tabler("pi-chart-line", GadgetTablerIcons.FLAME),
                cfg().put("sort", "POPULAR").put("rowStyle", "COMPACT").put("columns", 1).put("count", 20).put("dividers", false).put("periodTabs", true)
                        .set("meta", meta("COMMENTS")),
                null, "/popular", 18, 0, 6, 12);
        b.add("HIGHLIGHT", "핫 아이로 픽", true, false, plain("pi-sparkles").put("bottomSpace", "TIGHT"),
                cfg().put("lead", "HERO").put("thumbs", "ALL").put("count", 5).put("narrow", "LIST"), null, null, 0, 12, 9, 8);
        b.add("POST_LIST", "최신글", true, false, plain("pi-clock").put("bottomSpace", "TIGHT"),
                cfg().put("sort", "LATEST").put("rowStyle", "COMPACT").put("columns", 1).put("count", 12).put("dividers", false).put("likes", "LEAD")
                        .set("meta", meta("BOARD", "COMMENTS", "LIKES")),
                null, "/latest", 9, 12, 9, 8);
        b.add(GadgetKinds.AD, "상단 띠 배너", true, true, adIcon(), cfg().put("adType", "STRIP_BANNER"), null, null, 0, 29, 18, 3);
        b.add(GadgetKinds.AD, "광고", true, true, adIcon(), cfg().put("adType", "SQUARE_BANNER"), null, null, 18, 12, 6, 8);
        b.add("HIGHLIGHT", "핫 아이로 픽", false, true, plain("pi-sparkles"),
                cfg().put("lead", "HERO").put("thumbs", "ALL").put("count", 5).put("narrow", "DEFAULT"), null, null, 0, 20, 9, 8);
        b.add("POST_LIST", "최신글", false, true, plain("pi-clock"),
                cfg().put("sort", "LATEST").put("rowStyle", "HEADLINE").put("thumbnail", "NONE").put("titleLines", 1).put("columns", 2).put("count", 10)
                        .put("dividers", false).put("likes", "ICON").put("rowSpacing", "TIGHT").set("meta", meta("BOARD", "COMMENTS", "TIME", "LIKES")),
                null, "/latest", 9, 20, 9, 9);
        b.add("GROUPS", "인기 소모임", true, true, tabler("pi-users", GadgetTablerIcons.USERS_GROUP), cfg().put("count", 3), null, "/groups", 18, 20, 6, 7);
        b.add("BOARD_CARDS", "팁과강좌", true, true, plain("pi-lightbulb"), boardCard("tip"), null, "/boards/tip", 0, 32, 9, 8);
        b.add("BOARD_CARDS", "사용기", true, true, tabler("pi-th-large", GadgetTablerIcons.MESSAGE_STAR), boardCard("review"), null, "/boards/review", 9, 32, 9, 8);
        b.add("GALLERY", "영상·짤", true, true, tabler("pi-images", GadgetTablerIcons.PHOTO_VIDEO), cfg().put("count", 4).set("boardIds", meta("video")), null, "/boards/video",
                0, 40, 18, 8);
        b.add(GadgetKinds.AD, "띠 배너", true, true, adIcon(), cfg().put("adType", "STRIP_BANNER"), null, null, 0, 48, 18, 3);
        b.add("GROUP_BUY", "진행 중 공동구매", true, false, plain("pi-shopping-bag").set("empty", noKeep()), cfg().put("sort", "DEADLINE").put("count", 5), null, null,
                17, 51, 7, 13);
        return b.build();
    }

    /** 게시판 하나의 게시판 카드(카드 머리 없이 · 대표 글 사진 자리) — 아이로빗 팁과강좌·사용기 */
    private static ObjectNode boardCard(String slug) {
        return cfg().put("columns", "AUTO").put("perBoard", 4).put("cardHead", false).put("leadPlaceholder", true).put("boardScope", GadgetConfigs.PICK)
                .set("boardIds", meta(slug));
    }

    private static ObjectNode style() {
        return HomeLayoutJson.object();
    }

    private static ObjectNode plain(String icon) {
        return HomeLayoutJson.object().put("headerStyle", "PLAIN").put("cardLook", "DEFAULT").put("icon", icon);
    }

    /** 광고 가젯 꾸미기 — 종류 기본(머리 숨김 · 배너는 평평 · 비면 접기) 위에 아이콘만 */
    private static ObjectNode adIcon() {
        return HomeLayoutJson.object().put("icon", "pi-image");
    }

    /** 머리 = Tabler 아이콘 + 제목(2026-11-10 「가젯마다 어울리는 아이콘」) — PrimeIcons 이름은 출처를 PRIME 으로 되돌릴 때 쓴다 */
    private static ObjectNode tabler(String primeIcon, String tablerName) {
        return GadgetTablerIcons.apply(plain(primeIcon), tablerName);
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
        private final List<GadgetSpec> gadgets = new ArrayList<>();
        private final List<HomeLayoutItem> items = new ArrayList<>();

        Builder(String theme, int columns) {
            this.theme = theme;
            this.columns = columns;
        }

        void add(String type, String title, boolean desktop, boolean mobile, JsonNode styleOverride, ObjectNode config, String moreLabel, String moreTo,
                 int x, int y, int w, int h) {
            String id = SEED_PREFIX + (gadgets.size() + 1);
            ObjectNode style = GadgetKinds.kindStyle(GadgetKinds.baseStyle(theme), type); // 광고 가젯은 머리 숨김·평평·비면 접기(다른 종류는 그대로)
            if (styleOverride != null) {
                styleOverride.properties().forEach(e -> style.set(e.getKey(), e.getValue()));
            }
            ObjectNode more = null;
            if (moreTo != null) {
                more = HomeLayoutJson.object().put("label", moreLabel).put("to", moreTo);
            }
            gadgets.add(new GadgetSpec(id, type, title, desktop, mobile, style, config == null ? HomeLayoutJson.object() : config, more));
            items.add(new HomeLayoutItem(id, x, y, w, h));
        }

        Home build() {
            return new Home(new HomeGridLayout(columns, items), List.copyOf(gadgets));
        }
    }
}
