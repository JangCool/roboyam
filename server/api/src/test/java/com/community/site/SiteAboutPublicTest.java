package com.community.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.cache.definition.CacheNames;
import com.community.commerce.CommerceApiTestBase;
import com.community.installation.service.InstallationState;
import jakarta.servlet.Filter;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 사이트 소개 공개 쪽(V203) — {@code GET /api/site/about}(로그인 없이) 의 {@code html} 과 {@code GET /api/site/info} 의 {@code about}(있는가).
 * 실제 공개 사이트 앱 문맥 + 보안 필터 + PostgreSQL. 값은 SQL 로 넣고(관리자 저장·소독은 관리자 앱 시험), 사이트 설정 사본(20초 캐시)은 넣을 때마다 비운다.
 */
class SiteAboutPublicTest extends CommerceApiTestBase {

    @Autowired
    private WebApplicationContext web;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;
    @Autowired
    private CacheManager caches;

    private MockMvc mvc;
    private String savedHtml;
    private Timestamp savedAt;

    @BeforeEach
    void setUp() {
        installation.markCompleted();
        mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(securityFilterChain).build();
        long owner = newOperator();
        // SQL 로 남김: ON CONFLICT upsert — 설치 전 시험 DB 에 사이트 설정 행이 없을 때만 넣는다
        jdbc.update("INSERT INTO site_settings (id, site_name, public_url, updated_by) VALUES (1, '시험', 'http://localhost', ?) ON CONFLICT (id) DO NOTHING", owner);
        Map<String, Object> row = jdbc.queryForMap("SELECT about_html, about_updated_at FROM site_settings WHERE id = 1");
        savedHtml = (String) row.get("about_html");
        savedAt = (Timestamp) row.get("about_updated_at");
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE site_settings SET about_html = ?, about_updated_at = ? WHERE id = 1", savedHtml, savedAt);
        caches.getCache(CacheNames.SITE_INFO).clear();
    }

    private void about(String html) {
        jdbc.update("UPDATE site_settings SET about_html = ? WHERE id = 1", html);
        caches.getCache(CacheNames.SITE_INFO).clear();
    }

    @Test
    void 소개가_있으면_로그인_없이_본문을_읽고_info의_about은_true() throws Exception {
        about("<h2>아이로빗</h2><p>소개</p>");
        mvc.perform(get("/api/site/about")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.html").value("<h2>아이로빗</h2><p>소개</p>"))
                .andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/api/site/info")).andExpect(status().isOk()).andExpect(jsonPath("$.data.about").value(true));
    }

    @Test
    void 소개가_없으면_html은_null이고_info의_about은_false() throws Exception {
        about(null);
        mvc.perform(get("/api/site/about")).andExpect(status().isOk()).andExpect(jsonPath("$.data.html").doesNotExist());
        mvc.perform(get("/api/site/info")).andExpect(status().isOk()).andExpect(jsonPath("$.data.about").value(false));
    }

    @Test
    void 본문만_바뀌어도_ETag가_바뀐다() throws Exception {
        about("<p>처음</p>");
        MvcResult first = mvc.perform(get("/api/site/about")).andExpect(status().isOk()).andReturn();
        String etag = first.getResponse().getHeader("ETag");
        mvc.perform(get("/api/site/about").header("If-None-Match", etag)).andExpect(status().isNotModified());

        about("<p>바꾼 소개</p>");
        mvc.perform(get("/api/site/about").header("If-None-Match", etag)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.html").value("<p>바꾼 소개</p>"));
        assertThat(mvc.perform(get("/api/site/about")).andReturn().getResponse().getHeader("ETag")).isNotEqualTo(etag);
    }
}
