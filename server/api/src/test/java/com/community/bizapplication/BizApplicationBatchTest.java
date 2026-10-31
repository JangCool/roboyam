package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchFailureRecorder;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchLockService;
import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchRegistry;
import com.community.batch.core.BatchRunRecorder;
import com.community.batch.core.BatchRunner;
import com.community.batch.entity.BatchRequestKind;
import com.community.bizapplication.batch.BizPaymentDueNoticeJob;
import com.community.bizapplication.batch.BizPaymentExpireJob;
import com.community.bizapplication.batch.BizScheduleNoticeJob;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizSlotService;
import com.community.payment.dto.PaymentCheckout;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 배치 3개(설계 8절) — 결제 기한 만료(EXPIRED · 자리 반납 · 열린 결제 먼저 닫기 · 기한 전은 그대로) · 결제 기한 임박 알림(한 번) · 시작·종료 임박 알림(한 번씩). 배치 실행기로 「지금 실행」한다.
 */
class BizApplicationBatchTest extends BizApplicationApiTestBase {

    @Autowired
    private BizPaymentExpireJob expireJob;
    @Autowired
    private BizPaymentDueNoticeJob dueJob;
    @Autowired
    private BizScheduleNoticeJob scheduleJob;
    @Autowired
    private BizSlotService slots;

    private BatchRunner.RunReport run(BatchJob job) {
        // 앱의 트랜잭션 관리자(JPA) — 서비스의 @Transactional 이 덩어리 트랜잭션에 참여하게(운영과 같다)
        BatchRunner runner = new BatchRunner(new BatchLockService(dataSource), new BatchRunRecorder(dataSource, transactions, ids, 953),
                new BatchFailureRecorder(dataSource, transactions, ids), BatchRegistry.forJobs(List.of(job)), transactions);
        BatchRunner.RunReport report = runner.runRequested(new BatchRunner.RunRequest(job.definition(), BatchRequestKind.NOW, null, BatchParameters.of(Map.of()), null, null, null));
        if (report.runId() != null) {
            Map<String, Object> row = jdbc.queryForMap("SELECT status, error_message, message_key FROM batch_runs WHERE id = ?", report.runId());
            assertThat(row.get("status")).as(String.valueOf(row)).isIn("OK", "SKIP");
        } else {
            throw new AssertionError("배치가 돌지 않았다: " + report);
        }
        return report;
    }

    private int notices(long user, String kind) {
        return jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'BIZ_APPLICATION' AND payload->>'kind' = ?", Integer.class, user, kind);
    }

    @Test
    void 결제_기한이_지난_결제_대기는_만료_자리_반납_열린_결제_닫기_기한_전은_그대로() {
        long u1 = approvedBusiness();
        long overdue = submitBanner(u1, "STRIP", 0, 2, "ONLINE");
        BizPeriods.Period p = freshPeriod(2);
        approve(overdue, "ONLINE", p);
        PaymentCheckout checkout = members.startPayment(u1, overdue);
        long u2 = approvedBusiness();
        long notYet = submitTextLine(u2, 0, 3, "ONLINE");
        assertThat(slots.room(BizProduct.STRIP_BANNER, p.start(), p.end(), null).remaining()).isEqualTo(2);

        clock.set(app(overdue).paymentDeadlineAt().plusSeconds(1));
        approve(notYet, "ONLINE", freshPeriod(3)); // 지금(옮긴 시계) 기준 72시간 뒤 기한
        run(expireJob);
        assertThat(app(overdue).status()).isEqualTo(BizApplicationStatus.EXPIRED);
        assertThat(app(overdue).closedAt()).isNotNull();
        assertThat(app(notYet).status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        assertThat(slots.room(BizProduct.STRIP_BANNER, p.start(), p.end(), null).remaining()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_orders WHERE payment_id = ?", String.class, checkout.paymentId())).isEqualTo("EXPIRED");
        assertThat(notices(u1, "EXPIRED")).isEqualTo(1);
        run(expireJob); // 다시 돌아도 같은 결과(멱등)
        assertThat(notices(u1, "EXPIRED")).isEqualTo(1);
    }

    @Test
    void 결제_기한_임박_알림은_24시간_안에_한_번만() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "ONLINE");
        approve(id, "ONLINE", freshPeriod(3));
        run(dueJob);
        assertThat(notices(user, "PAYMENT_DUE")).isZero(); // 72시간 남음
        clock.set(app(id).paymentDeadlineAt().minus(Duration.ofHours(5)));
        run(dueJob);
        run(dueJob);
        assertThat(notices(user, "PAYMENT_DUE")).isEqualTo(1);
    }

    @Test
    void 시작_임박과_종료_임박_알림은_한_번씩() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "ONLINE");
        BizPeriods.Period p = freshPeriod(3);
        approve(id, "ONLINE", p);
        pay(user, id);
        clock.set(p.start().minus(Duration.ofHours(10)));
        run(scheduleJob);
        run(scheduleJob);
        assertThat(notices(user, "START_SOON")).isEqualTo(1);
        assertThat(notices(user, "END_SOON")).isZero();
        clock.set(p.end().minus(Duration.ofHours(3)));
        run(scheduleJob);
        run(scheduleJob);
        assertThat(notices(user, "END_SOON")).isEqualTo(1);
    }
}
