package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 광고 노출 자리 설정 변경 이력(화면·서버 공용 계약 8절, 사용자 지시 2026-11-01 「변경 이력 관리해.」) — {@code GET /api/admin/ads/placements/history}({@code ad.read}, no-store).
 * 저장 때 바뀐 칸만 한 줄(before/after 글자 · {@code labelKey settingHistory.field.<field>}), 같은 값 다시 저장은 줄 없음, 처음 저장은 {@code CREATE}(아홉 칸 모두 before null —
 * 메일 서버 설정 이력과 같다), 최신 먼저(시각, 같으면 id 큰 쪽)·쪽 넘김·쪽 규칙(메일과 같음), 운영자 이름(지워졌으면 대체 문구 · {@code operatorDeleted}).
 */
class AdPlacementHistoryHttpTest extends Commerce04AdminTestBase {

    private static final String HISTORY = "/api/admin/ads/placements/history";
    /** 기본값과 같은 값 */
    private static final String DEFAULTS = "{\"postInline\":{\"enabled\":true,\"adType\":\"TEXT_LINE\",\"position\":\"BETWEEN_CARDS\"},"
            + "\"wings\":{\"left\":true,\"right\":true,\"screens\":[\"HOME\",\"PAGE\",\"BOARD\",\"POST\"]},\"boardList\":{\"enabled\":true,\"after\":5},"
            + "\"adult\":{\"enabled\":false}}";
    private static final String VALID = "{\"postInline\":{\"enabled\":false,\"adType\":\"SQUARE_BANNER\",\"position\":\"AFTER_BODY\"},"
            + "\"wings\":{\"left\":true,\"right\":false,\"screens\":[\"POST\",\"HOME\"]},\"boardList\":{\"enabled\":true,\"after\":12},"
            + "\"adult\":{\"enabled\":true}}";

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM ad_placement_settings");
        jdbc.update("DELETE FROM ad_placement_history");
    }

    private ResultActions save(Cookie session, String body) throws Exception {
        return mvc.perform(withCsrf(put("/api/admin/ads/placements"), session).content(body)).andExpect(status().isOk());
    }

    private long rows() {
        return jdbc.queryForObject("SELECT count(*) FROM ad_placement_history", Long.class);
    }

    @Test
    void 권한_ad_read_없으면_403_있으면_200_no_store() throws Exception {
        Cookie none = login(operatorWith());
        Cookie settingsOnly = login(operatorWith(Permission.AD_SETTINGS));
        Cookie reader = login(operatorWith(Permission.AD_READ));
        mvc.perform(get(HISTORY).cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get(HISTORY).cookie(settingsOnly)).andExpect(status().isForbidden());
        mvc.perform(get(HISTORY).cookie(reader)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items.length()").value(0)).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void 처음_저장은_CREATE_아홉_칸_모두_before_없음_운영자_이름() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_SETTINGS);
        Cookie session = login(me);
        save(session, VALID);
        assertThat(rows()).isOne();
        mvc.perform(get(HISTORY).cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").isString())
                .andExpect(jsonPath("$.data.items[0].action").value("CREATE")).andExpect(jsonPath("$.data.items[0].source").value("ADMIN"))
                .andExpect(jsonPath("$.data.items[0].operatorId").value(Long.toString(me)))
                .andExpect(jsonPath("$.data.items[0].operatorName").value(operatorName(me)))
                .andExpect(jsonPath("$.data.items[0].operatorDeleted").value(false))
                .andExpect(jsonPath("$.data.items[0].changedAt").isString())
                .andExpect(jsonPath("$.data.items[0].changes.length()").value(10))
                .andExpect(jsonPath("$.data.items[0].changes[0].field").value("postInline.enabled"))
                .andExpect(jsonPath("$.data.items[0].changes[0].labelKey").value("settingHistory.field.postInline.enabled"))
                .andExpect(jsonPath("$.data.items[0].changes[0].before").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].changes[0].after").value("false"))
                .andExpect(jsonPath("$.data.items[0].changes[1].field").value("postInline.adType"))
                .andExpect(jsonPath("$.data.items[0].changes[1].after").value("SQUARE_BANNER"))
                .andExpect(jsonPath("$.data.items[0].changes[2].field").value("postInline.position"))
                .andExpect(jsonPath("$.data.items[0].changes[2].after").value("AFTER_BODY"))
                .andExpect(jsonPath("$.data.items[0].changes[3].field").value("wings.left")).andExpect(jsonPath("$.data.items[0].changes[3].after").value("true"))
                .andExpect(jsonPath("$.data.items[0].changes[4].field").value("wings.right")).andExpect(jsonPath("$.data.items[0].changes[4].after").value("false"))
                .andExpect(jsonPath("$.data.items[0].changes[5].field").value("wings.screens"))
                .andExpect(jsonPath("$.data.items[0].changes[5].after").value("HOME,POST")) // 보일 차례로 이음
                .andExpect(jsonPath("$.data.items[0].changes[6].field").value("boardList.enabled"))
                .andExpect(jsonPath("$.data.items[0].changes[7].field").value("boardList.after")).andExpect(jsonPath("$.data.items[0].changes[7].after").value("12"))
                .andExpect(jsonPath("$.data.items[0].changes[8].field").value("adult.enabled"))
                .andExpect(jsonPath("$.data.items[0].changes[8].labelKey").value("settingHistory.field.adult.enabled"))
                .andExpect(jsonPath("$.data.items[0].changes[8].after").value("true"))
                // V127 — 열째 칸 중도 해지 수수료율(환불 묶음을 안 보내면 기본 10 그대로)
                .andExpect(jsonPath("$.data.items[0].changes[9].field").value("refund.stopFeeRate"))
                .andExpect(jsonPath("$.data.items[0].changes[9].after").value("10"));
        // 이력 시각 = 설정 저장 시각, 운영자 ID 가 남는다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_placement_history h JOIN ad_placement_settings s ON s.updated_at = h.changed_at "
                + "WHERE h.operator_id = ?", Long.class, me)).isOne();
    }

    @Test
    void 처음_저장은_기본값과_같은_값이어도_CREATE_한_줄() throws Exception {
        Cookie session = login(operatorWith(Permission.AD_READ, Permission.AD_SETTINGS));
        save(session, DEFAULTS);
        mvc.perform(get(HISTORY).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].action").value("CREATE")).andExpect(jsonPath("$.data.items[0].changes.length()").value(10))
                .andExpect(jsonPath("$.data.items[0].changes[5].after").value("HOME,PAGE,BOARD,POST"));
        // 그다음 같은 값 — 줄 없음
        save(session, DEFAULTS);
        assertThat(rows()).isOne();
    }

    @Test
    void 바꾼_칸만_UPDATE_로_남고_같은_값_다시_저장은_줄이_없다() throws Exception {
        Cookie session = login(operatorWith(Permission.AD_READ, Permission.AD_SETTINGS));
        save(session, VALID);
        save(session, VALID); // 같은 값
        assertThat(rows()).isOne();

        // 유형 하나 · 화면 비움
        save(session, VALID.replace("SQUARE_BANNER", "TEXT_LINE").replace("[\"POST\",\"HOME\"]", "[]"));
        assertThat(rows()).isEqualTo(2);
        mvc.perform(get(HISTORY).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].action").value("UPDATE")).andExpect(jsonPath("$.data.items[0].changes.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].changes[0].field").value("postInline.adType"))
                .andExpect(jsonPath("$.data.items[0].changes[0].labelKey").value("settingHistory.field.postInline.adType"))
                .andExpect(jsonPath("$.data.items[0].changes[0].before").value("SQUARE_BANNER"))
                .andExpect(jsonPath("$.data.items[0].changes[0].after").value("TEXT_LINE"))
                .andExpect(jsonPath("$.data.items[0].changes[1].field").value("wings.screens"))
                .andExpect(jsonPath("$.data.items[0].changes[1].before").value("HOME,POST"))
                .andExpect(jsonPath("$.data.items[0].changes[1].after").value("")) // 빈 목록은 빈 글자
                .andExpect(jsonPath("$.data.items[1].action").value("CREATE"));

        // 빈 화면 → 두 화면(보낸 차례와 상관없이 보일 차례), 몇 번째 뒤 · 19금 끔
        save(session, VALID.replace("SQUARE_BANNER", "TEXT_LINE").replace("[\"POST\",\"HOME\"]", "[\"BOARD\",\"HOME\"]").replace("\"after\":12", "\"after\":3")
                .replace("\"adult\":{\"enabled\":true}", "\"adult\":{\"enabled\":false}"));
        mvc.perform(get(HISTORY).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].changes.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].changes[0].field").value("wings.screens"))
                .andExpect(jsonPath("$.data.items[0].changes[0].before").value(""))
                .andExpect(jsonPath("$.data.items[0].changes[0].after").value("HOME,BOARD"))
                .andExpect(jsonPath("$.data.items[0].changes[1].field").value("boardList.after"))
                .andExpect(jsonPath("$.data.items[0].changes[1].before").value("12")).andExpect(jsonPath("$.data.items[0].changes[1].after").value("3"))
                .andExpect(jsonPath("$.data.items[0].changes[2].field").value("adult.enabled"))
                .andExpect(jsonPath("$.data.items[0].changes[2].before").value("true")).andExpect(jsonPath("$.data.items[0].changes[2].after").value("false"));

        // 틀린 저장(400)은 이력도 남기지 않는다
        mvc.perform(withCsrf(put("/api/admin/ads/placements"), session).content(VALID.replace("\"after\":12", "\"after\":0"))).andExpect(status().isBadRequest());
        assertThat(rows()).isEqualTo(3);
    }

    @Test
    void 최신_먼저_쪽_넘김_쪽_규칙() throws Exception {
        Cookie session = login(operatorWith(Permission.AD_READ, Permission.AD_SETTINGS));
        save(session, VALID.replace("\"after\":12", "\"after\":1")); // CREATE
        for (int after = 2; after <= 5; after++) {
            save(session, VALID.replace("\"after\":12", "\"after\":" + after));
        }
        assertThat(rows()).isEqualTo(5);
        mvc.perform(get(HISTORY).param("page", "0").param("size", "2").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5)).andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].changes[0].after").value("5")).andExpect(jsonPath("$.data.items[1].changes[0].after").value("4"));
        mvc.perform(get(HISTORY).param("page", "1").param("size", "2").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].changes[0].after").value("3")).andExpect(jsonPath("$.data.items[1].changes[0].after").value("2"));
        mvc.perform(get(HISTORY).param("page", "2").param("size", "2").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].action").value("CREATE"));
        mvc.perform(get(HISTORY).param("page", "3").param("size", "2").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0)).andExpect(jsonPath("$.data.total").value(5));

        // 쪽 규칙 — 메일 서버 설정 이력과 같다(빈 값은 기본, 정수 아님·범위 밖은 400 fields.page · fields.size)
        mvc.perform(get(HISTORY).param("page", "").param("size", "").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(20));
        mvc.perform(get(HISTORY).param("size", "100").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(100));
        mvc.perform(get(HISTORY).param("page", "-1").param("size", "101").cookie(session)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.page").exists()).andExpect(jsonPath("$.error.fields.size").exists());
        mvc.perform(get(HISTORY).param("page", "x").cookie(session)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.page").exists());
        mvc.perform(get(HISTORY).param("size", "0").cookie(session)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.size").exists());

        // 시각이 같으면 id 큰 쪽이 먼저
        Instant future = Instant.parse("2099-01-01T00:00:00Z");
        long small = ids.nextId();
        long big = ids.nextId();
        String changes = "[{\"field\":\"boardList.after\",\"before\":\"5\",\"after\":\"6\"}]";
        jdbc.update("INSERT INTO ad_placement_history (id, action, source, operator_id, changed_at, changes) VALUES (?, 'UPDATE', 'ADMIN', NULL, ?, ?::jsonb)",
                small, ts(future), changes);
        jdbc.update("INSERT INTO ad_placement_history (id, action, source, operator_id, changed_at, changes) VALUES (?, 'UPDATE', 'ADMIN', NULL, ?, ?::jsonb)",
                big, ts(future), changes);
        mvc.perform(get(HISTORY).param("size", "2").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(Long.toString(big))).andExpect(jsonPath("$.data.items[1].id").value(Long.toString(small)));
    }

    @Test
    void 지워진_운영자는_대체_문구와_operatorDeleted() throws Exception {
        Cookie reader = login(operatorWith(Permission.AD_READ));
        long gone = ids.nextId(); // operators 에 없는 ID — FK 가 없어 이력은 남는다
        jdbc.update("INSERT INTO ad_placement_history (id, action, source, operator_id, changed_at, changes) VALUES (?, 'UPDATE', 'ADMIN', ?, now(), ?::jsonb)",
                ids.nextId(), gone, "[{\"field\":\"wings.left\",\"before\":\"true\",\"after\":\"false\"}]");
        mvc.perform(get(HISTORY).header("Accept-Language", "ko").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].operatorId").value(Long.toString(gone)))
                .andExpect(jsonPath("$.data.items[0].operatorDeleted").value(true))
                .andExpect(jsonPath("$.data.items[0].operatorName").value("삭제된 운영자"))
                .andExpect(jsonPath("$.data.items[0].changes[0].labelKey").value("settingHistory.field.wings.left"));
    }
}
