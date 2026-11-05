package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonImageFormat;
import com.community.emoticon.entity.EmoticonImageKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 올린 로보티콘 그림({@code emoticon_images}, V142) 장부 — 파일 바이트는 저장소({@code ImageStorage}) 또는 앱 정적 파일(시드)에 있다. Native SQL 인 이유는 {@link EmoticonPackStore} 와 같다 */
@Repository
public class EmoticonImageStore {

    /**
     * 그림 한 줄.
     *
     * @param storageKey 저장소 키(올린 그림) — 시드 그림이면 null
     * @param staticPath 앱 정적 파일 주소(시드 그림 {@code /emoticons/robotikon-m/01.gif}) — 올린 그림이면 null
     */
    public record ImageRow(long id, EmoticonImageKind kind, EmoticonImageFormat format, int width, int height, int bytes, int frames, boolean loopForever,
                           String contentHash, String storageKey, String staticPath, Long uploadedByUser, Long uploadedByOperator, Instant createdAt) {
    }

    private static final String COLUMNS = "id, kind, format, width, height, bytes, frames, loop_forever, content_hash, storage_key, static_path, uploaded_by_user, "
            + "uploaded_by_operator, created_at";

    private final JdbcTemplate jdbc;

    public EmoticonImageStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(ImageRow row) {
        jdbc.update("INSERT INTO emoticon_images (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", row.id(), row.kind().name(), row.format().name(),
                row.width(), row.height(), row.bytes(), row.frames(), row.loopForever(), row.contentHash(), row.storageKey(), row.staticPath(), row.uploadedByUser(),
                row.uploadedByOperator(), Timestamp.from(row.createdAt()));
    }

    public Optional<ImageRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM emoticon_images WHERE id = ?", EmoticonImageStore::rowOf, id).stream().findFirst();
    }

    public Map<Long, ImageRow> byIds(Collection<Long> ids) {
        Map<Long, ImageRow> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        String marks = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        jdbc.query("SELECT " + COLUMNS + " FROM emoticon_images WHERE id IN (" + marks + ")", rs -> {
            ImageRow row = rowOf(rs, 0);
            out.put(row.id(), row);
        }, ids.toArray());
        return out;
    }

    static ImageRow rowOf(ResultSet rs, int i) throws SQLException {
        return new ImageRow(rs.getLong(1), EmoticonImageKind.valueOf(rs.getString(2)), EmoticonImageFormat.valueOf(rs.getString(3)), rs.getInt(4), rs.getInt(5),
                rs.getInt(6), rs.getInt(7), rs.getBoolean(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getObject(12, Long.class),
                rs.getObject(13, Long.class), rs.getTimestamp(14).toInstant());
    }
}
