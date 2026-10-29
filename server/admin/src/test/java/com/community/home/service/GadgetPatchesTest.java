package com.community.home.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.zconfiguration.message.MessageRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 가젯 ⚙ 설정 칸 검사(gadget-page-contract 「가젯 설정」 2026-10-30) — 보낸 칸만 · 범위 · 자리별 대상 게시판 범위 · 바로가기 항목 · 안내 글 · 꾸미기. DB 없이 순수 계산만 본다.
 */
class GadgetPatchesTest {

    private static final Predicate<String> BOARDS = Set.of("free", "video")::contains;

    private static ObjectNode o() {
        return HomeLayoutJson.object();
    }

    private static ObjectNode config(String type, JsonNode raw, JsonNode current, GadgetConfigs.Place place, Map<String, MessageRef> fields) {
        return GadgetPatches.config(type, raw, current, place, BOARDS, "gadgets[0]", fields);
    }

    @Test
    void 보낸_칸만_돌려주고_표에_없는_칸은_버린다() {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        ObjectNode patch = config("POST_LIST", o().put("rowStyle", "RANK").put("badge", "x").put("newBadgeHours", 72), o(), GadgetConfigs.Place.HOME, fields);

        assertThat(fields).isEmpty();
        assertThat(patch.propertyNames()).containsExactlyInAnyOrder("rowStyle", "newBadgeHours");
        assertThat(config("GROUP_BUY", null, o(), GadgetConfigs.Place.HOME, fields)).isNull();
    }

    @Test
    void 대상_게시판_범위는_자리마다_다르다() {
        for (GadgetConfigs.Place place : GadgetConfigs.Place.values()) {
            Map<String, MessageRef> fields = new LinkedHashMap<>();
            config("GALLERY", o().put("boardScope", "BOARD"), o(), place, fields);
            assertThat(fields.containsKey("gadgets[0].config.boardScope")).isEqualTo(place != GadgetConfigs.Place.BOARD);
            fields.clear();
            config("GALLERY", o().put("boardScope", "FOLDER"), o(), place, fields);
            assertThat(fields.containsKey("gadgets[0].config.boardScope")).isEqualTo(place != GadgetConfigs.Place.PAGE);
        }
    }

    @Test
    void 직접_고르기는_덮은_결과에_게시판이_있어야_한다() {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        ObjectNode stored = o().put("boardScope", "PICK");
        stored.putArray("boardIds").add("free");

        // 범위만 보내도 저장된 게시판이 있으면 통과
        assertThat(config("GALLERY", o().put("boardScope", "PICK"), stored, GadgetConfigs.Place.HOME, fields)).isNotNull();
        assertThat(fields).isEmpty();

        ObjectNode empty = o().put("boardScope", "PICK");
        empty.putArray("boardIds");
        config("GALLERY", empty, o(), GadgetConfigs.Place.HOME, fields);
        assertThat(fields).containsOnlyKeys("gadgets[0].config.boardIds");
    }

    @Test
    void 기간_탭은_덮은_정렬이_인기일_때만() {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        config("POST_LIST", o().put("periodTabs", true), o().put("sort", "POPULAR"), GadgetConfigs.Place.HOME, fields);
        assertThat(fields).isEmpty();
        config("POST_LIST", o().put("periodTabs", true).put("sort", "LATEST"), o().put("sort", "POPULAR"), GadgetConfigs.Place.HOME, fields);
        assertThat(fields).containsOnlyKeys("gadgets[0].config.periodTabs");
    }

    @Test
    void 바로가기_항목은_이름_종류_값을_항목_경로로_본다() {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        ObjectNode raw = o().put("shape", "TAB");
        ArrayNode items = raw.putArray("items");
        items.addObject().put("label", " 자유 ").put("kind", "BOARD").put("value", "free");
        items.addObject().put("label", "인기").put("kind", "POPULAR").put("value", "ignored");
        items.addObject().put("label", "가이드").put("kind", "PATH").put("value", "/p/guide");
        ObjectNode ok = config("QUICK_LINKS", raw, o(), GadgetConfigs.Place.HOME, fields);

        assertThat(fields).isEmpty();
        assertThat(ok.path("items").get(0).path("label").asString()).isEqualTo("자유");
        assertThat(ok.path("items").get(1).path("value").isNull()).isTrue();

        ObjectNode bad = o();
        ArrayNode badItems = bad.putArray("items");
        badItems.addObject().put("label", "").put("kind", "BOARD").put("value", "nope");
        badItems.addObject().put("label", "밖").put("kind", "PATH").put("value", "//evil.example");
        badItems.addObject().put("label", "모름").put("kind", "HOME");
        config("QUICK_LINKS", bad, o(), GadgetConfigs.Place.HOME, fields);
        assertThat(fields).containsOnlyKeys("gadgets[0].config.items[0].label", "gadgets[0].config.items[0].value", "gadgets[0].config.items[1].value",
                "gadgets[0].config.items[2].kind");

        fields.clear();
        ObjectNode many = o();
        ArrayNode thirteen = many.putArray("items");
        for (int i = 0; i < 13; i++) {
            thirteen.addObject().put("label", "a" + i).put("kind", "ALL");
        }
        config("QUICK_LINKS", many, o(), GadgetConfigs.Place.HOME, fields);
        assertThat(fields).containsOnlyKeys("gadgets[0].config.items");
    }

    @Test
    void 안내_글은_300자까지_줄바꿈_허용() {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        ObjectNode ok = config("NOTICE_TEXT", o().put("text", "첫 줄\n둘째 줄").put("format", "BULLETS"), o(), GadgetConfigs.Place.HOME, fields);
        assertThat(fields).isEmpty();
        assertThat(ok.path("text").asString()).isEqualTo("첫 줄\n둘째 줄");

        config("NOTICE_TEXT", o().put("text", "가".repeat(301)).put("format", "HTML"), o(), GadgetConfigs.Place.HOME, fields);
        assertThat(fields).containsOnlyKeys("gadgets[0].config.text", "gadgets[0].config.format");
    }

    @Test
    void 게시판_카드_열_수는_AUTO_2_3() {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        assertThat(config("BOARD_CARDS", o().put("columns", "AUTO"), o(), GadgetConfigs.Place.HOME, fields).path("columns").asString()).isEqualTo("AUTO");
        assertThat(config("BOARD_CARDS", o().put("columns", 3), o(), GadgetConfigs.Place.HOME, fields).path("columns").asInt()).isEqualTo(3);
        config("BOARD_CARDS", o().put("columns", 4), o(), GadgetConfigs.Place.HOME, fields);
        assertThat(fields).containsOnlyKeys("gadgets[0].config.columns");
    }

    @Test
    void 꾸미기는_보낸_칸만_검사하고_비었을_때는_안쪽_칸을_합친다() {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        ObjectNode current = HomeGadgetKinds.baseStyle("naver");
        ObjectNode raw = o().put("cardColor", "TEAL");
        raw.putNull("icon");
        raw.putObject("empty").put("message", false);
        ObjectNode patch = GadgetPatches.style(raw, current, "gadgets[0]", fields);

        assertThat(fields).isEmpty();
        assertThat(patch.propertyNames()).containsExactlyInAnyOrder("cardColor", "icon", "empty");
        assertThat(patch.path("empty").path("keep").asBoolean()).isTrue();
        assertThat(patch.path("empty").path("message").asBoolean()).isFalse();

        GadgetPatches.style(o().put("mobileFold", "HALF").put("cardColor", "#12345"), current, "gadgets[0]", fields);
        assertThat(fields).containsOnlyKeys("gadgets[0].style.mobileFold", "gadgets[0].style.cardColor");
    }

    @Test
    void 관리자_보기는_8종_모두_표_칸을_처음_값으로_채운다() {
        assertThat(GadgetConfigs.view("NOTICE_TEXT", null).propertyNames()).containsExactlyInAnyOrder("text", "format");
        assertThat(GadgetConfigs.view("QUICK_LINKS", null).propertyNames()).containsExactlyInAnyOrder("shape", "items");
        assertThat(GadgetConfigs.view("HIGHLIGHT", o().put("count", 12)).path("count").asInt()).isEqualTo(6);
        assertThat(GadgetConfigs.view("POST_LIST", null).path("meta")).hasSize(4);
        assertThat(GadgetConfigs.view(HomeGadgetKinds.BOARD_BODY, null)).isNull();
        assertThat(GadgetStyles.view("daum", "GROUPS", o().put("titleColor", "nope")).path("titleColor").isNull()).isTrue();
    }
}
