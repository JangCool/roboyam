package com.community.home.service;

import com.community.home.dto.HomeGadgetSpec;
import com.community.home.dto.HomeGridLayout;
import com.community.home.dto.HomeLayoutItem;
import com.community.site.service.SiteThemes;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 가젯 페이지의 처음 배치 — 만들 때 기준 폴더의 게시판으로 한 번 만들어 {@code gadget_pages.initial_layout} 에 둔다(gadget-page-contract 「필드·검증」, admin-web {@code UtilGadgetPage.initialLayout} 과 같은 규칙).
 * 24칸, 줄마다 꽉 차게(사용자 결정 2026-10-29): 「{폴더} 인기글」(인기·기간 탭·대상 폴더, 24칸) / 「{게시판}」 갤러리(사진·영상 게시판 — 목록 모양 GALLERY 또는
 * 갤러리 보기 전환이 켜진 첫 폴더 게시판, 24칸 — 없으면 이 줄이 없다) / 나머지 게시판의 게시판 카드(게시판마다, 12+12 두 열 — 홀수면 마지막 24칸) / 「{폴더} 최신글」(대상 폴더, 24칸).
 * 나란한 가젯은 같은 높이(h — 최소 높이)라 아래 끝이 맞고, 높이는 보통 내용에 맞췄다(내용이 길면 격자가 늘린다).
 * 기준 폴더가 없으면 이름 자리는 「전체」. 가젯 ID 는 {@code seed-<n>} — 저장 요청에 이 ID 가 오면 그 설정 그대로 만든다.
 * 꾸미기는 저장하지 않고 읽을 때 그 테마의 기본 꾸미기({@link HomeGadgetKinds#baseStyle}) + 아이콘을 입힌다(로보얌은 홈 로보얌 시드처럼 카드 + 아이콘 머리). 제목은 운영자가 고칠 처음 데이터(한국어 시드)다.
 */
public final class GadgetPageInitial {

    private static final String ALL_NAME = "전체";
    /** 줄마다 높이(행) — 인기글 · 갤러리 · 게시판 카드 줄 · 최신글 */
    static final int TOP_H = 15;
    static final int GALLERY_H = 11;
    static final int CARDS_H = 13;
    static final int LATEST_H = 15;

    private GadgetPageInitial() {
    }

    /**
     * 처음 배치 JSON — {@code {columns, items[{gadgetId,x,y,w,h}], gadgets[{id,type,title,config,more,icon}]}}
     *
     * @param folderName 기준 폴더 이름(없으면 null)
     * @param boards     기준 폴더의 켜진 게시판(메뉴 차례)
     */
    public static ObjectNode build(String folderName, List<GadgetPageFolders.Board> boards) {
        String folder = folderName == null || folderName.isBlank() ? ALL_NAME : folderName;
        Builder b = new Builder();
        GadgetPageFolders.Board gallery = boards.stream().filter(x -> "GALLERY".equals(x.layout())).findFirst()
                .orElseGet(() -> boards.stream().filter(GadgetPageFolders.Board::gallery).findFirst().orElse(null));
        int y = 0;
        b.add("POST_LIST", folder + " 인기글", "pi-chart-line",
                scope(HomeLayoutJson.object().put("sort", "POPULAR").put("rowStyle", "RANK").put("periodTabs", true).put("count", 10), GadgetConfigs.FOLDER, List.of())
                        .set("meta", strings("BOARD", "COMMENTS", "TIME")),
                null, 0, y, 24, TOP_H);
        y += TOP_H;
        if (gallery != null) {
            b.add("GALLERY", gallery.name(), "pi-images", scope(HomeLayoutJson.object().put("count", 8), GadgetConfigs.PICK, List.of(gallery.slug())),
                    "/boards/" + gallery.slug(), 0, y, 24, GALLERY_H);
            y += GALLERY_H;
        }
        List<GadgetPageFolders.Board> cards = boards.stream().filter(x -> x != gallery).toList();
        for (int i = 0; i < cards.size(); i++) {
            GadgetPageFolders.Board board = cards.get(i);
            boolean left = i % 2 == 0;
            boolean last = i == cards.size() - 1;
            b.add("BOARD_CARDS", board.name(), "pi-th-large",
                    scope(HomeLayoutJson.object().put("columns", "AUTO").put("perBoard", 4), GadgetConfigs.PICK, List.of(board.slug())),
                    "/boards/" + board.slug(), left ? 0 : 12, y, left && last ? 24 : 12, CARDS_H);
            if (!left || last) {
                y += CARDS_H;
            }
        }
        b.add("POST_LIST", folder + " 최신글", "pi-clock",
                scope(HomeLayoutJson.object().put("sort", "LATEST").put("rowStyle", "HEADLINE").put("columns", 2).put("count", 16), GadgetConfigs.FOLDER, List.of())
                        .set("meta", strings("BOARD", "COMMENTS", "TIME")),
                null, 0, y, 24, LATEST_H);
        return b.json;
    }

    /** 처음 배치를 그 테마의 시드로 읽는다(꾸미기 = 테마 기본 꾸미기 + 아이콘). 모양이 아니면 빈 24칸 */
    public static HomeLayoutDefaults.Home read(String json, String theme) {
        JsonNode root = HomeLayoutJson.read(json);
        int columns = root != null && root.path("columns").asInt() == 12 ? 12 : 24;
        List<HomeLayoutItem> items = root == null ? List.of() : HomeLayoutJson.readItems(HomeLayoutJson.write(root.get("items")));
        List<HomeGadgetSpec> gadgets = new ArrayList<>();
        JsonNode raw = root == null ? null : root.get("gadgets");
        if (raw != null && raw.isArray()) {
            for (JsonNode g : raw) {
                ObjectNode style = HomeGadgetKinds.baseStyle(theme);
                if ("roboyam".equals(SiteThemes.normalize(theme))) {
                    // 홈 로보얌 시드와 같은 모양 — 흰 카드 + 아이콘 머리
                    style.put("headerStyle", "PLAIN").put("cardLook", "DEFAULT");
                }
                String icon = HomeLayoutJson.text(g, "icon", null);
                if (icon != null) {
                    style.put("icon", icon);
                }
                JsonNode config = g.get("config");
                JsonNode more = g.get("more");
                gadgets.add(new HomeGadgetSpec(g.path("id").asString(), g.path("type").asString(), g.path("title").asString(), true, true, style,
                        config != null && config.isObject() ? config.deepCopy() : HomeLayoutJson.object(), more != null && more.isObject() ? more.deepCopy() : null));
            }
        }
        return new HomeLayoutDefaults.Home(new HomeGridLayout(columns, items), List.copyOf(gadgets));
    }

    private static ObjectNode scope(ObjectNode config, String scope, List<String> slugs) {
        config.put("boardScope", scope);
        ArrayNode ids = config.putArray("boardIds");
        slugs.forEach(ids::add);
        return config;
    }

    private static ArrayNode strings(String... values) {
        ArrayNode out = HomeLayoutJson.array();
        for (String v : values) {
            out.add(v);
        }
        return out;
    }

    private static final class Builder {

        private final ObjectNode json = HomeLayoutJson.object().put("columns", 24);
        private final ArrayNode items = json.putArray("items");
        private final ArrayNode gadgets = json.putArray("gadgets");

        void add(String type, String title, String icon, JsonNode config, String moreTo, int x, int y, int w, int h) {
            String id = HomeLayoutDefaults.SEED_PREFIX + (gadgets.size() + 1);
            String safeTitle = title.codePointCount(0, title.length()) > HomeGadgetKinds.TITLE_MAX ? title.substring(0, title.offsetByCodePoints(0, HomeGadgetKinds.TITLE_MAX)) : title;
            ObjectNode g = gadgets.addObject().put("id", id).put("type", type).put("title", safeTitle).put("icon", icon);
            g.set("config", config);
            if (moreTo != null) {
                ObjectNode more = g.putObject("more");
                more.putNull("label");
                more.put("to", moreTo);
            }
            items.addObject().put("gadgetId", id).put("x", x).put("y", y).put("w", w).put("h", h);
        }
    }
}
