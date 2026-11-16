package com.community.pointgame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetentionClass;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 관리자 포인트 게임 설정(docs/features/point-games-contract.md 「당첨 확인 링크」·「권한」) — {@code GET/PUT /api/admin/point-games/settings}.
 * 권한 {@code pointshop.read}(조회)·{@code pointshop.manage}(저장), 처음 값 동행복권 {@code https://dhlottery.co.kr}, 이름 1~30자 · 링크 {@code https://} 절대 주소 300자 이하,
 * 저장은 감사(일반 운영, 바뀐 칸만 전→후 · 같은 값이면 감사 없음). 실제 필터 체인·세션·권한 인터셉터·PostgreSQL. 설정 행은 공용이라 시험 끝에 되돌린다.
 */
class PointGameSettingsAdminHttpTest extends Commerce04AdminTestBase {

    private static final String PATH = "/api/admin/point-games/settings";

    private Map<String, Object> saved;

    @BeforeEach
    void keep() {
        saved = jdbc.queryForMap("SELECT lottery_provider_name, lottery_check_url, updated_at, updated_by FROM point_game_settings WHERE id = 1");
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE point_game_settings SET lottery_provider_name = ?, lottery_check_url = ?, updated_at = ?, updated_by = ? WHERE id = 1",
                saved.get("lottery_provider_name"), saved.get("lottery_check_url"), saved.get("updated_at"), saved.get("updated_by"));
    }

    @Test
    void 조회는_pointshop_read_저장은_pointshop_manage_권한이_없으면_403_로그인_없으면_401() throws Exception {
        Cookie reader = login(operatorWith(Permission.POINTSHOP_READ));
        Cookie nobody = login(operatorWith(Permission.POINT_READ));
        String body = "{\"lotteryProviderName\":\"동행복권\",\"lotteryCheckUrl\":\"https://dhlottery.co.kr\"}";

        mvc.perform(get(PATH).cookie(reader)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(withCsrf(put(PATH), reader).content(body)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        mvc.perform(get(PATH).cookie(nobody)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void 처음_값은_동행복권과_그_주소다() throws Exception {
        jdbc.update("UPDATE point_game_settings SET lottery_provider_name = DEFAULT, lottery_check_url = DEFAULT, updated_at = NULL, updated_by = NULL WHERE id = 1");
        Cookie reader = login(operatorWith(Permission.POINTSHOP_READ));
        mvc.perform(get(PATH).cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lotteryProviderName").value("동행복권"))
                .andExpect(jsonPath("$.data.lotteryCheckUrl").value("https://dhlottery.co.kr"))
                .andExpect(jsonPath("$.data.defaultProviderName").value("동행복권"))
                .andExpect(jsonPath("$.data.defaultCheckUrl").value("https://dhlottery.co.kr"))
                .andExpect(jsonPath("$.data.updatedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.updatedBy").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 틀린_이름과_주소는_400_fields_로_막고_값을_바꾸지_않는다() throws Exception {
        Cookie manager = login(operatorWith(Permission.POINTSHOP_MANAGE, Permission.POINTSHOP_READ));
        String before = jdbc.queryForObject("SELECT lottery_check_url FROM point_game_settings WHERE id = 1", String.class);
        String ok = "https://dhlottery.co.kr";

        for (String name : List.of("", "   ", "가".repeat(31))) {
            save(manager, name, ok).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.error.fields.lotteryProviderName").value("복권 사업자 이름은 1~30자로 적어 주세요."))
                    .andExpect(jsonPath("$.error.fields.lotteryCheckUrl").doesNotExist());
        }
        for (String url : List.of("", "http://dhlottery.co.kr", "javascript:alert(1)", "https://localhost", "dhlottery.co.kr", "https://dh lottery.co.kr",
                "https://user@dhlottery.co.kr", "https://" + "a".repeat(290) + ".co.kr", "ftp://dhlottery.co.kr", "/relative/path")) {
            save(manager, "동행복권", url).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.lotteryCheckUrl").value("https:// 로 시작하는 주소를 300자 안으로 적어 주세요."))
                    .andExpect(jsonPath("$.error.fields.lotteryProviderName").doesNotExist());
        }
        // 두 칸 모두 틀리면 둘 다
        save(manager, "", "http://x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.lotteryProviderName").exists())
                .andExpect(jsonPath("$.error.fields.lotteryCheckUrl").exists());
        mvc.perform(withCsrf(put(PATH), manager).header("Accept-Language", "ko").content("{}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT lottery_check_url FROM point_game_settings WHERE id = 1", String.class)).isEqualTo(before);
    }

    @Test
    void 저장하면_앞뒤_공백을_빼고_남기며_바뀐_칸만_감사하고_같은_값이면_감사하지_않는다() throws Exception {
        jdbc.update("UPDATE point_game_settings SET lottery_provider_name = DEFAULT, lottery_check_url = DEFAULT, updated_at = NULL, updated_by = NULL WHERE id = 1");
        long operator = operatorWith(Permission.POINTSHOP_MANAGE, Permission.POINTSHOP_READ);
        Cookie manager = login(operator);
        int before = auditLogs.where().action(AuditActions.POINT_GAME_SETTINGS_UPDATE).actor(operator).count();

        save(manager, "  새 복권사  ", " https://lotto.example.com/result?round=1 ").andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.lotteryProviderName").value("새 복권사"))
                .andExpect(jsonPath("$.data.lotteryCheckUrl").value("https://lotto.example.com/result?round=1"))
                .andExpect(jsonPath("$.data.defaultProviderName").value("동행복권"))
                .andExpect(jsonPath("$.data.updatedBy").value(operatorName(operator)))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
        Map<String, Object> audit = auditLogs.where().action(AuditActions.POINT_GAME_SETTINGS_UPDATE).actor(operator).latest().orElseThrow().asMap();
        assertThat(audit.get("target_type")).isEqualTo("POINT_GAME_SETTINGS");
        assertThat(audit.get("retention_class")).isEqualTo(AuditRetentionClass.GENERAL.name());
        assertThat(audit.get("detail").toString()).contains("동행복권", "새 복권사", "https://dhlottery.co.kr", "https://lotto.example.com/result?round=1");

        // 주소만 바꾸면 주소만 감사
        save(manager, "새 복권사", "https://lotto.example.com/").andExpect(status().isOk());
        String detail = auditLogs.where().action(AuditActions.POINT_GAME_SETTINGS_UPDATE).actor(operator).latest().orElseThrow().asMap().get("detail").toString();
        assertThat(detail).contains("lotteryCheckUrl").doesNotContain("lotteryProviderName");

        // 같은 값은 감사 없이
        save(manager, "새 복권사", "https://lotto.example.com/").andExpect(status().isOk());
        assertThat(auditLogs.where().action(AuditActions.POINT_GAME_SETTINGS_UPDATE).actor(operator).count()).isEqualTo(before + 2);
    }

    private org.springframework.test.web.servlet.ResultActions save(Cookie session, String name, String url) throws Exception {
        String json = "{\"lotteryProviderName\":" + quote(name) + ",\"lotteryCheckUrl\":" + quote(url) + "}";
        return mvc.perform(withCsrf(put(PATH), session).header("Accept-Language", "ko").content(json));
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
