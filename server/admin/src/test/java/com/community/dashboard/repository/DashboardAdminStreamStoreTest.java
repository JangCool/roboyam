package com.community.dashboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.dashboard.dto.response.gadget.DashboardOperatorSecurityView;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import com.community.dashboard.repository.master.DashboardLayoutStore;
import com.community.mfa.MfaSpringTestBase;
import com.community.mfa.repository.master.MfaStore;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 대시보드가 읽는 admin 스트림 표 — 운영자 구성({@code operator_dashboards}, {@link DashboardLayoutStore})과 운영자 보안 가젯의 2차 인증 미등록 수
 * ({@code operator_mfa}, {@link DashboardGadgetStore#operatorSecurity}) — 실제 PostgreSQL. 다른 시험과 표를 같이 쓰므로 한 트랜잭션(스냅숏 고정)에서 하고 되돌린다.
 * 예전 네이티브 문장과 같은 값인지는 그 문장을 같은 트랜잭션에서 돌려 견준다.
 */
class DashboardAdminStreamStoreTest extends MfaSpringTestBase {

    @Autowired
    private DashboardLayoutStore layouts;
    @Autowired
    private DashboardGadgetStore gadgets;
    @Autowired
    private MfaStore mfaStore;
    @Autowired
    private PlatformTransactionManager transactions;

    private void inRolledBackSnapshot(Runnable work) {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            work.run();
        });
    }

    /** 예전 구현(JDBC)의 문장 그대로 — 같은 트랜잭션(같은 연결)에서 돈다 */
    private DashboardOperatorSecurityView legacyOperatorSecurity() {
        return jdbc.queryForObject("SELECT count(*), count(*) FILTER (WHERE o.locked), "
                + "count(*) FILTER (WHERE o.approved AND o.enabled AND NOT o.expired AND NOT EXISTS "
                + "(SELECT 1 FROM operator_mfa m WHERE m.operator_id = o.id AND m.enrolled_at IS NOT NULL)) FROM operators o",
                (rs, i) -> new DashboardOperatorSecurityView(rs.getLong(1), rs.getLong(2), rs.getLong(3)));
    }

    @Test
    void 구성은_없으면_비어_있고_저장하면_넣고_다시_저장하면_바꾸며_jsonb_는_정규화한_글자로_읽힌다() {
        inRolledBackSnapshot(() -> {
            long operator = newOperator();
            assertThat(layouts.find(operator)).isEmpty();

            layouts.save(operator, 2, 48, "[{\"id\":\"g1\",\"type\":\"REPORTS\",\"x\":0,\"y\":0,\"w\":12,\"h\":10,\"config\":{}}]", Instant.now());
            DashboardLayoutStore.Row first = layouts.find(operator).orElseThrow();
            assertThat(first.version()).isEqualTo(2);
            assertThat(first.columns()).isEqualTo(48);
            assertThat(first.gadgets()).isEqualTo(jdbc.queryForObject("SELECT gadgets::text FROM operator_dashboards WHERE operator_id = ?", String.class, operator));

            layouts.save(operator, 2, 48, "[]", Instant.now());
            assertThat(layouts.find(operator).orElseThrow().gadgets()).isEqualTo("[]");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_dashboards WHERE operator_id = ?", Integer.class, operator)).isOne();
        });
    }

    @Test
    void 운영자_보안은_전체_잠김_승인_사용_만료아님_중_확정_등록_없는_수를_예전_문장과_같게_센다() {
        inRolledBackSnapshot(() -> {
            DashboardOperatorSecurityView before = gadgets.operatorSecurity();
            assertThat(before).isEqualTo(legacyOperatorSecurity());

            newOperator(); // 미등록
            long enrolled = newOperator();
            mfa.enroll(enrolled, "v1:test");
            long locked = newOperator(); // 잠겨도 미등록에 센다(예전 문장 그대로)
            operatorFixture.changeStatus(locked, null, true, null, null);
            long pendingOnly = newOperator(); // 등록 중(확정 전)은 미등록
            mfaStore.startPending(pendingOnly, "v1:pending", false, Instant.now());
            long expired = newOperator(); // 만료 표시는 미등록에서 뺀다
            operatorFixture.changeStatus(expired, null, null, true, null);

            DashboardOperatorSecurityView after = gadgets.operatorSecurity();
            assertThat(after).isEqualTo(legacyOperatorSecurity());
            assertThat(after.total() - before.total()).isEqualTo(5);
            assertThat(after.locked() - before.locked()).isEqualTo(1);
            assertThat(after.mfaMissing() - before.mfaMissing()).isEqualTo(3);
        });
    }
}
