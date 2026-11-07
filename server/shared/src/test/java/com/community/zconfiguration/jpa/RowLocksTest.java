package com.community.zconfiguration.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.debate.entity.PostStanceEntity;
import com.community.debate.entity.PostStanceId;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.MemberFixture;
import com.community.testsupport.RowLockProbe;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserEntity;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link RowLocks} 가 전환 전 네이티브와 같은 {@code FOR UPDATE} 세기로 잠그는가 — 실제 PostgreSQL. 다른 연결에서 {@code FOR KEY SHARE NOWAIT}(그 행을 FK 로 가리키는 INSERT 의
 * 확인과 같은 세기)가 55P03 으로 막히면 {@code FOR UPDATE} 다. 비교로 JPA {@code PESSIMISTIC_WRITE}({@code FOR NO KEY UPDATE})는 그것을 막지 않는다(처음부터 JPA 였던 잠금은
 * 그대로 그 세기다).
 */
class RowLocksTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(947);
    private static final QUserEntity U = QUserEntity.userEntity;

    private static TransactionTemplate tx;
    private static JdbcTemplate jdbc;
    private static DataSource dataSource;
    private static EntityManager entityManager;
    private static MemberFixture members;
    private static final List<Long> users = new ArrayList<>();
    private static final List<Long> posts = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        tx = new TransactionTemplate(JpaTestContext.transactions());
        dataSource = JpaTestContext.dataSource();
        jdbc = new JdbcTemplate(dataSource);
        entityManager = JpaTestContext.entityManager();
        members = new MemberFixture(JpaTestContext.context());
    }

    @AfterAll
    static void tearDown() {
        for (Long post : posts) {
            jdbc.update("DELETE FROM post_stances WHERE post_id = ?", post);
        }
        for (Long user : users) {
            jdbc.update("DELETE FROM post_stances WHERE user_id = ?", user);
            jdbc.update("DELETE FROM users WHERE id = ?", user);
        }
    }

    private static long user() {
        long id = IDS.nextId();
        members.user(id, "rowlocks-" + id + "@test.local");
        users.add(id);
        return id;
    }

    private static long post() {
        long id = IDS.nextId();
        posts.add(id);
        return id;
    }

    private static boolean keyShareBlocked(long userId) {
        return RowLockProbe.keyShareBlocked(dataSource, "users", "id = ?", userId);
    }

    /** 다른 연결에서 그 회원을 FK 로 가리키는 줄(토론 입장)을 넣어 본다 — 잠금을 0.5초까지 기다리고 막히면 false. 넣었으면 되돌린다 */
    private static boolean referencingInsertGoes(long userId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement st = connection.createStatement();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO post_stances (post_id, user_id, stance, created_at, updated_at) VALUES (?, ?, 'PRO', now(), now())")) {
                st.execute("SET LOCAL lock_timeout = '500ms'");
                insert.setLong(1, post());
                insert.setLong(2, userId);
                insert.executeUpdate();
                return true;
            } catch (SQLException e) {
                if ("55P03".equals(e.getSQLState())) {
                    return false;
                }
                throw new IllegalStateException(e);
            } finally {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 한_행_잠금은_FOR_UPDATE_다_그_행을_FK_로_가리키는_INSERT_도_기다린다() {
        long member = user();
        tx.executeWithoutResult(status -> {
            assertThat(RowLocks.forUpdate(entityManager, UserEntity.class, member)).isTrue();
            assertThat(keyShareBlocked(member)).as("FOR KEY SHARE NOWAIT 가 막힌다 — FOR UPDATE").isTrue();
            assertThat(referencingInsertGoes(member)).as("그 회원을 FK 로 가리키는 INSERT 도 기다린다").isFalse();
        });
        assertThat(keyShareBlocked(member)).as("트랜잭션이 끝나면 풀린다").isFalse();
        assertThat(tx.<Boolean>execute(status -> RowLocks.forUpdate(entityManager, UserEntity.class, IDS.nextId()))).as("없는 행").isFalse();
    }

    @Test
    void 비교_JPA_PESSIMISTIC_WRITE_는_FOR_NO_KEY_UPDATE_라_FK_확인을_막지_않는다() {
        long member = user();
        tx.executeWithoutResult(status -> {
            assertThat(JpaTestContext.query().select(U.id).from(U).where(U.id.eq(member)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst()).isEqualTo(member);
            assertThat(RowLockProbe.noKeyUpdateBlocked(dataSource, "users", "id = ?", member)).as("쓰기 잠금끼리는 막힌다").isTrue();
            assertThat(keyShareBlocked(member)).as("FOR KEY SHARE 는 막지 않는다 — FOR NO KEY UPDATE").isFalse();
            assertThat(referencingInsertGoes(member)).as("FK 로 가리키는 INSERT 는 기다리지 않는다").isTrue();
        });
    }

    @Test
    void 여러_행은_id_차례로_잠그고_있는_id_만_돌려준다() {
        long a = user();
        long b = user();
        long missing = IDS.nextId();
        tx.executeWithoutResult(status -> {
            List<Long> locked = RowLocks.forUpdate(entityManager, UserEntity.class, List.of(b, missing, a));
            assertThat(locked).containsExactly(Math.min(a, b), Math.max(a, b));
            assertThat(keyShareBlocked(a)).isTrue();
            assertThat(keyShareBlocked(b)).isTrue();
            assertThat(RowLocks.forUpdate(entityManager, UserEntity.class, List.<Long>of())).isEmpty();
        });
    }

    @Test
    void 복합_키는_키_클래스로_잠근다() {
        long member = user();
        long other = user();
        long post = post();
        jdbc.update("INSERT INTO post_stances (post_id, user_id, stance, created_at, updated_at) VALUES (?, ?, 'PRO', now(), now()), (?, ?, 'CON', now(), now())", post, member,
                post, other);
        tx.executeWithoutResult(status -> {
            assertThat(RowLocks.forUpdate(entityManager, PostStanceEntity.class, new PostStanceId(post, member))).isTrue();
            assertThat(RowLockProbe.keyShareBlocked(dataSource, "post_stances", "post_id = ? AND user_id = ?", post, member)).as("그 줄만 FOR UPDATE").isTrue();
            assertThat(RowLockProbe.keyShareBlocked(dataSource, "post_stances", "post_id = ? AND user_id = ?", post, other)).as("다른 회원 줄은 아니다").isFalse();
            assertThat(RowLocks.forUpdate(entityManager, PostStanceEntity.class, new PostStanceId(post, IDS.nextId()))).as("없는 줄").isFalse();
        });
    }

    @Test
    void SKIP_LOCKED_판은_FK_로_가리키며_넣는_중인_행도_건너뛴다_JPA_판은_건너뛰지_않는다() throws SQLException {
        long member = user();
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement keyShare = holder.prepareStatement("SELECT 1 FROM users WHERE id = ? FOR KEY SHARE")) {
                keyShare.setLong(1, member); // 다른 트랜잭션이 그 회원을 FK 로 가리키는 줄을 넣는 중인 것과 같은 잠금
                keyShare.executeQuery().close();
            }
            try {
                assertThat(tx.<Boolean>execute(status -> RowLocks.forUpdateSkipLocked(entityManager, UserEntity.class, member))).as("FOR UPDATE SKIP LOCKED — 건너뛴다").isFalse();
                assertThat(tx.<Long>execute(status -> JpaTestContext.query().select(U.id).from(U).where(U.id.eq(member)).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .setHint("jakarta.persistence.lock.timeout", -2).fetchFirst())).as("FOR NO KEY UPDATE SKIP LOCKED — 건너뛰지 않는다").isEqualTo(member);
            } finally {
                holder.rollback();
            }
        }
        assertThat(tx.<Boolean>execute(status -> RowLocks.forUpdateSkipLocked(entityManager, UserEntity.class, member))).as("놓이면 잡는다").isTrue();
    }

    @Test
    void 트랜잭션_밖이면_예외() {
        long member = user();
        assertThatThrownBy(() -> RowLocks.forUpdate(entityManager, UserEntity.class, member)).isInstanceOf(IllegalStateException.class);
    }
}
