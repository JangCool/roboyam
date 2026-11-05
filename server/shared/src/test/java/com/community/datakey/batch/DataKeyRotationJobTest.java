package com.community.datakey.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.addressbook.dto.AddressInput;
import com.community.addressbook.dto.SavedAddress;
import com.community.addressbook.repository.master.MemberAddressStore;
import com.community.addressbook.service.AddressBookService;
import com.community.audit.dto.AuditEvent;
import com.community.audit.service.AuditLogService;
import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchDefinition;
import com.community.batch.core.BatchFailureRecorder;
import com.community.batch.core.BatchLockService;
import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchRegistry;
import com.community.batch.core.BatchRunRecorder;
import com.community.batch.core.BatchRunner;
import com.community.batch.core.BatchRunner.RunReport;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.BatchSource;
import com.community.groupbuy.service.GroupBuyJoinService;
import com.community.testsupport.CommerceTestDatabase;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.repository.master.WithdrawnContactRows;
import com.community.withdrawal.service.WithdrawnContactStore;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.support.CronExpression;

/**
 * 데이터 키 교체 배치 {@code data-key-rotation}(계약 「0-1」 키 교체 절차 · 「0-4」) — 실제 PostgreSQL 위에서 배치 core({@link BatchRunner})로 돌린다(commerce.md 18절).
 *
 * <p>DB 를 다른 시험과 같이 쓰므로 규칙 시험은 시험마다 만든 표({@code zz_rot_<n>})를 칸 하나로 삼은 작업으로 돈다(다른 시험의 행이 섞이지 않게). 마지막 시험은 실제 칸 목록
 * ({@link DataKeyColumns#ALL})으로 「새 키 → 배치 → 예전 키 비움」을 끝까지 해 본다 — 탈퇴 회원 연락처·회원 주소록은 실제 표와 실제 열람 길로, 목록의 모든 칸은
 * 실제 표 모양의 사본으로. 스키마의 암호문 칸이 목록에서 빠지지 않았는지도 여기서 본다.
 */
class DataKeyRotationJobTest {

    private static final int NODE = 778;
    private static final AtomicInteger TABLES = new AtomicInteger();
    private static final String SUFFIX = Long.toString(System.nanoTime() % 1_000_000L, 36);
    private static final DataPurpose PURPOSE = DataPurpose.of("zz.rotation-test");

    /** 옛 키(버전 1) · 새 키(버전 2) · 잃어버린 키(버전 3 — 설정에 없는 옛 키) */
    private static final String K1 = key(1);
    private static final String K2 = key(2);
    private static final String K3 = key(3);

    private static DataSource dataSource;
    private static DataSourceTransactionManager transactions;
    private static JdbcTemplate jdbc;
    private static SnowflakeIdGenerator ids;
    private static final AtomicLong ROW_IDS = new AtomicLong(5_000_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);

    @BeforeAll
    static void setUp() {
        dataSource = CommerceTestDatabase.migrated();
        transactions = new DataSourceTransactionManager(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        ids = new SnowflakeIdGenerator(NODE);
    }

    private static String key(int version) {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return version + ":" + Base64.getEncoder().encodeToString(raw);
    }

    private static DataCipher cipher(String active, String... previous) {
        return new DataCipher(active, String.join(",", previous));
    }

    // ── 시험용 칸·작업 ─────────────────────────────────────────────────────────

    private record TestDefinition(String key) implements BatchDefinition {
        @Override
        public BatchSource source() {
            return BatchSource.JAVA;
        }
    }

    /** 시험 하나의 표 + 칸 + 배치 키 */
    private record Fixture(EncryptedColumn column, BatchDefinition definition) {
        String table() {
            return column.table();
        }
    }

    private static Fixture fixture() {
        int n = TABLES.incrementAndGet();
        String table = "zz_rot_" + SUFFIX + "_" + n;
        jdbc.execute("CREATE TABLE " + table + " (id bigint PRIMARY KEY, secret text NOT NULL)");
        return new Fixture(new EncryptedColumn("items", table, "id", "secret", PURPOSE, "ZZ_ROTATION"), new TestDefinition("zz-dkr-" + SUFFIX + "-" + n));
    }

    /** 평문을 그 키로 잠가 넣는다 — 행 ID(오름차순) */
    private static List<Long> insert(Fixture fixture, DataCipher with, String... plains) {
        List<Long> created = new ArrayList<>();
        for (String plain : plains) {
            long id = ROW_IDS.incrementAndGet();
            jdbc.update("INSERT INTO " + fixture.table() + " (id, secret) VALUES (?, ?)", id, with.encrypt(PURPOSE, plain));
            created.add(id);
        }
        return created;
    }

    private static String secretOf(Fixture fixture, long id) {
        return jdbc.queryForObject("SELECT secret FROM " + fixture.table() + " WHERE id = ?", String.class, id);
    }

    private static void setting(String jobKey, String param, long value) {
        jdbc.update("INSERT INTO batch_params (job_key, param_key, param_value) VALUES (?, ?, ?) ON CONFLICT (job_key, param_key) DO UPDATE SET param_value = EXCLUDED.param_value",
                jobKey, param, value);
    }

    private static BatchRunner runner(DataKeyRotationJob job) {
        return new BatchRunner(new BatchLockService(dataSource), new BatchRunRecorder(dataSource, transactions, ids, NODE), new BatchFailureRecorder(dataSource, transactions, ids),
                BatchRegistry.forJobs(List.of(job)), transactions);
    }

    private static RunReport run(DataKeyRotationJob job, BatchRequestKind kind, Long sourceRunId) {
        return runner(job).runRequested(new BatchRunner.RunRequest(job.definition(), kind, null, BatchParameters.empty(), null, sourceRunId, null));
    }

    private static Map<String, Object> step(long runId, String step) {
        return jdbc.queryForMap("SELECT * FROM batch_run_steps WHERE run_id = ? AND step_name = ?", runId, step);
    }

    private static List<Map<String, Object>> failures(long runId) {
        return jdbc.queryForList("SELECT * FROM batch_run_failures WHERE run_id = ? ORDER BY id", runId);
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    // ── 규칙 ───────────────────────────────────────────────────────────────

    @Test
    void 옛_키_암호문은_새_키로_바뀌고_값은_그대로_이미_지금_키인_행은_고르지도_바꾸지도_않는다() {
        Fixture fixture = fixture();
        List<Long> old = insert(fixture, cipher(K1), "a@example.com", "b@example.com", "c@example.com");
        List<Long> current = insert(fixture, cipher(K2, K1), "d@example.com", "e@example.com");
        Map<Long, String> currentBefore = new LinkedHashMap<>();
        current.forEach(id -> currentBefore.put(id, secretOf(fixture, id)));
        DataCipher rotating = cipher(K2, K1);
        DataKeyRotationJob job = new DataKeyRotationJob(fixture.definition(), List.of(fixture.column()), dataSource, rotating);

        RunReport report = run(job, BatchRequestKind.NOW, null);

        assertThat(report.status()).isEqualTo(BatchRunStatus.OK);
        Map<String, Object> step = step(report.runId(), "items");
        assertThat(number(step.get("read_count"))).as("지금 키인 행은 읽지 않는다").isEqualTo(3);
        assertThat(number(step.get("processed_count"))).isEqualTo(3);
        DataCipher newOnly = cipher(K2);
        for (int i = 0; i < old.size(); i++) {
            String now = secretOf(fixture, old.get(i));
            assertThat(now).startsWith("d2:");
            assertThat(newOnly.decrypt(PURPOSE, now)).isEqualTo(List.of("a@example.com", "b@example.com", "c@example.com").get(i));
        }
        currentBefore.forEach((id, before) -> assertThat(secretOf(fixture, id)).as("지금 키인 행은 그대로").isEqualTo(before));

        RunReport again = run(job, BatchRequestKind.NOW, null);
        assertThat(again.status()).as("할 일이 없으면 건너뜀").isEqualTo(BatchRunStatus.SKIP);
        assertThat(number(step(again.runId(), "items").get("read_count"))).isZero();
        assertThat(jdbc.queryForObject("SELECT message_key FROM batch_runs WHERE id = ?", String.class, again.runId())).isEqualTo("batch.result.dataKeyRotation.none");
    }

    @Test
    void 저장은_조건부_UPDATE_라_읽은_뒤_다른_곳이_바꾼_값은_덮어쓰지_않고_건너뜀으로_센다() throws Exception {
        Fixture fixture = fixture();
        List<Long> rows = insert(fixture, cipher(K1), "first@example.com", "raced@example.com", "third@example.com");
        long raced = rows.get(1);
        String concurrentValue = cipher(K2).encrypt(PURPOSE, "changed@example.com");
        // 다시 잠그는 순간(읽은 뒤·저장 전)에 다른 연결이 그 행을 새 값으로 바꾼다
        DataCipher racing = new DataCipher(K2, K1) {
            @Override
            public String reencrypt(DataPurpose purpose, String cipherText) {
                if (decrypt(purpose, cipherText).equals("raced@example.com")) {
                    try (Connection other = dataSource.getConnection(); PreparedStatement update = other.prepareStatement("UPDATE " + fixture.table() + " SET secret = ? WHERE id = ?")) {
                        other.setAutoCommit(true);
                        update.setString(1, concurrentValue);
                        update.setLong(2, raced);
                        update.executeUpdate();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                return super.reencrypt(purpose, cipherText);
            }
        };
        DataKeyRotationJob job = new DataKeyRotationJob(fixture.definition(), List.of(fixture.column()), dataSource, racing);

        RunReport report = run(job, BatchRequestKind.NOW, null);

        assertThat(report.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(secretOf(fixture, raced)).as("다른 곳이 쓴 값이 남는다").isEqualTo(concurrentValue);
        assertThat(cipher(K2).decrypt(PURPOSE, secretOf(fixture, raced))).isEqualTo("changed@example.com");
        Map<String, Object> step = step(report.runId(), "items");
        assertThat(number(step.get("processed_count"))).isEqualTo(2);
        assertThat(number(step.get("skipped_count"))).isEqualTo(1);
        assertThat(failures(report.runId())).isEmpty();
    }

    @Test
    void 풀_수_없는_행은_그_건만_실패로_남고_나머지는_바뀐다_문구에_값과_암호문이_없다() {
        Fixture fixture = fixture();
        List<Long> good = insert(fixture, cipher(K1), "ok1@example.com", "ok2@example.com");
        List<Long> lost = insert(fixture, cipher(K3), "lost@example.com"); // 버전 3 키는 설정에 없다
        long broken = ROW_IDS.incrementAndGet();
        jdbc.update("INSERT INTO " + fixture.table() + " (id, secret) VALUES (?, 'd1:QUJDREVGR0hJSktMTU5PUFFSU1RVVldY')", broken); // 키는 있지만 깨진 암호문
        List<Long> more = insert(fixture, cipher(K1), "ok3@example.com");
        DataKeyRotationJob job = new DataKeyRotationJob(fixture.definition(), List.of(fixture.column()), dataSource, cipher(K2, K1));

        RunReport report = run(job, BatchRequestKind.NOW, null);

        assertThat(report.status()).isEqualTo(BatchRunStatus.OK);
        for (long id : List.of(good.get(0), good.get(1), more.get(0))) {
            assertThat(secretOf(fixture, id)).startsWith("d2:");
        }
        assertThat(secretOf(fixture, lost.get(0))).startsWith("d3:");
        assertThat(secretOf(fixture, broken)).startsWith("d1:");
        List<Map<String, Object>> failed = failures(report.runId());
        assertThat(failed).extracting(row -> row.get("target_id")).containsExactly(Long.toString(lost.get(0)), Long.toString(broken));
        assertThat(failed).allSatisfy(row -> {
            assertThat(row.get("error_code")).isEqualTo(DataKeyRotationJob.UNREADABLE);
            assertThat(row.get("target_type")).isEqualTo("ZZ_ROTATION");
            assertThat(row.get("step_name")).isEqualTo("items");
            assertThat((String) row.get("message")).doesNotContain("@example.com").doesNotContain("QUJD").doesNotContain(K1.substring(2, 12));
        });
        assertThat((String) failed.get(0).get("message")).contains("3");
        assertThat(number(step(report.runId(), "items").get("failed_count"))).isEqualTo(2);

        // 잃어버린 키를 예전 키로 다시 넣고 「실패 건만 다시」 — 그 건은 바뀌고, 깨진 암호문은 또 실패
        DataKeyRotationJob retried = new DataKeyRotationJob(fixture.definition(), List.of(fixture.column()), dataSource, cipher(K2, K1, K3));
        RunReport retry = run(retried, BatchRequestKind.RETRY_FAILED, report.runId());
        assertThat(cipher(K2).decrypt(PURPOSE, secretOf(fixture, lost.get(0)))).isEqualTo("lost@example.com");
        assertThat(failures(retry.runId())).extracting(row -> row.get("target_id")).containsExactly(Long.toString(broken));
    }

    @Test
    void 실패로_멈추면_체크포인트_다음부터_이어_받는다() {
        Fixture fixture = fixture();
        setting(fixture.definition().key(), "chunk_size", 2);
        setting(fixture.definition().key(), "failure_limit", 0);
        DataCipher k1 = cipher(K1);
        List<Long> head = insert(fixture, k1, "r1@example.com", "r2@example.com", "r3@example.com");
        List<Long> lost = insert(fixture, cipher(K3), "r4@example.com"); // 두 번째 덩어리 — 실패 한도 0 이라 여기서 멈춘다
        List<Long> tail = insert(fixture, k1, "r5@example.com", "r6@example.com");

        RunReport first = run(new DataKeyRotationJob(fixture.definition(), List.of(fixture.column()), dataSource, cipher(K2, K1)), BatchRequestKind.NOW, null);

        assertThat(first.status()).isEqualTo(BatchRunStatus.FAIL);
        String checkpoint = (String) step(first.runId(), "items").get("checkpoint");
        assertThat(checkpoint).as("끝낸 첫 덩어리의 마지막 행").isEqualTo(Long.toString(head.get(1)));
        assertThat(secretOf(fixture, head.get(0))).startsWith("d2:");
        assertThat(secretOf(fixture, tail.get(0))).as("멈춘 뒤의 행은 아직").startsWith("d1:");

        // 체크포인트 앞 번호에 옛 키 행이 새로 생겼다 — 이어 받기는 체크포인트 다음부터 읽으므로 건드리지 않는다
        long earlier = head.get(0) - 1;
        jdbc.update("INSERT INTO " + fixture.table() + " (id, secret) VALUES (?, ?)", earlier, k1.encrypt(PURPOSE, "r0@example.com"));
        RunReport resumed = run(new DataKeyRotationJob(fixture.definition(), List.of(fixture.column()), dataSource, cipher(K2, K1, K3)), BatchRequestKind.RESUME,
                first.runId());

        assertThat(resumed.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(jdbc.queryForObject("SELECT run_mode FROM batch_runs WHERE id = ?", String.class, resumed.runId())).isEqualTo("RESUME");
        for (long id : List.of(head.get(2), lost.get(0), tail.get(0), tail.get(1))) {
            assertThat(secretOf(fixture, id)).startsWith("d2:");
        }
        assertThat(secretOf(fixture, earlier)).as("체크포인트 앞은 읽지 않았다").startsWith("d1:");

        RunReport fresh = run(new DataKeyRotationJob(fixture.definition(), List.of(fixture.column()), dataSource, cipher(K2, K1)), BatchRequestKind.NOW, null);
        assertThat(number(step(fresh.runId(), "items").get("processed_count"))).as("처음부터 다시 돌리면 남은 것만").isEqualTo(1);
        assertThat(secretOf(fixture, earlier)).startsWith("d2:");
    }

    @Test
    void 지금_키가_없으면_실행_전체가_실패하고_아무것도_바꾸지_않는다() {
        Fixture fixture = fixture();
        List<Long> rows = insert(fixture, cipher(K1), "x@example.com");
        DataKeyRotationJob job = new DataKeyRotationJob(fixture.definition(), List.of(fixture.column()), dataSource, new DataCipher("", ""));

        RunReport report = run(job, BatchRequestKind.NOW, null);

        assertThat(report.status()).isEqualTo(BatchRunStatus.FAIL);
        assertThat(secretOf(fixture, rows.get(0))).startsWith("d1:");
        assertThatThrownBy(() -> new DataCipher("", "").requireConfigured()).isInstanceOf(CommonException.class);
    }

    @Test
    void 키_교체_절차_새_키_배치_예전_키_비움이_실제_칸_목록으로_끝까지_된다() throws IOException {
        // 0) 옛 키(버전 1)만 있던 때 저장한 탈퇴 회원 연락처
        DataCipher before = cipher(K1);
        WithdrawnContactRows contactRows = new WithdrawnContactRows(dataSource);
        long[] members = {ids.nextId(), ids.nextId()};
        for (long member : members) {
            contactRows.upsert(member, before.encrypt(WithdrawnContactStore.EMAIL, "left-" + member + "@example.com"), Instant.now(), Instant.now().plusSeconds(86_400));
        }
        // 1) 새 키를 버전을 올려 지금 키로, 옛 키를 예전 키로 두고 재기동  2) 배치(실제 카탈로그 줄·실제 칸 목록)
        setting(BatchCatalog.DATA_KEY_ROTATION.key(), "failure_limit", 100_000); // 같은 DB 의 다른 시험이 남긴(이 키로 풀 수 없는) 행은 건별 실패로 넘긴다
        DataKeyRotationJob job = new DataKeyRotationJob(dataSource, cipher(K2, K1));
        assertThat(job.definition()).isEqualTo(BatchCatalog.DATA_KEY_ROTATION);
        RunReport report = run(job, BatchRequestKind.NOW, null);
        jdbc.update("DELETE FROM batch_params WHERE job_key = ? AND param_key = 'failure_limit'", BatchCatalog.DATA_KEY_ROTATION.key());
        assertThat(report.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(failures(report.runId())).extracting(row -> row.get("target_id")).doesNotContain(Long.toString(members[0]), Long.toString(members[1]));

        // 3) 예전 키를 비운 뒤에도 읽힌다 — 열람 길(WithdrawnContactStore)로
        DataCipher after = cipher(K2);
        List<AuditEvent> audited = new ArrayList<>();
        AuditLogService audit = new AuditLogService(null, null) {
            @Override
            public void record(AuditEvent event) {
                audited.add(event);
            }
        };
        WithdrawnContactStore store = new WithdrawnContactStore(contactRows, after, audit, new WithdrawalProperties(30, 365, 5, 10, 10, ""),
                new StaticListableBeanFactory().getBeanProvider(Clock.class));
        for (long member : members) {
            assertThat(contactRows.find(member).orElseThrow().emailCipher()).startsWith("d2:");
            assertThat(store.reveal(member, "키 교체 확인", 1L, "127.0.0.1").email()).isEqualTo("left-" + member + "@example.com");
        }
        assertThat(audited).hasSize(2);
        // 옛 키만 가진 서버는 이제 풀지 못한다(키가 정말 바뀌었다)
        assertThatThrownBy(() -> before.decrypt(WithdrawnContactStore.EMAIL, contactRows.find(members[0]).orElseThrow().emailCipher())).isInstanceOf(CommonException.class);
    }

    @Test
    void 키_교체_절차를_거친_회원_주소록은_예전_키를_비운_서버에서_주소록_화면_길로_그대로_읽힌다() {
        // 0) 옛 키(버전 1)만 있던 때 주소록 저장 길(AddressBookService.create)로 저장 — 상세 주소·메모가 있는 것과 없는 것
        MemberAddressStore store = new MemberAddressStore(dataSource);
        long member = ids.nextId();
        jdbc.update("INSERT INTO users (id, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, 'v1', now(), now())", member);
        AddressBookService before = new AddressBookService(store, cipher(K1), ids, new StaticListableBeanFactory().getBeanProvider(Clock.class));
        SavedAddress full = before.create(member, new AddressInput("집", "홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101동 1001호", "문 앞에 두세요", true));
        SavedAddress bare = before.create(member, new AddressInput(null, "김철수", "01098765432", "48058", "부산 해운대구 센텀로 2", null, null, false));

        // 1) 새 키를 지금 키로, 옛 키를 예전 키로  2) 실제 칸 목록으로 배치
        setting(BatchCatalog.DATA_KEY_ROTATION.key(), "failure_limit", 100_000); // 같은 DB 의 다른 시험이 남긴(이 키로 풀 수 없는) 행은 건별 실패로 넘긴다
        RunReport report = run(new DataKeyRotationJob(dataSource, cipher(K2, K1)), BatchRequestKind.NOW, null);
        jdbc.update("DELETE FROM batch_params WHERE job_key = ? AND param_key = 'failure_limit'", BatchCatalog.DATA_KEY_ROTATION.key());
        assertThat(report.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(failures(report.runId())).extracting(row -> row.get("target_id")).doesNotContain(Long.toString(full.id()), Long.toString(bare.id()));

        // 3) 예전 키를 비운 서버의 주소록 목록 — 값이 그대로
        List<SavedAddress> after = new AddressBookService(store, cipher(K2), ids, new StaticListableBeanFactory().getBeanProvider(Clock.class)).list(member);
        assertThat(after).extracting(SavedAddress::id).containsExactly(full.id(), bare.id());
        assertThat(after.get(0)).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(full);
        assertThat(after.get(1)).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(bare);
        Map<String, Object> row = jdbc.queryForMap("SELECT recipient_enc, phone_enc, address1_enc, address2_enc, memo_enc FROM member_addresses WHERE id = ?", full.id());
        assertThat(row.values()).allSatisfy(value -> assertThat((String) value).startsWith("d2:"));
        assertThat(jdbc.queryForMap("SELECT address2_enc, memo_enc FROM member_addresses WHERE id = ?", bare.id()).values()).as("빈 칸은 그대로 비어 있다").containsOnlyNulls();
        // 옛 키만 가진 서버는 이제 풀지 못한다
        assertThatThrownBy(() -> before.list(member)).isInstanceOf(CommonException.class);
    }

    @Test
    void 목록의_모든_칸이_실제_표_모양에서_새_키로_바뀌고_예전_키를_비워도_그_칸의_용도로_읽힌다() {
        // 표마다 실제 표와 같은 칸의 사본(제약·외래 키 없이 — 다른 시험의 행이 섞이지 않게)을 만들고, 목록 칸을 같은 이름의 사본 칸으로 옮긴다
        Map<String, String> copies = new LinkedHashMap<>();
        for (String table : DataKeyColumns.ALL.stream().map(EncryptedColumn::table).distinct().toList()) {
            String copy = "zz_rotc_" + SUFFIX + "_" + TABLES.incrementAndGet();
            jdbc.execute("CREATE TABLE " + copy + " (LIKE " + table + ")");
            for (String column : jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ? AND is_nullable = 'NO'",
                    String.class, copy)) {
                jdbc.execute("ALTER TABLE " + copy + " ALTER COLUMN " + column + " DROP NOT NULL");
            }
            copies.put(table, copy);
        }
        List<EncryptedColumn> columns = DataKeyColumns.ALL.stream()
                .map(c -> new EncryptedColumn(c.step(), copies.get(c.table()), c.idColumn(), c.cipherColumn(), c.purpose(), c.targetType())).toList();

        // 표마다 옛 키 행 둘 · 이미 새 키인 행 하나 · 칸을 비운(마스킹·파기) 행 하나
        DataCipher old = cipher(K1);
        DataCipher current = cipher(K2);
        Map<String, Map<Long, String>> expected = new LinkedHashMap<>(); // 「표.칸」 → 행 → 평문
        Map<String, Long> emptied = new LinkedHashMap<>();
        Map<String, Long> alreadyCurrent = new LinkedHashMap<>();
        copies.forEach((table, copy) -> {
            List<EncryptedColumn> ofTable = columns.stream().filter(c -> c.table().equals(copy)).toList();
            String idColumn = ofTable.get(0).idColumn();
            String names = ofTable.stream().map(EncryptedColumn::cipherColumn).reduce((a, b) -> a + ", " + b).orElseThrow();
            String marks = ofTable.stream().map(c -> "?").reduce((a, b) -> a + ", " + b).orElseThrow();
            for (DataCipher with : List.of(old, old, current)) {
                long id = ROW_IDS.incrementAndGet();
                List<Object> values = new ArrayList<>(List.of(id));
                for (EncryptedColumn column : ofTable) {
                    String plain = column.step() + " 평문 " + id;
                    values.add(with.encrypt(column.purpose(), plain));
                    expected.computeIfAbsent(copy + "." + column.cipherColumn(), k -> new LinkedHashMap<>()).put(id, plain);
                }
                jdbc.update("INSERT INTO " + copy + " (" + idColumn + ", " + names + ") VALUES (?, " + marks + ")", values.toArray());
                if (with == current) {
                    alreadyCurrent.put(copy, id);
                }
            }
            long id = ROW_IDS.incrementAndGet();
            jdbc.update("INSERT INTO " + copy + " (" + idColumn + ") VALUES (?)", id);
            emptied.put(copy, id);
        });
        Map<String, String> currentBefore = new LinkedHashMap<>();
        columns.forEach(c -> currentBefore.put(c.table() + "." + c.cipherColumn(), cell(c, alreadyCurrent.get(c.table()))));

        Fixture fixture = fixture(); // 배치 키만 빌린다
        RunReport report = run(new DataKeyRotationJob(fixture.definition(), columns, dataSource, cipher(K2, K1)), BatchRequestKind.NOW, null);

        assertThat(report.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(failures(report.runId())).isEmpty();
        Map<String, Object> runRow = jdbc.queryForMap("SELECT affected_count, read_count FROM batch_runs WHERE id = ?", report.runId());
        assertThat(number(runRow.get("affected_count"))).as("실행 줄 건수는 첫 단계가 아니라 단계 합").isEqualTo(2L * columns.size());
        assertThat(number(runRow.get("read_count"))).isEqualTo(2L * columns.size());
        for (EncryptedColumn column : columns) {
            String name = column.table() + "." + column.cipherColumn();
            assertThat(number(step(report.runId(), column.step()).get("processed_count"))).as(column.step() + " — 옛 키 행만").isEqualTo(2);
            expected.get(name).forEach((id, plain) -> {
                String now = cell(column, id);
                assertThat(now).as(name).startsWith("d2:");
                assertThat(current.decrypt(column.purpose(), now)).as(name).isEqualTo(plain);
            });
            assertThat(cell(column, alreadyCurrent.get(column.table()))).as(name + " — 이미 새 키인 행은 그대로").isEqualTo(currentBefore.get(name));
            assertThat(cell(column, emptied.get(column.table()))).as(name + " — 비운 칸은 고르지 않는다").isNull();
            long anyOld = expected.get(name).keySet().iterator().next();
            assertThatThrownBy(() -> old.decrypt(column.purpose(), cell(column, anyOld))).as(name + " — 옛 키만으로는 못 푼다").isInstanceOf(CommonException.class);
        }
    }

    private static String cell(EncryptedColumn column, long id) {
        return jdbc.queryForObject("SELECT " + column.cipherColumn() + " FROM " + column.table() + " WHERE " + column.idColumn() + " = ?", String.class, id);
    }

    /** {@code DataCipher} 가 아니라 설정 비밀값 키(ROBOYAM_CRYPTO_KEY, {@code SecretEncryptor} — {@code v1:} 암호문)로 잠그는 칸 */
    private static final Set<String> SECRET_ENCRYPTOR_COLUMNS = Set.of("portone_settings.value_cipher", "public_data_settings.value_cipher",
            "social_provider_settings.client_secret_enc", "mail_settings.password_enc", "notification_channels.bot_token_enc",
            "notification_channels.webhook_url_enc");

    /** 칸은 있지만 아직 저장하는 코드가 없다 — 저장을 시작하면 여기서 빼고 {@link DataKeyColumns} 에 올린다 */
    private static final Set<String> NOT_WRITTEN_YET = Set.of("group_buy_participations.cash_receipt_no_enc");

    @Test
    void 스키마의_암호문_칸은_모두_교체_목록이나_설정_비밀값_칸으로_적혀_있다() {
        Map<String, String> schema = new LinkedHashMap<>(); // 「표.칸」 → 자료형
        jdbc.query("SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_schema = current_schema() AND column_name ~ '_(enc|cipher)$' "
                + "AND table_name NOT LIKE 'zz\\_%' ORDER BY table_name, column_name", rs -> {
                    schema.put(rs.getString(1) + "." + rs.getString(2), rs.getString(3));
                });
        Set<String> listed = new LinkedHashSet<>(DataKeyColumns.ALL.stream().map(c -> c.table() + "." + c.cipherColumn()).toList());

        assertThat(listed).as("교체 목록 칸은 스키마에 있다").isSubsetOf(schema.keySet());
        Set<String> unclassified = new TreeSet<>(schema.keySet());
        unclassified.removeAll(listed);
        unclassified.removeAll(SECRET_ENCRYPTOR_COLUMNS);
        unclassified.removeAll(NOT_WRITTEN_YET);
        assertThat(unclassified).as("DataCipher 로 잠그면 DataKeyColumns 에, SecretEncryptor 면 이 시험의 목록에 올린다").isEmpty();
        assertThat(NOT_WRITTEN_YET).isSubsetOf(schema.keySet());
        assertThat(SECRET_ENCRYPTOR_COLUMNS).isSubsetOf(schema.keySet());
        for (EncryptedColumn column : DataKeyColumns.ALL) {
            assertThat(schema.get(column.table() + "." + column.cipherColumn())).as(column.step()).isIn("text", "character varying");
            assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                    String.class, column.table(), column.idColumn())).as(column.step() + " 행 키").isEqualTo("bigint");
        }
    }

    @Test
    void 카탈로그_줄은_수동_실행_위주의_Java_배치이고_칸마다_고를_수_있는_단계_문구는_두_언어() throws IOException {
        BatchCatalog definition = BatchCatalog.DATA_KEY_ROTATION;
        assertThat(BatchCatalog.ofKey("data-key-rotation")).isEqualTo(definition);
        assertThat(definition.isJava()).isTrue();
        assertThat(definition.defaultEnabled()).as("예약은 기본 꺼짐").isFalse();
        assertThat(CronExpression.isValidExpression(definition.defaultCron())).isTrue();

        DataKeyRotationJob job = new DataKeyRotationJob(dataSource, cipher(K2));
        assertThat(job.resumable()).isTrue();
        assertThat(job.steps()).extracting(s -> s.name()).containsExactlyElementsOf(DataKeyColumns.ALL.stream().map(EncryptedColumn::step).toList());
        assertThat(job.steps()).allMatch(s -> s.selectable());
        assertThat(DataKeyColumns.ALL).extracting(EncryptedColumn::step).doesNotHaveDuplicates();
        assertThat(DataKeyColumns.ALL).extracting(c -> c.table() + "." + c.cipherColumn()).doesNotHaveDuplicates();
        assertThat(DataKeyColumns.ALL).extracting(EncryptedColumn::purpose).doesNotHaveDuplicates()
                .startsWith(WithdrawnContactStore.EMAIL)
                .contains(AddressBookService.RECIPIENT, AddressBookService.PHONE, AddressBookService.ADDRESS1, AddressBookService.ADDRESS2, AddressBookService.MEMO)
                .contains(GroupBuyJoinService.RECIPIENT, GroupBuyJoinService.PHONE, GroupBuyJoinService.ADDRESS1, GroupBuyJoinService.ADDRESS2, GroupBuyJoinService.MEMO);
        assertThat(DataKeyColumns.MEMBER_ADDRESS).extracting(EncryptedColumn::table).containsOnly("member_addresses");
        assertThat(DataKeyColumns.GROUP_BUY_ADDRESS).extracting(EncryptedColumn::table).containsOnly("group_buy_participations");
        assertThat(DataKeyColumns.WITHDRAWN_CONTACT_EMAIL.table()).isEqualTo("withdrawn_member_contacts");
        assertThat(DataKeyColumns.WITHDRAWN_CONTACT_EMAIL.cipherColumn()).isEqualTo("email_cipher");

        for (String language : List.of("ko", "en")) {
            Properties messages = new Properties();
            try (InputStream in = DataKeyRotationJobTest.class.getResourceAsStream("/messages/messages_" + language + ".properties")) {
                messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            List<String> keys = new ArrayList<>(List.of(definition.nameKey(), definition.descriptionKey(), "batch.result.dataKeyRotation",
                    "batch.result.dataKeyRotation.none"));
            DataKeyColumns.ALL.forEach(column -> {
                keys.add("batch.job." + definition.key() + ".step." + column.step());
                keys.add("batch.target." + column.targetType());
            });
            for (String key : keys) {
                assertThat(messages.getProperty(key)).as(language + " " + key).isNotBlank();
            }
        }
    }

    @Test
    void 칸_정의는_식별자_규칙을_지킨다() {
        assertThatThrownBy(() -> new EncryptedColumn("items", "bad;table", "id", "secret", PURPOSE, "ZZ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptedColumn("Items", "t", "id", "secret", PURPOSE, "ZZ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptedColumn("items", "t", "id", "secret", PURPOSE, "zz")).isInstanceOf(IllegalArgumentException.class);
    }
}
