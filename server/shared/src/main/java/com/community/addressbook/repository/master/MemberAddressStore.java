package com.community.addressbook.repository.master;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회원 주소록 표 {@code member_addresses}(V139) — JDBC 행 레코드(공구 저장소와 같은 모양, 커머스 계약 2b-2 결정 12). 기본 배송지는 부분 유일 색인
 * {@code ux_member_addresses_default} 로 막으므로 「앞 기본 끄기 → 새 기본 켜기」 차례를 SQL 문 순서로 정해야 한다(JPA 변경 감지는 INSERT 를 UPDATE 보다 먼저 내보낸다).
 * 회원 단위 잠금({@link #lockMember})은 부르는 서비스의 트랜잭션 안에서만 뜻이 있다. 암호문 칸은 {@code toString} 에 싣지 않는다.
 */
@Repository
public class MemberAddressStore {

    /** 저장된 한 줄(암호문 그대로) */
    public record Row(long id, long userId, String label, String recipientEnc, String phoneEnc, String postcode, String address1Enc, String address2Enc, String memoEnc,
                      boolean isDefault, Instant createdAt, Instant updatedAt) {

        @Override
        public String toString() {
            return "MemberAddressStore.Row[id=" + id + ", userId=" + userId + ", default=" + isDefault + "]";
        }
    }

    /** 넣거나 고칠 값(암호문) */
    public record Values(String label, String recipientEnc, String phoneEnc, String postcode, String address1Enc, String address2Enc, String memoEnc) {

        @Override
        public String toString() {
            return "MemberAddressStore.Values[가림]";
        }
    }

    private static final String COLUMNS = "id, user_id, label, recipient_enc, phone_enc, postcode, address1_enc, address2_enc, memo_enc, is_default, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public MemberAddressStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 회원 하나의 주소록을 줄 세우는 어드바이저리 잠금(트랜잭션 범위) — 10개 한도·기본 하나를 동시 요청에도 지킨다. 키 = {@code hashtext('ab:<회원>')}.
     * 공구 주문 트랜잭션은 공구·주문 행을 잡은 뒤 이 잠금을 잡는다 — 이 잠금을 쥔 쪽은 주소록 행만 만지므로 거꾸로 기다리는 일이 없다.
     */
    public void lockMember(long userId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", "ab:" + userId);
    }

    /** 내 목록 — 기본 → 최근 고친 순 */
    public List<Row> list(long userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM member_addresses WHERE user_id = ? ORDER BY is_default DESC, updated_at DESC, id DESC", MemberAddressStore::row, userId);
    }

    public int count(long userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM member_addresses WHERE user_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** 내 배송지 하나(남의 것이면 비어 있다) */
    public Optional<Row> find(long userId, long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM member_addresses WHERE id = ? AND user_id = ?", MemberAddressStore::row, id, userId).stream().findFirst();
    }

    public void insert(long id, long userId, Values v, boolean isDefault, Instant now) {
        jdbc.update("INSERT INTO member_addresses (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, userId, v.label(), v.recipientEnc(), v.phoneEnc(),
                v.postcode(), v.address1Enc(), v.address2Enc(), v.memoEnc(), isDefault, Timestamp.from(now), Timestamp.from(now));
    }

    public void update(long userId, long id, Values v, Instant now) {
        jdbc.update("UPDATE member_addresses SET label = ?, recipient_enc = ?, phone_enc = ?, postcode = ?, address1_enc = ?, address2_enc = ?, memo_enc = ?, updated_at = ? "
                + "WHERE id = ? AND user_id = ?", v.label(), v.recipientEnc(), v.phoneEnc(), v.postcode(), v.address1Enc(), v.address2Enc(), v.memoEnc(),
                Timestamp.from(now), id, userId);
    }

    /** 이 배송지만 기본으로 — 앞 기본을 먼저 끄고 켠다(부분 유일 색인) */
    public void makeDefault(long userId, long id) {
        jdbc.update("UPDATE member_addresses SET is_default = false WHERE user_id = ? AND is_default AND id <> ?", userId, id);
        jdbc.update("UPDATE member_addresses SET is_default = true WHERE id = ? AND user_id = ? AND NOT is_default", id, userId);
    }

    /** 회원의 기본을 끈다(새 기본 배송지를 넣기 전) */
    public void clearDefault(long userId) {
        jdbc.update("UPDATE member_addresses SET is_default = false WHERE user_id = ? AND is_default", userId);
    }

    public void delete(long userId, long id) {
        jdbc.update("DELETE FROM member_addresses WHERE id = ? AND user_id = ?", id, userId);
    }

    /** 기본이 없으면 남은 것 중 가장 최근({@code updated_at}) 것을 기본으로 — 기본을 지운 뒤 */
    public void promoteNewestIfNoDefault(long userId) {
        jdbc.update("UPDATE member_addresses SET is_default = true WHERE id = (SELECT id FROM member_addresses WHERE user_id = ? ORDER BY updated_at DESC, id DESC LIMIT 1) "
                + "AND NOT EXISTS (SELECT 1 FROM member_addresses WHERE user_id = ? AND is_default)", userId, userId);
    }

    private static Row row(ResultSet rs, int i) throws SQLException {
        return new Row(rs.getLong("id"), rs.getLong("user_id"), rs.getString("label"), rs.getString("recipient_enc"), rs.getString("phone_enc"), rs.getString("postcode"),
                rs.getString("address1_enc"), rs.getString("address2_enc"), rs.getString("memo_enc"), rs.getBoolean("is_default"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
