package com.community.ad.repository.master;

import com.community.ad.dto.AdPlacements;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 광고 노출 자리 설정(V118 {@code ad_placement_settings} — 한 행, id = 1 · V119 {@code adult_enabled} 기본 끔). 행이 없으면 {@link AdPlacements#DEFAULTS} */
@Repository
public class AdPlacementStore {

    /** 중도 해지 수수료율 기본값(%, V127 — 행이 없을 때) */
    public static final int DEFAULT_STOP_FEE_RATE = 10;
    public static final int STOP_FEE_RATE_MIN = 0;
    public static final int STOP_FEE_RATE_MAX = 30;

    /**
     * 저장한 설정 + 마지막으로 고친 때·사람(운영자 이름은 지워졌으면 null) + 중도 해지 수수료율(V127 {@code stop_fee_rate} — 광고·사업자 핫딜 공통, 신청 건을 낼 때 그 건에 고정한다).
     * 수수료율은 공개 자리 설정({@link AdPlacements})에 싣지 않는다(공개는 {@code GET /api/business/refund-policy}).
     */
    public record Stored(AdPlacements value, Instant updatedAt, long updatedBy, String updatedByName, int stopFeeRate) {
    }

    private static final String SELECT = "SELECT s.*, o.operator_name FROM ad_placement_settings s LEFT JOIN operators o ON o.id = s.updated_by WHERE s.id = 1";

    private final JdbcTemplate jdbc;

    public AdPlacementStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<Stored> find() {
        return jdbc.query(SELECT, (rs, i) -> stored(rs)).stream().findFirst();
    }

    /** 저장 트랜잭션 안에서 행을 잠그고 읽는다(없으면 비어 있다) */
    public Optional<Stored> lock() {
        return jdbc.query("SELECT s.*, NULL AS operator_name FROM ad_placement_settings s WHERE s.id = 1 FOR UPDATE", (rs, i) -> stored(rs)).stream().findFirst();
    }

    /** 지금 설정 — 저장한 적 없으면 기본값 */
    public AdPlacements current() {
        return find().map(Stored::value).orElse(AdPlacements.DEFAULTS);
    }

    /** 지금 중도 해지 수수료율(%) — 저장한 적 없으면 기본 10 */
    public int stopFeeRate() {
        return jdbc.query("SELECT stop_fee_rate FROM ad_placement_settings WHERE id = 1", (rs, i) -> rs.getInt(1)).stream().findFirst().orElse(DEFAULT_STOP_FEE_RATE);
    }

    /** 설정 + 중도 해지 수수료율을 함께 저장한다(관리자 「광고 기본 설정」) */
    public void save(AdPlacements p, int stopFeeRate, long operatorId, Instant now) {
        save(p, operatorId, now);
        jdbc.update("UPDATE ad_placement_settings SET stop_fee_rate = ? WHERE id = 1", stopFeeRate);
    }

    /** 자리 설정만 저장한다 — 수수료율은 그대로(처음 저장이면 기본 10) */
    public void save(AdPlacements p, long operatorId, Instant now) {
        jdbc.update("INSERT INTO ad_placement_settings (id, post_inline_enabled, post_inline_type, post_inline_position, wing_left, wing_right, wing_screens, "
                        + "board_list_enabled, board_list_after, adult_enabled, updated_at, updated_by) VALUES (1, ?, ?, ?, ?, ?, string_to_array(?, ','), ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET post_inline_enabled = EXCLUDED.post_inline_enabled, post_inline_type = EXCLUDED.post_inline_type, "
                        + "post_inline_position = EXCLUDED.post_inline_position, wing_left = EXCLUDED.wing_left, wing_right = EXCLUDED.wing_right, "
                        + "wing_screens = EXCLUDED.wing_screens, board_list_enabled = EXCLUDED.board_list_enabled, board_list_after = EXCLUDED.board_list_after, "
                        + "adult_enabled = EXCLUDED.adult_enabled, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by",
                p.postInline().enabled(), p.postInline().adType(), p.postInline().position(), p.wings().left(), p.wings().right(),
                String.join(",", p.wings().screens()), p.boardList().enabled(), p.boardList().after(), p.adult().enabled(), Timestamp.from(now), operatorId);
    }

    private static Stored stored(ResultSet rs) throws SQLException {
        Array screens = rs.getArray("wing_screens");
        List<String> list = screens == null ? List.of() : Arrays.stream((Object[]) screens.getArray()).map(String::valueOf).filter(s -> !s.isEmpty()).toList();
        AdPlacements value = new AdPlacements(new AdPlacements.PostInline(rs.getBoolean("post_inline_enabled"), rs.getString("post_inline_type"),
                rs.getString("post_inline_position")), new AdPlacements.Wings(rs.getBoolean("wing_left"), rs.getBoolean("wing_right"), list),
                new AdPlacements.BoardList(rs.getBoolean("board_list_enabled"), rs.getInt("board_list_after")), new AdPlacements.Adult(rs.getBoolean("adult_enabled")));
        return new Stored(value, rs.getTimestamp("updated_at").toInstant(), rs.getLong("updated_by"), rs.getString("operator_name"), rs.getInt("stop_fee_rate"));
    }
}
