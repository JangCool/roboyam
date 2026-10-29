package com.community.address.repository.master;

import com.community.address.dto.response.AddressSearchResultResponse;
import com.community.address.service.AddressSearchPolicy;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 자체 주소 DB({@code address_entries}) 검색.
 *
 * <p>Native SQL 인 이유: 낱말마다 {@code search_text ILIKE '%낱말%'} 를 AND 로 거는 질의가 pg_trgm GIN 색인({@code gin_trgm_ops})을 타야 하고, 표 자체가 적재 때
 * 이름 맞바꾸기(RENAME)로 통째로 바뀌는 검색 전용 표라 JPA 엔티티를 두지 않는다. 정렬은 {@code id}(파일 순서 — 시·도, 도로 순)라 넓은 검색어도
 * 앞에서 한 쪽만 읽고 멈출 수 있다. 한 질의는 {@value #QUERY_TIMEOUT_SECONDS}초를 넘기면 DB 가 끊는다(부르는 쪽이 「더 자세히」로 답한다).
 */
@Repository
public class AddressEntryQueries {

    static final int QUERY_TIMEOUT_SECONDS = 3;

    private final JdbcTemplate jdbc;

    public AddressEntryQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
    }

    /** 주소 DB 를 한 번이라도 넣었는가 */
    public boolean loaded() {
        Boolean loaded = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM address_entries)", Boolean.class);
        return Boolean.TRUE.equals(loaded);
    }

    /** 모든 낱말이 들어 있는 줄 — {@code id} 순 한 쪽 */
    public List<AddressSearchResultResponse> search(List<String> terms, int size, long offset) {
        List<Object> args = patterns(terms);
        args.add(size);
        args.add(offset);
        return jdbc.query("SELECT zipcode, road_address, jibun_address, building_name, road_address_en FROM address_entries WHERE " + where(terms)
                        + " ORDER BY id LIMIT ? OFFSET ?",
                (rs, n) -> new AddressSearchResultResponse(rs.getString(1), rs.getString(2), nullToEmpty(rs.getString(3)), nullToEmpty(rs.getString(4)), rs.getString(5)),
                args.toArray());
    }

    /** 맞는 줄 수 — {@code cap} 까지만 센다 */
    public long count(List<String> terms, int cap) {
        List<Object> args = patterns(terms);
        args.add(cap);
        Long count = jdbc.queryForObject("SELECT count(*) FROM (SELECT 1 FROM address_entries WHERE " + where(terms) + " LIMIT ?) capped", Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private static String where(List<String> terms) {
        return String.join(" AND ", terms.stream().map(term -> "search_text ILIKE ? ESCAPE '\\'").toList());
    }

    private static List<Object> patterns(List<String> terms) {
        List<Object> args = new ArrayList<>();
        terms.forEach(term -> args.add(AddressSearchPolicy.containsPattern(term)));
        return args;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
