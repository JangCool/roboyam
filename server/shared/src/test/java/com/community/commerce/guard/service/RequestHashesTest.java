package com.community.commerce.guard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.guard.dto.RequestAction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 멱등 키의 요청 해시 — 같은 본문이면 같고(칸 차례 무관), 본문·요청 종류가 다르면 다르다 */
class RequestHashesTest {

    record Purchase(long itemId, int quantity, List<String> options) {
    }

    @Test
    void 칸_차례만_다른_같은_본문은_같은_해시다() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("itemId", 3);
        a.put("quantity", 2);
        a.put("nested", Map.of("b", 1, "a", 2));
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("nested", new LinkedHashMap<>(Map.of("a", 2, "b", 1)));
        b.put("quantity", 2);
        b.put("itemId", 3);
        assertThat(RequestHashes.of(RequestAction.SHOP_PURCHASE, a)).isEqualTo(RequestHashes.of(RequestAction.SHOP_PURCHASE, b)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void DTO_와_같은_모양의_맵은_같은_해시다() {
        Purchase dto = new Purchase(3, 2, List.of("red"));
        Map<String, Object> map = Map.of("quantity", 2, "options", List.of("red"), "itemId", 3);
        assertThat(RequestHashes.of(RequestAction.SHOP_PURCHASE, dto)).isEqualTo(RequestHashes.of(RequestAction.SHOP_PURCHASE, map));
    }

    @Test
    void 본문이나_요청_종류가_다르면_다르다() {
        String base = RequestHashes.of(RequestAction.SHOP_PURCHASE, Map.of("itemId", 3));
        assertThat(RequestHashes.of(RequestAction.SHOP_PURCHASE, Map.of("itemId", 4))).isNotEqualTo(base);
        assertThat(RequestHashes.of(RequestAction.REFUND_REQUEST, Map.of("itemId", 3))).isNotEqualTo(base);
        assertThat(RequestHashes.of(RequestAction.SHOP_PURCHASE, List.of(1, 2))).isNotEqualTo(RequestHashes.of(RequestAction.SHOP_PURCHASE, List.of(2, 1)));
        assertThat(RequestHashes.of(RequestAction.SHOP_PURCHASE, null)).isEqualTo(RequestHashes.of(RequestAction.SHOP_PURCHASE, null));
    }
}
