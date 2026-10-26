package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.permission.entity.Permission;
import com.community.withdrawal.service.WithdrawnContactStore;
import com.community.zconfiguration.time.ServiceTime;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 탈퇴 회원 연락용 이메일(분리 보관) API — commerce.md 17-2, 계약 「0-4」. 전용 권한 + 사유, 감사(사유 포함·이메일 없음)를 남긴 뒤에만 값, {@code no-store}, 열람 기록은 감사에서.
 */
class WithdrawnContactHttpTest extends Commerce04AdminTestBase {

    @Autowired
    private WithdrawnContactStore contacts;

    private static String path(long memberId) {
        return "/api/admin/members/" + memberId + "/withdrawn-contact";
    }

    /** 탈퇴 회원 + 분리 보관 이메일(앱의 데이터 키로 암호화) */
    private long withdrawnWithContact(String email, Instant lastTransactionAt) {
        Instant withdrawnAt = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.MILLIS);
        long member = withdrawnMember(withdrawnAt);
        contacts.store(member, email, withdrawnAt, lastTransactionAt);
        return member;
    }

    @Test
    void 상태는_member_read_로_보고_이메일은_싣지_않는다() throws Exception {
        Instant lastTx = Instant.parse("2026-03-10T15:30:00Z"); // 한국 시간 3월 11일 00:30 — 파기 예정일은 서비스 날짜 기준
        long member = withdrawnWithContact("left.someone@example.com", lastTx);
        Cookie reader = login(operatorWith(Permission.MEMBER_READ));

        MvcResult result = mvc.perform(get(path(member)).cookie(reader)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.stored").value(true))
                .andExpect(jsonPath("$.data.withdrawnAt").isNotEmpty())
                .andExpect(jsonPath("$.data.purgeAfter").value("2031-03-11"))
                .andExpect(jsonPath("$.data.reveals.length()").value(0))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("left.someone").doesNotContain("d1:");
        assertThat(ServiceTime.dateOf(contacts.purgeAfter(lastTx)).toString()).isEqualTo("2031-03-11");

        mvc.perform(get(path(member)).cookie(login(operatorWith()))).andExpect(status().isForbidden());
    }

    @Test
    void 열람은_전용_권한이_없으면_403_이고_감사도_값도_없다() throws Exception {
        long member = withdrawnWithContact("left.denied@example.com", Instant.now());
        Cookie reader = login(operatorWith(Permission.MEMBER_READ, Permission.MEMBER_PII));

        MvcResult result = mvc.perform(withCsrf(post(path(member) + "/reveal"), reader).content("{\"reason\":\"분쟁 문의 회신\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("left.denied");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'WITHDRAWN_CONTACT_REVEAL' AND target_id = ?", Integer.class,
                Long.toString(member))).isZero();
    }

    @Test
    void 사유를_남기고_열면_이메일과_10분_뒤_가림_no_store_감사에는_사유만_기록이_목록에_보인다() throws Exception {
        long member = withdrawnWithContact("left.revealed@example.com", Instant.now());
        long operator = operatorWith(Permission.MEMBER_READ, Permission.MEMBER_WITHDRAWN_CONTACT);
        Cookie session = login(operator);
        Instant before = Instant.now();

        MvcResult result = mvc.perform(withCsrf(post(path(member) + "/reveal"), session).content("{\"reason\":\"공동구매 하자 반품 문의 회신\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.email").value("left.revealed@example.com"))
                .andReturn();
        String maskAt = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.maskAt");
        assertThat(Duration.between(before, Instant.parse(maskAt))).isBetween(Duration.ofMinutes(10), Duration.ofMinutes(11));

        List<String> details = jdbc.queryForList("SELECT detail FROM audit_logs WHERE action = 'WITHDRAWN_CONTACT_REVEAL' AND target_type = 'MEMBER' AND target_id = ? "
                + "AND actor_id = ?", String.class, Long.toString(member), operator);
        assertThat(details).hasSize(1);
        assertThat(details.get(0)).contains("공동구매 하자 반품 문의 회신").doesNotContain("left.revealed").doesNotContain("@example.com");

        mvc.perform(get(path(member)).cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reveals.length()").value(1))
                .andExpect(jsonPath("$.data.reveals[0].operatorName").value(operatorName(operator)))
                .andExpect(jsonPath("$.data.reveals[0].reason").value("공동구매 하자 반품 문의 회신"))
                .andExpect(jsonPath("$.data.reveals[0].revealedAt").isNotEmpty());
    }

    @Test
    void 사유가_짧거나_길면_400_이고_감사가_없다() throws Exception {
        long member = withdrawnWithContact("left.reason@example.com", Instant.now());
        Cookie session = login(operatorWith(Permission.MEMBER_WITHDRAWN_CONTACT));

        mvc.perform(withCsrf(post(path(member) + "/reveal"), session).content("{\"reason\":\"짧음\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").isNotEmpty());
        mvc.perform(withCsrf(post(path(member) + "/reveal"), session).content("{\"reason\":\"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'WITHDRAWN_CONTACT_REVEAL' AND target_id = ?", Integer.class,
                Long.toString(member))).isZero();
    }

    @Test
    void 탈퇴하지_않았거나_보관한_것이_없으면_404_없는_회원도_404() throws Exception {
        Cookie session = login(operatorWith(Permission.MEMBER_READ, Permission.MEMBER_WITHDRAWN_CONTACT));
        long active = newMember();
        long withdrawnOnly = withdrawnMember(Instant.now().minus(Duration.ofDays(5)).truncatedTo(ChronoUnit.MILLIS));

        mvc.perform(withCsrf(post(path(active) + "/reveal"), session).content("{\"reason\":\"분쟁 문의 회신\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("WITHDRAWN_CONTACT_NOT_FOUND"));
        mvc.perform(withCsrf(post(path(withdrawnOnly) + "/reveal"), session).content("{\"reason\":\"분쟁 문의 회신\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("WITHDRAWN_CONTACT_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'WITHDRAWN_CONTACT_REVEAL' AND target_id IN (?, ?)", Integer.class,
                Long.toString(active), Long.toString(withdrawnOnly))).isZero();

        // 상태: 결제 이력 없이 탈퇴 = 보관 없음(탈퇴 시각은 회원의 것), 탈퇴하지 않음 = 보관 없음·시각 없음
        mvc.perform(get(path(withdrawnOnly)).cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stored").value(false))
                .andExpect(jsonPath("$.data.withdrawnAt").isNotEmpty())
                .andExpect(jsonPath("$.data.purgeAfter").isEmpty());
        mvc.perform(get(path(active)).cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stored").value(false))
                .andExpect(jsonPath("$.data.withdrawnAt").isEmpty());

        long missing = ids.nextId();
        mvc.perform(get(path(missing)).cookie(session)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
        mvc.perform(withCsrf(post(path(missing) + "/reveal"), session).content("{\"reason\":\"분쟁 문의 회신\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
    }
}
