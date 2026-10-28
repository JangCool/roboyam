package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.Resolution;
import com.community.groupbuy.service.GroupBuyRequestService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 공구 개인정보 보관 정리 DB 배치 {@code groupbuy-privacy-cleanup}(R__batch_32 — commerce.md 10-9, 계약 결정 34): 배송지(배송완료·취소 뒤 180일, 만료 주문 30일 — 공구 설정) ·
 * 다음 판이 생긴 지 180일 지난 수정 이력 · 끝난 지 3년 지난 요청의 회원 사유 글·사진. 거래 기록(금액·상태)은 남고, 열린 요청이 있는 주문은 미룬다.
 */
class GroupBuyPrivacyCleanupBatchTest {

    private static final String JOB = "groupbuy-privacy-cleanup";
    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    private void run() {
        // 「지금 실행」— 예약 실행은 같은 초에 두 번이면 건너뛴다(예약 시각 유일)
        kit.jdbc.execute("CALL batch_groupbuy_privacy_cleanup('MANUAL', NULL, NULL)");
    }

    private Map<String, Object> address(long participationId) {
        return kit.jdbc.queryForMap("SELECT recipient_enc, phone_enc, postcode, address1_enc, address_masked_at, status, amount FROM group_buy_participations WHERE id = ?",
                participationId);
    }

    @Test
    void 카탈로그는_DB_배치이고_매일_04시_35분() {
        assertThat(BatchCatalog.GROUPBUY_PRIVACY_CLEANUP.isJava()).isFalse();
        assertThat(BatchCatalog.GROUPBUY_PRIVACY_CLEANUP.procedureName()).isEqualTo("batch_groupbuy_privacy_cleanup");
        Map<String, Object> defined = kit.jdbc.queryForMap("SELECT procedure_name, kst_hour, kst_minute FROM batch_db_jobs WHERE job_key = ?", JOB);
        assertThat(((Number) defined.get("kst_hour")).intValue()).isEqualTo(4);
        assertThat(((Number) defined.get("kst_minute")).intValue()).isEqualTo(35);
        assertThat(kit.jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = ? AND param_key = 'delivered_days'", Long.class, JOB)).isEqualTo(180L);
        assertThat(kit.jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = ? AND param_key = 'request_days'", Long.class, JOB)).isEqualTo(1095L);
    }

    @Test
    void 배송완료_취소_180일_만료_30일_지난_배송지만_지우고_열린_요청이_있으면_미룬다_거래_기록은_남는다() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).price(25_000).open();
        JoinResult oldDelivered = kit.delivered(kit.newMember(), gb, GroupBuyTestKit.lines(kit.mainSku(gb), 1));
        JoinResult recentDelivered = kit.delivered(kit.newMember(), gb, GroupBuyTestKit.lines(kit.mainSku(gb), 1));
        long withRequestUser = kit.newMember();
        JoinResult withRequest = kit.delivered(withRequestUser, gb, GroupBuyTestKit.lines(kit.mainSku(gb), 1));
        kit.requests.create(withRequestUser, withRequest.participationId(), new GroupBuyRequestService.RequestInput(RequestType.RETURN, CollectionMethod.CUSTOMER_SEND,
                Resolution.REFUND, List.of(new GroupBuyRequestService.LineInput(kit.mainSku(gb), 1, RequestReason.DEFECT)), "불량이에요", List.of(), null));
        long canceledUser = kit.newMember();
        JoinResult canceled = kit.paidOrder(canceledUser, gb, 1);
        kit.cancels.cancelByMember(canceledUser, canceled.participationId(), List.of());
        JoinResult expired = kit.order(kit.newMember(), gb, 1);
        kit.advance(Duration.ofMinutes(6));
        kit.holds.expireOverdue(100);

        kit.jdbc.update("UPDATE group_buy_participations SET delivered_at = now() - interval '181 days' WHERE id IN (?, ?)", oldDelivered.participationId(),
                withRequest.participationId());
        kit.jdbc.update("UPDATE group_buy_participations SET delivered_at = now() - interval '179 days' WHERE id = ?", recentDelivered.participationId());
        kit.jdbc.update("UPDATE group_buy_participations SET canceled_at = now() - interval '181 days' WHERE id = ?", canceled.participationId());
        kit.jdbc.update("UPDATE group_buy_participations SET expired_at = now() - interval '31 days' WHERE id = ?", expired.participationId());

        run();

        for (long erased : List.of(oldDelivered.participationId(), canceled.participationId(), expired.participationId())) {
            Map<String, Object> a = address(erased);
            assertThat(a.get("recipient_enc")).as("배송지 암호문 삭제 " + erased).isNull();
            assertThat(a.get("phone_enc")).isNull();
            assertThat(a.get("address1_enc")).isNull();
            assertThat(a.get("postcode")).isNull();
            assertThat(a.get("address_masked_at")).isNotNull();
            assertThat(a.get("amount")).as("거래 기록은 남는다").isNotNull();
        }
        assertThat(address(recentDelivered.participationId()).get("recipient_enc")).as("179일 — 그대로").isNotNull();
        assertThat(address(withRequest.participationId()).get("recipient_enc")).as("열린 요청 — 미룬다").isNotNull();
        assertThat(kit.jdbc.queryForObject("SELECT status FROM batch_runs WHERE job_key = ? ORDER BY started_at DESC, id DESC LIMIT 1", String.class, JOB))
                .isEqualTo("OK");
    }

    @Test
    void 다음_판이_생긴_지_180일_지난_수정_이력만_지우고_마지막_판은_남긴다() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).open();
        kit.admin.addStock(gb, null, 5, "추가 입고 시험", kit.operator, "ip");
        kit.admin.addStock(gb, null, 5, "추가 입고 시험", kit.operator, "ip");
        List<Integer> before = kit.jdbc.queryForList("SELECT revision_no FROM group_buy_revisions WHERE group_buy_id = ? ORDER BY revision_no", Integer.class, gb);
        assertThat(before).hasSize(3);
        // 2판이 생긴 지 181일 → 1판 삭제, 3판은 최근 → 2판은 남는다
        kit.jdbc.update("UPDATE group_buy_revisions SET created_at = now() - interval '181 days' WHERE group_buy_id = ? AND revision_no = ?", gb, before.get(1));
        kit.jdbc.update("UPDATE group_buy_revisions SET created_at = now() - interval '200 days' WHERE group_buy_id = ? AND revision_no = ?", gb, before.get(0));

        run();

        assertThat(kit.jdbc.queryForList("SELECT revision_no FROM group_buy_revisions WHERE group_buy_id = ? ORDER BY revision_no", Integer.class, gb))
                .containsExactly(before.get(1), before.get(2));
    }

    @Test
    void 끝난_지_3년_지난_요청의_회원_사유_글과_회원_사진만_지운다() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).open();
        long m = kit.newMember();
        JoinResult o = kit.delivered(m, gb, GroupBuyTestKit.lines(kit.mainSku(gb), 1));
        long photo = kit.requests.registerMemberPhoto(m, "groupbuy/request/202601/1.jpg");
        var created = kit.requests.create(m, o.participationId(), new GroupBuyRequestService.RequestInput(RequestType.RETURN, CollectionMethod.CUSTOMER_SEND, Resolution.REFUND,
                List.of(new GroupBuyRequestService.LineInput(kit.mainSku(gb), 1, RequestReason.DEFECT)), "포장이 찢겨 왔어요", List.of(photo), null));
        kit.requests.withdraw(m, created.requestId());
        kit.jdbc.update("UPDATE group_buy_requests SET updated_at = now() - interval '1096 days' WHERE id = ?", created.requestId());

        run();

        Map<String, Object> last = kit.jdbc.queryForMap("SELECT status, error_message, message_key FROM batch_runs WHERE job_key = ? ORDER BY started_at DESC, id DESC LIMIT 1", JOB);
        assertThat(kit.jdbc.queryForObject("SELECT memo FROM group_buy_requests WHERE id = ?", String.class, created.requestId())).as("배치 " + last + " 요청 " + kit.jdbc.queryForMap("SELECT status, updated_at FROM group_buy_requests WHERE id = ?", created.requestId())).isNull();
        assertThat(kit.count("SELECT count(*) FROM group_buy_request_photos WHERE id = ?", photo)).isZero();
        assertThat(kit.count("SELECT count(*) FROM group_buy_requests WHERE id = ?", created.requestId())).as("요청 줄(청약철회 기록)은 남는다").isEqualTo(1);
    }
}
