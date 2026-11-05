package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonStudioSaveRequest;
import com.community.emoticon.service.EmoticonReviewService;
import com.community.emoticon.service.EmoticonStudioService;
import com.community.payment.gateway.fake.FakePgStore;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 로보티콘 판매 내역 · 예외 환불(emoticon-contract S3 — 권한 {@code emoticon.sales}) — 실제 필터 체인·세션·권한 인터셉터·PostgreSQL.
 * 목록 모양(산 때 이름 · 구매 회원 · 작가 · 결제 ID · 처음 쓴 시각 · 셀프 환불 판정 · 환불 기록) · 거르기 · 합계, 예외 환불(사유 · 쓴 구매도 · 보유 해제 · PG 취소 대기열 · 감사 · 다시 하면 409).
 */
class EmoticonSalesAdminHttpTest extends Commerce04AdminTestBase {

    private static final int PRICE = 2500;

    @Autowired
    private EmoticonStudioService studio;
    @Autowired
    private EmoticonReviewService reviews;
    @Autowired
    private PlatformTransactionManager transactions;

    private long member() {
        long id = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        return id;
    }

    private long image(long creator, String kind, String format, int frames) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO emoticon_images (id, kind, format, width, height, bytes, frames, loop_forever, content_hash, static_path, uploaded_by_user, created_at) "
                + "VALUES (?, ?, ?, 200, 200, 1000, ?, true, lpad(to_hex(?::bigint), 64, '0'), '/emoticons/test.gif', ?, now())", id, kind, format, frames, id, creator);
        return id;
    }

    /** 승인된 작가 팩(무료로 승인 — 판매 기록은 SQL 로 넣는다) — 팩 ID */
    private long pack(long creator, long operator) {
        List<EmoticonStudioSaveRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(new EmoticonStudioSaveRequest.Item(Long.toString(image(creator, "EMOTICON", "GIF", 2)), "표" + i));
        }
        EmoticonStudioSaveRequest body = new EmoticonStudioSaveRequest("판매 시험 팩", "설명", List.of("시험"), Long.toString(image(creator, "COVER", "PNG", 1)), items, "FREE",
                null, true, true, true, null);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        long packId = Long.parseLong(tx.execute(s -> studio.submit(creator, null, body)).id());
        String review = jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ?", Long.class, packId).toString();
        tx.executeWithoutResult(s -> reviews.approve(operator, "127.0.0.1", review, new EmoticonReviewDecisionRequest(0, null)));
        return packId;
    }

    /** 결제 완료된 판매 하나(결제 주문 PAID + 구매 PAID + 보유 줄 + 가짜 PG 결제) — 구매 ID */
    private long sale(long buyer, long pack, long creator, Instant paidAt, Instant usedAt) {
        long purchase = ids.nextId();
        long order = ids.nextId();
        String paymentId = "pay_" + String.format("%032d", order).substring(0, 32);
        jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, paid_amount, paid_at, expires_at, confirmed_via, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '로보티콘', ?, 'PAID', ?, ?, ?, 'WEBHOOK', ?, ?)", order, paymentId, buyer, purchase, PRICE, PRICE,
                Timestamp.from(paidAt), Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), Timestamp.from(paidAt));
        jdbc.update("INSERT INTO emoticon_purchases (id, user_id, pack_id, creator_id, pack_name, amount, status, refund_notice_agreed_at, expires_at, created_at, "
                + "payment_order_id, paid_at, refund_until, used_at) VALUES (?, ?, ?, ?, '산 때 이름', ?, 'PAID', ?, ?, ?, ?, ?, ?, ?)", purchase, buyer, pack, creator, PRICE,
                Timestamp.from(paidAt), Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), order, Timestamp.from(paidAt),
                Timestamp.from(paidAt.plus(Duration.ofDays(7))), usedAt == null ? null : Timestamp.from(usedAt));
        jdbc.update("INSERT INTO emoticon_acquisitions (id, user_id, pack_id, kind, purchase_id, first_acquired_at, acquired_at) VALUES (?, ?, ?, 'PURCHASE', ?, ?, ?)",
                ids.nextId(), buyer, pack, purchase, Timestamp.from(paidAt), Timestamp.from(paidAt));
        new FakePgStore(dataSource).pay(paymentId, PRICE, "KRW", paidAt);
        return purchase;
    }

    @Test
    void 판매_내역은_emoticon_sales_권한이고_목록_모양_거르기_합계가_목업과_같다() throws Exception {
        long operator = operatorWith(Permission.EMOTICON_READ, Permission.EMOTICON_REVIEW);
        long creator = member();
        long pack = pack(creator, operator);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long fresh = sale(member(), pack, creator, now.minus(Duration.ofHours(1)), null);
        long used = sale(member(), pack, creator, now.minus(Duration.ofHours(2)), now.minus(Duration.ofMinutes(90)));
        long old = sale(member(), pack, creator, now.minus(Duration.ofDays(8)), null);
        Cookie reader = login(operator);
        mvc.perform(get("/api/admin/emoticons/sales").cookie(reader)).andExpect(status().isForbidden());
        Cookie sales = login(operatorWith(Permission.EMOTICON_SALES));
        mvc.perform(get("/api/admin/emoticons/sales").param("packId", Long.toString(pack)).param("page", "0").param("size", "20").cookie(sales))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.paidAmount").value(3 * PRICE))
                .andExpect(jsonPath("$.data.items[0].id").value(Long.toString(fresh))).andExpect(jsonPath("$.data.items[0].packName").value("산 때 이름"))
                .andExpect(jsonPath("$.data.items[0].coverUrl").isNotEmpty()).andExpect(jsonPath("$.data.items[0].creator.memberNo").value(Long.toString(creator)))
                .andExpect(jsonPath("$.data.items[0].buyer.memberNo").isNotEmpty()).andExpect(jsonPath("$.data.items[0].paymentId").value(org.hamcrest.Matchers.startsWith("pay_")))
                .andExpect(jsonPath("$.data.items[0].status").value("PAID")).andExpect(jsonPath("$.data.items[0].selfRefund.allowed").value(true))
                .andExpect(jsonPath("$.data.items[0].selfRefund.blockedBy").doesNotExist()).andExpect(jsonPath("$.data.items[0].refund").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].settlementMonth").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].id").value(Long.toString(used))).andExpect(jsonPath("$.data.items[1].firstUsedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[1].selfRefund.blockedBy").value("USED"))
                .andExpect(jsonPath("$.data.items[2].id").value(Long.toString(old))).andExpect(jsonPath("$.data.items[2].selfRefund.blockedBy").value("EXPIRED"));
        // 결제 시각 거르기(UTC ISO — 이상·미만) · 쪽 크기 · 틀린 형식
        mvc.perform(get("/api/admin/emoticons/sales").param("packId", Long.toString(pack)).param("from", now.minus(Duration.ofDays(1)).toString()).cookie(sales))
                .andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/admin/emoticons/sales").param("packId", Long.toString(pack)).param("size", "1").param("page", "1").cookie(sales))
                .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].id").value(Long.toString(used)));
        mvc.perform(get("/api/admin/emoticons/sales").param("from", "어제").cookie(sales)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.from").exists());
        mvc.perform(get("/api/admin/emoticons/sales").param("packId", Long.toString(pack)).param("status", "REFUNDED").cookie(sales)).andExpect(jsonPath("$.data.total").value(0));
        // 관리자 팩 줄·작가 요약의 판매 칸이 실제 값
        Cookie packReader = login(operatorWith(Permission.EMOTICON_READ));
        mvc.perform(get("/api/admin/emoticons/packs").param("creator", Long.toString(creator)).cookie(packReader)).andExpect(jsonPath("$.data.items[0].salesCount").value(3))
                .andExpect(jsonPath("$.data.items[0].salesAmount").value(3 * PRICE));
        mvc.perform(get("/api/admin/emoticons/creators/" + creator).cookie(packReader)).andExpect(jsonPath("$.data.salesAmount").value(3 * PRICE));
    }

    @Test
    void 예외_환불은_사유가_필요하고_쓴_구매도_환불하며_보유를_빼고_PG_취소와_감사를_남긴다() throws Exception {
        long operator = operatorWith(Permission.EMOTICON_READ, Permission.EMOTICON_REVIEW);
        long creator = member();
        long pack = pack(creator, operator);
        long buyer = member();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long used = sale(buyer, pack, creator, now.minus(Duration.ofDays(10)), now.minus(Duration.ofDays(9)));
        String path = "/api/admin/emoticons/sales/" + used + "/refund";
        mvc.perform(withCsrf(post(path), login(operator)).content("{\"reason\":\"두 번 결제됐다는 문의\"}")).andExpect(status().isForbidden());
        long salesOperator = operatorWith(Permission.EMOTICON_SALES);
        Cookie sales = login(salesOperator);
        mvc.perform(withCsrf(post(path), sales).content("{\"reason\":\"짧음\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());
        mvc.perform(withCsrf(post("/api/admin/emoticons/sales/123/refund"), sales).content("{\"reason\":\"두 번 결제됐다는 문의\"}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EMOTICON_SALE_NOT_FOUND"));
        mvc.perform(withCsrf(post(path), sales).content("{\"reason\":\"두 번 결제됐다는 문의\"}")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.sale.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.sale.refund.kind").value("EXCEPTION")).andExpect(jsonPath("$.data.sale.refund.reason").value("두 번 결제됐다는 문의"))
                .andExpect(jsonPath("$.data.sale.refund.by").value(operatorName(salesOperator))).andExpect(jsonPath("$.data.sale.selfRefund").doesNotExist());
        mvc.perform(withCsrf(post(path), sales).content("{\"reason\":\"두 번 결제됐다는 문의\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMOTICON_SALE_ALREADY_REFUNDED"));
        Map<String, Object> row = jdbc.queryForMap("SELECT status, refund_kind, refunded_by, refund_cancel_id FROM emoticon_purchases WHERE id = ?", used);
        assertThat(row).containsEntry("status", "REFUNDED").containsEntry("refund_kind", "EXCEPTION").containsEntry("refunded_by", salesOperator);
        assertThat(row.get("refund_cancel_id")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_cancels WHERE request_key = ? AND kind = 'REFUND' AND requested_by = ?", Integer.class,
                "EMOTICON_REFUND:" + used, salesOperator)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_acquisitions WHERE purchase_id = ? AND removed_at IS NULL", Integer.class, used)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'EMOTICON_SALE_REFUND' AND target_type = 'EMOTICON_PURCHASE' AND target_id = ?",
                Integer.class, Long.toString(used))).isEqualTo(1);
        // 회원 알림 — 운영자 예외 환불 한 번(두 번째 요청은 409 라 없다), 운영자 사유는 싣지 않는다(후속 결정 2)
        List<Map<String, Object>> notices = jdbc.queryForList("SELECT payload->>'kind' AS kind, payload->>'path' AS path, payload::text AS raw FROM member_notifications "
                + "WHERE user_id = ? AND type = 'EMOTICON_PURCHASE'", buyer);
        assertThat(notices).hasSize(1);
        assertThat(notices.get(0)).containsEntry("kind", "REFUNDED_EXCEPTION").containsEntry("path", "/me/robotikon");
        assertThat((String) notices.get(0).get("raw")).doesNotContain("두 번 결제됐다는 문의");
        mvc.perform(get("/api/admin/emoticons/sales").param("packId", Long.toString(pack)).param("status", "REFUNDED").cookie(sales))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.paidAmount").value(0));
    }
}
