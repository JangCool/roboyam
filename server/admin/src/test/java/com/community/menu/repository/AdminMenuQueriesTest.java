package com.community.menu.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.menu.dto.AdminMenuNode;
import com.community.menu.repository.master.AdminMenuQueries;
import com.community.mfa.MfaSpringTestBase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 메뉴 구성 저장소({@link AdminMenuQueries} — admin 스트림 V2, JPA·QueryDSL) — 실제 PostgreSQL. 표를 다른 시험과 같이 쓰므로 모두 한 트랜잭션 안에서 하고 되돌린다.
 * 「요청 하나 = 트랜잭션 하나」는 영속성 문맥을 비워(flush·clear) 흉내 낸다.
 */
class AdminMenuQueriesTest extends MfaSpringTestBase {

    @Autowired
    private AdminMenuQueries queries;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private SnowflakeIdGenerator snowflake;

    private static AdminMenuNode screen(String key, boolean visible) {
        return new AdminMenuNode(null, AdminMenuNode.SCREEN, null, "화면", "Screen", "pi pi-home", visible, AdminMenuNode.ADMIN_SCREEN, key, null, true, null, List.of());
    }

    private static AdminMenuNode url(String href, boolean newTab, String permission) {
        return new AdminMenuNode(null, AdminMenuNode.SCREEN, null, "링크", null, null, true, AdminMenuNode.URL, null, href, newTab, permission, List.of());
    }

    private static AdminMenuNode group(String kind, String labelKey, List<AdminMenuNode> children) {
        return new AdminMenuNode(null, kind, labelKey, null, null, "pi pi-folder", true, null, null, null, true, null, children);
    }

    private void inRolledBackTransaction(Runnable work) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            status.setRollbackOnly();
            work.run();
        });
    }

    private void nextRequest() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 전체_교체는_두_모드의_트리를_차례대로_넣고_같은_칸으로_읽히며_저장하면_버전이_오른다() {
        long operator = newOperator();
        inRolledBackTransaction(() -> {
            AdminMenuQueries.State before = queries.lockState();
            Map<String, List<AdminMenuNode>> wanted = new LinkedHashMap<>();
            wanted.put("normal", List.of(
                    group(AdminMenuNode.SECTION, "menu.a", List.of(screen("dashboard", true), group(AdminMenuNode.FOLDER, null,
                            List.of(screen("members", false), url("https://example.com/x", false, "site.read"))))),
                    group(AdminMenuNode.SECTION, "menu.b", List.of(url("https://example.com/y", true, null)))));
            wanted.put("settings", List.of(group(AdminMenuNode.SECTION, "menu.c", List.of(screen("site", true)))));

            Map<String, List<AdminMenuNode>> saved = queries.replace(wanted, node -> snowflake.nextId());
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            queries.saved(operator, now);
            nextRequest();

            assertThat(queries.load()).isEqualTo(saved);
            assertThat(saved.get("normal").get(0).children().get(1).children().get(1)).satisfies(node -> {
                assertThat(node.id()).isNotNull();
                assertThat(node.href()).isEqualTo("https://example.com/x");
                assertThat(node.newTab()).isFalse();
                assertThat(node.permission()).isEqualTo("site.read");
            });
            assertThat(saved.get("normal").get(0).children().get(1).children().get(0).visible()).isFalse();
            AdminMenuQueries.State after = queries.state();
            assertThat(after.version()).isEqualTo(before.version() + 1);
            assertThat(after.updatedAt()).isEqualTo(now);
        });
    }

    @Test
    void 다시_저장하면_있던_ID_를_다시_써서_차례를_바꿀_수_있다() {
        long operator = newOperator();
        inRolledBackTransaction(() -> {
            queries.lockState();
            Map<String, List<AdminMenuNode>> first = queries.replace(Map.of("normal", List.of(group(AdminMenuNode.SECTION, "menu.a",
                    List.of(screen("one", true), screen("two", true), screen("three", true))))), node -> snowflake.nextId());
            queries.saved(operator, Instant.now());
            nextRequest();

            AdminMenuNode section = queries.load().get("normal").get(0);
            List<AdminMenuNode> reversed = section.children().reversed();
            queries.lockState();
            Map<String, List<AdminMenuNode>> second = queries.replace(Map.of("normal", List.of(section.withIdAndChildren(section.id(), reversed))),
                    AdminMenuNode::id);
            queries.saved(operator, Instant.now());
            nextRequest();

            List<AdminMenuNode> loaded = queries.load().get("normal").get(0).children();
            assertThat(loaded).extracting(AdminMenuNode::targetKey).containsExactly("three", "two", "one");
            assertThat(loaded).extracting(AdminMenuNode::id)
                    .containsExactlyElementsOf(first.get("normal").get(0).children().reversed().stream().map(AdminMenuNode::id).toList());
            assertThat(queries.load()).isEqualTo(second);
        });
    }

    @Test
    void 상태_잠금은_다른_트랜잭션의_잠금_읽기를_막는다() throws Exception {
        inRolledBackTransaction(() -> {
            queries.lockState();
            CompletableFuture<Throwable> other = CompletableFuture.supplyAsync(() -> {
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(status -> {
                        status.setRollbackOnly();
                        new JdbcTemplate(dataSource).execute("SET LOCAL lock_timeout = '300ms'");
                        queries.lockState();
                    });
                    return null;
                } catch (Throwable e) {
                    return e;
                }
            });
            Throwable failure;
            try {
                failure = other.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            assertThat(failure).isNotNull();
            Throwable root = failure;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertThat(root.getMessage()).contains("lock timeout");
        });
    }
}
