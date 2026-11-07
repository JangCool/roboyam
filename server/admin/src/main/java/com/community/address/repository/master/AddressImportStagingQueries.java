package com.community.address.repository.master;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 주소 DB 적재({@code AddressImportWorker})의 DB 문장 — 스테이징 표({@code address_entries_staging}·{@code address_jibun_staging}·{@code address_english_staging})를 비우고 채우고,
 * 지번·영문을 붙이고, 색인을 만들고, 이름 세 번 바꾸기로 {@code address_entries} 와 맞바꾼다. <b>부르는 쪽 트랜잭션(적재 한 번 = 한 트랜잭션)에 참여한다</b> — 어디서 멈추든 되돌려진다.
 *
 * <p>Native SQL 인 이유: {@code TRUNCATE}·{@code CREATE/DROP INDEX}·{@code ANALYZE}·{@code ALTER TABLE … RENAME}·{@code SET LOCAL}·임시 표({@code CREATE TEMP TABLE … AS})는 DDL·DB
 * 명령이고, 수백만 줄 넣기는 JDBC 배치({@code batchUpdate}), 붙이기는 {@code UPDATE … FROM}(덩어리 범위), 색인 이름은 시스템 표({@code pg_index})에서 읽는다. 표 자체가 맞바꾸기로
 * 통째로 바뀌는 검색 전용 표라 엔티티를 두지 않는다({@link AddressEntryQueries} 와 같다). 색인 이름 접미사는 적재 번호(숫자)이고 값은 모두 바인딩한다.
 */
@Repository
public class AddressImportStagingQueries {

    private static final String INSERT_ROAD = """
            INSERT INTO address_entries_staging (road_addr_mgmt_no, zipcode, sido, sigungu, eupmyeondong, road_name, building_no_main, building_no_sub,
                                                 building_name, road_address, search_text)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INSERT_JIBUN = """
            INSERT INTO address_jibun_staging (road_addr_mgmt_no, beopjeong_dong, beopjeong_ri, is_mountain, jibun_main, jibun_sub) VALUES (?, ?, ?, ?, ?, ?)""";
    private static final String INSERT_ENGLISH = "INSERT INTO address_english_staging (road_addr_mgmt_no, road_address_en) VALUES (?, ?)";

    /** 관련지번을 관리번호마다 한 번만 모은다(덩어리마다 GROUP BY 를 되풀이하지 않게) — 트랜잭션이 끝나면 사라진다 */
    private static final String JIBUN_AGG = """
            CREATE TEMP TABLE address_jibun_agg ON COMMIT DROP AS
            SELECT road_addr_mgmt_no, string_agg(DISTINCT jibun_text, ', ' ORDER BY jibun_text) AS jibun_text
              FROM (SELECT road_addr_mgmt_no,
                           CASE WHEN beopjeong_ri <> '' THEN beopjeong_ri ELSE beopjeong_dong END || ' '
                           || CASE WHEN is_mountain THEN '산' ELSE '' END || jibun_main
                           || CASE WHEN jibun_sub > 0 THEN '-' || jibun_sub ELSE '' END AS jibun_text
                      FROM address_jibun_staging) j
             GROUP BY road_addr_mgmt_no""";
    private static final String MERGE_JIBUN = """
            UPDATE address_entries_staging e SET jibun_address = j.jibun_text, search_text = e.search_text || ' ' || j.jibun_text
              FROM address_jibun_agg j
             WHERE e.road_addr_mgmt_no = j.road_addr_mgmt_no AND e.id BETWEEN ? AND ?""";
    private static final String MERGE_ENGLISH = """
            UPDATE address_entries_staging e SET road_address_en = g.road_address_en, search_text = e.search_text || ' ' || g.road_address_en
              FROM address_english_staging g
             WHERE e.road_addr_mgmt_no = g.road_addr_mgmt_no AND e.id BETWEEN ? AND ?""";

    private final JdbcTemplate jdbc;

    public AddressImportStagingQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 세 스테이징 표를 비운다(도로명 표는 번호도 처음부터) */
    public void truncateAll() {
        jdbc.execute("TRUNCATE TABLE address_entries_staging RESTART IDENTITY");
        truncateSide();
    }

    /** 지번·영문 스테이징만 비운다(붙인 뒤) */
    public void truncateSide() {
        jdbc.execute("TRUNCATE TABLE address_jibun_staging, address_english_staging");
    }

    /** 스테이징의 색인은 표를 맞바꿀 때 따라다녀 이름이 제각각이다 — 기본 키 말고 모두 지운다 */
    public void dropStagingIndexes() {
        List<String> names = jdbc.queryForList("""
                SELECT i.relname FROM pg_index x JOIN pg_class i ON i.oid = x.indexrelid JOIN pg_class t ON t.oid = x.indrelid
                 WHERE t.relname = 'address_entries_staging' AND t.relnamespace = to_regnamespace(current_schema()) AND NOT x.indisprimary""", String.class);
        for (String name : names) {
            jdbc.execute("DROP INDEX " + quoteIdent(name));
        }
    }

    /** 지금 주소 DB 의 줄 수 */
    public Long countEntries() {
        return jdbc.queryForObject("SELECT count(*) FROM address_entries", Long.class);
    }

    /** 도로명 줄 덩어리(값 배열 — 칸 차례는 {@code INSERT_ROAD}) */
    public void insertRoads(List<Object[]> rows) {
        jdbc.batchUpdate(INSERT_ROAD, rows);
    }

    /** 관련지번 줄 덩어리(칸 차례는 {@code INSERT_JIBUN}) */
    public void insertJibuns(List<Object[]> rows) {
        jdbc.batchUpdate(INSERT_JIBUN, rows);
    }

    /** 영문 줄 덩어리(칸 차례는 {@code INSERT_ENGLISH}) */
    public void insertEnglishes(List<Object[]> rows) {
        jdbc.batchUpdate(INSERT_ENGLISH, rows);
    }

    /** 관리번호 색인(붙이기용) */
    public void createMgmtNoIndex(long loadId) {
        jdbc.execute("CREATE INDEX ix_address_entries_" + loadId + "_mgmt_no ON address_entries_staging (road_addr_mgmt_no)");
    }

    /** 검색 색인(pg_trgm GIN) */
    public void createSearchIndex(long loadId) {
        jdbc.execute("CREATE INDEX ix_address_entries_" + loadId + "_search_trgm ON address_entries_staging USING gin (search_text gin_trgm_ops)");
    }

    /** 방금 넣은 표의 통계 — 붙이기 전에 꼭(통계가 없으면 계획기가 「0줄」로 보고 중첩 루프로 짠다) */
    public void analyzeEntries() {
        jdbc.execute("ANALYZE address_entries_staging");
    }

    /** 관련지번을 관리번호마다 모은 임시 표를 만들고 통계를 낸다 */
    public void prepareJibunMerge() {
        jdbc.execute("ANALYZE address_jibun_staging");
        jdbc.execute(JIBUN_AGG);
        jdbc.execute("ANALYZE address_jibun_agg");
    }

    /** 영문 스테이징 통계 */
    public void prepareEnglishMerge() {
        jdbc.execute("ANALYZE address_english_staging");
    }

    /** 스테이징 id 범위 {최소, 최대}(비었으면 {0, -1}) */
    public long[] idRange() {
        return jdbc.queryForObject("SELECT coalesce(min(id), 0), coalesce(max(id), -1) FROM address_entries_staging", (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)});
    }

    /** id 범위 하나에 관련지번을 붙인다 — 바뀐 줄 수 */
    public int mergeJibun(long fromId, long toId) {
        return jdbc.update(MERGE_JIBUN, fromId, toId);
    }

    /** id 범위 하나에 영문 주소를 붙인다 — 바뀐 줄 수 */
    public int mergeEnglish(long fromId, long toId) {
        return jdbc.update(MERGE_ENGLISH, fromId, toId);
    }

    /**
     * 스테이징과 지금 표를 맞바꾸고 옛 표(이제 스테이징)를 비운다. 이름 바꾸기는 검색 중인 질의가 끝나기를 기다린다 — 너무 오래 막히면({@code lock_timeout} 15초) 예외로 멈춘다
     * (트랜잭션이 되돌려 지금 DB 는 그대로).
     */
    public void swapIntoPlace() {
        jdbc.execute("SET LOCAL lock_timeout = '15s'");
        jdbc.execute("ALTER TABLE address_entries RENAME TO address_entries_old");
        jdbc.execute("ALTER TABLE address_entries_staging RENAME TO address_entries");
        jdbc.execute("ALTER TABLE address_entries_old RENAME TO address_entries_staging");
        jdbc.execute("TRUNCATE TABLE address_entries_staging");
    }

    private static String quoteIdent(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
