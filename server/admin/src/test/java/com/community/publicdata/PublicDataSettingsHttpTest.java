package com.community.publicdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import com.community.publicdata.service.PublicDataSettingsService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 공공데이터포털 설정 API(business-contract 「국세청 진위확인 · 공공데이터 설정」) — 조회·연결 시험 {@code site.read}, 저장·지우기 {@code site.secret}. 키 값은 어떤 응답·감사에도
 * 없다. 키 읽는 차례: 화면 저장값 → 환경 변수({@code irobit.business.nts.service-key}) → 없음 — 이 시험은 환경 변수 키를 둔다. 연결 시험은 구현이 가짜여도 실제 HTTP 를 부른다 —
 * 시험에서는 국세청 주소를 닫힌 포트로 돌려 「응답 없음(TIMEOUT)」을 본다(밖으로 나가지 않는다). 설정 표는 사이트에 하나라 시험 앞뒤로 원래 줄을 되돌린다.
 */
@TestPropertySource(properties = {
        "irobit.business.nts.service-key=" + PublicDataSettingsHttpTest.ENV_KEY,
        "irobit.business.nts.base-url=http://127.0.0.1:9"
})
class PublicDataSettingsHttpTest extends Commerce04AdminTestBase {

    static final String ENV_KEY = "env-nts-key-from-deploy-77";
    private static final String SCREEN_KEY = "screen+nts/key==Decoding-9a8b7c";

    @Autowired
    private PublicDataSettingsService service;

    private List<Map<String, Object>> saved;

    @BeforeEach
    void keepRows() {
        saved = jdbc.queryForList("SELECT * FROM public_data_settings");
        jdbc.update("DELETE FROM public_data_settings");
        jdbc.update("DELETE FROM public_data_endpoints");
    }

    @AfterEach
    void restoreRows() {
        jdbc.update("DELETE FROM public_data_endpoints");
        jdbc.update("DELETE FROM public_data_settings");
        for (Map<String, Object> row : saved) {
            jdbc.update("INSERT INTO public_data_settings (key, value_cipher, updated_at, updated_by, tested_result, tested_at, tested_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    row.get("key"), row.get("value_cipher"), row.get("updated_at"), row.get("updated_by"), row.get("tested_result"), row.get("tested_at"), row.get("tested_by"));
        }
    }

    @Test
    void 조회와_시험은_site_read_저장과_지우기는_site_secret_이_없으면_403() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE));

        mvc.perform(get("/api/admin/public-data-settings").cookie(none)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        mvc.perform(withCsrf(post("/api/admin/public-data-settings/test"), none)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/public-data-settings").cookie(reader)).andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/admin/public-data-settings"), reader).content("{\"serviceKey\":\"" + SCREEN_KEY + "\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(delete("/api/admin/public-data-settings/service-key"), reader)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public_data_settings WHERE value_cipher IS NOT NULL", Integer.class)).isZero();
    }

    @Test
    void 처음에는_환경_변수_키를_쓰고_화면에는_값이_없다() throws Exception {
        MvcResult result = mvc.perform(get("/api/admin/public-data-settings").cookie(login(operatorWith(Permission.SITE_READ)))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.ntsProvider").value("FAKE")) // local 프로필의 가짜 구현
                .andExpect(jsonPath("$.data.cryptoKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.serviceKey.source").value("ENV"))
                .andExpect(jsonPath("$.data.serviceKey.updatedAt").isEmpty())
                .andExpect(jsonPath("$.data.lastTest").isEmpty())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(ENV_KEY);
        assertThat(service.ntsServiceKey()).isEqualTo(ENV_KEY);
    }

    @Test
    void 저장하면_화면_키가_환경_변수보다_먼저고_암호문만_남고_값은_응답과_감사에_없다_지우면_환경_변수로_돌아간다() throws Exception {
        long operator = operatorWith(Permission.SITE_READ, Permission.SITE_SECRET);
        Cookie session = login(operator);

        MvcResult saveResult = mvc.perform(withCsrf(put("/api/admin/public-data-settings"), session).content("{\"serviceKey\":\"  " + SCREEN_KEY + "  \"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.serviceKey.source").value("SCREEN"))
                .andExpect(jsonPath("$.data.serviceKey.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.serviceKey.updatedBy").value(operatorName(operator)))
                .andReturn();
        assertThat(saveResult.getResponse().getContentAsString()).doesNotContain(SCREEN_KEY).doesNotContain("v1:");
        String cipher = jdbc.queryForObject("SELECT value_cipher FROM public_data_settings WHERE key = 'NTS_SERVICE_KEY'", String.class);
        assertThat(cipher).startsWith("v1:").doesNotContain(SCREEN_KEY);
        assertThat(service.ntsServiceKey()).as("화면 값이 먼저 — 앞뒤 공백은 지운다(재기동 없이 바로)").isEqualTo(SCREEN_KEY);
        String detail = auditLogs.where().actor(operator).action("PUBLIC_DATA_SETTINGS_UPDATE").lastById().orElseThrow().detail();
        assertThat(detail).contains("serviceKey").doesNotContain(SCREEN_KEY);
        assertThat(auditLogs.where().actor(operator).action("PUBLIC_DATA_SETTINGS_UPDATE").lastById().map(row -> row.targetType() + "/" + row.targetId()).orElseThrow()).isEqualTo("PUBLIC_DATA_SETTINGS/nts");

        mvc.perform(withCsrf(delete("/api/admin/public-data-settings/service-key"), session)).andExpect(status().isNoContent());
        assertThat(service.ntsServiceKey()).isEqualTo(ENV_KEY);
        mvc.perform(get("/api/admin/public-data-settings").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceKey.source").value("ENV"))
                .andExpect(jsonPath("$.data.serviceKey.updatedBy").value(operatorName(operator)));
        assertThat(auditLogs.where().actor(operator).action("PUBLIC_DATA_SETTINGS_DELETE").lastById().orElseThrow().detail()).contains("\"field\":\"serviceKey\"");

        mvc.perform(withCsrf(delete("/api/admin/public-data-settings/service-key"), session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PUBLIC_DATA_KEY_NOT_FOUND"));
    }

    @Test
    void 빈_값_공백_포함_500자_초과는_400_fields_serviceKey_이고_저장하지_않는다() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_SECRET));
        for (String body : new String[] {"{\"serviceKey\":\"   \"}", "{}", "{\"serviceKey\":\"abc def\"}", "{\"serviceKey\":\"" + "k".repeat(501) + "\"}"}) {
            mvc.perform(withCsrf(put("/api/admin/public-data-settings"), session).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.error.fields.serviceKey").isNotEmpty());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public_data_settings", Integer.class)).isZero();
        mvc.perform(withCsrf(put("/api/admin/public-data-settings"), session).content("{\"serviceKey\":\"" + "k".repeat(500) + "\"}")).andExpect(status().isOk());
    }

    @Test
    void 연결_시험은_지금_키로_실제_HTTP_를_부르고_결과를_남긴다_키를_바꾸면_결과를_지운다() throws Exception {
        long operator = operatorWith(Permission.SITE_READ, Permission.SITE_SECRET);
        Cookie session = login(operator);
        mvc.perform(withCsrf(put("/api/admin/public-data-settings"), session).content("{\"serviceKey\":\"" + SCREEN_KEY + "\"}")).andExpect(status().isOk());

        MvcResult test = mvc.perform(withCsrf(post("/api/admin/public-data-settings/test"), session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("TIMEOUT")) // 닫힌 포트 — 가짜 구현이어도 실제로 불렀다
                .andExpect(jsonPath("$.data.testedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.testedBy").value(operatorName(operator)))
                .andReturn();
        assertThat(test.getResponse().getContentAsString()).doesNotContain(SCREEN_KEY);
        mvc.perform(get("/api/admin/public-data-settings").cookie(session)).andExpect(jsonPath("$.data.lastTest.result").value("TIMEOUT"));

        mvc.perform(withCsrf(put("/api/admin/public-data-settings"), session).content("{\"serviceKey\":\"" + SCREEN_KEY + "-new\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.lastTest").isEmpty());
    }

    // ── API 주소(사용자 지시 2026-11-05) ─────────────────────────────────────

    @Test
    void API_주소는_보기_site_read_저장과_되돌리기는_site_secret_없으면_403() throws Exception {
        Cookie reader = login(operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE));

        mvc.perform(get("/api/admin/public-data-settings").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endpoints.nts.url.value").value("http://127.0.0.1:9")) // 배포 설정 값(이 시험) — 화면에서 넣은 값이 없을 때
                .andExpect(jsonPath("$.data.endpoints.nts.url.custom").value(false))
                .andExpect(jsonPath("$.data.endpoints.holiday.url.value").value("https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo"))
                .andExpect(jsonPath("$.data.endpoints.holiday.dateField.defaultValue").value("locdate"));
        mvc.perform(withCsrf(put("/api/admin/public-data-settings/endpoints/nts"), reader).content("{\"url\":\"https://nts.example.kr\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(put("/api/admin/public-data-settings/endpoints/holiday"), reader).content("{\"url\":\"https://h.example.kr\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(delete("/api/admin/public-data-settings/endpoints/holiday"), reader)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public_data_endpoints", Integer.class)).isZero();
    }

    @Test
    void API_주소를_저장하면_바로_쓰고_감사를_남기며_https_가_아니면_400_기본값으로_되돌린다() throws Exception {
        long operator = operatorWith(Permission.SITE_READ, Permission.SITE_SECRET);
        Cookie session = login(operator);

        mvc.perform(withCsrf(put("/api/admin/public-data-settings/endpoints/nts"), session).content("{\"url\":\"http://nts.example.kr\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.url").isNotEmpty());
        mvc.perform(withCsrf(put("/api/admin/public-data-settings/endpoints/holiday"), session).content("{\"queryTemplate\":\"solYear={year}\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.queryTemplate").isNotEmpty());
        mvc.perform(withCsrf(put("/api/admin/public-data-settings/endpoints/other"), session).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PUBLIC_DATA_ENDPOINT_NOT_FOUND"));

        mvc.perform(withCsrf(put("/api/admin/public-data-settings/endpoints/nts"), session).content("{\"url\":\"https://127.0.0.1:9/\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.nts.url.value").value("https://127.0.0.1:9")).andExpect(jsonPath("$.data.nts.url.custom").value(true))
                .andExpect(jsonPath("$.data.nts.updatedBy").value(operatorName(operator)));
        assertThat(auditLogs.where().actor(operator).action("PUBLIC_DATA_ENDPOINT_UPDATE").lastById().orElseThrow().detail()).contains("https://127.0.0.1:9").contains("url");

        // 키를 넣고 특일 정보 연결 시험 — 지금 주소(닫힌 포트)로 불러 응답 없음(바깥으로 나가지 않는다)
        mvc.perform(withCsrf(put("/api/admin/public-data-settings"), session).content("{\"serviceKey\":\"" + SCREEN_KEY + "\"}")).andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/admin/public-data-settings/endpoints/holiday"), session)
                        .content("{\"url\":\"https://127.0.0.1:9/getRestDeInfo\",\"dateField\":\"ymd\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.holiday.dateField.value").value("ymd"))
                .andExpect(jsonPath("$.data.holiday.queryTemplate.custom").value(false));
        mvc.perform(withCsrf(post("/api/admin/public-data-settings/holiday-test"), session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("TIMEOUT")).andExpect(jsonPath("$.data.testedBy").value(operatorName(operator)));
        mvc.perform(get("/api/admin/public-data-settings").cookie(session)).andExpect(jsonPath("$.data.endpoints.holiday.lastTest.result").value("TIMEOUT"));

        mvc.perform(withCsrf(delete("/api/admin/public-data-settings/endpoints/holiday"), session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holiday.url.custom").value(false)).andExpect(jsonPath("$.data.holiday.dateField.value").value("locdate"))
                .andExpect(jsonPath("$.data.nts.url.custom").value(true));
        assertThat(auditLogs.where().actor(operator).action("PUBLIC_DATA_ENDPOINT_RESET").count()).isEqualTo(1);
    }
}
