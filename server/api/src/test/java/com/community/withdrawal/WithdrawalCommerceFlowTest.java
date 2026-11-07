package com.community.withdrawal;


import static com.community.withdrawal.WithdrawalTestSupport.PASSWORD;
import static com.community.withdrawal.WithdrawalTestSupport.groupBuy;
import static com.community.withdrawal.WithdrawalTestSupport.orderNo;
import static com.community.withdrawal.WithdrawalTestSupport.participation;
import static com.community.withdrawal.WithdrawalTestSupport.plain;
import static com.community.withdrawal.WithdrawalTestSupport.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.community.block.exception.MemberProfileNotFoundException;
import com.community.block.service.BlockService;
import com.community.block.service.MemoService;
import com.community.commerce.CommerceApiTestBase;
import com.community.dm.exception.DmSendBlockedException;
import com.community.dm.service.DmService;
import com.community.groupbuy.service.MyGroupBuyOrderService;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.media.service.LocalDiskImageStorage;
import com.community.withdrawal.batch.WithdrawalAutoCompleteJob;
import com.community.withdrawal.dto.response.WithdrawalResult;
import com.community.withdrawal.dto.response.WithdrawalView;
import com.community.withdrawal.service.WithdrawalService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.community.auth.dto.AuthUser;
import com.community.commerce.support.MoneyRequestGuard;
import com.community.user.entity.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 회원 탈퇴 — 공구 주문·반품 요청·배송완료 7일·닉네임 이미지 파일·쪽지·차단·메모 규칙(commerce.md 17-1 · 17-2, 분석 문서 4절)을 실제 PostgreSQL 에서 확인한다.
 * 공구 행은 {@link WithdrawalTestSupport} 가 최소 시드로 만든다.
 */
class WithdrawalCommerceFlowTest extends CommerceApiTestBase {

    private static final String IP = "127.0.0.1";

    @Autowired
    private WithdrawalService service;
    @Autowired
    private WithdrawalAutoCompleteJob job;
    @Autowired
    private MyGroupBuyOrderService myOrders;
    @Autowired
    private DmService dm;
    @Autowired
    private BlockService blocks;
    @Autowired
    private MemoService memos;
    @Autowired
    private ImageStorage storage;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private DataCipher cipher;
    @Autowired
    private WebApplicationContext context;

    private long member() {
        long id = newMember();
        WithdrawalTestSupport.withPassword(jdbc, encoder, id, clock.instant());
        return id;
    }

    private String status(long userId) {
        return memberFixture.status(userId);
    }

    private long withdrawalId(long userId) {
        return jdbc.queryForObject("SELECT id FROM member_withdrawals WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, userId);
    }

    private WithdrawalAutoCompleteJob.Outcome process(long withdrawalId) {
        return tx.execute(s -> job.process(withdrawalId));
    }

    private Map<String, Object> participationRow(long id) {
        return jdbc.queryForMap("SELECT status, amount, recipient_enc, phone_enc, postcode, address1_enc, address2_enc, memo_enc, cash_receipt_no_enc, address_masked_at "
                + "FROM group_buy_participations WHERE id = ?", id);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    // ── 진행 중 공구 주문 ──────────────────────────────────────────────────────

    @Nested
    class 진행_중_공구_주문 {

        @ParameterizedTest
        @ValueSource(strings = {"PENDING_PAYMENT", "JOINED", "CONFIRMED", "PREPARING", "SHIPPED"})
        void 진행_중_상태마다_남은_일_GROUP_BUY_ORDER_로_예약된다(String state) {
            long member = member();
            long order = participation(jdbc, ids, cipher, groupBuy(jdbc, ids), member, state, null);

            WithdrawalView view = service.view(member);
            assertThat(view.pending()).extracting(WithdrawalView.PendingItem::code).containsExactly("GROUP_BUY_ORDER");
            assertThat(view.pending().get(0).label()).isEqualTo(orderNo(jdbc, order));
            assertThat(view.pending().get(0).link()).isEqualTo("/me/orders/" + orderNo(jdbc, order));

            WithdrawalResult result = service.request(member, plain(), IP);
            assertThat(result.state()).isEqualTo("SCHEDULED");
            assertThat(result.pending()).extracting(WithdrawalView.PendingItem::code).containsExactly("GROUP_BUY_ORDER");
            assertThat(status(member)).isEqualTo("WITHDRAWING");
            assertThat(process(withdrawalId(member))).isEqualTo(WithdrawalAutoCompleteJob.Outcome.WAITING);
        }

        @Test
        void 주문이_취소되면_배치가_완료한다() {
            long member = member();
            long order = participation(jdbc, ids, cipher, groupBuy(jdbc, ids), member, "JOINED", null);
            assertThat(service.request(member, plain(), IP).state()).isEqualTo("SCHEDULED");

            jdbc.update("UPDATE group_buy_participations SET status = 'CANCELED', canceled_at = now() WHERE id = ?", order);

            assertThat(process(withdrawalId(member))).isEqualTo(WithdrawalAutoCompleteJob.Outcome.COMPLETED);
            assertThat(status(member)).isEqualTo("WITHDRAWN");
            Map<String, Object> row = participationRow(order);
            assertThat(row.get("status")).isEqualTo("CANCELED");
            assertThat(((Number) row.get("amount")).longValue()).isEqualTo(13000); // 주문·금액·상태는 남는다
            assertThat(row.get("recipient_enc")).isNull(); // 완료 때 배송지 마스킹
            assertThat(row.get("address_masked_at")).isNotNull();
        }

        @Test
        void 배송완료_뒤_7일이_지나면_배치가_완료한다() {
            long member = member();
            long order = participation(jdbc, ids, cipher, groupBuy(jdbc, ids), member, "SHIPPED", null);
            assertThat(service.request(member, plain(), IP).state()).isEqualTo("SCHEDULED");
            long id = withdrawalId(member);

            // 배송완료 3일째 — 기본 선택(WAIT)이라 반품 기한을 기다린다
            // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
            jdbc.update("UPDATE group_buy_participations SET status = 'DELIVERED', delivered_at = ? WHERE id = ?", ts(clock.instant().minus(Duration.ofDays(3))), order);
            assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.WAITING);
            assertThat(service.view(member).scheduled().pending()).extracting(WithdrawalView.PendingItem::code).containsExactly("DELIVERED_RETURN_WINDOW");
            assertThat(service.view(member).scheduled().expectedCompleteAt()).isEqualTo(clock.instant().plus(Duration.ofDays(4)));

            clock.advance(Duration.ofDays(5));
            assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.COMPLETED);
            assertThat(status(member)).isEqualTo("WITHDRAWN");
        }
    }

    // ── 배송완료 7일 안 ────────────────────────────────────────────────────────

    @Nested
    class 배송완료_7일_안 {

        @Test
        void WAIT_면_DELIVERED_RETURN_WINDOW_로_예약되고_기한이_지나면_배치가_완료한다() {
            long member = member();
            Instant delivered = clock.instant().minus(Duration.ofDays(2));
            long order = participation(jdbc, ids, cipher, groupBuy(jdbc, ids), member, "DELIVERED", delivered);

            WithdrawalView view = service.view(member);
            assertThat(view.pending()).isEmpty(); // 선택에 달린 일이라 고정 목록에는 없다
            assertThat(view.delivered()).hasSize(1);
            assertThat(view.delivered().get(0).orderNo()).isEqualTo(orderNo(jdbc, order));
            assertThat(view.delivered().get(0).returnDeadline()).isEqualTo(delivered.plus(Duration.ofDays(7)));

            WithdrawalResult result = service.request(member, request(null, "WAIT", null, null, null), IP);
            assertThat(result.state()).isEqualTo("SCHEDULED");
            assertThat(result.pending()).extracting(WithdrawalView.PendingItem::code).containsExactly("DELIVERED_RETURN_WINDOW");
            assertThat(result.expectedCompleteAt()).isEqualTo(delivered.plus(Duration.ofDays(7)));
            assertThat(participationRow(order).get("recipient_enc")).isNotNull(); // 진행 중에는 배송지 그대로(반품 처리에 필요)

            long id = withdrawalId(member);
            assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.WAITING);
            clock.advance(Duration.ofDays(6));
            assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.COMPLETED);
            assertThat(status(member)).isEqualTo("WITHDRAWN");
            assertThat(participationRow(order).get("recipient_enc")).isNull();
        }

        @Test
        void P30d_NOW_면_즉시_완료되고_연락용_이메일을_분리_보관하며_배송지_연락처_현금영수증_암호문은_즉시_비운다() {
            long member = member();
            String email = memberFixture.email(member);
            long order = participation(jdbc, ids, cipher, groupBuy(jdbc, ids), member, "DELIVERED", clock.instant().minus(Duration.ofDays(3)));
            long paymentOrder = ids.nextId();
            // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
            jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, paid_amount, paid_at, confirmed_via, expires_at, "
                    + "created_at, updated_at) VALUES (?, ?, ?, 'GROUP_BUY', ?, '탈퇴 시험 공구', 13000, 'PAID', 13000, ?, 'COMPLETE_API', now(), now(), now())", paymentOrder,
                    "pay_" + String.format("%032d", paymentOrder), member, order, ts(clock.instant().minus(Duration.ofDays(10))));
            jdbc.update("UPDATE group_buy_participations SET payment_order_id = ? WHERE id = ?", paymentOrder, order);

            WithdrawalResult result = service.request(member, request(null, "NOW", null, null, null), IP);

            assertThat(result.state()).isEqualTo("COMPLETED");
            assertThat(result.hasPaymentHistory()).isTrue();
            assertThat(status(member)).isEqualTo("WITHDRAWN");
            Map<String, Object> contact = jdbc.queryForMap("SELECT email_cipher FROM withdrawn_member_contacts WHERE user_id = ?", member);
            assertThat(String.valueOf(contact.get("email_cipher"))).startsWith("d").doesNotContain(email);
            Map<String, Object> row = participationRow(order);
            for (String column : List.of("recipient_enc", "phone_enc", "postcode", "address1_enc", "address2_enc", "memo_enc", "cash_receipt_no_enc")) {
                assertThat(row.get(column)).as(column).isNull();
            }
            assertThat(row.get("address_masked_at")).isNotNull();
            assertThat(row.get("status")).isEqualTo("DELIVERED");
            assertThat(((Number) row.get("amount")).longValue()).isEqualTo(13000);
            assertThat(jdbc.queryForObject("SELECT status FROM payment_orders WHERE id = ?", String.class, paymentOrder)).isEqualTo("PAID"); // 거래 기록은 남는다
        }

        @Test
        void 배송완료_7일이_지난_주문은_선택_대상이_아니고_남은_일도_아니다() {
            long member = member();
            participation(jdbc, ids, cipher, groupBuy(jdbc, ids), member, "DELIVERED", clock.instant().minus(Duration.ofDays(8)));

            WithdrawalView view = service.view(member);
            assertThat(view.delivered()).isEmpty();
            assertThat(view.pending()).isEmpty();
            assertThat(service.request(member, request(null, "WAIT", null, null, null), IP).state()).isEqualTo("COMPLETED");
        }
    }

    // ── 열린 취소·반품 요청 ────────────────────────────────────────────────────

    @Nested
    class 열린_취소_반품_요청 {

        @ParameterizedTest
        @ValueSource(strings = {"AWAITING_FEE", "REQUESTED", "APPROVED", "RECEIVED"})
        void 열린_요청은_남은_일_RETURN_REQUEST_이고_끝나면_배치가_완료한다(String state) {
            long member = member();
            long order = participation(jdbc, ids, cipher, groupBuy(jdbc, ids), member, "DELIVERED", clock.instant().minus(Duration.ofDays(20)));
            long req = request(jdbc, ids, order, member, state);

            WithdrawalView view = service.view(member);
            assertThat(view.pending()).extracting(WithdrawalView.PendingItem::code).containsExactly("RETURN_REQUEST");
            assertThat(view.pending().get(0).label()).isEqualTo(orderNo(jdbc, order));
            assertThat(service.request(member, plain(), IP).state()).isEqualTo("SCHEDULED");
            long id = withdrawalId(member);
            assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.WAITING);

            jdbc.update("UPDATE group_buy_requests SET status = 'COMPLETED' WHERE id = ?", req);
            assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.COMPLETED);
            assertThat(status(member)).isEqualTo("WITHDRAWN");
        }

        @Test
        void 끝난_요청은_남은_일이_아니고_진행_중에도_주문_보기는_막히지_않는다() {
            long member = member();
            long order = participation(jdbc, ids, cipher, groupBuy(jdbc, ids), member, "SHIPPED", null);
            request(jdbc, ids, order, member, "COMPLETED");
            assertThat(service.view(member).pending()).extracting(WithdrawalView.PendingItem::code).containsExactly("GROUP_BUY_ORDER");
            assertThat(service.request(member, plain(), IP).state()).isEqualTo("SCHEDULED");
            assertThat(status(member)).isEqualTo("WITHDRAWING");

            assertThatCode(() -> myOrders.detail(member, orderNo(jdbc, order))).doesNotThrowAnyException();
            assertThat(myOrders.detail(member, orderNo(jdbc, order)).status()).isEqualTo("SHIPPED");
        }
    }

    // ── 닉네임 이미지 파일 ────────────────────────────────────────────────────

    @Nested
    class 닉네임_이미지_파일 {

        @Test
        void 완료_뒤_닉네임_이미지_파일이_디스크에서_사라지고_신청_줄이_지워진다() throws Exception {
            long member = member();
            String imageKey = "nickname-images/" + member + "/image.png";
            String stillKey = "nickname-images/" + member + "/still.png";
            storage.put(imageKey, "png".getBytes(StandardCharsets.UTF_8));
            storage.put(stillKey, "png".getBytes(StandardCharsets.UTF_8));
            // SQL 로 남김: 아바타·이미지 칸을 바로 두는 도메인 메서드가 없다
            jdbc.update("INSERT INTO nickname_image_requests (id, user_id, status, image_key, still_key, content_type, bytes, width, height, created_at) VALUES (?, ?, 'APPROVED', ?, ?, "
                    + "'image/png', 3, 1, 1, now())", ids.nextId(), member, imageKey, stillKey);
            jdbc.update("UPDATE profiles SET nickname_image = ? WHERE user_id = ?", imageKey, member);
            Path root = ((LocalDiskImageStorage) storage).root();
            assertThat(Files.exists(root.resolve(imageKey))).isTrue();

            assertThat(service.request(member, plain(), IP).state()).isEqualTo("COMPLETED");

            assertThat(Files.exists(root.resolve(imageKey))).isFalse();
            assertThat(Files.exists(root.resolve(stillKey))).isFalse();
            assertThat(count("SELECT count(*) FROM nickname_image_requests WHERE user_id = ?", member)).isZero();
            assertThat(jdbc.queryForObject("SELECT nickname_image FROM profiles WHERE user_id = ?", String.class, member)).isNull();
        }
    }

    // ── 쪽지·차단·메모 ───────────────────────────────────────────────────────

    @Nested
    class 쪽지_차단_메모 {

        /** 쪽지를 보낼 수 있는 회원(가입 8일, 인증) */
        private long sender() {
            long id = member();
            // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
            jdbc.update("UPDATE users SET created_at = now() - interval '8 days' WHERE id = ?", id);
            return id;
        }

        /** 두 사람 사이의 대화 하나(양쪽 쪽지함 + 메시지 한 통) — 대화 ID */
        private long conversation(long a, long b) {
            long thread = ids.nextId();
            long message = ids.nextId();
            jdbc.update("INSERT INTO dm_threads (id, user_low_id, user_high_id, low_sent, high_sent, last_message_id, last_message_at, created_at) VALUES (?, ?, ?, true, true, ?, now(), now())",
                    thread, Math.min(a, b), Math.max(a, b), message);
            jdbc.update("INSERT INTO dm_messages (id, thread_id, sender_id, body, created_at) VALUES (?, ?, ?, '안녕하세요', now())", message, thread, a);
            jdbc.update("INSERT INTO dm_boxes (thread_id, user_id, partner_id, last_message_id) VALUES (?, ?, ?, ?)", thread, a, b, message);
            jdbc.update("INSERT INTO dm_boxes (thread_id, user_id, partner_id, last_message_id) VALUES (?, ?, ?, ?)", thread, b, a, message);
            return thread;
        }

        @Test
        void 진행_중_회원에게는_보낼_수_없고_받는_사람_검색_차단_대상_찾기에서_빠진다() {
            long leaving = member();
            long other = sender();
            long transfer = ids.nextId();
            jdbc.update("INSERT INTO commerce_bank_transfers (id, source_kind, source_id, user_id, amount, status, reason, created_at) VALUES (?, 'GROUP_BUY_REFUND', ?, ?, 10000, "
                    + "'AWAITING_ACCOUNT', 'test', now())", transfer, transfer, leaving);
            long thread = conversation(other, leaving);
            assertThat(dm.canSend(other, Long.toString(leaving)).canSend()).isTrue();
            assertThat(dm.recipients(other, WithdrawalTestSupport.nick(leaving)).items()).isNotEmpty();

            assertThat(service.request(leaving, plain(), IP).state()).isEqualTo("SCHEDULED");

            assertThat(dm.canSend(other, Long.toString(leaving)).reason()).isEqualTo("RECIPIENT_WITHDRAWN");
            assertThatThrownBy(() -> dm.send(other, Long.toString(leaving), "다시 안녕"))
                    .isInstanceOf(DmSendBlockedException.class).hasFieldOrPropertyWithValue("errorCode", "DM_RECIPIENT_WITHDRAWN");
            assertThat(dm.room(other, Long.toString(thread), null, null).thread().reason()).isEqualTo("RECIPIENT_WITHDRAWN");
            assertThat(dm.recipients(other, WithdrawalTestSupport.nick(leaving)).items()).isEmpty();
            assertThatThrownBy(() -> blocks.block(other, Long.toString(leaving))).isInstanceOf(MemberProfileNotFoundException.class);
            assertThatThrownBy(() -> memos.save(other, Long.toString(leaving), "메모")).isInstanceOf(MemberProfileNotFoundException.class);
            // 진행 중인 회원 자신도 새 쪽지를 보낼 수 없다(새 활동 차단)
            assertThatThrownBy(() -> dm.send(leaving, Long.toString(other), "보내기")).isInstanceOf(DmSendBlockedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_RESTRICTED");
        }

        @Test
        void 완료_뒤_내_쪽지함은_지워지고_상대_쪽지함의_대화는_탈퇴한_회원으로_남으며_차단_메모는_양방향_삭제() {
            long leaving = member();
            long other = sender();
            long third = member();
            long thread = conversation(leaving, other);
            Instant now = clock.instant();
            jdbc.update("INSERT INTO user_blocks (id, user_id, target_id, created_at) VALUES (?, ?, ?, ?)", ids.nextId(), leaving, third, ts(now));
            jdbc.update("INSERT INTO user_blocks (id, user_id, target_id, created_at) VALUES (?, ?, ?, ?)", ids.nextId(), other, leaving, ts(now));
            jdbc.update("INSERT INTO user_blocks (id, user_id, target_id, created_at) VALUES (?, ?, ?, ?)", ids.nextId(), other, third, ts(now)); // 남의 것은 그대로
            jdbc.update("INSERT INTO user_memos (id, user_id, target_id, memo, created_at, updated_at) VALUES (?, ?, ?, '내가 쓴 메모', ?, ?)", ids.nextId(), leaving, third, ts(now), ts(now));
            jdbc.update("INSERT INTO user_memos (id, user_id, target_id, memo, created_at, updated_at) VALUES (?, ?, ?, '나에 대한 메모', ?, ?)", ids.nextId(), third, leaving, ts(now), ts(now));

            assertThat(service.request(leaving, plain(), IP).state()).isEqualTo("COMPLETED");

            assertThat(count("SELECT count(*) FROM dm_boxes WHERE user_id = ?", leaving)).isZero();
            assertThat(count("SELECT count(*) FROM dm_boxes WHERE user_id = ? AND thread_id = ?", other, thread)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM dm_messages WHERE thread_id = ?", thread)).isEqualTo(1); // 상대의 기록이라 남는다
            assertThat(dm.room(other, Long.toString(thread), null, null).thread().partner().withdrawn()).isTrue();
            assertThat(dm.room(other, Long.toString(thread), null, null).thread().reason()).isEqualTo("RECIPIENT_WITHDRAWN");
            assertThat(count("SELECT count(*) FROM user_blocks WHERE user_id = ? OR target_id = ?", leaving, leaving)).isZero();
            assertThat(count("SELECT count(*) FROM user_memos WHERE user_id = ? OR target_id = ?", leaving, leaving)).isZero();
            assertThat(count("SELECT count(*) FROM user_blocks WHERE user_id = ? AND target_id = ?", other, third)).isEqualTo(1);
        }
    }

    // ── 진행 중 회원의 새 반품 요청(실제 회원 HTTP 경로) ─────────────────────────────

    @Nested
    class 진행_중_반품_요청_새로_내기 {

        private MockMvc mvc() {
            return MockMvcBuilders.webAppContextSetup(context).build();
        }

        private MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
            AuthUser user = new AuthUser(memberId, Role.USER);
            return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).contentType(MediaType.APPLICATION_JSON)
                    .header(MoneyRequestGuard.HEADER, "wd-" + ids.nextId());
        }

        private static String returnBody(long sku, String reason, String method) {
            return "{\"type\":\"RETURN\",\"items\":[{\"skuId\":\"" + sku + "\",\"qty\":1,\"reason\":\"" + reason + "\"}],\"collectionMethod\":\"" + method + "\"}";
        }

        /** 송금 대기 환불로 탈퇴를 예약해 WITHDRAWING 으로 만든다 — 송금 ID(요청을 낸 뒤 {@link #finish} 하면 남은 일은 요청뿐) */
        private long scheduled(long member) {
            long transfer = ids.nextId();
            jdbc.update("INSERT INTO commerce_bank_transfers (id, source_kind, source_id, user_id, amount, status, reason, created_at) VALUES (?, 'GROUP_BUY_REFUND', ?, ?, 10000, "
                    + "'AWAITING_ACCOUNT', 'test', now())", transfer, transfer, member);
            assertThat(service.request(member, plain(), IP).state()).isEqualTo("SCHEDULED");
            assertThat(status(member)).isEqualTo("WITHDRAWING");
            return transfer;
        }

        private void finish(long transfer) {
            jdbc.update("UPDATE commerce_bank_transfers SET status = 'SENT', sent_at = now(), account_erased_at = now() WHERE id = ?", transfer);
        }

        @Test
        void 진행_중_회원이_배송완료_주문에_단순_변심_반품을_내면_성공하고_그_요청이_끝날_때까지_탈퇴가_기다린다() throws Exception {
            long member = member();
            // 배송완료 3일째 — 단순 변심 반품 기한(7일) 안. 「지금 탈퇴」를 고른 회원이라 배송완료 주문은 남은 일이 아니다
            long[] order = WithdrawalTestSupport.participationWithItem(jdbc, ids, cipher, groupBuy(jdbc, ids), member, "DELIVERED", clock.instant().minus(Duration.ofDays(3)));
            String orderNo = orderNo(jdbc, order[0]);
            long transfer = scheduled(member);
            long id = withdrawalId(member);
            assertThat(service.view(member).scheduled().pending()).extracting(WithdrawalView.PendingItem::code).containsExactlyInAnyOrder("DELIVERED_RETURN_WINDOW", "PAYMENT_CANCEL");

            mvc().perform(as(member, post("/api/me/orders/" + orderNo + "/requests")).content(returnBody(order[1], "CHANGE_OF_MIND", "CUSTOMER_SEND")))
                    .andExpect(MockMvcResultMatchers.status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.request.type").value("RETURN"))
                    .andExpect(jsonPath("$.data.request.status").value("REQUESTED"));
            finish(transfer);

            long requestId = jdbc.queryForObject("SELECT id FROM group_buy_requests WHERE participation_id = ?", Long.class, order[0]);
            WithdrawalView view = service.view(member);
            assertThat(view.scheduled().pending()).extracting(WithdrawalView.PendingItem::code).containsExactlyInAnyOrder("RETURN_REQUEST", "DELIVERED_RETURN_WINDOW");
            assertThat(view.scheduled().pending()).allMatch(p -> p.label().equals(orderNo));
            assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.WAITING);
            assertThat(status(member)).isEqualTo("WITHDRAWING");

            // 요청이 끝나고 반품 기한도 지나면 완료
            jdbc.update("UPDATE group_buy_requests SET status = 'COMPLETED' WHERE id = ?", requestId);
            clock.advance(Duration.ofDays(5));
            assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.COMPLETED);
            assertThat(status(member)).isEqualTo("WITHDRAWN");
        }

        @Test
        void 진행_중_회원도_배송완료_3개월_안_하자_반품을_낼_수_있고_단순_변심은_7일이_지나_409() throws Exception {
            long member = member();
            long[] order = WithdrawalTestSupport.participationWithItem(jdbc, ids, cipher, groupBuy(jdbc, ids), member, "DELIVERED", clock.instant().minus(Duration.ofDays(60)));
            String orderNo = orderNo(jdbc, order[0]);
            long transfer = scheduled(member);

            mvc().perform(as(member, post("/api/me/orders/" + orderNo + "/requests")).content(returnBody(order[1], "CHANGE_OF_MIND", "COMPANY_PICKUP")))
                    .andExpect(MockMvcResultMatchers.status().isConflict()).andExpect(jsonPath("$.error.code").value("GROUP_BUY_RETURN_WINDOW_CLOSED"));
            mvc().perform(as(member, post("/api/me/orders/" + orderNo + "/requests")).content(returnBody(order[1], "DEFECT", "COMPANY_PICKUP")))
                    .andExpect(MockMvcResultMatchers.status().isOk()).andExpect(jsonPath("$.data.request.status").value("REQUESTED"));
            finish(transfer);

            long requestId = jdbc.queryForObject("SELECT id FROM group_buy_requests WHERE participation_id = ?", Long.class, order[0]);
            assertThat(jdbc.queryForObject("SELECT seller_fault FROM group_buy_requests WHERE id = ?", Boolean.class, requestId)).isTrue();
            long id = withdrawalId(member);
            assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.WAITING);
            jdbc.update("UPDATE group_buy_requests SET status = 'COMPLETED' WHERE id = ?", requestId);
            assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.COMPLETED);
            assertThat(status(member)).isEqualTo("WITHDRAWN");
        }
    }
}
