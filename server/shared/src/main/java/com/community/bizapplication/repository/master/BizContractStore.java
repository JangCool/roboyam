package com.community.bizapplication.repository.master;

import static com.community.bizapplication.repository.master.BizApplicationStore.instant;
import static com.community.bizapplication.repository.master.BizApplicationStore.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 계약서 사본(V112 {@code biz_contract_files}) — 내용은 암호문 bytea 라 목록·정보 조회는 내용을 읽지 않는다. 줄을 지우지 않고, 바꾸면 옛 줄에 「대체됨」을 적는다.
 */
@Repository
public class BizContractStore {

    /** 계약서 한 장의 정보(내용 없음) — 올린 사람·대체한 사람 이름 포함 */
    public record ContractRow(long id, long applicationId, String fileName, String contentType, int sizeBytes, String sha256, Long uploadedBy, String uploadedByName,
                              Instant uploadedAt, Instant replacedAt, Long replacedBy, String replacedByName, Long replacedByFileId) {

        public boolean active() {
            return replacedAt == null;
        }
    }

    private static final String SELECT = "SELECT c.id, c.application_id, c.file_name, c.content_type, c.size_bytes, c.sha256, c.uploaded_by, u.operator_name AS uploader, "
            + "c.uploaded_at, c.replaced_at, c.replaced_by, r.operator_name AS replacer, c.replaced_by_file_id FROM biz_contract_files c "
            + "LEFT JOIN operators u ON u.id = c.uploaded_by LEFT JOIN operators r ON r.id = c.replaced_by ";

    private final JdbcTemplate jdbc;

    public BizContractStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(long id, long applicationId, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256, long operatorId, Instant now) {
        jdbc.update("INSERT INTO biz_contract_files (id, application_id, file_name, content_type, size_bytes, content, sha256, uploaded_by, uploaded_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, applicationId, fileName, contentType, sizeBytes, encrypted, sha256, operatorId, ts(now));
    }

    /** 건이 생기기 전에 올린 사본(광고 일정표 운영자 등록 서랍 — application_id 없음, V115) */
    public void insertDraft(long id, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256, long operatorId, Instant now) {
        jdbc.update("INSERT INTO biz_contract_files (id, application_id, file_name, content_type, size_bytes, content, sha256, uploaded_by, uploaded_at) "
                + "VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?)", id, fileName, contentType, sizeBytes, encrypted, sha256, operatorId, ts(now));
    }

    /** 미리 올린 사본 한 장(아직 건에 붙지 않은 것) */
    public Optional<ContractRow> findDraft(long fileId) {
        return jdbc.query(SELECT + "WHERE c.application_id IS NULL AND c.id = ?", (rs, i) -> row(rs), fileId).stream().findFirst();
    }

    /** 미리 올린 사본을 건에 붙인다 — 아직 붙지 않은 것만. 붙인 장수 */
    public int attachDrafts(long applicationId, java.util.Collection<Long> fileIds) {
        int n = 0;
        for (Long fileId : fileIds) {
            n += jdbc.update("UPDATE biz_contract_files SET application_id = ? WHERE id = ? AND application_id IS NULL", applicationId, fileId);
        }
        return n;
    }

    /** 옛 파일을 「대체됨」으로 — 아직 대체되지 않았을 때만 */
    public boolean replace(long fileId, long applicationId, long newFileId, long operatorId, Instant now) {
        return jdbc.update("UPDATE biz_contract_files SET replaced_at = ?, replaced_by = ?, replaced_by_file_id = ? WHERE id = ? AND application_id = ? AND replaced_at IS NULL",
                ts(now), operatorId, newFileId, fileId, applicationId) == 1;
    }

    public List<ContractRow> list(long applicationId) {
        return jdbc.query(SELECT + "WHERE c.application_id = ? ORDER BY c.uploaded_at, c.id", (rs, i) -> row(rs), applicationId);
    }

    public Optional<ContractRow> find(long applicationId, long fileId) {
        return jdbc.query(SELECT + "WHERE c.application_id = ? AND c.id = ?", (rs, i) -> row(rs), applicationId, fileId).stream().findFirst();
    }

    /** 대체되지 않은 사본 수 */
    public int activeCount(long applicationId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM biz_contract_files WHERE application_id = ? AND replaced_at IS NULL", Integer.class, applicationId);
        return n == null ? 0 : n;
    }

    /** 암호문 내용 */
    public Optional<byte[]> content(long applicationId, long fileId) {
        return jdbc.query("SELECT content FROM biz_contract_files WHERE application_id = ? AND id = ?", (rs, i) -> rs.getBytes(1), applicationId, fileId).stream().findFirst();
    }

    private static ContractRow row(ResultSet rs) throws SQLException {
        return new ContractRow(rs.getLong("id"), rs.getLong("application_id"), rs.getString("file_name"), rs.getString("content_type"), rs.getInt("size_bytes"),
                rs.getString("sha256"), (Long) rs.getObject("uploaded_by", Long.class), rs.getString("uploader"), instant(rs.getTimestamp("uploaded_at")),
                instant(rs.getTimestamp("replaced_at")), (Long) rs.getObject("replaced_by", Long.class), rs.getString("replacer"),
                (Long) rs.getObject("replaced_by_file_id", Long.class));
    }
}
