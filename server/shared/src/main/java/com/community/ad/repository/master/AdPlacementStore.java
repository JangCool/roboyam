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

/** 광고 노출 자리 설정(V118 {@code ad_placement_settings} — 한 행, id = 1). 행이 없으면 {@link AdPlacements#DEFAULTS} */
@Repository
public class AdPlacementStore {

    /** 저장한 설정 + 마지막으로 고친 때·사람(운영자 이름은 지워졌으면 null) */
    public record Stored(AdPlacements value, Instant updatedAt, long updatedBy, String updatedByName) {
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

    public void save(AdPlacements p, long operatorId, Instant now) {
        jdbc.update("INSERT INTO ad_placement_settings (id, post_inline_enabled, post_inline_type, post_inline_position, wing_left, wing_right, wing_screens, "
                        + "board_list_enabled, board_list_after, updated_at, updated_by) VALUES (1, ?, ?, ?, ?, ?, string_to_array(?, ','), ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET post_inline_enabled = EXCLUDED.post_inline_enabled, post_inline_type = EXCLUDED.post_inline_type, "
                        + "post_inline_position = EXCLUDED.post_inline_position, wing_left = EXCLUDED.wing_left, wing_right = EXCLUDED.wing_right, "
                        + "wing_screens = EXCLUDED.wing_screens, board_list_enabled = EXCLUDED.board_list_enabled, board_list_after = EXCLUDED.board_list_after, "
                        + "updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by",
                p.postInline().enabled(), p.postInline().adType(), p.postInline().position(), p.wings().left(), p.wings().right(),
                String.join(",", p.wings().screens()), p.boardList().enabled(), p.boardList().after(), Timestamp.from(now), operatorId);
    }

    private static Stored stored(ResultSet rs) throws SQLException {
        Array screens = rs.getArray("wing_screens");
        List<String> list = screens == null ? List.of() : Arrays.stream((Object[]) screens.getArray()).map(String::valueOf).filter(s -> !s.isEmpty()).toList();
        AdPlacements value = new AdPlacements(new AdPlacements.PostInline(rs.getBoolean("post_inline_enabled"), rs.getString("post_inline_type"),
                rs.getString("post_inline_position")), new AdPlacements.Wings(rs.getBoolean("wing_left"), rs.getBoolean("wing_right"), list),
                new AdPlacements.BoardList(rs.getBoolean("board_list_enabled"), rs.getInt("board_list_after")));
        return new Stored(value, rs.getTimestamp("updated_at").toInstant(), rs.getLong("updated_by"), rs.getString("operator_name"));
    }
}
