package com.community.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.mfa.dto.MfaStage;
import com.community.mfa.dto.PendingMfa;
import com.community.mfa.dto.request.MfaVerifyRequest;
import com.community.mfa.exception.MfaCodeReusedException;
import com.community.mfa.exception.RecoveryCodeInvalidException;
import com.community.mfa.repository.master.MfaStore;
import com.community.mfa.service.MfaVerifyService;
import com.community.mfa.service.RecoveryCodes;
import com.community.mfa.totp.Totp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 TOTP 코드·같은 복구 코드를 <b>동시에</b> 낸 요청 중 정확히 하나만 이긴다(commerce-foundation-contract 「0-3」 — 받아들인 칸 이하 거절, 복구 코드 한 번만).
 * 스레드마다 연결을 따로 잡고 출발선({@link CountDownLatch})에서 한꺼번에 보낸다. 서비스 수준(행 잠금 + 조건부 UPDATE)과
 * 저장소의 조건부 UPDATE 자체(행 잠금 없이 — 스레드마다 자기 트랜잭션·연결로 한 문장씩)를 따로 확인한다.
 */
class MfaConcurrencyTest extends MfaSpringTestBase {

    private static final int THREADS = 8;

    @Autowired
    private MfaVerifyService verifier;
    @Autowired
    private MfaStore store;
    @Autowired
    private PlatformTransactionManager transactions;

    /** 모든 작업을 출발선에 세웠다가 한꺼번에 돌린다. 결과(값 또는 던진 예외)를 돌려준다 */
    private static <T> List<Object> race(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 같은_TOTP_코드를_동시에_내면_하나만_통과하고_나머지는_MFA_CODE_REUSED() throws Exception {
        mfaOn();
        long operator = newOperator();
        String secret = enrollDirect(operator);
        String code = codeNow(secret);
        PendingMfa pending = new PendingMfa(operator, MfaStage.VERIFY, clock.millis());

        List<Object> results = race(4, () -> verifier.verify(pending, new MfaVerifyRequest(code, null, false), IP, null));

        assertThat(results).filteredOn(r -> !(r instanceof Exception)).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof Exception).hasSize(3).allMatch(r -> r instanceof MfaCodeReusedException);
        assertThat(auditCount(operator, "LOGIN_SUCCESS")).isEqualTo(1);
    }

    @Test
    void 같은_복구_코드를_동시에_내면_하나만_통과하고_나머지는_RECOVERY_CODE_INVALID() throws Exception {
        mfaOn();
        long operator = newOperator();
        Enrolled enrolled = enrollViaService(operator);
        String code = enrolled.recoveryCodes().get(0);
        PendingMfa pending = new PendingMfa(operator, MfaStage.VERIFY, clock.millis());

        List<Object> results = race(4, () -> verifier.verify(pending, new MfaVerifyRequest(null, code, false), IP, null));

        assertThat(results).filteredOn(r -> !(r instanceof Exception)).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof Exception).hasSize(3).allMatch(r -> r instanceof RecoveryCodeInvalidException);
        assertThat(mfa.usedRecoveryCodes(operator)).isEqualTo(1);
    }

    @Test
    void 받아들인_칸_저장은_조건부_UPDATE_라_연결_여럿이_동시에_같은_칸을_내도_하나만_이긴다() throws Exception {
        long operator = newOperator();
        enrollDirect(operator);
        TransactionTemplate separateTransactions = new TransactionTemplate(transactions); // 부를 때마다 새 트랜잭션(풀의 다른 연결, 끝나면 커밋), 행 잠금 없음
        long step = Totp.step(clock.instant());

        List<Object> results = race(THREADS, () -> separateTransactions.execute(status -> store.acceptStep(operator, step, clock.instant())));

        assertThat(results).allMatch(r -> r instanceof Boolean);
        assertThat(results).filteredOn(Boolean.TRUE::equals).hasSize(1);
        assertThat(mfa.mfa(operator).orElseThrow().lastStep()).isEqualTo(step);
    }

    @Test
    void 복구_코드_사용_표시는_조건부_UPDATE_라_연결_여럿이_동시에_써도_하나만_이긴다() throws Exception {
        long operator = newOperator();
        Enrolled enrolled = enrollViaService(operator);
        String hash = RecoveryCodes.hash(operator, RecoveryCodes.normalize(enrolled.recoveryCodes().get(5)).orElseThrow());
        TransactionTemplate separateTransactions = new TransactionTemplate(transactions);

        List<Object> results = race(THREADS, () -> separateTransactions.execute(status -> store.useRecoveryCode(operator, hash, clock.instant())));

        assertThat(results).allMatch(r -> r instanceof Boolean);
        assertThat(results).filteredOn(Boolean.TRUE::equals).hasSize(1);
    }
}
