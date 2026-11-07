package com.community.sanction;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.permission.entity.Permission;
import com.community.sanction.exception.SanctionNotAppealableException;
import com.community.sanction.service.BoardSanctionService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게시판 제재 이의제기 접수와 운영자 무효가 겹칠 때 — 무효가 된 제재에 PENDING 이의제기가 남지 않는다(실제 PostgreSQL, 두 연결).
 * 접수가 먼저 회원 행을 쥐면 무효는 그 접수가 커밋된 뒤 다시 보고 인용으로 닫고, 무효가 먼저면 접수는 VOIDED 를 보고 거절된다.
 */
class BoardSanctionAppealVoidRaceTest extends CommerceApiTestBase {

    private static final String IP = "127.0.0.1";
    private static final String APPEAL = "이의제기 사유를 열 글자 넘게 적습니다";

    @Autowired
    private BoardSanctionService sanctions;
    @Autowired
    private PlatformTransactionManager transactions;

    private long board;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        board = ids.nextId();
        boardFixture.board(board, "sv" + Long.toString(board, 36), "제재 경합 시험 " + Long.toString(board, 36), 962);
        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private long member() {
        long id = newMember();
        memberFixture.verifyEmail(id);
        String name = Long.toString(id, 36);
        memberFixture.profile(id, "v" + name.substring(Math.max(0, name.length() - 11)), "REGULAR");
        return id;
    }

    /** 제재를 줄 수 있는 운영자 회원 계정(회원 ID) */
    private long issuer() {
        long operator = newOperator();
        // SQL 로 남김(ON CONFLICT upsert): 권한 표는 관리자 앱이 뜰 때 맞춘다 — 공개 사이트 문맥만 띄운 시험이라 없으면 코드 값으로 넣는다
        jdbc.update("INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at) VALUES (?, 'OPERATOR', ?, ?, ?, now()) ON CONFLICT (code) DO NOTHING",
                Permission.SANCTION_MANAGE.code(), Permission.SANCTION_MANAGE.type().id(), Permission.SANCTION_MANAGE.unit().name(), Permission.SANCTION_MANAGE.featureReady());
        operatorFixture.grantRole(operator, "ROLE_ZZ_SV_" + operator, "sv-" + (operator % 1_000_000_000L), List.of(Permission.SANCTION_MANAGE.code()));
        long user = member();
        // SQL 로 남김: 운영자 회원 계정 연결은 관리자 쪽 흐름이 만든다(공개 사이트 문맥에는 그 서비스가 없다)
        jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", operator, user);
        return user;
    }

    /** 경고 한 건을 주고 그 ID */
    private long warn(long target) {
        return sanctions.issue(new BoardSanctionService.Issue(issuer(), board, target, "WARNING", "SPAM", null, null, null, IP)).row().id();
    }

    /** 다른 연결이 행 잠금을 기다리고 있을 때까지(최대 10초) */
    private void awaitLockWaiter() throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            Integer waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("다른 트랜잭션이 잠금을 기다리지 않았다");
    }

    private String appealStatus(long sanctionId) {
        List<String> found = jdbc.queryForList("SELECT status FROM sanction_appeals WHERE board_sanction_id = ?", String.class, sanctionId);
        return found.isEmpty() ? null : found.get(0);
    }

    private String sanctionStatus(long sanctionId) {
        return jdbc.queryForObject("SELECT status FROM board_sanctions WHERE id = ?", String.class, sanctionId);
    }

    @Test
    void 접수가_먼저_회원_행을_쥐면_무효는_커밋된_접수를_다시_보고_인용으로_닫는다() throws Exception {
        long target = member();
        long sanctionId = warn(target);
        long operator = newOperator();
        CountDownLatch filed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> appeal = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            sanctions.fileAppeal(target, sanctionId, APPEAL); // 회원 행을 쥔 채 커밋 전
            filed.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(filed.await(10, TimeUnit.SECONDS)).isTrue();
        Future<?> voiding = pool.submit(() -> sanctions.voidByOperator(sanctionId, "운영자 판단으로 무효", operator, IP));
        awaitLockWaiter(); // 무효가 회원 행에서 기다린다
        release.countDown();
        appeal.get(10, TimeUnit.SECONDS);
        voiding.get(10, TimeUnit.SECONDS);

        assertThat(sanctionStatus(sanctionId)).isEqualTo("VOIDED");
        assertThat(appealStatus(sanctionId)).as("무효가 된 제재에 PENDING 이의제기가 남지 않는다").isEqualTo("ACCEPTED");
    }

    @Test
    void 무효가_먼저_회원_행을_쥐면_접수는_무효를_보고_거절된다() throws Exception {
        long target = member();
        long sanctionId = warn(target);
        long operator = newOperator();
        CountDownLatch voided = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> voiding = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            sanctions.voidByOperator(sanctionId, "운영자 판단으로 무효", operator, IP); // 회원 행을 쥔 채 커밋 전
            voided.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(voided.await(10, TimeUnit.SECONDS)).isTrue();
        Future<Throwable> appeal = pool.submit(() -> {
            try {
                sanctions.fileAppeal(target, sanctionId, APPEAL);
                return null;
            } catch (RuntimeException e) {
                return e;
            }
        });
        awaitLockWaiter(); // 접수가 회원 행에서 기다린다
        release.countDown();
        voiding.get(10, TimeUnit.SECONDS);

        assertThat(appeal.get(10, TimeUnit.SECONDS)).isInstanceOf(SanctionNotAppealableException.class);
        assertThat(sanctionStatus(sanctionId)).isEqualTo("VOIDED");
        assertThat(appealStatus(sanctionId)).as("접수가 남지 않는다").isNull();
    }
}
