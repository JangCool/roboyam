package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 관리자 링크 규칙·사이트 제휴 몰 API(3단계) — 도메인 정규화·유일(409 + fields.domain)·keyParams 10개·추적 파라미터 거절·대형몰 필수·영향 미리 보기(접미사)·삭제 404·권한·감사·
 * 규칙 사본 무효화, 사이트 제휴 몰 검사(틀 {@code {url}} 1개·1000자·http(s) · 쿼리 형식·300자 · 이름 40자)·유일·404·권한.
 */
class HotdealAdminRulesHttpTest extends HotdealAdminTestBase {

    private Cookie domainManager() throws Exception {
        return login(operatorWith(Permission.HOTDEAL_READ, Permission.HOTDEAL_DOMAIN_MANAGE));
    }

    private static String domainBody(String domain, String rule, boolean common, String keyParams, String note) {
        return "{\"domain\":\"" + domain + "\",\"rule\":" + (rule == null ? "null" : "\"" + rule + "\"") + ",\"common\":" + common + ",\"keyParams\":" + keyParams
                + ",\"note\":" + (note == null ? "null" : "\"" + note + "\"") + "}";
    }

    @Test
    void 링크_규칙은_정규화해_저장하고_같은_도메인은_409_고치기는_바뀐_칸만_감사() throws Exception {
        long operator = operatorWith(Permission.HOTDEAL_READ, Permission.HOTDEAL_DOMAIN_MANAGE);
        Cookie cookie = login(operator);
        String domain = domain("nm");
        JsonNode created = data(mvc.perform(withCsrf(post("/api/admin/hotdeal-domains"), cookie)
                        .content(domainBody("HTTPS://WWW." + domain.toUpperCase() + ":8080/path?x=1", "BLOCK", false, "[\"prdNo\",\"prdNo\"]", "시험 메모")))
                .andExpect(status().isOk()).andReturn());
        assertThat(created.path("domain").asString()).isEqualTo(domain);
        assertThat(created.path("rule").asString()).isEqualTo("BLOCK");
        assertThat(created.path("keyParams")).hasSize(1); // 같은 이름은 한 번
        assertThat(created.path("updatedBy").asString()).isEqualTo(operatorName(operator));
        assertThat(lastAudit(operator, AuditActions.HOTDEAL_DOMAIN_CREATE)).isNotNull();
        // 저장 뒤 이 서버의 규칙 사본이 바로 따라온다(무효화)
        assertThat(rules.judge(java.util.List.of("a." + domain)).blocked()).isTrue();

        mvc.perform(withCsrf(post("/api/admin/hotdeal-domains"), cookie).content(domainBody(domain, "AFFILIATE", false, "[]", null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("HOTDEAL_DOMAIN_DUPLICATE"))
                .andExpect(jsonPath("$.error.fields.domain").exists());

        String id = created.path("id").asString();
        mvc.perform(withCsrf(put("/api/admin/hotdeal-domains/" + id), cookie).content(domainBody(domain, "AFFILIATE", false, "[\"prdNo\"]", "시험 메모")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rule").value("AFFILIATE"));
        String detail = (String) lastAudit(operator, AuditActions.HOTDEAL_DOMAIN_UPDATE).get("detail");
        assertThat(detail).contains("BLOCK").contains("AFFILIATE").doesNotContain("시험 메모"); // 바뀐 칸만
        assertThat(rules.judge(java.util.List.of(domain)).affiliate()).isTrue();

        JsonNode list = data(mvc.perform(get("/api/admin/hotdeal-domains").param("rule", "AFFILIATE").param("q", domain).cookie(cookie)).andExpect(status().isOk())
                .andReturn());
        assertThat(list.path("items")).hasSize(1);
        assertThat(list.path("affiliateMode").asString()).isEqualTo("MEMBER");

        mvc.perform(withCsrf(delete("/api/admin/hotdeal-domains/" + id), cookie)).andExpect(status().isNoContent());
        assertThat(lastAudit(operator, AuditActions.HOTDEAL_DOMAIN_DELETE)).isNotNull();
        assertThat(rules.judge(java.util.List.of(domain)).rule()).isNull();
        mvc.perform(withCsrf(delete("/api/admin/hotdeal-domains/" + id), cookie)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("HOTDEAL_DOMAIN_NOT_FOUND"));
        mvc.perform(withCsrf(put("/api/admin/hotdeal-domains/" + id), cookie).content(domainBody(domain, "BLOCK", false, "[]", null))).andExpect(status().isNotFound());
    }

    @Test
    void 링크_규칙_칸_검사_keyParams_10개_추적_파라미터_대형몰_메모() throws Exception {
        Cookie cookie = domainManager();
        String domain = domain("kp");
        String eleven = IntStream.range(0, 11).mapToObj(i -> "\"k" + i + "\"").collect(Collectors.joining(",", "[", "]"));
        String ten = IntStream.range(0, 10).mapToObj(i -> "\"k" + i + "\"").collect(Collectors.joining(",", "[", "]"));
        mvc.perform(withCsrf(post("/api/admin/hotdeal-domains"), cookie).content(domainBody(domain, "BLOCK", false, eleven, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.keyParams").exists());
        for (String tracking : new String[] {"utm_source", "fbclid", "affid", "ref", "_trkparms"}) {
            mvc.perform(withCsrf(post("/api/admin/hotdeal-domains"), cookie).content(domainBody(domain, "BLOCK", false, "[\"" + tracking + "\"]", null)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.keyParams").exists());
        }
        mvc.perform(withCsrf(post("/api/admin/hotdeal-domains"), cookie).content(domainBody(domain, "BLOCK", false, "[\"bad name\"]", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.keyParams").exists());
        // 규칙 없음이면 대형몰 필수
        mvc.perform(withCsrf(post("/api/admin/hotdeal-domains"), cookie).content(domainBody(domain, null, false, "[]", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.common").exists());
        mvc.perform(withCsrf(post("/api/admin/hotdeal-domains"), cookie).content(domainBody("not a domain", "BLOCK", false, "[]", "가".repeat(101))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.domain").exists()).andExpect(jsonPath("$.error.fields.note").exists());
        mvc.perform(withCsrf(post("/api/admin/hotdeal-domains"), cookie).content(domainBody(domain, "WHAT", false, "[]", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.rule").exists());
        // 10개 · 규칙 없음 + 대형몰은 된다
        mvc.perform(withCsrf(post("/api/admin/hotdeal-domains"), cookie).content(domainBody(domain, null, true, ten, null))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rule").isEmpty()).andExpect(jsonPath("$.data.common").value(true)).andExpect(jsonPath("$.data.keyParams.length()").value(10));
        // 읽기 권한만으로는 못 고친다 · 목록은 본다
        Cookie reader = login(operatorWith(Permission.HOTDEAL_READ));
        mvc.perform(withCsrf(post("/api/admin/hotdeal-domains"), reader).content(domainBody(domain("ro"), "BLOCK", false, "[]", null))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/hotdeal-domains").cookie(reader)).andExpect(status().isOk());
    }

    @Test
    void 영향_미리_보기는_하위_도메인과_최종_호스트까지_진행_중_공개_글만_센다() throws Exception {
        long board = dealBoard();
        long author = newMember();
        String domain = domain("im");
        long direct = deal(board, author, domain);
        long sub = deal(board, author, "shop." + domain);
        long viaFinal = deal(board, author, "s" + uniq() + ".other.example");
        jdbc.update("UPDATE post_deals SET final_host = ? WHERE post_id = ?", "m." + domain, viaFinal);
        long ended = deal(board, author, domain);
        endByAuthor(ended);
        long hidden = deal(board, author, domain);
        jdbc.update("UPDATE posts SET hidden_at = now(), hidden_reason = 'STAFF' WHERE id = ?", hidden);
        deal(board, author, "not" + domain.replace(".", "")); // 접미사가 점 경계가 아니면 안 걸린다
        Cookie reader = login(operatorWith(Permission.HOTDEAL_READ));

        JsonNode impact = data(mvc.perform(get("/api/admin/hotdeal-domains/impact").param("domain", "https://www." + domain + "/x").cookie(reader))
                .andExpect(status().isOk()).andReturn());
        assertThat(impact.path("domain").asString()).isEqualTo(domain);
        assertThat(impact.path("activeCount").asLong()).isEqualTo(3);
        assertThat(impact.path("samples")).hasSize(3);
        assertThat(impact.path("samples").get(0).path("postId").asString()).isEqualTo(Long.toString(viaFinal));
        assertThat(impact.path("samples").get(0).has("storeName")).isTrue();
        assertThat(java.util.List.of(direct, sub)).isNotEmpty();
        mvc.perform(get("/api/admin/hotdeal-domains/impact").param("domain", "!!").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.domain").exists());
    }

    // ── 사이트 제휴 몰 ────────────────────────────────────────────────────

    private static String storeBody(String domain, String program, String type, String value, Boolean enabled) {
        String transform = type == null ? "null" : "{\"type\":\"" + type + "\",\"" + ("TEMPLATE".equals(type) ? "template" : "query") + "\":" + (value == null ? "null"
                : "\"" + value + "\"") + "}";
        return "{\"domain\":\"" + domain + "\",\"programName\":" + (program == null ? "null" : "\"" + program + "\"") + ",\"transform\":" + transform
                + (enabled == null ? "" : ",\"enabled\":" + enabled) + "}";
    }

    @Test
    void 사이트_제휴_몰_칸_검사() throws Exception {
        Cookie cookie = login(operatorWith(Permission.HOTDEAL_MANAGE));
        String domain = domain("sa");
        String base = "/api/admin/hotdeal-site-affiliates";
        // 틀: {url} 없음 · 둘 · http(s) 아님 · 1000자 넘음
        for (String bad : new String[] {"https://link.example.com/deeplink?id=1", "https://link.example.com/d?u={url}&v={url}", "ftp://link.example.com/?u={url}",
                "{url}", "https://link.example.com/?u={url}&p=" + "a".repeat(1000)}) {
            mvc.perform(withCsrf(post(base), cookie).content(storeBody(domain, "쿠팡 파트너스", "TEMPLATE", bad, null)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.template").exists());
        }
        // 쿼리: 형식 · 300자 넘음 · 빔
        for (String bad : new String[] {"tag", "a=1&&b=2", "a b=1", "a=1#x", "a=" + "b".repeat(300), ""}) {
            mvc.perform(withCsrf(post(base), cookie).content(storeBody(domain, "프로그램", "QUERY", bad, null)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.query").exists());
        }
        // 이름: 빔 · 41자 · 도메인 형식 · 변환 종류
        mvc.perform(withCsrf(post(base), cookie).content(storeBody("bad domain", "", "QUERY", "a=1", null))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.domain").exists()).andExpect(jsonPath("$.error.fields.programName").exists());
        mvc.perform(withCsrf(post(base), cookie).content(storeBody(domain, "가".repeat(41), "QUERY", "a=1", null))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.programName").exists());
        mvc.perform(withCsrf(post(base), cookie).content(storeBody(domain, "프로그램", null, null, null))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.transform").exists());
        // 앞의 ? 는 떼고 받는다 · 이름 40자 · 틀 1000자 딱
        mvc.perform(withCsrf(post(base), cookie).content(storeBody(domain, "가".repeat(40), "QUERY", "?aff_id=A1&sub=x", null))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transform.query").value("aff_id=A1&sub=x")).andExpect(jsonPath("$.data.transform.template").doesNotExist());
        String head = "https://link.example.com/d?u={url}&p=";
        String exact = head + "a".repeat(1000 - head.length());
        String domain2 = domain("sb");
        mvc.perform(withCsrf(post(base), cookie).content(storeBody(domain2, "틀", "TEMPLATE", exact, null))).andExpect(status().isOk());
    }

    @Test
    void 사이트_제휴_몰은_유일하고_켬끔도_PUT_지우면_404_감사와_사본_무효화() throws Exception {
        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        Cookie cookie = login(operator);
        String domain = domain("sc");
        String base = "/api/admin/hotdeal-site-affiliates";
        JsonNode created = data(mvc.perform(withCsrf(post(base), cookie).content(storeBody("www." + domain, "쿠팡 파트너스", "TEMPLATE",
                "https://link.example.com/deeplink?url={url}&id=AF1", null))).andExpect(status().isOk()).andReturn());
        assertThat(created.path("domain").asString()).isEqualTo(domain);
        assertThat(created.path("enabled").asBoolean()).isTrue(); // 새 몰은 켠 채로
        assertThat(created.path("transform").path("type").asString()).isEqualTo("TEMPLATE");
        assertThat(created.path("transform").has("query")).isFalse();
        assertThat(created.path("updatedBy").asString()).isEqualTo(operatorName(operator));
        assertThat(rules.siteStore("a." + domain, null)).isPresent();
        mvc.perform(withCsrf(post(base), cookie).content(storeBody(domain, "다른", "QUERY", "a=1", true))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HOTDEAL_SITE_AFFILIATE_DUPLICATE")).andExpect(jsonPath("$.error.fields.domain").exists());

        String id = created.path("id").asString();
        mvc.perform(withCsrf(put(base + "/" + id), cookie).content(storeBody(domain, "쿠팡 파트너스", "TEMPLATE", "https://link.example.com/deeplink?url={url}&id=AF1", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        assertThat((String) lastAudit(operator, AuditActions.HOTDEAL_SITE_AFFILIATE_UPDATE).get("detail")).contains("enabled").doesNotContain("programName");
        assertThat(rules.siteStore("a." + domain, null)).isEmpty(); // 끈 몰은 판정에 들지 않는다
        JsonNode list = data(mvc.perform(get(base).cookie(cookie)).andExpect(status().isOk()).andReturn());
        java.util.List<String> listed = new java.util.ArrayList<>();
        list.path("items").forEach(i -> listed.add(i.path("domain").asString()));
        assertThat(listed).contains(domain);

        mvc.perform(withCsrf(delete(base + "/" + id), cookie)).andExpect(status().isNoContent());
        assertThat(lastAudit(operator, AuditActions.HOTDEAL_SITE_AFFILIATE_DELETE)).isNotNull();
        mvc.perform(withCsrf(delete(base + "/" + id), cookie)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("HOTDEAL_SITE_AFFILIATE_NOT_FOUND"));
        mvc.perform(withCsrf(put(base + "/" + id), cookie).content(storeBody(domain, "x", "QUERY", "a=1", true))).andExpect(status().isNotFound());
        // 조회도 hotdeal.manage(계약) — 읽기 권한만으로는 403
        mvc.perform(get(base).cookie(login(operatorWith(Permission.HOTDEAL_READ)))).andExpect(status().isForbidden());
    }
}
