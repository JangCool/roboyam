package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.groupbuy.repository.master.GroupBuyMemberQueries;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 회원 공구 목록·홈 가젯 저장소(3단계 2차 — {@code GroupBuyMemberService} 안의 SQL 을 QueryDSL 로 옮긴 것)가 <b>예전 SQL 과 같은 결과</b>(건수·차례·쪽)를 내는지 실제
 * PostgreSQL 에서 견준다. 예전 문장은 옮기기 전 서비스의 것을 그대로 적었다. 다른 시험이 남긴 공구도 함께 읽으므로 표 전체를 견준다(이 시험의 공구는 끝나면 카테고리를 숨긴다).
 */
class GroupBuyMemberQueriesParityTest extends CommerceApiTestBase {

    private static final String VISIBLE_FROM = " FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id JOIN group_buy_categories c ON c.id = p.category_id "
            + "LEFT JOIN group_buy_categories pc ON pc.id = c.parent_id WHERE g.published_at IS NOT NULL AND NOT c.hidden AND NOT COALESCE(pc.hidden, false) AND ";
    private static final String DEADLINE_ORDER = "COALESCE(g.extended_until, g.end_at), g.id";

    @Autowired
    private GroupBuyMemberQueries queries;

    private final List<Long> categories = new ArrayList<>();

    @AfterEach
    void restore() {
        categories.forEach(id -> jdbc.update("UPDATE group_buy_categories SET hidden = true WHERE id = ?", id));
    }

    @Test
    void 탭_목록과_홈_가젯_차례가_예전_SQL_과_같다() {
        long top = category(null, false);
        long child = category(top, false);
        long hiddenTop = category(null, true);
        long underHidden = category(hiddenTop, false);
        Instant now = Instant.now();
        for (long c : new long[] {top, child, hiddenTop, underHidden}) {
            long product = product(c);
            groupBuy(product, "DRAFT", now.plus(Duration.ofDays(3)), null, null, 0);
            groupBuy(product, "RECRUITING", now.plus(Duration.ofHours(5)), null, null, 3);
            groupBuy(product, "RECRUITING", now.plus(Duration.ofHours(2)), now.plus(Duration.ofHours(9)), null, 3);
            groupBuy(product, "CLOSING", now.minus(Duration.ofHours(1)), null, now.minus(Duration.ofMinutes(30)), 1);
            groupBuy(product, "CANCELED", now.minus(Duration.ofDays(2)), null, null, 0);
            groupBuy(product, "COMPLETED", now.minus(Duration.ofDays(9)), null, now.minus(Duration.ofDays(8)), 5);
        }

        for (GroupBuyMemberQueries.Phase phase : GroupBuyMemberQueries.Phase.values()) {
            String where = switch (phase) {
                case UPCOMING -> "g.status = 'DRAFT'";
                case ONGOING -> "g.status IN ('RECRUITING', 'CLOSING')";
                case ENDED -> "g.status NOT IN ('DRAFT', 'RECRUITING', 'CLOSING')";
            };
            String order = switch (phase) {
                case UPCOMING -> "g.start_at, g.id";
                case ONGOING -> DEADLINE_ORDER;
                case ENDED -> "g.closing_at DESC NULLS LAST, g.updated_at DESC, g.id DESC";
            };
            for (Long category : new Long[] {null, top, child, hiddenTop, underHidden}) {
                StringBuilder sql = new StringBuilder(VISIBLE_FROM).append(where);
                List<Object> args = new ArrayList<>();
                if (category != null) {
                    sql.append(" AND (c.id = ? OR c.parent_id = ?)");
                    args.add(category);
                    args.add(category);
                }
                assertThat(queries.count(phase, category)).as("수 %s %s", phase, category).isEqualTo(jdbc.queryForObject("SELECT count(*)" + sql, Long.class, args.toArray()));
                for (int page = 0; page < 3; page++) {
                    List<Object> pageArgs = new ArrayList<>(args);
                    pageArgs.add(3);
                    pageArgs.add(page * 3);
                    assertThat(queries.pageIds(phase, category, page * 3L, 3)).as("쪽 %s %s %d", phase, category, page)
                            .isEqualTo(jdbc.queryForList("SELECT g.id" + sql + " ORDER BY " + order + " LIMIT ? OFFSET ?", Long.class, pageArgs.toArray()));
                }
            }
        }
        for (GroupBuyMemberQueries.RecruitingSort sort : GroupBuyMemberQueries.RecruitingSort.values()) {
            String order = switch (sort) {
                case DEADLINE -> DEADLINE_ORDER;
                case NEWEST -> "g.start_at DESC, g.id DESC";
                case POPULAR -> "g.current_qty DESC, " + DEADLINE_ORDER;
            };
            assertThat(queries.recruitingIds(sort, 10)).as("가젯 %s", sort)
                    .isEqualTo(jdbc.queryForList("SELECT g.id" + VISIBLE_FROM + "g.status = 'RECRUITING' ORDER BY " + order + " LIMIT ?", Long.class, 10));
        }
    }

    private long category(Long parent, boolean hidden) {
        long id = ids.nextId();
        Timestamp t = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO group_buy_categories (id, parent_id, name, hidden, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)", id, parent, "패리티 " + id, hidden, t, t);
        categories.add(id);
        return id;
    }

    private long product(long category) {
        long id = ids.nextId();
        Timestamp t = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO group_buy_products (id, category_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", id, category, "패리티 상품 " + id, t, t);
        return id;
    }

    private void groupBuy(long product, String status, Instant endAt, Instant extendedUntil, Instant closingAt, int currentQty) {
        long id = ids.nextId();
        Instant start = endAt.minus(Duration.ofDays(1)).minusSeconds(id % 600);
        jdbc.update("INSERT INTO group_buys (id, product_id, title, group_price, shipping_policy_code, shipping_fee, return_shipping_fee, return_fee_mode, min_qty, max_qty, "
                        + "initial_max_qty, current_qty, start_at, end_at, extended_until, closing_at, fulfillment, ship_within_business_days, status, published_at, round_root_id, "
                        + "revision_no, created_at, updated_at) VALUES (?, ?, '패리티 공구', 10000, 'DEFAULT', 3000, 3000, 'ONE_WAY', 0, 10, 10, ?, ?, ?, ?, ?, 'IMMEDIATE', 3, ?, ?, ?, "
                        + "1, ?, ?)", id, product, currentQty, Timestamp.from(start), Timestamp.from(endAt), extendedUntil == null ? null : Timestamp.from(extendedUntil),
                closingAt == null ? null : Timestamp.from(closingAt), status, Timestamp.from(start), id, Timestamp.from(start), Timestamp.from(start));
    }
}
