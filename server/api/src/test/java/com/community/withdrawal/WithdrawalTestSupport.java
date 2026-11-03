package com.community.withdrawal;

import com.community.groupbuy.service.GroupBuyJoinService;
import com.community.withdrawal.dto.request.WithdrawalRequest;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 탈퇴 시험의 시드 도우미(시험 전용 — 운영 코드에 시험 코드를 넣지 않는다). 공구·상품·차수는 실제 공구 생성 흐름 대신 최소 행만 SQL 로 넣는다(남은 일 판정·마스킹·배송 기한 규칙은
 * 참여 행과 공구 행만 본다). 여러 시험 클래스(공구·사업자·핫딜)가 같이 쓴다.
 */
public final class WithdrawalTestSupport {

    public static final String PASSWORD = "Withdraw1!pass";
    /** DataCipher 암호문 모양(d<버전>:)만 흉내 낸 값 — 마스킹 시험은 값이 비는지만 본다 */
    public static final String CIPHER = "d1:AAAA";

    private WithdrawalTestSupport() {
    }

    /** 이미 있는 회원을 비밀번호·인증 메일·방금 로그인으로 만든다. 프로필이 없으면 만든다 */
    public static void withPassword(JdbcTemplate jdbc, PasswordEncoder encoder, long userId, Instant lastLogin) {
        jdbc.update("UPDATE users SET password_hash = ?, email_verified_at = coalesce(email_verified_at, now()), last_login_at = ? WHERE id = ?", encoder.encode(PASSWORD),
                Timestamp.from(lastLogin), userId);
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?) ON CONFLICT (user_id) DO NOTHING", userId, nick(userId));
    }

    public static String nick(long id) {
        return "w" + Long.toString(id % 100_000_000_000L, 36);
    }

    /** 비밀번호만 넣은 평범한 신청 */
    public static WithdrawalRequest plain() {
        return new WithdrawalRequest(PASSWORD, null, null, null, null, null, null, null);
    }

    public static WithdrawalRequest request(String paidChoice, String deliveredChoice, String bizChoice, List<WithdrawalRequest.GroupChoice> groups,
                                            WithdrawalRequest.ForfeitConsent consent) {
        return new WithdrawalRequest(PASSWORD, null, null, paidChoice, deliveredChoice, bizChoice, groups, consent);
    }

    /** 최소 공구 하나(분류 → 상품 → 모집 중 공구) — 공구 ID */
    public static long groupBuy(JdbcTemplate jdbc, SnowflakeIdGenerator ids) {
        long category = ids.nextId();
        jdbc.update("INSERT INTO group_buy_categories (id, name, created_at, updated_at) VALUES (?, ?, now(), now())", category, "탈퇴" + Long.toString(category, 36).substring(4));
        long product = ids.nextId();
        jdbc.update("INSERT INTO group_buy_products (id, category_id, name, created_at, updated_at) VALUES (?, ?, '탈퇴 시험 상품', now(), now())", product, category);
        long groupBuy = ids.nextId();
        jdbc.update("INSERT INTO group_buys (id, product_id, title, group_price, shipping_policy_code, shipping_fee, return_shipping_fee, return_fee_mode, max_qty, "
                + "initial_max_qty, start_at, end_at, fulfillment, ship_within_business_days, status, published_at, revision_no, round_root_id, created_at, updated_at) "
                + "VALUES (?, ?, '탈퇴 시험 공구', 10000, 'TEST', 3000, 3000, 'ONE_WAY', 100, 100, now() - interval '1 day', now() + interval '30 days', 'IMMEDIATE', 3, "
                + "'RECRUITING', now() - interval '1 day', 1, ?, now(), now())", groupBuy, product, groupBuy);
        return groupBuy;
    }

    /** 주문(참여) 한 건 — 배송지·연락처는 실제 데이터 키 암호문(주문 보기가 복호화한다), 현금영수증 번호는 모양만 흉내 낸 값. {@code deliveredAt} 은 DELIVERED 일 때만 */
    public static long participation(JdbcTemplate jdbc, SnowflakeIdGenerator ids, DataCipher cipher, long groupBuyId, long userId, String status, Instant deliveredAt) {
        long id = ids.nextId();
        String orderNo = "G20261103" + String.format("%05d", Math.floorMod(id, 100_000)) + Long.toString(Math.floorMod(id / 100_000, 100_000));
        jdbc.update("INSERT INTO group_buy_participations (id, order_no, group_buy_id, user_id, status, items_amount, shipping_fee, amount, cash_amount, group_buy_revision, "
                + "reserved_until, recipient_enc, phone_enc, postcode, address1_enc, address2_enc, memo_enc, cash_receipt_type, cash_receipt_no_enc, delivered_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, 10000, 3000, 13000, 13000, 1, now() + interval '5 minutes', ?, ?, '06236', ?, ?, ?, 'PERSONAL', ?, ?, now(), now())",
                id, orderNo, groupBuyId, userId, status, cipher.encrypt(GroupBuyJoinService.RECIPIENT, "받는이"), cipher.encrypt(GroupBuyJoinService.PHONE, "01012345678"),
                cipher.encrypt(GroupBuyJoinService.ADDRESS1, "서울 강남구 테헤란로 1"), cipher.encrypt(GroupBuyJoinService.ADDRESS2, "101호"), cipher.encrypt(GroupBuyJoinService.MEMO, "문 앞"),
                CIPHER, deliveredAt == null ? null : Timestamp.from(deliveredAt));
        return id;
    }

    public static String orderNo(JdbcTemplate jdbc, long participationId) {
        return jdbc.queryForObject("SELECT order_no FROM group_buy_participations WHERE id = ?", String.class, participationId);
    }

    /** 취소·반품 요청 한 건 — {@code status} 는 AWAITING_FEE·REQUESTED·APPROVED·RECEIVED·COMPLETED 등. AWAITING_FEE 는 반품(수수료·마감) */
    public static long request(JdbcTemplate jdbc, SnowflakeIdGenerator ids, long participationId, long userId, String status) {
        long id = ids.nextId();
        long groupBuyId = jdbc.queryForObject("SELECT group_buy_id FROM group_buy_participations WHERE id = ?", Long.class, participationId);
        boolean returning = !"REQUESTED".equals(status);
        jdbc.update("INSERT INTO group_buy_requests (id, participation_id, group_buy_id, user_id, type, status, preview_amount, collection_method, fee_amount, closes_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 10000, ?, ?, ?, now(), now())", id, participationId, groupBuyId, userId, returning ? "RETURN" : "CANCEL", status,
                returning ? "COMPANY_PICKUP" : null, "AWAITING_FEE".equals(status) ? 3000 : 0, "AWAITING_FEE".equals(status) ? Timestamp.from(Instant.now().plusSeconds(86_400)) : null);
        return id;
    }

    /**
     * 주문 줄까지 있는 주문 — 상품 단품 → 공구 단품(MAIN) → 참여 줄(1개, 10,000원). 반품·취소 요청 생성({@code GroupBuyRequestService.create})이 읽는 재료.
     *
     * @return {참여 ID, 공구 단품(sku) ID}
     */
    public static long[] participationWithItem(JdbcTemplate jdbc, SnowflakeIdGenerator ids, DataCipher cipher, long groupBuyId, long userId, String status,
                                               Instant deliveredAt) {
        long participationId = participation(jdbc, ids, cipher, groupBuyId, userId, status, deliveredAt);
        long productId = jdbc.queryForObject("SELECT product_id FROM group_buys WHERE id = ?", Long.class, groupBuyId);
        long productSku = ids.nextId();
        jdbc.update("INSERT INTO group_buy_product_skus (id, product_id, name) VALUES (?, ?, '기본')", productSku, productId);
        long sku = ids.nextId();
        jdbc.update("INSERT INTO group_buy_skus (id, group_buy_id, kind, product_sku_id, name, extra_price) VALUES (?, ?, 'MAIN', ?, '기본', 0)", sku, groupBuyId, productSku);
        jdbc.update("INSERT INTO group_buy_participation_items (id, participation_id, group_buy_id, sku_id, qty, unit_price, item_amount) VALUES (?, ?, ?, ?, 1, 10000, 10000)",
                ids.nextId(), participationId, groupBuyId, sku);
        return new long[] {participationId, sku};
    }
}
