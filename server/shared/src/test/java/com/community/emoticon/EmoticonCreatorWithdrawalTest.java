package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonSettlementConfirmRequest;
import com.community.emoticon.dto.request.EmoticonSettlementPaidRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.dto.response.EmoticonSettlementView;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonNotOwnedException;
import com.community.emoticon.exception.EmoticonPackNotFoundException;
import com.community.emoticon.handler.EmoticonPackPaymentHandler;
import com.community.emoticon.repository.master.EmoticonPurchaseStore;
import com.community.emoticon.service.EmoticonCreatorWithdrawal;
import com.community.emoticon.service.EmoticonSettlementMath;
import com.community.holiday.service.BusinessDayCalendar;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentOrderType;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataAccessException;

/**
 * 후속 결정 6(사용자 결정 2026-11-05 「탈퇴 작가의 팩은 새 판매만 멈춰」) — 실제 PostgreSQL 위에서 탈퇴 완료가 부르는 {@link EmoticonCreatorWithdrawal} 과 그 뒤의 규칙을 본다.
 * 판매 멈춤(판매 중·운영자 판매 중단 → 작가 탈퇴 판매 중단, 대기 심사 건 반려로 닫기, 다른 작가는 그대로, 감사 시스템, 회원 알림 없음) · 가진 사람은 계속 씀(토큰·카탈로그·내 로보티콘·상세) ·
 * 되살리기 막기(다시 판매·가격 바꾸기·새 판 승인 409 · DB 그물) · 결제창 주문은 「판매 중 아님」 거절 · 남은 정산 한 번에 지급과 그 뒤 파기 배치.
 * (탈퇴 API 를 지나는 흐름 — 같은 트랜잭션 · 결제 준비 409 · PG 자동 취소 · 셀프 환불 — 은 api {@code EmoticonPurchaseHttpTest})
 */
class EmoticonCreatorWithdrawalTest extends EmoticonTestBase {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int PRICE = 2500;
    private static final String IP = "127.0.0.1";

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private static String code(Executable call) {
        try {
            call.execute();
        } catch (CommonException e) {
            return e.getErrorCode();
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("오류가 나지 않았다");
    }

    private EmoticonCreatorWithdrawal.Result withdraw(long creator) {
        return kit.inTx(() -> kit.creatorWithdrawal.withdraw(creator));
    }

    /** 탈퇴 완료의 회원 쪽(익명화 상태 · 완료 줄 — 파기 배치가 보는 값) */
    private void markWithdrawn(long user, Instant at) {
        kit.jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", user);
        kit.jdbc.update("INSERT INTO member_withdrawals (id, user_id, status, requested_at, completed_at) VALUES (?, ?, 'COMPLETED', ?, ?)", kit.ids.nextId(), user,
                Timestamp.from(at), Timestamp.from(at));
    }

    private Map<String, Object> pack(long id) {
        return kit.jdbc.queryForMap("SELECT status, suspend_kind, suspend_reason, suspended_by, price FROM emoticon_packs WHERE id = ?", id);
    }

    private long review(long packId) {
        return kit.jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ? ORDER BY submitted_at DESC, id DESC LIMIT 1", Long.class, packId);
    }

    private long approvedPaid(long creator) {
        long pack = kit.approvedPack(creator);
        kit.jdbc.update("UPDATE emoticon_packs SET price = ? WHERE id = ?", PRICE, pack);
        return pack;
    }

    private long submitUpdate(long creator, long pack) {
        kit.inTx(() -> kit.studio.submit(creator, Long.toString(pack), EmoticonTestKit.body("새 판", kit.uploadCover(creator), kit.uploads(creator, 8))));
        return kit.pendingReview(pack);
    }

    private int notices(long user) {
        return kit.count("SELECT count(*) FROM member_notifications WHERE user_id = ?", user);
    }

    private List<AuditEvent> audits(String action) {
        return kit.audits.stream().filter(e -> e.action().equals(action)).toList();
    }

    /** 결제 대기 구매(결제 준비가 만드는 줄) — 구매 ID */
    private long pending(long user, long pack) {
        long id = kit.ids.nextId();
        Instant now = kit.clock.instant();
        Long creator = kit.jdbc.queryForObject("SELECT creator_id FROM emoticon_packs WHERE id = ?", Long.class, pack);
        kit.purchases.insert(new EmoticonPurchaseStore.NewPurchase(id, user, pack, creator, "산 때 이름", PRICE, now, now.plus(Duration.ofMinutes(30)), now));
        return id;
    }

    private PaymentDecision onPaid(long user, long purchase) {
        long order = kit.ids.nextId();
        Instant now = kit.clock.instant();
        kit.jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, expires_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '시험', ?, 'READY', ?, ?, ?)", order, "pay_" + String.format("%032d", order).substring(0, 32), user, purchase, PRICE,
                Timestamp.from(now.plus(Duration.ofMinutes(30))), Timestamp.from(now), Timestamp.from(now));
        return kit.inTx(() -> kit.handler.onPaid(new PaidPayment(order, "pay_x", user, PaymentOrderType.ROBOTIKON_PACK, purchase, PRICE, now, ConfirmedVia.WEBHOOK)));
    }

    private String purchaseStatus(long purchase) {
        return kit.jdbc.queryForObject("SELECT status FROM emoticon_purchases WHERE id = ?", String.class, purchase);
    }

    // ── 1. 판매 멈춤 ────────────────────────────────────────────────────────

    @Test
    void 탈퇴하면_그_작가의_판매_중_팩과_운영자_판매_중단_팩은_작가_탈퇴_판매_중단이_되고_대기_심사는_반려로_닫히며_다른_작가는_그대로() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long free = kit.approvedPack(creator);
            long paid = approvedPaid(creator);
            long operatorStopped = kit.approvedPack(creator);
            kit.inTx(() -> kit.packs.suspend(kit.operator, IP, Long.toString(operatorStopped), "운영 판단으로 멈춤"));
            long firstPending = Long.parseLong(kit.submitNew(creator, "대기 팩").id());
            long withUpdate = kit.approvedPack(creator);
            long updateReview = submitUpdate(creator, withUpdate);
            long firstReview = kit.pendingReview(firstPending);
            long otherCreator = kit.member();
            long otherPack = kit.approvedPack(otherCreator);
            int noticesBefore = notices(creator);
            kit.audits.clear();

            EmoticonCreatorWithdrawal.Result result = withdraw(creator);

            assertThat(result.stoppedPacks()).isEqualTo(4);
            assertThat(result.closedReviews()).isEqualTo(2);
            for (long id : new long[] {free, paid, operatorStopped, withUpdate}) {
                Map<String, Object> row = pack(id);
                assertThat(row.get("status")).as("팩 " + id).isEqualTo("SUSPENDED");
                assertThat(row.get("suspend_kind")).as("팩 " + id).isEqualTo("CREATOR_WITHDRAWN");
                assertThat(row.get("suspend_reason")).isEqualTo(EmoticonCreatorWithdrawal.PACK_REASON);
                assertThat(row.get("suspended_by")).as("시스템 처리").isNull();
            }
            assertThat(pack(paid).get("price")).as("가격은 그대로(가진 사람의 금액·정산 기록은 구매가 갖는다)").isEqualTo(PRICE);
            // 대기 심사 건 — 반려로 닫고(처리한 운영자 없음), 처음 신청이면 팩도 반려(상점에 나오지 않는다)
            for (long id : new long[] {firstReview, updateReview}) {
                Map<String, Object> r = kit.jdbc.queryForMap("SELECT status, decided_by, reason, system_cause FROM emoticon_reviews WHERE id = ?", id);
                assertThat(r.get("status")).isEqualTo("REJECTED");
                assertThat(r.get("decided_by")).isNull();
                assertThat(r.get("reason")).isEqualTo(EmoticonCreatorWithdrawal.REVIEW_REASON);
                assertThat(r.get("system_cause")).isEqualTo("CREATOR_WITHDRAWN");
            }
            assertThat(kit.status(firstPending)).isEqualTo("REJECTED");
            EmoticonAdminView.Decision decision = kit.reviews.detail(Long.toString(updateReview)).decision();
            assertThat(decision.cause()).isEqualTo("CREATOR_WITHDRAWN");
            assertThat(decision.decidedBy()).isNull();
            // 다른 작가의 팩은 영향 없음
            assertThat(pack(otherPack).get("status")).isEqualTo("APPROVED");
            assertThat(pack(otherPack).get("suspend_kind")).isNull();
            // 새로 받기는 판매 중단과 같은 409(사는 사람에게는 까닭을 가르지 않는다)
            long newcomer = kit.member();
            assertThat(code(() -> kit.inTx(() -> kit.acquire.acquire(newcomer, Long.toString(free))))).isEqualTo(EmoticonConflictException.PACK_SUSPENDED);
            assertThatCode(() -> kit.inTx(() -> kit.acquire.acquire(newcomer, Long.toString(otherPack)))).doesNotThrowAnyException();
            // 감사(시스템) — 팩마다 하나, 운영자 판매 중단이었던 팩은 그 종류·사유를 싣는다 · 닫은 심사 건은 시스템 반려
            List<AuditEvent> stopped = audits(AuditActions.EMOTICON_PACK_CREATOR_WITHDRAWN);
            assertThat(stopped).hasSize(4).allSatisfy(e -> {
                assertThat(e.actorType()).isEqualTo(AuditActorType.SYSTEM);
                assertThat(e.actorId()).isNull();
                assertThat(e.targetType()).isEqualTo("EMOTICON_PACK");
                assertThat(e.detail()).containsEntry("creator", Long.toString(creator));
            });
            AuditEvent wasOperator = stopped.stream().filter(e -> e.targetId().equals(Long.toString(operatorStopped))).findFirst().orElseThrow();
            assertThat(wasOperator.detail()).containsEntry("from", "SUSPENDED").containsEntry("fromKind", "OPERATOR").containsEntry("fromReason", "운영 판단으로 멈춤");
            assertThat(audits(AuditActions.EMOTICON_REVIEW_REJECT)).hasSize(2)
                    .allSatisfy(e -> assertThat(e.actorType()).isEqualTo(AuditActorType.SYSTEM))
                    .allSatisfy(e -> assertThat(e.detail()).containsEntry("cause", "CREATOR_WITHDRAWN"));
            // 회원 알림은 보내지 않는다(탈퇴한 사람)
            assertThat(notices(creator)).isEqualTo(noticesBefore);
            // 다시 불러도 같다
            assertThat(withdraw(creator)).isEqualTo(new EmoticonCreatorWithdrawal.Result(0, 0));
        });
    }

    @Test
    void 관리자_목록_상세는_작가_탈퇴_판매_중단을_가르고_다시_판매_단추를_감출_칸을_준다_운영_판단의_판매_중단은_그대로() {
        long creator = kit.member();
        long stopped = kit.approvedPack(creator);
        long other = kit.approvedPack(kit.member());
        withdraw(creator);

        EmoticonAdminView.PackDetail d = kit.packs.detail(Long.toString(stopped));
        assertThat(d.status()).isEqualTo("SUSPENDED");
        assertThat(d.suspendKind()).isEqualTo("CREATOR_WITHDRAWN");
        assertThat(d.resumable()).isFalse();
        assertThat(d.suspension().reason()).isEqualTo(EmoticonCreatorWithdrawal.PACK_REASON);
        assertThat(d.suspension().by()).as("시스템").isNull();
        EmoticonAdminView.PackRow row = kit.packs.list("CREATOR", "SUSPENDED", null, Long.toString(creator)).items().stream()
                .filter(r -> r.id().equals(Long.toString(stopped))).findFirst().orElseThrow();
        assertThat(row.suspendKind()).isEqualTo("CREATOR_WITHDRAWN");
        // 운영 판단의 판매 중단은 된다 — 종류 OPERATOR · 다시 판매 단추가 있다 · 다시 판매하면 칸이 빈다
        EmoticonAdminView.PackDetail op = kit.inTx(() -> kit.packs.suspend(kit.operator, IP, Long.toString(other), "운영 판단으로 멈춤"));
        assertThat(op.suspendKind()).isEqualTo("OPERATOR");
        assertThat(op.resumable()).isTrue();
        assertThat(op.suspension().by()).isNotNull();
        EmoticonAdminView.PackDetail back = kit.inTx(() -> kit.packs.resume(kit.operator, IP, Long.toString(other)));
        assertThat(back.status()).isEqualTo("APPROVED");
        assertThat(back.suspendKind()).isNull();
        assertThat(back.resumable()).isFalse();
        // 작가 탈퇴로 멈춘 팩은 이미 판매 중단이라 운영자 판매 중단은 기존 409
        assertThat(code(() -> kit.inTx(() -> kit.packs.suspend(kit.operator, IP, Long.toString(stopped), "다시 멈춰 봅니다")))).isEqualTo(EmoticonConflictException.PACK_NOT_ON_SALE);
    }

    // ── 2. 이미 가진 회원은 계속 씀 ─────────────────────────────────────────

    @Test
    void 이미_받았거나_산_회원은_토큰을_계속_쓰고_카탈로그_내_로보티콘_상세에_남으며_가지지_않은_회원은_쓰지_못한다() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long free = kit.approvedPack(creator);
            long paid = approvedPaid(creator);
            long holder = kit.member();
            kit.inTx(() -> kit.acquire.acquire(holder, Long.toString(free)));
            long buyer = kit.member();
            long purchase = pending(buyer, paid);
            assertThat(onPaid(buyer, purchase)).isEqualTo(PaymentDecision.accept());
            long stranger = kit.member();

            withdraw(creator);
            markWithdrawn(creator, kit.clock.instant());

            String freeToken = ":emo-" + kit.liveEmoticons(free).get(0) + ":";
            String paidToken = ":emo-" + kit.liveEmoticons(paid).get(0) + ":";
            assertThatCode(() -> kit.guard.requireOwned(holder, "받은 팩 " + freeToken)).doesNotThrowAnyException();
            assertThatCode(() -> kit.guard.requireOwned(buyer, "산 팩 " + paidToken)).doesNotThrowAnyException();
            assertThatThrownBy(() -> kit.guard.requireOwned(stranger, freeToken)).isInstanceOf(EmoticonNotOwnedException.class);
            // 카탈로그에 남는다(가진 사람의 글이 그림을 그린다)
            assertThat(kit.shop.catalog().catalog().items()).anySatisfy(i -> assertThat(i.packId()).isEqualTo(Long.toString(free)))
                    .anySatisfy(i -> assertThat(i.packId()).isEqualTo(Long.toString(paid)));
            // 내 로보티콘 — 판매 중단 표시로 남는다
            EmoticonPublicView.MyPack mine = kit.acquire.mine(buyer).packs().stream().filter(p -> p.id().equals(Long.toString(paid))).findFirst().orElseThrow();
            assertThat(mine.suspended()).isTrue();
            assertThat(mine.purchaseId()).isEqualTo(Long.toString(purchase));
            // 팩 상세 — 가진 사람에게만, 판매 중단의 종류를 싣는다
            EmoticonPublicView.PackDetail detail = kit.shop.pack(holder, Long.toString(free));
            assertThat(detail.saleState()).isEqualTo("SUSPENDED");
            assertThat(detail.suspendKind()).isEqualTo("CREATOR_WITHDRAWN");
            assertThat(detail.acquired()).isTrue();
            assertThatThrownBy(() -> kit.shop.pack(stranger, Long.toString(free))).isInstanceOf(EmoticonPackNotFoundException.class);
            assertThatThrownBy(() -> kit.shop.pack(null, Long.toString(paid))).isInstanceOf(EmoticonPackNotFoundException.class);
            // 판매 중 팩의 상세는 종류 칸이 비어 있다
            long onSale = kit.approvedPack(kit.member());
            assertThat(kit.shop.pack(null, Long.toString(onSale)).suspendKind()).isNull();
        });
    }

    // ── 3. 되살리기 막기 ────────────────────────────────────────────────────

    @Test
    void 작가_탈퇴로_멈춘_팩은_다시_판매_가격_바꾸기_새_판_승인이_409_이고_DB_도_되살리지_못한다() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long free = kit.approvedPack(creator);
            long paid = approvedPaid(creator);
            long updateReview = submitUpdate(creator, free);
            long firstPending = Long.parseLong(kit.submitNew(creator, "처음 신청").id());
            long firstReview = kit.pendingReview(firstPending);
            withdraw(creator);

            assertThat(code(() -> kit.inTx(() -> kit.packs.resume(kit.operator, IP, Long.toString(free))))).isEqualTo(EmoticonConflictException.PACK_CREATOR_WITHDRAWN);
            assertThat(code(() -> kit.inTx(() -> kit.packs.changePrice(kit.operator, IP, Long.toString(paid), new EmoticonReviewDecisionRequest(1500, "가격을 바꿔 봅니다")))))
                    .isEqualTo(EmoticonConflictException.PACK_CREATOR_WITHDRAWN);
            assertThat(code(() -> kit.inTx(() -> kit.packs.changePrice(kit.operator, IP, Long.toString(free), new EmoticonReviewDecisionRequest(1500, "가격을 바꿔 봅니다")))))
                    .isEqualTo(EmoticonConflictException.PACK_CREATOR_WITHDRAWN);
            // 닫힌 심사 건은 기존 409(이미 처리됨)
            assertThat(code(() -> kit.inTx(() -> kit.reviews.approve(kit.operator, IP, Long.toString(updateReview), new EmoticonReviewDecisionRequest(0, null)))))
                    .isEqualTo(EmoticonConflictException.REVIEW_NOT_PENDING);
            // 탈퇴와 엇갈려 대기로 남은 건(그물) — 새 판 승인 · 처음 신청 승인 모두 409, 팩은 그대로
            kit.jdbc.update("UPDATE emoticon_reviews SET status = 'PENDING', decided_at = NULL, reason = NULL, system_cause = NULL WHERE id IN (?, ?)", updateReview, firstReview);
            kit.jdbc.update("UPDATE emoticon_packs SET status = 'PENDING' WHERE id = ?", firstPending);
            markWithdrawn(creator, kit.clock.instant());
            try {
                assertThat(code(() -> kit.inTx(() -> kit.reviews.approve(kit.operator, IP, Long.toString(updateReview), new EmoticonReviewDecisionRequest(0, null)))))
                        .isEqualTo(EmoticonConflictException.PACK_CREATOR_WITHDRAWN);
                assertThat(code(() -> kit.inTx(() -> kit.reviews.approve(kit.operator, IP, Long.toString(firstReview), new EmoticonReviewDecisionRequest(0, null)))))
                        .isEqualTo(EmoticonConflictException.PACK_CREATOR_WITHDRAWN);
                assertThat(pack(free).get("suspend_kind")).isEqualTo("CREATOR_WITHDRAWN");
                assertThat(kit.status(firstPending)).isEqualTo("PENDING");
                // DB 그물 — 작가 탈퇴로 멈춘 팩의 상태·종류·상점 판 바꾸기 · 탈퇴한 작가 팩의 판매 시작
                assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_packs SET status = 'APPROVED', suspend_kind = NULL, suspend_reason = NULL, suspended_at = NULL "
                        + "WHERE id = ?", free)).isInstanceOf(DataAccessException.class);
                assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_packs SET suspend_kind = 'OPERATOR' WHERE id = ?", free)).isInstanceOf(DataAccessException.class);
                long revision = kit.jdbc.queryForObject("SELECT revision_id FROM emoticon_reviews WHERE id = ?", Long.class, updateReview);
                assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_packs SET live_revision_id = ? WHERE id = ?", revision, free)).isInstanceOf(DataAccessException.class);
                long firstRevision = kit.jdbc.queryForObject("SELECT revision_id FROM emoticon_reviews WHERE id = ?", Long.class, firstReview);
                assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_packs SET status = 'APPROVED', live_revision_id = ?, published_at = now() WHERE id = ?", firstRevision,
                        firstPending)).isInstanceOf(DataAccessException.class);
                // CHECK — 작가 탈퇴 판매 중단은 운영자가 없다 · 판매 중단이면 종류가 있다
                assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_packs SET suspended_by = ? WHERE id = ?", kit.operator, free)).isInstanceOf(DataAccessException.class);
                long another = kit.approvedPack(kit.member());
                assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_packs SET status = 'SUSPENDED', suspend_reason = '종류 없이 멈춤', suspended_at = now() WHERE id = ?",
                        another)).isInstanceOf(DataAccessException.class);
                // 가격 칸은 막지 않는다(멈춘 팩은 팔리지 않는다 — 서비스가 409 로 막는다)
                assertThat(kit.jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE id = ?", paid)).isEqualTo(1);
                // 운영자의 보기는 된다
                assertThat(kit.packs.detail(Long.toString(free)).resumable()).isFalse();
                assertThat(kit.reviews.detail(Long.toString(updateReview)).status()).isEqualTo("PENDING");
            } finally {
                // 공유 DB 정리 — 다시 닫는다
                kit.jdbc.update("UPDATE emoticon_reviews SET status = 'REJECTED', decided_at = now(), reason = '시험 정리로 닫음', system_cause = 'CREATOR_WITHDRAWN' "
                        + "WHERE id IN (?, ?) AND status = 'PENDING'", updateReview, firstReview);
                kit.jdbc.update("UPDATE emoticon_packs SET status = 'REJECTED' WHERE id = ? AND status = 'PENDING'", firstPending);
            }
        });
    }

    // ── 4. 결제창에 들어가 있던 주문 ────────────────────────────────────────

    @Test
    void 탈퇴_순간_결제창에_있던_구매의_완료는_판매_중_아님으로_거절되어_닫히고_다른_작가의_결제는_받아들인다() {
        kit.withPaid(() -> {
            long creator = kit.member();
            long paid = approvedPaid(creator);
            long other = approvedPaid(kit.member());
            long buyer = kit.member();
            long waiting = pending(buyer, paid);
            long otherWaiting = pending(buyer, other);

            withdraw(creator);
            markWithdrawn(creator, kit.clock.instant());

            assertThat(onPaid(buyer, waiting)).isEqualTo(PaymentDecision.reject(EmoticonPackPaymentHandler.REJECT_NOT_ON_SALE));
            assertThat(purchaseStatus(waiting)).isEqualTo("CLOSED");
            assertThat(kit.count("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", buyer, paid)).isZero();
            assertThat(onPaid(buyer, otherWaiting)).isEqualTo(PaymentDecision.accept());
            assertThat(purchaseStatus(otherWaiting)).isEqualTo("PAID");
        });
    }

    // ── 5. 남은 정산은 한 번에 지급 ─────────────────────────────────────────

    private void sale(long creator, long pack, Instant paidAt) {
        long purchase = kit.ids.nextId();
        long order = kit.ids.nextId();
        long buyer = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, expires_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ROBOTIKON_PACK', ?, '시험', ?, 'READY', ?, ?, ?)", order, "pay_" + String.format("%032d", order).substring(0, 32), buyer, purchase, PRICE,
                Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), Timestamp.from(paidAt));
        kit.jdbc.update("INSERT INTO emoticon_purchases (id, user_id, pack_id, creator_id, pack_name, amount, status, refund_notice_agreed_at, expires_at, created_at, "
                + "payment_order_id, paid_at, refund_until) VALUES (?, ?, ?, ?, '탈퇴 정산 팩', ?, 'PAID', ?, ?, ?, ?, ?, ?)", purchase, buyer, pack, creator, PRICE,
                Timestamp.from(paidAt), Timestamp.from(paidAt.plus(Duration.ofMinutes(30))), Timestamp.from(paidAt), order, Timestamp.from(paidAt),
                Timestamp.from(paidAt.plus(Duration.ofDays(7))));
    }

    private static String line(long creator, LocalDate month) {
        return String.format("%04d%02d-%d", month.getYear(), month.getMonthValue(), creator);
    }

    private EmoticonSettlementView.BulkResult confirm(String... ids) {
        return kit.inTx(() -> kit.settlements.confirm(kit.operator, IP, new EmoticonSettlementConfirmRequest(List.of(ids))));
    }

    private EmoticonSettlementView.AdminRow listed(LocalDate month, long creator) {
        return kit.settlements.list(String.format("%04d-%02d", month.getYear(), month.getMonthValue())).items().stream()
                .filter(r -> r.creator().memberNo().equals(Long.toString(creator))).findFirst().orElseThrow();
    }

    private boolean hasPayout(long user) {
        return kit.count("SELECT count(*) FROM emoticon_payout_accounts WHERE user_id = ?", user) == 1;
    }

    @Test
    void 탈퇴_작가는_최소_지급액_미만이어도_다음_확정에서_넘긴_금액까지_지급되고_지급_기록_뒤_파기_배치가_정산_정보를_지운다() {
        Map<String, Object> was = kit.settingsRow();
        try {
            kit.jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = 30, payout_minimum = 10000, settlement_day = 10, refund_days = 7 WHERE id = 1");
            Instant t0 = Instant.now().minus(Duration.ofDays(100)).truncatedTo(ChronoUnit.MICROS);
            kit.clock.set(t0);
            LocalDate m0 = ServiceTime.dateOf(t0).withDayOfMonth(1).minusMonths(2);
            LocalDate m1 = m0.plusMonths(1);
            Instant soldAt = m0.plusDays(2).atTime(12, 0).atZone(SEOUL).toInstant();
            long gone = kit.member();             // 탈퇴하는 작가
            kit.registerPayout(gone);
            sale(gone, kit.approvedPack(gone), soldAt);
            long stay = kit.member();             // 탈퇴하지 않는 작가 — 같은 금액, 지금처럼 넘긴다
            kit.registerPayout(stay);
            sale(stay, kit.approvedPack(stay), soldAt);
            long noAccount = kit.member();        // 정산 정보 없이 탈퇴 — 확정 건너뜀 그대로
            sale(noAccount, kit.approvedPack(noAccount), soldAt);

            // 판매 달 — 2,500 → 정산 기준 2,272 → 작가 몫 1,590 < 최소 지급액 10,000 → 넘김(둘 다 탈퇴 전)
            assertThat(confirm(line(gone, m0), line(stay, m0)).confirmed()).isEqualTo(2);
            assertThat(kit.jdbc.queryForObject("SELECT carried_out FROM emoticon_settlements WHERE creator_id = ? AND sales_month = ?", Long.class, gone, m0))
                    .isEqualTo(1590L);

            withdraw(gone);
            markWithdrawn(gone, t0);
            withdraw(noAccount);
            markWithdrawn(noAccount, t0);

            // 다음 달(넘어온 금액만) — 탈퇴 작가는 최소 지급액과 상관없이 지급 대상, 이월 없음 · 다른 작가는 지금처럼 넘긴다
            EmoticonSettlementView.AdminDetail goneLine = kit.settlements.detail(line(gone, m1));
            assertThat(goneLine.carriedIn()).isEqualTo(1590);
            assertThat(goneLine.withholdingAmount()).isEqualTo(50);
            assertThat(goneLine.payoutAmount()).isEqualTo(1540);
            assertThat(goneLine.carriedOut()).isZero();
            assertThat(goneLine.creator().withdrawn()).isTrue();
            EmoticonSettlementView.AdminDetail stayLine = kit.settlements.detail(line(stay, m1));
            assertThat(stayLine.payoutAmount()).isZero();
            assertThat(stayLine.carriedOut()).isEqualTo(1590);
            // 관리자 목록도 같은 규칙
            assertThat(listed(m1, gone).payoutAmount()).isEqualTo(1540);
            assertThat(listed(m1, stay).payoutAmount()).isZero();

            EmoticonSettlementView.BulkResult result = confirm(line(gone, m1), line(stay, m1), line(noAccount, m0));
            assertThat(result.confirmed()).isEqualTo(2);
            assertThat(result.skippedItems()).containsExactly(new EmoticonSettlementView.Skipped(line(noAccount, m0), "PAYOUT_ACCOUNT_MISSING"));
            Map<String, Object> frozen = kit.jdbc.queryForMap("SELECT status, payout_amount, carried_out FROM emoticon_settlements WHERE creator_id = ? AND sales_month = ?",
                    gone, m1);
            assertThat(frozen).containsEntry("status", "CONFIRMED").containsEntry("payout_amount", 1540L).containsEntry("carried_out", 0L);
            // 확정했지만 지급 기록 전 → 정산 정보는 그대로
            kit.jdbc.execute("CALL batch_emoticon_payout_account_cleanup('MANUAL', NULL, NULL)");
            assertThat(hasPayout(gone)).isTrue();

            LocalDate transfer = ServiceTime.dateOf(t0);
            BusinessDayCalendar cal = kit.holidays.calendar();
            while (!cal.businessDay(transfer)) {
                transfer = transfer.minusDays(1);
            }
            String date = transfer.toString();
            kit.inTx(() -> kit.settlements.markPaid(kit.operator, IP, line(gone, m1), new EmoticonSettlementPaidRequest(date, null)));

            // 지급 기록 + 여유 30일 뒤 — 파기 배치가 지운다(미확정 3 해소) · 탈퇴하지 않은 작가는 그대로
            kit.jdbc.execute("CALL batch_emoticon_payout_account_cleanup('MANUAL', NULL, NULL)");
            assertThat(hasPayout(gone)).isFalse();
            assertThat(hasPayout(stay)).isTrue();
            assertThat(kit.jdbc.queryForObject("SELECT status FROM emoticon_settlements WHERE creator_id = ? AND sales_month = ?", String.class, gone, m1)).isEqualTo("PAID");
            // 정산 정보가 없어 확정하지 못한 탈퇴 작가는 예정으로 남는다(끝나지 않음)
            assertThat(kit.settlements.detail(line(noAccount, m0)).status()).isEqualTo("ESTIMATED");
            // 음수 이월(작가에게 받을 돈)은 최소 지급액이 없어도 지급하지 않고 넘긴다 — 끝난 것으로 본다
            EmoticonSettlementMath.Month negative = EmoticonSettlementMath.month(0, 0, 30, -500, true, 0);
            assertThat(negative.payout()).isZero();
            assertThat(negative.carriedOut()).isEqualTo(-500);
        } finally {
            kit.restoreSettings(was);
        }
    }
}
