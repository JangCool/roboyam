package com.community.home;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.testsupport.CommerceTestDatabase;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * shared V235 의 아이로빗 홈 손질(point-games-contract 「홈 가젯」) — 이미 설치된 DB 의 저장된 배치에서 오른쪽 「광고」(SQUARE_BANNER) 바로 아래에 「포인트 게임」 가젯을 넣고,
 * 그 아래 같은 열 가젯을 내리며(위로 당겨 정리 — 겹침 없음), 저장된 모바일 순서에서는 광고 바로 뒤에 둔다. 두 번 돌려도 하나만. 시험 DB 를 같이 쓰므로 한 트랜잭션에서 하고 되돌린다.
 */
class PointGamesHomeMigrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void 설치된_DB_의_아이로빗_홈에서_광고_바로_아래에_넣고_모바일_순서는_광고_뒤() throws Exception {
        DataSource ds = CommerceTestDatabase.migrated();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        String block = doBlock();
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.update("UPDATE installation SET status = 'COMPLETED', installed_at = coalesce(installed_at, now()), board_preset_applied_at = coalesce(board_preset_applied_at, now()) "
                    + "WHERE id = 1");
            jdbc.update("DELETE FROM home_layouts WHERE theme = 'irobit'");
            jdbc.update("DELETE FROM home_gadgets WHERE theme = 'irobit' AND page_id IS NULL AND NOT board_layout");
            gadget(jdbc, 9001, "POST_LIST", "{}");
            gadget(jdbc, 9002, "AD", "{\"adType\":\"SQUARE_BANNER\"}");
            gadget(jdbc, 9003, "GROUPS", "{\"count\":3}");
            gadget(jdbc, 9004, "GROUP_BUY", "{}");
            gadget(jdbc, 9005, "POST_LIST", "{\"sort\":\"POPULAR\"}");
            // 설치 프리셋과 같은 모양 — 본문 18칸 + 오른쪽 6칸(인기글 · 광고 h5 · 인기 소모임), 공동구매(x17)가 아래에
            String items = "[{\"gadgetId\":\"9001\",\"x\":0,\"y\":0,\"w\":18,\"h\":40},{\"gadgetId\":\"9005\",\"x\":18,\"y\":0,\"w\":6,\"h\":12},"
                    + "{\"gadgetId\":\"9002\",\"x\":18,\"y\":12,\"w\":6,\"h\":5},{\"gadgetId\":\"9003\",\"x\":18,\"y\":17,\"w\":6,\"h\":7},"
                    + "{\"gadgetId\":\"9004\",\"x\":17,\"y\":40,\"w\":7,\"h\":13}]";
            jdbc.update("INSERT INTO home_layouts (theme, columns, items, mobile_order) VALUES ('irobit', 24, ?::jsonb, ?::jsonb)", items, "[\"9001\",\"9005\",\"9002\",\"9003\"]");

            jdbc.execute(block);
            jdbc.execute(block); // 두 번째는 아무 일도 하지 않는다

            List<Map<String, Object>> games = jdbc.queryForList("SELECT id, title, desktop, mobile, config::text AS config, style ->> 'icon' AS icon FROM home_gadgets "
                    + "WHERE theme = 'irobit' AND type = 'POINT_GAMES'");
            assertThat(games).hasSize(1);
            String id = games.get(0).get("id").toString();
            assertThat(games.get(0).get("title")).isEqualTo("포인트 게임");
            assertThat(games.get(0).get("desktop")).isEqualTo(true);
            assertThat(games.get(0).get("mobile")).isEqualTo(true);
            assertThat(games.get(0).get("config")).isEqualTo("{}");
            assertThat(games.get(0).get("icon")).isEqualTo("pi-gift");

            Map<String, Object> row = jdbc.queryForMap("SELECT items::text AS items, mobile_order::text AS mobile_order FROM home_layouts WHERE theme = 'irobit'");
            JsonNode layout = read(row.get("items").toString());
            assertThat(item(layout, id)).containsExactly(18, 17, 6, 6);
            assertThat(item(layout, "9002")).as("광고는 그대로").containsExactly(18, 12, 6, 5);
            assertThat(item(layout, "9003")).as("인기 소모임은 새 가젯 아래로").containsExactly(18, 23, 6, 7);
            assertThat(item(layout, "9004")).as("공동구매는 자리 그대로(겹치지 않는다)").containsExactly(17, 40, 7, 13);
            assertNoOverlap(layout);
            List<String> order = new java.util.ArrayList<>();
            read(row.get("mobile_order").toString()).forEach(e -> order.add(e.asString()));
            assertThat(order).containsExactly("9001", "9005", "9002", id, "9003");
        });
    }

    @Test
    void 설치_전이면_손대지_않는다() throws Exception {
        DataSource ds = CommerceTestDatabase.migrated();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        String block = doBlock();
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.update("UPDATE installation SET status = 'PENDING' WHERE id = 1");
            jdbc.update("DELETE FROM home_layouts WHERE theme = 'irobit'");
            jdbc.update("DELETE FROM home_gadgets WHERE theme = 'irobit' AND page_id IS NULL AND NOT board_layout");
            gadget(jdbc, 9102, "AD", "{\"adType\":\"SQUARE_BANNER\"}");
            jdbc.update("INSERT INTO home_layouts (theme, columns, items) VALUES ('irobit', 24, '[{\"gadgetId\":\"9102\",\"x\":18,\"y\":0,\"w\":6,\"h\":5}]'::jsonb)");
            jdbc.execute(block);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM home_gadgets WHERE theme = 'irobit' AND type = 'POINT_GAMES'", Integer.class)).isZero();
        });
    }

    private static void gadget(JdbcTemplate jdbc, long id, String type, String config) {
        jdbc.update("INSERT INTO home_gadgets (id, theme, type, title, config) VALUES (?, 'irobit', ?, ?, ?::jsonb)", id, type, type, config);
    }

    private static String doBlock() throws Exception {
        try (InputStream in = PointGamesHomeMigrationTest.class.getResourceAsStream("/db/migration/shared/V235__point_games.sql")) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return sql.substring(sql.indexOf("DO $v234$"));
        }
    }

    private static JsonNode read(String json) {
        return JSON.readTree(json);
    }

    /** {x, y, w, h} */
    private static List<Integer> item(JsonNode items, String id) {
        for (JsonNode it : items) {
            if (id.equals(it.path("gadgetId").asString())) {
                return List.of(it.path("x").asInt(), it.path("y").asInt(), it.path("w").asInt(), it.path("h").asInt());
            }
        }
        throw new AssertionError("배치에 없다: " + id);
    }

    private static void assertNoOverlap(JsonNode items) {
        for (JsonNode a : items) {
            for (JsonNode b : items) {
                if (a == b) {
                    continue;
                }
                boolean cols = a.path("x").asInt() < b.path("x").asInt() + b.path("w").asInt() && b.path("x").asInt() < a.path("x").asInt() + a.path("w").asInt();
                boolean rows = a.path("y").asInt() < b.path("y").asInt() + b.path("h").asInt() && b.path("y").asInt() < a.path("y").asInt() + a.path("h").asInt();
                assertThat(cols && rows).as(a + " ↔ " + b).isFalse();
            }
        }
    }
}
