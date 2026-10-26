package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberEventLockReason;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notificationtemplate.catalog.MessageTargetCatalog;
import com.community.notificationtemplate.catalog.MessageTargetType;
import com.community.permission.entity.Permission;
import com.community.pointshop.dto.SelfRefundOutcome;
import com.community.pointshop.dto.request.PointShopPurchaseQuery;
import com.community.pointshop.dto.response.MyItemListView;
import com.community.pointshop.dto.response.MyItemView;
import com.community.pointshop.dto.response.PointShopPurchaseRowView;
import com.community.pointshop.dto.response.PointShopRefundDecisionView;
import com.community.pointshop.entity.EquipSlot;
import com.community.pointshop.entity.PointShopRefundRequestStatus;
import com.community.pointshop.exception.NicknameTicketRequiredException;
import com.community.pointshop.exception.PointShopRefundNotAllowedException;
import com.community.pointshop.exception.PointShopRefundPendingException;
import com.community.pointshop.exception.PointShopRefundRejectedException;
import com.community.pointshop.exception.PointShopRefundRequestNotFoundException;
import com.community.pointshop.exception.PointShopRefundRequestNotPendingException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정지 중 환불 신청(계약 「1-2 정지 중 환불 신청」, V87) — 정지 중 셀프 환불 = 신청(포인트·아이템 그대로)·신청 순간 판정 고정·대기 중 장착·고르기·변경권·셀프 환불·재신청·예외 환불 409·
 * 대기 하나 유일·수락(기존 환불 길·환불 종류 REQUEST·감사 돈 관련·회원 알림)·거절(사유·다시 쓸 수 있음·정지 중 재신청 409·풀린 뒤 셀프 환불)·이미 결정됨 409·조건 밖 409·
 * 알림 종류 CHECK·운영자 이벤트·관리자 구매 내역의 신청 칸과 거르기. 실제 PostgreSQL.
 */
class PointShopRefundRequestTest extends PointShopTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static MyItemView row(MyItemListView list, long userItemId) {
        return list.items().stream().filter(item -> item.id().equals(Long.toString(userItemId))).findFirst().orElseThrow();
    }

    private long requestIdOf(SelfRefundOutcome outcome) {
        assertThat(outcome.isRequest()).isTrue();
        return Long.parseLong(outcome.requested().requestId());
    }

    private JsonNode lastNotice(long member, String type) {
        String payload = kit.jdbc.queryForObject("SELECT payload FROM member_notifications WHERE user_id = ? AND type = ? ORDER BY id DESC LIMIT 1", String.class, member, type);
        return JSON.readTree(payload);
    }

    @Test
    void 정지_중_셀프_환불은_신청이_되고_포인트와_아이템은_그대로다() {
        long item = kit.border(500, "GOLD", 3);
        long member = kit.newMember();
        kit.grant(member, 500);
        long userItem = kit.buyItem(member, item);
        kit.suspend(member);

        SelfRefundOutcome outcome = kit.selfRefund(member, userItem);

        assertThat(outcome.isRequest()).isTrue();
        assertThat(outcome.refunded()).isNull();
        assertThat(outcome.requested().status()).isEqualTo(PointShopRefundRequestStatus.PENDING);
        assertThat(outcome.requested().requestedAt()).isEqualTo(kit.clock.instant());
        assertThat(kit.total(member)).as("포인트 그대로").isZero();
        assertThat(kit.userItemRow(userItem)).containsEntry("status", "OWNED");
        assertThat(kit.purchaseRow(kit.purchaseOf(userItem))).containsEntry("status", "COMPLETED");
        assertThat(kit.stock(item)).isEqualTo(2);
        assertThat(kit.operatorNotices).containsExactly(AdminNotificationEvent.POINT_SHOP_REFUND_REQUESTED);
        Map<String, Object> request = kit.jdbc.queryForMap("SELECT * FROM point_shop_refund_requests WHERE id = ?", requestIdOf(outcome));
        assertThat(request).containsEntry("status", "PENDING").containsEntry("user_id", member).containsEntry("user_item_id", userItem)
                .containsEntry("purchase_id", kit.purchaseOf(userItem));

        MyItemListView list = kit.userItems.myItems(member);
        assertThat(list.refundNeedsApproval()).as("정지 중이라 [환불 신청]").isTrue();
        MyItemView view = row(list, userItem);
        assertThat(view.refundable()).as("대기 중엔 다시 누를 수 없다").isFalse();
        assertThat(view.refundRequest().status()).isEqualTo(PointShopRefundRequestStatus.PENDING);
        assertThat(view.refundRequest().id()).isEqualTo(outcome.requested().requestId());
        kit.assertInvariants(member);
    }

    @Test
    void 판정은_신청_순간으로_고정되어_기한이_지난_뒤_수락해도_원래_묶음으로_환불된다() {
        long item = kit.border(700, "SILVER", 4);
        long member = kit.newMember();
        kit.grant(member, 300);
        long paidLot = kit.chargePaid(member, 400);
        long userItem = kit.buyItem(member, item);
        kit.suspend(member);
        long requestId = requestIdOf(kit.selfRefund(member, userItem));
        Instant requested = kit.clock.instant();

        kit.clock.set(requested.plus(Duration.ofDays(30)));
        PointShopRefundDecisionView decision = kit.inTx(() -> kit.refunds.approveRequest(requestId, kit.operator, "10.0.0.9"));

        assertThat(decision.status()).isEqualTo(PointShopRefundRequestStatus.APPROVED);
        assertThat(decision.refundedPoints()).isEqualTo(700);
        assertThat(kit.balance(member)).containsExactly(300, 400, 0, 700);
        assertThat(kit.jdbc.queryForObject("SELECT remaining_amount FROM point_lots WHERE id = ?", Long.class, paidLot)).isEqualTo(400);
        assertThat(kit.stock(item)).as("재고 복원").isEqualTo(4);
        assertThat(kit.userItemRow(userItem)).containsEntry("status", "REVOKED");
        long purchase = kit.purchaseOf(userItem);
        assertThat(kit.purchaseRow(purchase)).containsEntry("status", "REFUNDED").containsEntry("refund_kind", "REQUEST").containsEntry("refunded_by_operator_id", kit.operator);
        assertThat(kit.purchaseRow(purchase).get("refund_reason")).isNull();
        assertThat(kit.jdbc.queryForMap("SELECT status, decided_by_operator_id FROM point_shop_refund_requests WHERE id = ?", requestId)).containsEntry("status", "APPROVED")
                .containsEntry("decided_by_operator_id", kit.operator);

        AuditEvent audit = kit.audits.stream().filter(e -> e.action().equals(AuditActions.POINT_SHOP_REFUND)).findFirst().orElseThrow();
        assertThat(audit.detail()).containsEntry("kind", "REQUEST").containsEntry("requestId", Long.toString(requestId)).containsEntry("refundedPoints", 700L);
        assertThat(audit.ip()).isEqualTo("10.0.0.9");
        assertThat(AuditRetention.classify(AuditActions.POINT_SHOP_REFUND)).isEqualTo(AuditRetentionClass.MONEY);

        JsonNode notice = lastNotice(member, MemberNotificationType.POINT_SHOP_REFUND_APPROVED.name());
        assertThat(notice.get("points").asLong()).isEqualTo(700);
        assertThat(notice.get("requestId").asString()).isEqualTo(Long.toString(requestId));
        assertThat(notice.get("itemName").asString()).startsWith("시험 ");

        PointShopPurchaseRowView adminRow = kit.purchases.adminPurchases(new PointShopPurchaseQuery(null, null, null, null, Long.toString(member), 0, 20, null)).items().get(0);
        assertThat(adminRow.refund().kind()).isEqualTo("REQUEST");
        assertThat(adminRow.refund().operatorName()).isNotNull();
        assertThat(adminRow.refundRequest().status()).isEqualTo(PointShopRefundRequestStatus.APPROVED);
        kit.assertInvariants(member);
    }

    @Test
    void 대기_중에는_다시_신청_셀프_환불_장착_예외_환불이_409_이고_정지가_풀려도_그렇다() {
        long item = kit.color(200, "#AB0000");
        long member = kit.newMember();
        kit.grant(member, 200);
        long userItem = kit.buyItem(member, item);
        kit.suspend(member);
        kit.selfRefund(member, userItem);

        assertThatThrownBy(() -> kit.selfRefund(member, userItem)).isInstanceOf(PointShopRefundPendingException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("POINT_SHOP_REFUND_PENDING");
        kit.unsuspend(member);
        assertThatThrownBy(() -> kit.selfRefund(member, userItem)).as("풀린 뒤 셀프 환불").isInstanceOf(PointShopRefundPendingException.class);
        assertThatThrownBy(() -> kit.inTx(() -> kit.userItems.equip(member, EquipSlot.NICKNAME_COLOR, userItem))).isInstanceOf(PointShopRefundPendingException.class);
        assertThatThrownBy(() -> kit.inTx(() -> kit.refunds.refundByOperator(kit.purchaseOf(userItem), "예외로 돌려 드려요", kit.operator, null)))
                .isInstanceOf(PointShopRefundPendingException.class);
        assertThat(kit.userItemRow(userItem).get("first_used_at")).as("막힌 장착은 사용이 아니다").isNull();
        assertThat(kit.total(member)).isZero();
        assertThat(kit.count("SELECT count(*) FROM point_shop_refund_requests WHERE purchase_id = ?", kit.purchaseOf(userItem))).isEqualTo(1);
    }

    @Test
    void 대기_중인_유료_아바타는_고를_수_없고_대기_중인_변경권은_쓰이지_않는다() {
        long member = kit.newMember();
        kit.grant(member, 5000);
        long avatar = kit.buyItem(member, kit.avatarItem("job-police-m"));
        long ticket = kit.buyItem(member, kit.seedTicket());
        kit.suspend(member);
        kit.selfRefund(member, avatar);
        kit.selfRefund(member, ticket);
        kit.unsuspend(member);

        assertThatThrownBy(() -> kit.inTx(() -> kit.profileAvatars.select(member, "job-police-m"))).isInstanceOf(PointShopRefundPendingException.class);
        assertThat(kit.profileAvatars.catalog(member).items().stream().filter(a -> a.id().equals("job-police-m")).findFirst().orElseThrow().refundable()).isFalse();
        assertThat(kit.tickets.count(member)).as("대기 중인 장은 세지 않는다").isZero();
        assertThatThrownBy(() -> kit.inTx(() -> kit.tickets.useOne(member))).isInstanceOf(NicknameTicketRequiredException.class);
        assertThat(kit.userItemRow(ticket)).containsEntry("status", "OWNED");
    }

    @Test
    void 같은_구매의_대기_신청은_하나뿐이다_동시_두_신청과_DB_색인() throws Exception {
        long item = kit.color(100, "#00AB00");
        long member = kit.newMember();
        kit.grant(member, 100);
        long userItem = kit.buyItem(member, item);
        kit.suspend(member);

        List<Callable<String>> jobs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            jobs.add(() -> {
                try {
                    return kit.selfRefund(member, userItem).isRequest() ? "REQUESTED" : "REFUNDED";
                } catch (PointShopRefundPendingException e) {
                    return e.getErrorCode();
                }
            });
        }
        assertThat(PointShopTestKit.runTogether(jobs)).containsExactlyInAnyOrder("REQUESTED", "POINT_SHOP_REFUND_PENDING");
        long purchase = kit.purchaseOf(userItem);
        assertThat(kit.count("SELECT count(*) FROM point_shop_refund_requests WHERE purchase_id = ? AND status = 'PENDING'", purchase)).isEqualTo(1);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO point_shop_refund_requests (id, purchase_id, user_item_id, user_id, status, requested_at) "
                + "VALUES (?, ?, ?, ?, 'PENDING', now())", kit.ids.nextId(), purchase, userItem, member)).isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_point_shop_refund_requests_pending");
    }

    @Test
    void 거절하면_아이템을_다시_쓸_수_있고_정지_중_재신청은_409_풀린_뒤_기한_안이면_셀프_환불된다() {
        long item = kit.color(300, "#0000AB");
        long member = kit.newMember();
        kit.grant(member, 300);
        long userItem = kit.buyItem(member, item);
        kit.suspend(member);
        long requestId = requestIdOf(kit.selfRefund(member, userItem));

        assertThatThrownBy(() -> kit.inTx(() -> kit.refunds.rejectRequest(requestId, " 짧음 ", kit.operator, null))).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((CommonException) e).getFields()).containsOnlyKeys("reason"));
        PointShopRefundDecisionView decision = kit.inTx(() -> kit.refunds.rejectRequest(requestId, "  사용 흔적이 확인됐어요  ", kit.operator, "10.0.0.2"));

        assertThat(decision.status()).isEqualTo(PointShopRefundRequestStatus.REJECTED);
        assertThat(decision.refundedPoints()).isZero();
        assertThat(kit.jdbc.queryForMap("SELECT status, reject_reason FROM point_shop_refund_requests WHERE id = ?", requestId)).containsEntry("status", "REJECTED")
                .containsEntry("reject_reason", "사용 흔적이 확인됐어요");
        assertThat(kit.userItemRow(userItem)).containsEntry("status", "OWNED");
        assertThat(kit.total(member)).isZero();
        AuditEvent audit = kit.audits.stream().filter(e -> e.action().equals(AuditActions.POINT_SHOP_REFUND_REJECT)).findFirst().orElseThrow();
        assertThat(audit.detail()).containsEntry("reason", "사용 흔적이 확인됐어요").containsEntry("requestId", Long.toString(requestId));
        assertThat(AuditRetention.classify(AuditActions.POINT_SHOP_REFUND_REJECT)).as("거절도 돈 관련").isEqualTo(AuditRetentionClass.MONEY);
        assertThat(lastNotice(member, MemberNotificationType.POINT_SHOP_REFUND_REJECTED.name()).get("reason").asString()).isEqualTo("사용 흔적이 확인됐어요");

        MyItemView view = row(kit.userItems.myItems(member), userItem);
        assertThat(view.refundRequest().status()).isEqualTo(PointShopRefundRequestStatus.REJECTED);
        assertThat(view.refundRequest().rejectReason()).isEqualTo("사용 흔적이 확인됐어요");
        assertThat(view.refundable()).as("정지 중 거절된 구매는 다시 못 누른다").isFalse();
        assertThatThrownBy(() -> kit.selfRefund(member, userItem)).isInstanceOf(PointShopRefundRejectedException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("POINT_SHOP_REFUND_REJECTED");

        kit.unsuspend(member);
        assertThat(row(kit.userItems.myItems(member), userItem).refundable()).isTrue();
        assertThat(kit.refundSelf(member, userItem)).as("풀린 뒤 기한 안 셀프 환불").isEqualTo(300);
        assertThat(kit.purchaseRow(kit.purchaseOf(userItem))).containsEntry("refund_kind", "SELF");
        kit.assertInvariants(member);
    }

    @Test
    void 거절된_아이템은_정지가_풀리면_장착할_수_있다() {
        long item = kit.color(100, "#ABAB00");
        long member = kit.newMember();
        kit.grant(member, 100);
        long userItem = kit.buyItem(member, item);
        kit.suspend(member);
        long requestId = requestIdOf(kit.selfRefund(member, userItem));
        kit.inTx(() -> kit.refunds.rejectRequest(requestId, "환불 조건이 아니에요", kit.operator, null));
        kit.unsuspend(member);

        assertThat(kit.inTx(() -> kit.userItems.equip(member, EquipSlot.NICKNAME_COLOR, userItem)).nicknameColor()).isNotNull();
    }

    @Test
    void 이미_결정된_신청은_다시_수락_거절할_수_없고_없는_신청은_404() {
        long item = kit.color(100, "#00ABAB");
        long member = kit.newMember();
        kit.grant(member, 200);
        long first = kit.buyItem(member, item);
        long second = kit.buyItem(member, kit.color(100, "#AB00AB"));
        kit.suspend(member);
        long approved = requestIdOf(kit.selfRefund(member, first));
        long rejected = requestIdOf(kit.selfRefund(member, second));
        kit.inTx(() -> kit.refunds.approveRequest(approved, kit.operator, null));
        kit.inTx(() -> kit.refunds.rejectRequest(rejected, "환불 조건이 아니에요", kit.operator, null));

        for (long id : new long[] {approved, rejected}) {
            assertThatThrownBy(() -> kit.inTx(() -> kit.refunds.approveRequest(id, kit.operator, null))).isInstanceOf(PointShopRefundRequestNotPendingException.class)
                    .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("POINT_SHOP_REFUND_REQUEST_NOT_PENDING");
            assertThatThrownBy(() -> kit.inTx(() -> kit.refunds.rejectRequest(id, "다시 거절해 봐요", kit.operator, null)))
                    .isInstanceOf(PointShopRefundRequestNotPendingException.class);
        }
        assertThatThrownBy(() -> kit.inTx(() -> kit.refunds.approveRequest(Long.MAX_VALUE, kit.operator, null))).isInstanceOf(PointShopRefundRequestNotFoundException.class);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'POINT_SHOP_REFUND'", member)).as("포인트는 한 번만").isEqualTo(1);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_shop_refund_requests SET status = 'PENDING', decided_at = NULL, decided_by_operator_id = NULL WHERE id = ?",
                approved)).hasMessageContaining("decision is final");
        kit.assertInvariants(member);
    }

    @Test
    void 조건_밖이면_정지_중에도_신청을_만들지_않고_지금처럼_409() {
        long member = kit.newMember();
        kit.grant(member, 200);
        long used = kit.buyItem(member, kit.color(100, "#123ABC"));
        kit.inTx(() -> kit.userItems.equip(member, EquipSlot.NICKNAME_COLOR, used));
        long late = kit.buyItem(member, kit.color(100, "#ABC123"));
        kit.suspend(member);

        assertThatThrownBy(() -> kit.selfRefund(member, used)).isInstanceOf(PointShopRefundNotAllowedException.class)
                .satisfies(e -> assertThat(((CommonException) e).getExtras()).containsEntry("blockedBy", "USED"));
        kit.clock.set(kit.clock.instant().plus(Duration.ofDays(8)));
        assertThatThrownBy(() -> kit.selfRefund(member, late)).isInstanceOf(PointShopRefundNotAllowedException.class)
                .satisfies(e -> assertThat(((CommonException) e).getExtras()).containsEntry("blockedBy", "EXPIRED"));
        assertThat(kit.count("SELECT count(*) FROM point_shop_refund_requests WHERE user_id = ?", member)).isZero();
        assertThat(kit.operatorNotices).isEmpty();
    }

    @Test
    void 관리자_구매_내역은_신청_대기를_거르고_줄에_신청_칸을_싣는다() {
        long member = kit.newMember();
        kit.grant(member, 200);
        long pending = kit.buyItem(member, kit.color(100, "#CC0011"));
        kit.buyItem(member, kit.color(100, "#CC0022"));
        kit.suspend(member);
        kit.selfRefund(member, pending);

        List<PointShopPurchaseRowView> rows = kit.purchases.adminPurchases(new PointShopPurchaseQuery(null, null, null, null, Long.toString(member), 0, 20,
                PointShopRefundRequestStatus.PENDING)).items();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).id()).isEqualTo(Long.toString(kit.purchaseOf(pending)));
        assertThat(rows.get(0).refundRequest().status()).isEqualTo(PointShopRefundRequestStatus.PENDING);
        assertThat(rows.get(0).refundRequest().requestedAt()).isNotNull();
        assertThat(kit.purchases.adminPurchases(new PointShopPurchaseQuery(null, null, null, null, Long.toString(member), 0, 20, null)).items()).hasSize(2)
                .anyMatch(row -> row.refundRequest() == null);
    }

    @Test
    void 신청_표의_CHECK_결정_칸_조합과_환불_종류_조합() {
        long member = kit.newMember();
        kit.grant(member, 100);
        long userItem = kit.buyItem(member, kit.color(100, "#DD0011"));
        long purchase = kit.purchaseOf(userItem);
        String insert = "INSERT INTO point_shop_refund_requests (id, purchase_id, user_item_id, user_id, status, requested_at, decided_at, decided_by_operator_id, reject_reason) "
                + "VALUES (?, ?, ?, ?, ?, now(), ?, ?, ?)";
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), purchase, userItem, member, "REJECTED", java.sql.Timestamp.from(Instant.now()), kit.operator,
                null)).as("거절은 사유 필수").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), purchase, userItem, member, "APPROVED", null, null, null)).as("결정은 결정 칸 필수")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), purchase, userItem, member, "LOST", null, null, null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_shop_purchases SET status = 'REFUNDED', refunded_at = now(), refund_ledger_id = 1, refund_kind = 'REQUEST' "
                + "WHERE id = ?", purchase)).as("신청 수락은 처리 운영자 필수").isInstanceOf(DataAccessException.class);
    }

    @Test
    void 알림_종류와_운영자_이벤트가_등록되어_있고_문구가_두_언어에_있다() throws IOException {
        assertThat(MemberEvent.of(MemberNotificationType.POINT_SHOP_REFUND_APPROVED)).isEqualTo(MemberEvent.POINT_SHOP_REFUND_APPROVED);
        assertThat(MemberEvent.POINT_SHOP_REFUND_REJECTED.lockReason()).isEqualTo(MemberEventLockReason.ACTION);
        assertThat(MemberEvent.POINT_SHOP_REFUND_APPROVED.featureReady()).isTrue();
        assertThat(MessageTargetCatalog.find(MessageTargetType.EVENT, "POINT_SHOP_REFUND_APPROVED")).get()
                .satisfies(target -> assertThat(target.inApp().argNames()).containsExactly("itemName", "amount"));
        assertThat(MessageTargetCatalog.find(MessageTargetType.EVENT, "POINT_SHOP_REFUND_REJECTED")).isPresent();
        AdminNotificationEvent event = AdminNotificationEvent.POINT_SHOP_REFUND_REQUESTED;
        assertThat(event.featureReady()).isTrue();
        assertThat(event.recipientPermissions()).containsExactly(Permission.POINTSHOP_REFUND);
        assertThat(event.linkPath()).isEqualTo("/point-shop/purchases?refundRequest=PENDING");
        assertThat(MessageTargetCatalog.find(MessageTargetType.EVENT, "POINT_SHOP_REFUND_REQUESTED")).isPresent();

        List<String> keys = new ArrayList<>(List.of(event.nameKey(), event.descriptionKey(), event.recipientsKey(), event.titleKey(),
                "notification.event.POINT_SHOP_REFUND_REQUESTED.body", "member.notification.POINT_SHOP_REFUND_APPROVED.title",
                "member.notification.POINT_SHOP_REFUND_APPROVED.body", "member.notification.POINT_SHOP_REFUND_REJECTED.title", "audit.action.POINT_SHOP_REFUND_REJECT",
                "error.POINT_SHOP_REFUND_PENDING", "error.POINT_SHOP_REFUND_REJECTED", "error.POINT_SHOP_REFUND_REQUEST_NOT_PENDING",
                "error.POINT_SHOP_REFUND_REQUEST_NOT_FOUND"));
        keys.addAll(MemberEvent.POINT_SHOP_REFUND_APPROVED.bundleKeys());
        keys.addAll(MemberEvent.POINT_SHOP_REFUND_REJECTED.bundleKeys());
        for (String variable : List.of("refundItemName", "refundAmount", "refundRejectReason")) {
            keys.add("notificationTemplate.var." + variable + ".desc");
            keys.add("notificationTemplate.var." + variable + ".sample");
        }
        for (String language : List.of("ko", "en")) {
            Properties messages = new Properties();
            try (InputStream in = getClass().getResourceAsStream("/messages/messages_" + language + ".properties")) {
                messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            for (String key : keys) {
                assertThat(messages.getProperty(key)).as(language + " " + key).isNotBlank();
            }
        }
        // 회원 알림 종류 CHECK(V87 합집합) — 새 두 종류가 들어가고 옛 종류도 그대로
        String check = kit.jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_member_notifications_type'", String.class);
        for (MemberNotificationType type : MemberNotificationType.values()) {
            assertThat(check).as(type.name()).contains("'" + type.name() + "'");
        }
    }
}
