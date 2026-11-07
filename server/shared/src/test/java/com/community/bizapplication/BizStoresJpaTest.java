package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.ad.dto.AdPlacements;
import com.community.ad.repository.master.AdPlacementStore;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.CreativeRow;
import com.community.bizapplication.repository.master.BizApplicationStore.NewOperatorApp;
import com.community.bizapplication.repository.master.BizContractStore;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.repository.master.BizRefundStore.NewRefund;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizPricing.Amount;
import com.community.docintake.entity.DocIntakeChannel;
import com.community.docintake.entity.DocIntakeDoc;
import com.community.docintake.entity.DocIntakeHandling;
import com.community.docintake.repository.master.DocIntakeStore;
import com.community.docintake.repository.master.DocIntakeStore.IntakeRow;
import com.community.docintake.repository.master.DocIntakeStore.NewIntake;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.RowLockProbe;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사업자 신청 건·환불·계약서·종이 접수·광고 자리 설정 저장소(JDBC → JPA·QueryDSL 전환)가 예전 문장과 같은 결과를 내는가 — 실제 PostgreSQL({@link JpaTestContext}).
 * 다른 시험과 같은 DB 를 쓰므로 시험마다 트랜잭션 안에서 넣고 끝나면 되돌린다. 전환에서 모양이 바뀐 문장(그룹별 최신 1건 {@code DISTINCT ON} → 「더 늦은 줄 없음」,
 * {@code UPDATE … RETURNING} → UPDATE 뒤 읽기, {@code ON CONFLICT} 설정 upsert → 고치기 없으면 넣기, {@code string_to_array} → 배열 칸)을 직접 본다.
 */
class BizStoresJpaTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(933);
    /** 다른 시험의 줄과 겹치지 않는 먼 시각 */
    private static final Instant START = Instant.parse("2091-04-01T00:00:00Z");
    private static final Instant END = Instant.parse("2091-04-08T00:00:00Z");

    private static TransactionTemplate tx;
    private static JdbcTemplate jdbc;
    private static BizApplicationStore apps;
    private static BizRefundStore refunds;
    private static BizContractStore contracts;
    private static DocIntakeStore intakes;
    private static AdPlacementStore placements;

    @BeforeAll
    static void wire() {
        tx = new TransactionTemplate(JpaTestContext.transactions());
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        apps = JpaTestContext.create(BizApplicationStore.class);
        refunds = JpaTestContext.create(BizRefundStore.class);
        contracts = JpaTestContext.create(BizContractStore.class);
        intakes = JpaTestContext.create(DocIntakeStore.class);
        placements = JpaTestContext.create(AdPlacementStore.class);
    }

    /** 트랜잭션 안에서 돌리고 되돌린다 */
    private static void rolledBack(Consumer<TransactionStatus> work) {
        tx.executeWithoutResult(status -> {
            work.accept(status);
            status.setRollbackOnly();
        });
    }

    private static long operatorApp(Instant now) {
        long id = IDS.nextId();
        apps.insertOperator(new NewOperatorApp(id, BizApplicationKind.AD_TEXT_LINE, null, null, "광고주 " + id, null, null, "제목", START, END,
                new Amount(10_000, 1_000, 11_000), 7, 10_000, null, 1L, 10, now));
        return id;
    }

    private static Instant at(String text) {
        return Instant.parse(text).truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    void 신청_건_번호는_한국_날짜_연도이고_상태_전이는_지금_상태가_맞을_때만_한_번() {
        rolledBack(status -> {
            Instant now = at("2026-12-31T15:30:00Z"); // 한국 날짜로는 2027-01-01
            long id = operatorApp(now);
            AppRow row = apps.find(id).orElseThrow();
            assertThat(row.no()).matches("BA-2027-\\d{5}");
            assertThat(row.status()).isEqualTo(BizApplicationStatus.SUBMITTED);
            assertThat(row.weight()).isEqualTo(7);
            assertThat(row.version()).isZero();
            assertThat(row.activeContractCount()).isZero();
            assertThat(row.pauseCount()).isZero();
            assertThat(row.approved()).isNull();
            assertThat(row.quote()).isEqualTo(new Amount(10_000, 1_000, 11_000));
            assertThat(row.paymentMethod()).isNull();
            assertThat(row.preferredMethod()).isEqualTo(BizPaymentMethod.MANUAL);

            Instant later = now.plusSeconds(60);
            assertThat(apps.move(id, BizApplicationStatus.SUBMITTED, BizApplicationStatus.CANCELED, later)).isTrue();
            assertThat(apps.move(id, BizApplicationStatus.SUBMITTED, BizApplicationStatus.CANCELED, later.plusSeconds(1))).as("이미 바뀐 상태").isFalse();
            AppRow moved = apps.find(id).orElseThrow();
            assertThat(moved.status()).isEqualTo(BizApplicationStatus.CANCELED);
            assertThat(moved.closedAt()).isEqualTo(later);
            assertThat(moved.updatedAt()).isEqualTo(later);
            assertThat(moved.version()).isOne();

            assertThat(apps.lock(id)).isPresent();
            assertThat(apps.lockOwned(id, 42L)).as("회원 건이 아니다").isEmpty();
            assertThat(apps.markNotified(id, "start_notified_at", later)).isTrue();
            assertThat(apps.markNotified(id, "start_notified_at", later)).as("이미 알렸다").isFalse();
        });
    }

    @Test
    void 신청_건과_열린_환불_잠금은_전환_전과_같은_FOR_UPDATE_다_FK_로_가리키는_INSERT_도_기다린다() {
        Instant now = at("2091-03-02T00:00:00Z");
        long app = tx.execute(status -> operatorApp(now));
        long refund = IDS.nextId();
        tx.executeWithoutResult(status -> refunds.insert(new NewRefund(refund, app, BizRefundType.PRORATED, BizRefundStatus.REQUESTED, BizPaymentMethod.MANUAL, 11_000,
                5_000, 1, 7, null, "사유", null, now, null, null, null)));
        try {
            tx.executeWithoutResult(status -> {
                assertThat(apps.lock(app)).isPresent();
                assertThat(RowLockProbe.keyShareBlocked(JpaTestContext.dataSource(), "biz_applications", "id = ?", app)).as("신청 건 행 — FOR UPDATE").isTrue();
                assertThat(refunds.lockOpen(app)).hasValueSatisfying(r -> assertThat(r.id()).isEqualTo(refund));
                assertThat(RowLockProbe.keyShareBlocked(JpaTestContext.dataSource(), "biz_refunds", "id = ?", refund)).as("열린 환불 줄 — FOR UPDATE").isTrue();
            });
            assertThat(RowLockProbe.keyShareBlocked(JpaTestContext.dataSource(), "biz_applications", "id = ?", app)).as("끝나면 풀린다").isFalse();
            tx.executeWithoutResult(status -> {
                jdbc.update("UPDATE biz_refunds SET status = 'DENIED' WHERE id = ?", refund);
                assertThat(refunds.lockOpen(app)).as("열린 줄이 없으면 비어 있다(잠근 뒤 조건을 다시 본다)").isEmpty();
                status.setRollbackOnly();
            });
        } finally {
            jdbc.update("DELETE FROM biz_refunds WHERE application_id = ?", app);
            for (String child : List.of("biz_application_events", "biz_application_items", "biz_creative_versions")) {
                jdbc.update("DELETE FROM " + child + " WHERE application_id = ?", app);
            }
            jdbc.update("DELETE FROM biz_applications WHERE id = ?", app);
        }
    }

    @Test
    void 소재는_건마다_가장_높은_버전이_최신이고_승인은_그_버전에만_적는다() {
        rolledBack(status -> {
            Instant now = at("2091-03-01T00:00:00Z");
            long a = operatorApp(now);
            long b = operatorApp(now);
            for (int v = 1; v <= 3; v++) {
                apps.insertCreative(IDS.nextId(), a, v, Map.of("title", "a" + v, "n", v), BizActorType.OPERATOR, 1L, now);
            }
            apps.insertCreative(IDS.nextId(), b, 1, Map.of("title", "b1"), BizActorType.OPERATOR, 1L, now);

            Map<Long, CreativeRow> latest = apps.latestCreatives(List.of(a, b));
            assertThat(latest).containsOnlyKeys(a, b);
            assertThat(latest.get(a).version()).isEqualTo(3);
            assertThat(latest.get(a).content()).containsEntry("title", "a3").containsEntry("n", 3);
            assertThat(latest.get(b).version()).isOne();
            assertThat(apps.latestCreative(a).orElseThrow().version()).isEqualTo(3);

            Instant approved = now.plusSeconds(5);
            apps.approveLatestCreative(a, approved);
            assertThat(apps.creatives(a)).extracting(CreativeRow::version, CreativeRow::approvedAt)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(1, null), org.assertj.core.groups.Tuple.tuple(2, null),
                            org.assertj.core.groups.Tuple.tuple(3, approved));
        });
    }

    @Test
    void 건마다_가장_최근_환불은_요청_시각이_늦은_줄이고_같으면_ID_가_큰_줄() {
        rolledBack(status -> {
            Instant now = at("2091-03-01T00:00:00Z");
            long a = operatorApp(now);
            long b = operatorApp(now);
            // a — 나중에 만든(ID 가 큰) 줄이 요청 시각은 더 이르다
            long aEarlyId = IDS.nextId();
            long aLateId = IDS.nextId();
            refunds.insert(denied(aLateId, a, now.plusSeconds(10)));
            refunds.insert(denied(aEarlyId, a, now.plusSeconds(20)));
            // b — 요청 시각이 같다
            long bSmall = IDS.nextId();
            long bLarge = IDS.nextId();
            refunds.insert(denied(bLarge, b, now));
            refunds.insert(denied(bSmall, b, now));

            Map<Long, RefundRow> latest = refunds.latest(List.of(a, b));
            assertThat(latest).containsOnlyKeys(a, b);
            assertThat(latest.get(a).id()).isEqualTo(aEarlyId);
            assertThat(latest.get(b).id()).isEqualTo(bLarge);
            assertThat(refunds.latest(a).orElseThrow()).isEqualTo(latest.get(a));
            assertThat(refunds.latest(b).orElseThrow()).isEqualTo(latest.get(b));
            assertThat(refunds.all(a)).extracting(RefundRow::id).containsExactly(aLateId, aEarlyId);
            assertThat(refunds.exists(a)).isTrue();
            assertThat(refunds.approvedTotal(a)).isZero();
        });
    }

    @Test
    void 취소_완료는_그_취소의_줄을_돌려주고_처음_완료_시각을_둔다() {
        rolledBack(status -> {
            Instant now = at("2091-03-01T00:00:00Z");
            long app = operatorApp(now);
            long id = IDS.nextId();
            refunds.insert(new NewRefund(id, app, BizRefundType.PRORATED, BizRefundStatus.APPROVED, BizPaymentMethod.ONLINE, 11_000, 5_000, 1, 7, 5_000L, null, null,
                    now, now, 1L, null));
            long cancelId = IDS.nextId();
            refunds.linkCancel(id, cancelId);

            Instant first = now.plusSeconds(30);
            RefundRow done = refunds.completeByCancel(cancelId, first).orElseThrow();
            assertThat(done.id()).isEqualTo(id);
            assertThat(done.completedAt()).isEqualTo(first);
            assertThat(refunds.completeByCancel(cancelId, first.plusSeconds(30)).orElseThrow().completedAt()).as("처음 시각 그대로").isEqualTo(first);
            assertThat(refunds.completeByCancel(IDS.nextId(), first)).as("걸린 줄 없음").isEmpty();
            assertThat(refunds.approvedTotal(app)).isEqualTo(5_000);
        });
    }

    @Test
    void 계약서_사본과_종이_접수는_열린_줄만_바꾸고_잘못_기록이_아닌_줄을_근거로_센다() {
        rolledBack(status -> {
            Instant now = at("2091-03-01T00:00:00Z");
            long app = operatorApp(now);
            long file = IDS.nextId();
            byte[] secret = {1, 2, 3, 4};
            contracts.insert(file, app, "계약서.pdf", "application/pdf", 4, secret, "a".repeat(64), 1L, now);
            assertThat(contracts.list(app)).singleElement().satisfies(c -> {
                assertThat(c.applicationId()).isEqualTo(app);
                assertThat(c.active()).isTrue();
                assertThat(c.sizeBytes()).isEqualTo(4);
            });
            assertThat(contracts.content(app, file)).hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(secret));
            assertThat(contracts.activeCount(app)).isOne();

            long keep = IDS.nextId();
            long wrong = IDS.nextId();
            LocalDate received = LocalDate.of(2091, 3, 1);
            intakes.insert(new NewIntake(keep, DocIntakeDoc.CONTRACT, app, DocIntakeChannel.MAIL, received, 3, "원본", DocIntakeHandling.KEEP, "금고 1", 1L, now));
            intakes.insert(new NewIntake(wrong, DocIntakeDoc.CONTRACT, app, DocIntakeChannel.FAX, received, null, null, DocIntakeHandling.DISPOSE_PENDING, null, 1L,
                    now.plusSeconds(1)));
            assertThat(intakes.list(DocIntakeDoc.CONTRACT, app)).extracting(IntakeRow::id).as("최근 기록 먼저").containsExactly(wrong, keep);
            assertThat(intakes.find(DocIntakeDoc.CERTIFICATE, app, keep)).as("다른 대상").isEmpty();
            assertThat(intakes.contractEvidence(app)).isEqualTo(2);

            assertThat(intakes.markVoid(wrong, "잘못 적은 줄", 1L, now.plusSeconds(2))).isTrue();
            assertThat(intakes.markVoid(wrong, "다시 긋기", 1L, now.plusSeconds(3))).as("이미 잘못 기록").isFalse();
            assertThat(intakes.changeHandling(wrong, DocIntakeHandling.KEEP, null, now.plusSeconds(3))).isFalse();
            assertThat(intakes.contractEvidence(app)).isOne();

            assertThat(intakes.changeHandling(keep, DocIntakeHandling.DISPOSE_PENDING, null, now.plusSeconds(4))).isTrue();
            assertThat(intakes.dispose(keep, LocalDate.of(2091, 3, 2), "보관 기간 끝", file, null, 1L, now.plusSeconds(5))).isTrue();
            assertThat(intakes.dispose(keep, LocalDate.of(2091, 3, 2), "보관 기간 끝", file, null, 1L, now.plusSeconds(6))).as("이미 폐기").isFalse();
            IntakeRow disposed = intakes.find(DocIntakeDoc.CONTRACT, app, keep).orElseThrow();
            assertThat(disposed.handling()).isEqualTo(DocIntakeHandling.DISPOSED);
            assertThat(disposed.storageLocation()).isNull();
            assertThat(disposed.scanContractFileId()).isEqualTo(file);
            assertThat(disposed.open()).isFalse();
            assertThat(intakes.contractEvidence(app)).as("폐기한 줄은 근거로 남는다").isOne();
        });
    }

    @Test
    void 광고_자리_설정은_처음_저장이면_넣고_뒤로는_고치며_날개_화면_배열을_그대로_읽는다() {
        rolledBack(status -> {
            jdbc.update("DELETE FROM ad_placement_settings"); // 같은 트랜잭션 — 끝나면 되돌린다
            assertThat(placements.find()).isEmpty();
            assertThat(placements.current()).isEqualTo(AdPlacements.DEFAULTS);
            assertThat(placements.stopFeeRate()).isEqualTo(AdPlacementStore.DEFAULT_STOP_FEE_RATE);
            assertThat(placements.lock()).isEmpty();

            Instant now = at("2091-03-01T00:00:00Z");
            AdPlacements d = AdPlacements.DEFAULTS;
            AdPlacements two = new AdPlacements(d.postInline(), new AdPlacements.Wings(true, false, List.of("HOME", "POST")), d.boardList(), d.adult());
            placements.save(two, 7L, now);
            AdPlacementStore.Stored first = placements.find().orElseThrow();
            assertThat(first.value()).isEqualTo(two);
            assertThat(first.updatedBy()).isEqualTo(7L);
            assertThat(first.updatedAt()).isEqualTo(now);
            assertThat(first.stopFeeRate()).as("처음 저장의 수수료율은 기본").isEqualTo(AdPlacementStore.DEFAULT_STOP_FEE_RATE);
            assertThat(jdbc.queryForObject("SELECT array_to_string(wing_screens, ',') FROM ad_placement_settings WHERE id = 1", String.class)).isEqualTo("HOME,POST");

            AdPlacements none = new AdPlacements(d.postInline(), new AdPlacements.Wings(false, false, List.of()), d.boardList(), new AdPlacements.Adult(true));
            placements.save(none, 20, 8L, now.plusSeconds(1));
            assertThat(placements.current()).isEqualTo(none);
            assertThat(placements.stopFeeRate()).isEqualTo(20);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_placement_settings", Integer.class)).isOne();
            placements.save(two, 9L, now.plusSeconds(2));
            assertThat(placements.stopFeeRate()).as("자리 설정만 저장하면 수수료율 그대로").isEqualTo(20);
            assertThat(placements.lock().orElseThrow().updatedByName()).isNull();
        });
    }

    private static NewRefund denied(long id, long app, Instant requestedAt) {
        return new NewRefund(id, app, BizRefundType.PRORATED, BizRefundStatus.DENIED, BizPaymentMethod.MANUAL, 11_000, 5_000, 1, 7, null, "사유", null, requestedAt,
                requestedAt, 1L, null);
    }
}
