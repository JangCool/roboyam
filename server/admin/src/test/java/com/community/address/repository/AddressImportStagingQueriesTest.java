package com.community.address.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.address.repository.master.AddressImportStagingQueries;
import com.community.testsupport.JpaTestContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주소 DB 적재 문장({@link AddressImportStagingQueries} — {@code AddressImportWorker} 에서 옮겼다)을 적재 한 번과 같은 차례로 한 트랜잭션에서 돌려 본다(실제 PostgreSQL).
 * 맞바꾼 결과를 트랜잭션 안에서 확인하고 <b>되돌린다</b> — DDL 도 트랜잭션이라 시험 DB 의 주소 표는 그대로 남는다.
 */
class AddressImportStagingQueriesTest {

    private static AddressImportStagingQueries staging;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;

    @BeforeAll
    static void setUp() {
        staging = JpaTestContext.create(AddressImportStagingQueries.class);
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        tx = new TransactionTemplate(JpaTestContext.transactions());
    }

    @Test
    void 비우기_넣기_붙이기_색인_맞바꾸기를_한_트랜잭션에서() {
        long loadId = 990_000_000_000L + (System.nanoTime() % 1_000_000L);
        tx.executeWithoutResult(status -> {
            long before = staging.countEntries();
            staging.truncateAll();
            staging.dropStagingIndexes();
            staging.insertRoads(List.of(
                    new Object[] {"M-1", "04524", "서울특별시", "중구", "", "세종대로", 110, 0, "서울시청", "서울특별시 중구 세종대로 110", "서울특별시 중구 세종대로 110 서울시청"},
                    new Object[] {"M-2", "04525", "서울특별시", "중구", "", "세종대로", 124, 0, "", "서울특별시 중구 세종대로 124", "서울특별시 중구 세종대로 124"}));
            staging.insertJibuns(List.<Object[]>of(new Object[] {"M-1", "태평로1가", "", false, 31, 0}));
            staging.insertEnglishes(List.<Object[]>of(new Object[] {"M-2", "124 Sejong-daero, Jung-gu, Seoul"}));

            staging.createMgmtNoIndex(loadId);
            staging.analyzeEntries();
            long[] range = staging.idRange();
            assertThat(range[1] - range[0] + 1).isEqualTo(2);
            staging.prepareJibunMerge();
            assertThat(staging.mergeJibun(range[0], range[1])).isEqualTo(1);
            staging.prepareEnglishMerge();
            assertThat(staging.mergeEnglish(range[0], range[1])).isEqualTo(1);
            staging.truncateSide();
            staging.createSearchIndex(loadId);
            staging.analyzeEntries();
            staging.swapIntoPlace();

            assertThat(staging.countEntries()).isEqualTo(2);
            Map<String, Object> m1 = jdbc.queryForMap("SELECT jibun_address, search_text FROM address_entries WHERE road_addr_mgmt_no = 'M-1'");
            assertThat(m1.get("jibun_address")).isEqualTo("태평로1가 31");
            assertThat((String) m1.get("search_text")).endsWith(" 태평로1가 31");
            assertThat(jdbc.queryForObject("SELECT road_address_en FROM address_entries WHERE road_addr_mgmt_no = 'M-2'", String.class))
                    .isEqualTo("124 Sejong-daero, Jung-gu, Seoul");
            // 옛 표는 스테이징 이름으로 돌아와 비었고, 새 색인은 맞바꾼 표를 따라갔다
            assertThat(jdbc.queryForObject("SELECT count(*) FROM address_entries_staging", Long.class)).isZero();
            assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'address_entries' AND schemaname = current_schema()", String.class))
                    .contains("ix_address_entries_" + loadId + "_mgmt_no", "ix_address_entries_" + loadId + "_search_trgm");
            // 다음 적재의 「기본 키 말고 모두 지우기」는 맞바꿔 들어온 옛 표의 색인을 지운다
            staging.dropStagingIndexes();
            assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'address_entries_staging' AND schemaname = current_schema()", String.class))
                    .allMatch(name -> name.endsWith("_pkey"));
            assertThat(before).isNotNegative();
            status.setRollbackOnly();
        });
    }
}
