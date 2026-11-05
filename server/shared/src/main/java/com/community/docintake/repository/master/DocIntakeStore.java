package com.community.docintake.repository.master;

import com.community.docintake.entity.DocIntakeChannel;
import com.community.docintake.entity.DocIntakeDoc;
import com.community.docintake.entity.DocIntakeHandling;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사업자 서류 종이 접수 기록(V140 {@code biz_doc_intakes}) — 줄을 지우지 않는다(사업자 삭제만 {@code BusinessStore#deleteProfile} 이 그 신원 줄을 지운다). 쓰기는 부르는 쪽이 대상(신청 건·사업자 신원) 줄을 잠근
 * 같은 트랜잭션에서 하고, 처리·폐기·잘못 기록은 「아직 열린 줄(잘못 기록 아님 · 폐기 안 함)」일 때만 바꾼다(아니면 false — 409).
 */
@Repository
public class DocIntakeStore {

    /** 접수 한 줄 — 운영자 이름 포함(지워진 운영자면 null) */
    public record IntakeRow(long id, DocIntakeDoc doc, Long applicationId, Long profileId, DocIntakeChannel channel, LocalDate receivedOn, Integer pages, String memo,
                            DocIntakeHandling handling, String storageLocation, Long receivedBy, String receivedByName, Instant recordedAt, LocalDate disposedOn,
                            Instant disposedAt, Long disposedBy, String disposedByName, String disposeReason, Long scanContractFileId, Long scanDocumentId,
                            Instant voidedAt, Long voidedBy, String voidedByName, String voidReason) {

        /** 처리를 바꾸거나 폐기·잘못 기록할 수 있는가 */
        public boolean open() {
            return voidedAt == null && handling.open();
        }
    }

    /** 새 접수 한 줄 */
    public record NewIntake(long id, DocIntakeDoc doc, long ownerId, DocIntakeChannel channel, LocalDate receivedOn, Integer pages, String memo, DocIntakeHandling handling,
                            String storageLocation, long operatorId, Instant now) {
    }

    private static final String SELECT = "SELECT i.*, r.operator_name AS received_name, d.operator_name AS disposed_name, v.operator_name AS voided_name FROM biz_doc_intakes i "
            + "LEFT JOIN operators r ON r.id = i.received_by LEFT JOIN operators d ON d.id = i.disposed_by LEFT JOIN operators v ON v.id = i.voided_by ";

    private final JdbcTemplate jdbc;

    public DocIntakeStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(NewIntake n) {
        Long applicationId = n.doc() == DocIntakeDoc.CONTRACT ? n.ownerId() : null;
        Long profileId = n.doc() == DocIntakeDoc.CERTIFICATE ? n.ownerId() : null;
        jdbc.update("INSERT INTO biz_doc_intakes (id, doc, application_id, profile_id, channel, received_on, pages, memo, handling, storage_location, received_by, "
                        + "recorded_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", n.id(), n.doc().name(), applicationId, profileId, n.channel().name(),
                Date.valueOf(n.receivedOn()), n.pages(), n.memo(), n.handling().name(), n.storageLocation(), n.operatorId(), Timestamp.from(n.now()),
                Timestamp.from(n.now()));
    }

    /** 그 대상의 접수 줄 — 최근 기록 먼저 */
    public List<IntakeRow> list(DocIntakeDoc doc, long ownerId) {
        return jdbc.query(SELECT + "WHERE i.doc = ? AND " + ownerColumn(doc) + " = ? ORDER BY i.recorded_at DESC, i.id DESC", (rs, i) -> row(rs), doc.name(), ownerId);
    }

    /** 그 대상의 접수 한 줄(다른 대상의 줄이면 비어 있다) */
    public Optional<IntakeRow> find(DocIntakeDoc doc, long ownerId, long id) {
        return jdbc.query(SELECT + "WHERE i.id = ? AND i.doc = ? AND " + ownerColumn(doc) + " = ?", (rs, i) -> row(rs), id, doc.name(), ownerId).stream().findFirst();
    }

    /** 처리 바꾸기(계속 보관 ↔ 폐기 예정) — 열린 줄만 */
    public boolean changeHandling(long id, DocIntakeHandling handling, String storageLocation, Instant now) {
        return jdbc.update("UPDATE biz_doc_intakes SET handling = ?, storage_location = ?, updated_at = ? WHERE id = ? AND voided_at IS NULL AND handling <> 'DISPOSED'",
                handling.name(), storageLocation, Timestamp.from(now), id) == 1;
    }

    /** 폐기 — 열린 줄만. 스캔본은 서류에 맞는 칸 하나(등록증은 없어도 된다) */
    public boolean dispose(long id, LocalDate disposedOn, String reason, Long scanContractFileId, Long scanDocumentId, long operatorId, Instant now) {
        return jdbc.update("UPDATE biz_doc_intakes SET handling = 'DISPOSED', storage_location = NULL, disposed_on = ?, disposed_at = ?, disposed_by = ?, dispose_reason = ?, "
                        + "scan_contract_file_id = ?, scan_document_id = ?, updated_at = ? WHERE id = ? AND voided_at IS NULL AND handling <> 'DISPOSED'",
                Date.valueOf(disposedOn), Timestamp.from(now), operatorId, reason, scanContractFileId, scanDocumentId, Timestamp.from(now), id) == 1;
    }

    /** 잘못 기록 — 열린 줄만(폐기한 줄은 근거라 그을 수 없다) */
    public boolean markVoid(long id, String reason, long operatorId, Instant now) {
        return jdbc.update("UPDATE biz_doc_intakes SET voided_at = ?, voided_by = ?, void_reason = ?, updated_at = ? WHERE id = ? AND voided_at IS NULL AND handling <> 'DISPOSED'",
                Timestamp.from(now), operatorId, reason, Timestamp.from(now), id) == 1;
    }

    /** 신청 건의 계약서 종이 근거 수 — 잘못 기록이 아닌 줄(폐기한 줄 포함) */
    public int contractEvidence(long applicationId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM biz_doc_intakes WHERE doc = 'CONTRACT' AND application_id = ? AND voided_at IS NULL", Integer.class,
                applicationId);
        return n == null ? 0 : n;
    }

    /** 사업자 신원의 등록증 종이 접수 중 잘못 기록이 아니고 그 시각 이후에 기록한 줄이 있는가(이번 신청 이후 받은 것) */
    public boolean hasCertificateSince(long profileId, Instant since) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM biz_doc_intakes WHERE doc = 'CERTIFICATE' AND profile_id = ? AND voided_at IS NULL "
                + "AND recorded_at >= ?)", Boolean.class, profileId, Timestamp.from(since));
        return Boolean.TRUE.equals(exists);
    }

    private static String ownerColumn(DocIntakeDoc doc) {
        return doc == DocIntakeDoc.CONTRACT ? "i.application_id" : "i.profile_id";
    }

    private static IntakeRow row(ResultSet rs) throws SQLException {
        Date disposedOn = rs.getDate("disposed_on");
        return new IntakeRow(rs.getLong("id"), DocIntakeDoc.valueOf(rs.getString("doc")), (Long) rs.getObject("application_id", Long.class),
                (Long) rs.getObject("profile_id", Long.class), DocIntakeChannel.valueOf(rs.getString("channel")), rs.getDate("received_on").toLocalDate(),
                (Integer) rs.getObject("pages", Integer.class), rs.getString("memo"), DocIntakeHandling.valueOf(rs.getString("handling")), rs.getString("storage_location"),
                (Long) rs.getObject("received_by", Long.class), rs.getString("received_name"), instant(rs.getTimestamp("recorded_at")),
                disposedOn == null ? null : disposedOn.toLocalDate(), instant(rs.getTimestamp("disposed_at")), (Long) rs.getObject("disposed_by", Long.class),
                rs.getString("disposed_name"), rs.getString("dispose_reason"), (Long) rs.getObject("scan_contract_file_id", Long.class),
                (Long) rs.getObject("scan_document_id", Long.class), instant(rs.getTimestamp("voided_at")), (Long) rs.getObject("voided_by", Long.class),
                rs.getString("voided_name"), rs.getString("void_reason"));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
