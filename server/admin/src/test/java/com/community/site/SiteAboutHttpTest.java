package com.community.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사이트 소개 관리자 API({@code GET·PUT /api/admin/site/about}, V203) — 권한(site.read / site.manage) · 서버 소독 · 길이 400 · 빈 값 null · 감사.
 * 실제 관리자 앱 문맥 + 보안 필터 + PostgreSQL. 사이트 설정은 1행 표라 소개 칸을 시험마다 처음 값으로 되돌린다.
 */
class SiteAboutHttpTest extends Commerce04AdminTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private String savedHtml;
    private Timestamp savedAt;

    @BeforeEach
    void keep() {
        long owner = operatorWith();
        // SQL 로 남김: ON CONFLICT upsert — 설치 전 시험 DB 에 사이트 설정 행이 없을 때만 넣는다
        jdbc.update("INSERT INTO site_settings (id, site_name, public_url, updated_by) VALUES (1, '시험', 'http://localhost', ?) ON CONFLICT (id) DO NOTHING", owner);
        Map<String, Object> row = jdbc.queryForMap("SELECT about_html, about_updated_at FROM site_settings WHERE id = 1");
        savedHtml = (String) row.get("about_html");
        savedAt = (Timestamp) row.get("about_updated_at");
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE site_settings SET about_html = ?, about_updated_at = ? WHERE id = 1", savedHtml, savedAt);
    }

    private static String body(String html) {
        return JSON.writeValueAsString(java.util.Collections.singletonMap("html", html));
    }

    private String stored() {
        return jdbc.queryForObject("SELECT about_html FROM site_settings WHERE id = 1", String.class);
    }

    @Test
    void 읽기는_site_read_저장은_site_manage_권한이_있어야_한다() throws Exception {
        Cookie reader = login(operatorWith(Permission.SITE_READ));
        String publicUrl = jdbc.queryForObject("SELECT public_url FROM site_settings WHERE id = 1", String.class);
        mvc.perform(get("/api/admin/site/about").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUrl").value(publicUrl == null || publicUrl.isBlank() ? null : publicUrl));
        mvc.perform(withCsrf(put("/api/admin/site/about"), reader).content(body("<p>바꿈</p>"))).andExpect(status().isForbidden());
        assertThat(stored()).isEqualTo(savedHtml);

        Cookie nobody = login(operatorWith());
        mvc.perform(get("/api/admin/site/about").cookie(nobody)).andExpect(status().isForbidden());
    }

    @Test
    void 저장은_서버가_소독하고_링크에_새_탭_규칙을_붙인다_감사가_남는다() throws Exception {
        long manager = operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE);
        Cookie session = login(manager);
        String raw = "<h2 onclick=\"x()\">소개</h2><script>alert(1)</script><p style=\"color:red\">안녕 <a href=\"https://irobit.test/a\" target=\"_self\">링크</a>"
                + " <a href=\"javascript:alert(1)\">나쁜 링크</a></p><img src=x onerror=alert(1)><ul><li>하나</li></ul>";

        String expected = "<h2>소개</h2><p>안녕 <a href=\"https://irobit.test/a\" target=\"_blank\" rel=\"nofollow noopener noreferrer\">링크</a> 나쁜 링크</p>"
                + "<ul><li>하나</li></ul>";
        mvc.perform(withCsrf(put("/api/admin/site/about"), session).content(body(raw))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.html").value(expected))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
        assertThat(stored()).isEqualTo(expected);
        assertThat(auditLogs.where().actor(manager).action("SITE_ABOUT_UPDATE").count()).isEqualTo(1);

        mvc.perform(get("/api/admin/site/about").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.html").value(expected));

        // 같은 값은 감사를 남기지 않는다
        mvc.perform(withCsrf(put("/api/admin/site/about"), session).content(body(raw))).andExpect(status().isOk());
        assertThat(auditLogs.where().actor(manager).action("SITE_ABOUT_UPDATE").count()).isEqualTo(1);
    }

    @Test
    void 소독_전_20000자를_넘으면_400_html_칸_오류_값은_그대로() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE));
        String exact = "<p>" + "가".repeat(20_000 - 7) + "</p>";
        assertThat(exact).hasSize(20_000);
        mvc.perform(withCsrf(put("/api/admin/site/about"), session).content(body(exact))).andExpect(status().isOk());

        String over = "<p>" + "가".repeat(20_000 - 6) + "</p>";
        mvc.perform(withCsrf(put("/api/admin/site/about"), session).header("Accept-Language", "ko").content(body(over))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.html").value("사이트 소개는 20000자 이하로 입력해 주세요."));
        assertThat(stored()).isEqualTo(exact);
    }

    @Test
    void 비우거나_공백뿐이거나_보이는_글자가_없으면_null로_저장한다() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE));
        for (String empty : new String[] {null, "", "   \n ", "<p></p><p><br></p>", "<script>alert(1)</script>"}) {
            jdbc.update("UPDATE site_settings SET about_html = '<p>있음</p>' WHERE id = 1");
            mvc.perform(withCsrf(put("/api/admin/site/about"), session).content(body(empty))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.html").doesNotExist());
            assertThat(stored()).as("보낸 값 %s", empty).isNull();
        }
    }
}
