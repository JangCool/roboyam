package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 관리자 핫딜 글의 출처(biz-hotdeal-contract G — V123): {@code source=MEMBER|BUSINESS} 거르기, 줄·상세의 {@code source}·{@code business{name, applicationId}} · 사업자 글 [유지]·운영진 종료·재개 기록(결정 11·12).
 */
class HotdealAdminBusinessHttpTest extends HotdealAdminTestBase {

    @Test
    void 출처로_거르고_사업자_글_줄은_상호와_신청_건을_싣는다() throws Exception {
        long board = dealBoard();
        long author = newMember();
        long member = deal(board, author, "m" + uniq() + ".shop.example");
        long business = deal(board, author, "b" + uniq() + ".shop.example");
        long applicationId = ids.nextId();
        jdbc.update("UPDATE post_deals SET source = 'BUSINESS', biz_application_id = ?, business_name = '출처상사' WHERE post_id = ?", applicationId, business);
        Cookie reader = login(operatorWith(Permission.HOTDEAL_READ));

        JsonNode all = data(mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("board", Long.toString(board)).cookie(reader)).andExpect(status().isOk())
                .andReturn());
        assertThat(all.path("total").asLong()).isEqualTo(2);
        JsonNode biz = data(mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("board", Long.toString(board)).param("source", "BUSINESS").cookie(reader))
                .andExpect(status().isOk()).andReturn());
        assertThat(biz.path("total").asLong()).isEqualTo(1);
        JsonNode row = biz.path("items").get(0);
        assertThat(row.path("postId").asString()).isEqualTo(Long.toString(business));
        assertThat(row.path("source").asString()).isEqualTo("BUSINESS");
        assertThat(row.path("business").path("name").asString()).isEqualTo("출처상사");
        assertThat(row.path("business").path("applicationId").asString()).isEqualTo(Long.toString(applicationId));
        assertThat(row.path("deal").path("business").path("name").asString()).isEqualTo("출처상사");
        assertThat(biz.path("counts").path("ALL").asLong()).isEqualTo(1);

        JsonNode mem = data(mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("board", Long.toString(board)).param("source", "member").cookie(reader))
                .andExpect(status().isOk()).andReturn());
        List<String> rows = new ArrayList<>();
        mem.path("items").forEach(i -> rows.add(i.path("postId").asString() + ":" + i.path("source").asString() + ":" + (i.path("business").isNull() || i.path("business").isMissingNode())));
        assertThat(rows).containsExactly(member + ":MEMBER:true");

        JsonNode detail = data(mvc.perform(get("/api/admin/hotdeals/" + business).cookie(reader)).andExpect(status().isOk()).andReturn());
        assertThat(detail.path("row").path("business").path("applicationId").asString()).isEqualTo(Long.toString(applicationId));
    }

    @Test
    void 사업자_글_유지는_확인_필요일_때만_처리_기록과_감사_운영진_종료_재개도_신청_건_처리_기록() throws Exception {
        long board = dealBoard();
        long author = newMember();
        long applicationId = application();
        long business = deal(board, author, "k" + uniq() + ".shop.example");
        jdbc.update("UPDATE post_deals SET source = 'BUSINESS', biz_application_id = ?, business_name = '유지상사' WHERE post_id = ?", applicationId, business);
        long member = deal(board, author, "n" + uniq() + ".shop.example");
        long manager = operatorWith(Permission.HOTDEAL_READ, Permission.HOTDEAL_MANAGE);
        Cookie cookie = login(manager);
        Cookie reader = login(operatorWith(Permission.HOTDEAL_READ));

        // 의심이 아니면 409, 읽기 권한만이면 403
        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + business + "/keep"), cookie).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HOTDEAL_STATE_CHANGED"));
        jdbc.update("UPDATE post_deals SET flagged_at = now() WHERE post_id IN (?, ?)", business, member);
        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + business + "/keep"), reader).content("{}")).andExpect(status().isForbidden());
        // 회원 글은 유지가 없다(회원 규칙 그대로)
        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + member + "/keep"), cookie).content("{}")).andExpect(status().isConflict());
        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + business + "/keep"), cookie).content("{\"reason\":\"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + business + "/keep"), cookie).content("{\"reason\":\"재고 확인함\"}")).andExpect(status().isNoContent());
        assertThat(dealRow(business).get("flagged_at")).isNull();
        assertThat(dealRow(business).get("status")).isEqualTo("ACTIVE");
        assertThat((String) lastAudit(manager, AuditActions.HOTDEAL_KEEP).get("detail")).contains("재고 확인함");
        assertThat(bizEvent(applicationId, "DEAL_KEPT_BY_OPERATOR")).isEqualTo("재고 확인함");

        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + business + "/end"), cookie).content("{\"reason\":\"품절 확인\"}")).andExpect(status().isNoContent());
        assertThat(bizEvent(applicationId, "DEAL_ENDED_BY_OPERATOR")).isEqualTo("품절 확인");
        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + business + "/reopen"), cookie).content("{\"reason\":\"다시 판매\"}")).andExpect(status().isNoContent());
        assertThat(bizEvent(applicationId, "DEAL_REOPENED_BY_OPERATOR")).isEqualTo("다시 판매");
        // 회원 글 종료는 신청 건 처리 기록이 없다
        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + member + "/end"), cookie).content("{}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_application_events WHERE application_id = ?", Integer.class, applicationId)).isEqualTo(3);
    }

    /** 운영자 등록 사업자 핫딜 신청 건 한 줄(처리 기록이 붙을 자리 — 회원 없음이라 사업자 알림은 건너뛴다) */
    private long application() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO biz_applications (id, application_no, kind, status, source, advertiser_name, title, start_at, end_at, preferred_method, quote_supply, "
                        + "quote_vat, quote_total, ad_policy_version, refund_policy_version, agreed_at, stop_fee_rate, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'HOTDEAL_POST', 'SUBMITTED', 'OPERATOR', '유지상사', '시험 핫딜', date_trunc('minute', now()) + interval '1 day', "
                        + "date_trunc('minute', now()) + interval '8 day', 'ONLINE', 0, 0, 0, 'v1', 'v1', now(), 0, ?, now(), now())", id, "HK-" + (id % 1_000_000_000_000L), operatorWith(Permission.HOTDEAL_MANAGE));
        return id;
    }

    private String bizEvent(long applicationId, String action) {
        return jdbc.queryForObject("SELECT note FROM biz_application_events WHERE application_id = ? AND action = ? ORDER BY id DESC LIMIT 1", String.class, applicationId,
                action);
    }
}
