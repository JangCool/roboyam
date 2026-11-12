package com.community.home.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.zconfiguration.message.MessageRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 핫딜 가젯 {@code HOT_DEALS} 설정 검사(hotdeal-contract 「가젯 HOT_DEALS 설정」 — 공동구매 가젯 ⚙ 방식) — 종류 목록·최소 크기 · {@code count} 1~10(5) · {@code sort} 셋 ·
 * {@code boards}(있는 핫딜 게시판 slug 0~20, 비면 모두) · 편집기가 보는 설정. DB 없이 순수 계산만 본다.
 */
class GadgetPatchesHotDealsTest {

    private static final Predicate<String> BOARDS = Set.of("free", "hotdeal", "deal2")::contains;
    private static final Predicate<String> DEALS = Set.of("hotdeal", "deal2")::contains;

    private static ObjectNode o() {
        return HomeLayoutJson.object();
    }

    private static ObjectNode config(ObjectNode raw, Map<String, MessageRef> fields) {
        return GadgetPatches.config(GadgetKinds.HOT_DEALS, raw, o(), GadgetConfigs.Place.HOME, BOARDS, DEALS, "gadgets[0]", fields);
    }

    @Test
    void 종류로_등록되고_최소_크기는_공구_가젯과_같다() {
        assertThat(GadgetKinds.isKnown(GadgetKinds.HOT_DEALS)).isTrue();
        assertThat(GadgetKinds.types()).contains(GadgetKinds.HOT_DEALS);
        assertThat(GadgetKinds.minW(GadgetKinds.HOT_DEALS, 12)).isEqualTo(3);
        assertThat(GadgetKinds.minW(GadgetKinds.HOT_DEALS, 24)).isEqualTo(6);
        assertThat(GadgetKinds.minH(GadgetKinds.HOT_DEALS)).isEqualTo(8);
        assertThat(GadgetConfigs.settable(GadgetKinds.HOT_DEALS)).isTrue();
        assertThat(GadgetKinds.baseStyle("irobit", GadgetKinds.HOT_DEALS).get("icon").asString()).isEqualTo("pi-ticket");
    }

    @Test
    void 개수와_정렬과_핫딜_게시판() {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        ObjectNode raw = o().put("count", 7).put("sort", "ENDING_SOON");
        raw.putArray("boards").add("hotdeal").add("deal2").add("hotdeal");
        ObjectNode patch = config(raw, fields);
        assertThat(fields).isEmpty();
        assertThat(patch.get("count").asInt()).isEqualTo(7);
        assertThat(patch.get("sort").asString()).isEqualTo("ENDING_SOON");
        assertThat(patch.get("boards")).hasSize(2);

        ObjectNode empty = o();
        empty.putArray("boards");
        assertThat(config(empty, fields).get("boards")).isEmpty(); // 비면 모든 핫딜 게시판
        assertThat(fields).isEmpty();
    }

    @Test
    void 틀린_칸은_경로로_모은다() {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        config(o().put("count", 0), fields);
        assertThat(fields).containsKey("gadgets[0].config.count");
        fields.clear();
        config(o().put("count", 11), fields);
        assertThat(fields).containsKey("gadgets[0].config.count");
        fields.clear();
        config(o().put("sort", "DEADLINE"), fields);
        assertThat(fields.get("gadgets[0].config.sort").key()).isEqualTo("homeLayout.gadget.config.hotDealSort");
        fields.clear();
        ObjectNode general = o();
        general.putArray("boards").add("free"); // 핫딜 게시판이 아니다
        config(general, fields);
        assertThat(fields.get("gadgets[0].config.boards").key()).isEqualTo("homeLayout.gadget.config.hotDealBoards");
        fields.clear();
        config(o().put("boards", "hotdeal"), fields);
        assertThat(fields).containsKey("gadgets[0].config.boards");
        fields.clear();
        ObjectNode many = o();
        ArrayNode arr = many.putArray("boards");
        for (int i = 0; i < 21; i++) {
            arr.add("hotdeal");
        }
        assertThat(config(many, fields).get("boards")).hasSize(1); // 겹치면 하나
    }

    @Test
    void 편집기가_보는_설정은_처음_값으로_채운다() {
        ObjectNode view = GadgetConfigs.view(GadgetKinds.HOT_DEALS, null);
        assertThat(view.get("count").asInt()).isEqualTo(5);
        assertThat(view.get("sort").asString()).isEqualTo("LATEST");
        assertThat(view.get("boards")).isEmpty();
        ObjectNode stored = o().put("count", 99).put("sort", "NOPE");
        stored.putArray("boards").add("hotdeal").add(" ").add("hotdeal");
        ObjectNode read = GadgetConfigs.view(GadgetKinds.HOT_DEALS, stored);
        assertThat(read.get("sort").asString()).isEqualTo("LATEST");
        assertThat(read.get("boards")).hasSize(1);
        assertThat(GadgetKinds.hotDealsCount(stored)).isBetween(1, 10);
    }
}
