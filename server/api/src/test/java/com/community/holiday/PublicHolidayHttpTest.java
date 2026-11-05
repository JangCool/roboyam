package com.community.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.CommerceApiTestBase;
import com.community.installation.service.InstallationState;
import jakarta.servlet.Filter;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 공개 공휴일 API(사용자 지시 2026-11-05 — {@code GET /api/holidays}) — 비회원도 보안 필터를 지난다 · <b>공휴일만</b>(회사 휴무일은 날도 이름도 없다) · 같은 날 둘이면 두 줄 ·
 * {@code Cache-Control: public, max-age=300} + ETag/304 · 범위 최대 2년(넘으면 400). 2044 년 줄만 쓰고 끝나면 지운다.
 */
class PublicHolidayHttpTest extends CommerceApiTestBase {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;

    private MockMvc secured;

    @BeforeEach
    void setUp() {
        installation.markCompleted();
        secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
        cleanup();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM site_holidays WHERE holiday_date BETWEEN ? AND ?", LocalDate.of(2044, 1, 1), LocalDate.of(2044, 12, 31));
    }

    private void insert(String date, String name, String source) {
        jdbc.update("INSERT INTO site_holidays (holiday_date, name, source) VALUES (?::date, ?, ?)", date, name, source);
    }

    @Test
    void 비회원도_공휴일만_받고_회사_휴무일은_보이지_않으며_캐시_머리글과_ETag_304() throws Exception {
        insert("2044-05-05", "어린이날", "PUBLIC_DATA");
        insert("2044-05-05", "부처님오신날", "PUBLIC_DATA");
        insert("2044-05-06", "기본값 공휴일", "SEED");
        insert("2044-05-07", "회사 비밀 휴무", "OPERATOR");
        insert("2044-05-05", "회사 행사", "OPERATOR");

        MvcResult first = secured.perform(get("/api/holidays").param("from", "2044-05-01").param("to", "2044-05-31")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=300"))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].date").value("2044-05-05")).andExpect(jsonPath("$.data[0].name").value("어린이날"))
                .andExpect(jsonPath("$.data[1].name").value("부처님오신날")).andExpect(jsonPath("$.data[2].kind").value("HOLIDAY"))
                .andReturn();
        assertThat(first.getResponse().getContentAsString()).doesNotContain("회사").doesNotContain("COMPANY").doesNotContain("2044-05-07");
        String etag = first.getResponse().getHeader("ETag");
        assertThat(etag).startsWith("\"").endsWith("\"");

        secured.perform(get("/api/holidays").param("from", "2044-05-01").param("to", "2044-05-31").header("If-None-Match", etag))
                .andExpect(status().isNotModified()).andExpect(header().string("ETag", etag));
        insert("2044-05-15", "새 공휴일", "PUBLIC_DATA");
        secured.perform(get("/api/holidays").param("from", "2044-05-01").param("to", "2044-05-31").header("If-None-Match", etag))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    void 범위는_최대_2년_뒤집히거나_비거나_날짜가_아니면_400() throws Exception {
        secured.perform(get("/api/holidays").param("from", "2044-01-01").param("to", "2046-01-01")).andExpect(status().isOk());
        secured.perform(get("/api/holidays").param("from", "2044-01-01").param("to", "2046-01-02")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.to").isNotEmpty());
        secured.perform(get("/api/holidays").param("from", "2044-02-01").param("to", "2044-01-01")).andExpect(status().isBadRequest());
        secured.perform(get("/api/holidays").param("to", "2044-01-01")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.from").isNotEmpty());
        secured.perform(get("/api/holidays").param("from", "2044-1-1").param("to", "2044-01-31")).andExpect(status().isBadRequest());
    }
}
