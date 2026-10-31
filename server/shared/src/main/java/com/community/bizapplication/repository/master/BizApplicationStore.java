package com.community.bizapplication.repository.master;

import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizRejectCode;
import com.community.bizapplication.entity.BizSource;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.service.BizPricing.Amount;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 신청 건 표(V112 — JDBC, 공구·결제 모듈과 같은 방식) — 신청 건 · 자리/금액 줄 · 소재 버전 · 처리 기록 · 입금 확인. 상태를 바꾸는 UPDATE 는 모두 <b>지금 상태를 조건으로</b>
 * 걸고 바뀐 행 수로 이겼는지 돌려준다(서비스는 행을 잠근 뒤 부르지만, 잠그지 않는 길 — 알림 표시 등 — 도 같은 규칙).
 */
@Repository
public class BizApplicationStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    /** 신청 건 한 줄 */
    public record AppRow(long id, String no, BizApplicationKind kind, BizApplicationStatus status, BizSource source, Long businessProfileId, Long userId,
                         String advertiserName, String companyName, String bizNoLast4, String title, Instant startAt, Instant endAt,
                         BizPaymentMethod preferredMethod, BizPaymentMethod paymentMethod, Amount quote, Amount approved, String adjustReason, Instant approvedAt,
                         Long approvedBy, Instant paymentDeadlineAt, Instant depositDeadlineAt, Instant paidAt, Long paymentOrderId, int activeContractCount,
                         Instant depositConfirmedAt, BizRejectCode rejectCode, String rejectText, Instant takenDownAt, BizTakedownCause takedownCause,
                         String takedownText, String adPolicyVersion, String refundPolicyVersion, Instant agreedAt, Instant closedAt, int version,
                         Instant createdAt, Instant updatedAt) {

        /** 결제·입금 기한(승인 뒤) */
        public Instant deadlineAt() {
            return paymentDeadlineAt != null ? paymentDeadlineAt : depositDeadlineAt;
        }

        /** 사업자 쪽 표시 이름(상호 또는 광고주) */
        public String businessName() {
            return companyName != null ? companyName : advertiserName;
        }
    }

    /** 새 신청 건 */
    public record NewApp(long id, BizApplicationKind kind, BizSource source, Long businessProfileId, Long userId, String advertiserName, String companyName,
                         String bizNoLast4, String title, Instant startAt, Instant endAt, BizPaymentMethod preferredMethod, Amount quote, String adPolicyVersion,
                         String refundPolicyVersion, Instant agreedAt, Instant now) {
    }

    /** 자리·금액 줄 */
    public record ItemRow(long id, long applicationId, BizProduct product, long unitPrice, int units, long supply, Instant startAt, Instant endAt) {
    }

    /** 소재 버전 */
    public record CreativeRow(long id, long applicationId, int version, Map<String, Object> content, String createdByType, Long createdBy, Instant createdAt,
                              Instant approvedAt) {
    }

    /** 처리 기록 한 줄 — 이름은 운영자 이름 또는 회원 닉네임(없으면 null) */
    public record EventRow(Instant at, String action, BizActorType actorType, Long actorId, String actorName, String note) {
    }

    /** 입금 확인 — 확인자 이름 포함 */
    public record DepositRow(long applicationId, long amount, LocalDate depositedOn, String depositorName, String mismatchReason, long confirmedBy,
                             String confirmedByName, Instant confirmedAt) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public BizApplicationStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    // ── 신청 건 ─────────────────────────────────────────────────────────────

    /** 넣고 화면 번호(BA-연도-일련번호)를 돌려준다 — 연도는 한국 날짜 */
    public String insert(NewApp a) {
        Timestamp now = ts(a.now());
        String no = jdbc.queryForObject("SELECT 'BA-' || to_char(?::timestamptz AT TIME ZONE 'Asia/Seoul', 'YYYY') || '-' || lpad(nextval('biz_application_no_seq')::text, 5, '0')",
                String.class, now);
        jdbc.update("INSERT INTO biz_applications (id, application_no, kind, status, source, business_profile_id, user_id, advertiser_name, company_name, biz_no_last4, "
                        + "title, start_at, end_at, preferred_method, quote_supply, quote_vat, quote_total, ad_policy_version, refund_policy_version, agreed_at, "
                        + "created_at, updated_at) VALUES (?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                a.id(), no, a.kind().name(), a.source().name(), a.businessProfileId(), a.userId(), a.advertiserName(), a.companyName(), a.bizNoLast4(), a.title(),
                ts(a.startAt()), ts(a.endAt()), a.preferredMethod().name(), a.quote().supply(), a.quote().vat(), a.quote().total(), a.adPolicyVersion(),
                a.refundPolicyVersion(), ts(a.agreedAt()), now, now);
        return no;
    }

    public Optional<AppRow> find(long id) {
        return jdbc.query("SELECT * FROM biz_applications WHERE id = ?", (rs, i) -> app(rs), id).stream().findFirst();
    }

    /** 행을 잠그고 읽는다(처리 — 결제 확정과 겹쳐도 한쪽만) */
    public Optional<AppRow> lock(long id) {
        return jdbc.query("SELECT * FROM biz_applications WHERE id = ? FOR UPDATE", (rs, i) -> app(rs), id).stream().findFirst();
    }

    /** 그 회원의 신청 건을 잠근다 — 남의 건은 비어 있다(404) */
    public Optional<AppRow> lockOwned(long id, long userId) {
        return jdbc.query("SELECT * FROM biz_applications WHERE id = ? AND user_id = ? FOR UPDATE", (rs, i) -> app(rs), id, userId).stream().findFirst();
    }

    public Optional<AppRow> findOwned(long id, long userId) {
        return jdbc.query("SELECT * FROM biz_applications WHERE id = ? AND user_id = ?", (rs, i) -> app(rs), id, userId).stream().findFirst();
    }

    public List<AppRow> findAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return named.query("SELECT * FROM biz_applications WHERE id IN (:ids)", new MapSqlParameterSource("ids", ids), (rs, i) -> app(rs));
    }

    /** 사업자 신원의 신청 건(상태 거르기) — 정지·해제·탈퇴 훅 */
    public List<AppRow> byProfile(long profileId, Set<BizApplicationStatus> statuses) {
        return named.query("SELECT * FROM biz_applications WHERE business_profile_id = :p AND status IN (:s) ORDER BY start_at, id",
                new MapSqlParameterSource("p", profileId).addValue("s", names(statuses)), (rs, i) -> app(rs));
    }

    /** 회원의 신청 건 전부(최근 먼저, 최대 500) — 공개 목록은 묶음별 건수를 함께 낸다 */
    public List<AppRow> byUser(long userId) {
        return jdbc.query("SELECT * FROM biz_applications WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 500", (rs, i) -> app(rs), userId);
    }

    /** 관리자 큐 — 종류·검색(번호·상호·광고주) 거르기. 큐는 상태·기한·환불로 서비스가 가른다(최근 2,000건까지) */
    public List<AppRow> forAdmin(BizApplicationKind kind, String q) {
        StringBuilder sql = new StringBuilder("SELECT * FROM biz_applications WHERE 1 = 1");
        MapSqlParameterSource args = new MapSqlParameterSource();
        if (kind != null) {
            sql.append(" AND kind = :kind");
            args.addValue("kind", kind.name());
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (application_no ILIKE :like OR company_name ILIKE :like OR advertiser_name ILIKE :like OR title ILIKE :like)");
            args.addValue("like", "%" + q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT 2000");
        return named.query(sql.toString(), args, (rs, i) -> app(rs));
    }

    /** 승인 — 심사 대기에서만. 기간·요금표 계산·확정 금액·결제 방식·기한 */
    public boolean approve(long id, BizApplicationStatus to, Instant start, Instant end, Amount quote, Amount approved, String adjustReason, BizPaymentMethod method,
                           Instant deadline, long operatorId, Instant now) {
        boolean online = method == BizPaymentMethod.ONLINE;
        return jdbc.update("UPDATE biz_applications SET status = ?, start_at = ?, end_at = ?, quote_supply = ?, quote_vat = ?, quote_total = ?, approved_supply = ?, "
                        + "approved_vat = ?, approved_total = ?, adjust_reason = ?, approved_at = ?, approved_by = ?, payment_method = ?, payment_deadline_at = ?, "
                        + "deposit_deadline_at = ?, payment_due_notified_at = NULL, updated_at = ?, version = version + 1 WHERE id = ? AND status = 'SUBMITTED'",
                to.name(), ts(start), ts(end), quote.supply(), quote.vat(), quote.total(), approved.supply(), approved.vat(), approved.total(), adjustReason, ts(now),
                operatorId, method.name(), online ? ts(deadline) : null, online ? null : ts(deadline), ts(now), id) == 1;
    }

    public boolean reject(long id, BizRejectCode code, String text, Instant now) {
        return jdbc.update("UPDATE biz_applications SET status = 'REJECTED', reject_code = ?, reject_text = ?, closed_at = ?, updated_at = ?, version = version + 1 "
                + "WHERE id = ? AND status = 'SUBMITTED'", code.name(), text, ts(now), ts(now), id) == 1;
    }

    /** 상태만 바꾼다(끝난 상태면 닫은 시각). 지금 상태가 {@code from} 이어야 한다 */
    public boolean move(long id, BizApplicationStatus from, BizApplicationStatus to, Instant now) {
        boolean closed = BizApplicationStatus.CLOSED.contains(to);
        return jdbc.update("UPDATE biz_applications SET status = ?, closed_at = CASE WHEN ? THEN ?::timestamptz ELSE closed_at END, updated_at = ?, version = version + 1 "
                + "WHERE id = ? AND status = ?", to.name(), closed, ts(now), ts(now), id, from.name()) == 1;
    }

    /** 승인 뒤 결제 전 소재 바꾸기 → 다시 심사 대기 — 승인 스냅샷·결제 방식·기한을 비운다(자리 반납) */
    public boolean backToReview(long id, BizApplicationStatus from, String title, Instant now) {
        return jdbc.update("UPDATE biz_applications SET status = 'SUBMITTED', title = ?, approved_supply = NULL, approved_vat = NULL, approved_total = NULL, "
                        + "adjust_reason = NULL, approved_at = NULL, approved_by = NULL, payment_method = NULL, payment_deadline_at = NULL, deposit_deadline_at = NULL, "
                        + "payment_due_notified_at = NULL, updated_at = ?, version = version + 1 WHERE id = ? AND status = ?",
                title, ts(now), id, from.name()) == 1;
    }

    public boolean retitle(long id, String title, Instant now) {
        return jdbc.update("UPDATE biz_applications SET title = ?, updated_at = ?, version = version + 1 WHERE id = ?", title, ts(now), id) == 1;
    }

    /** 결제 대기 → 수동(입금 대기) */
    public boolean switchManual(long id, Instant depositDeadline, Instant now) {
        return jdbc.update("UPDATE biz_applications SET status = 'AWAITING_DEPOSIT', payment_method = 'MANUAL', payment_deadline_at = NULL, deposit_deadline_at = ?, "
                + "updated_at = ?, version = version + 1 WHERE id = ? AND status = 'AWAITING_PAYMENT'", ts(depositDeadline), ts(now), id) == 1;
    }

    /** 온라인 결제 완료 → 예약 */
    public boolean markPaid(long id, Instant paidAt, long paymentOrderId, Instant now) {
        return jdbc.update("UPDATE biz_applications SET status = 'SCHEDULED', paid_at = ?, payment_order_id = ?, updated_at = ?, version = version + 1 "
                + "WHERE id = ? AND status = 'AWAITING_PAYMENT' AND payment_method = 'ONLINE'", ts(paidAt), paymentOrderId, ts(now), id) == 1;
    }

    public void setContractCount(long id, int count, Instant now) {
        jdbc.update("UPDATE biz_applications SET active_contract_count = ?, updated_at = ? WHERE id = ?", count, ts(now), id);
    }

    public void setDepositConfirmed(long id, Instant at, Instant now) {
        jdbc.update("UPDATE biz_applications SET deposit_confirmed_at = ?, updated_at = ? WHERE id = ?", ts(at), ts(now), id);
    }

    public boolean setDepositDeadline(long id, Instant deadline, Instant now) {
        return jdbc.update("UPDATE biz_applications SET deposit_deadline_at = ?, updated_at = ?, version = version + 1 WHERE id = ? AND status = 'AWAITING_DEPOSIT'",
                ts(deadline), ts(now), id) == 1;
    }

    /** 예약 → 내림 */
    public boolean takeDown(long id, BizTakedownCause cause, String text, Instant now) {
        return jdbc.update("UPDATE biz_applications SET status = 'TAKEN_DOWN', taken_down_at = ?, takedown_cause = ?, takedown_text = ?, updated_at = ?, "
                + "version = version + 1 WHERE id = ? AND status = 'SCHEDULED'", ts(now), cause.name(), text, ts(now), id) == 1;
    }

    /** 내림 → 예약(끝 시각을 바꿀 수 있다 — 연장 후 되돌리기) */
    public boolean restore(long id, Instant end, Instant now) {
        return jdbc.update("UPDATE biz_applications SET status = 'SCHEDULED', end_at = ?, taken_down_at = NULL, takedown_cause = NULL, takedown_text = NULL, "
                + "end_notified_at = NULL, updated_at = ?, version = version + 1 WHERE id = ? AND status = 'TAKEN_DOWN'", ts(end), ts(now), id) == 1;
    }

    /** 알림 보낸 표시 — 아직 안 보냈을 때만(배치가 겹쳐도 한 번) */
    public boolean markNotified(long id, String column, Instant now) {
        if (!Set.of("payment_due_notified_at", "start_notified_at", "end_notified_at").contains(column)) {
            throw new IllegalArgumentException(column);
        }
        return jdbc.update("UPDATE biz_applications SET " + column + " = ? WHERE id = ? AND " + column + " IS NULL", ts(now), id) == 1;
    }

    // ── 배치 대상 ───────────────────────────────────────────────────────────

    /** 결제 기한이 지난 결제 대기 — ID 키셋(처리하면 대상이 아니게 되어 다시 고르지 않는다) */
    public List<AppRow> paymentOverdue(Instant now, long afterId, int limit) {
        return jdbc.query("SELECT * FROM biz_applications WHERE status = 'AWAITING_PAYMENT' AND payment_deadline_at <= ? AND id > ? ORDER BY id LIMIT ?",
                (rs, i) -> app(rs), ts(now), afterId, limit);
    }

    /** 결제 기한이 {@code until} 안이고 아직 알리지 않은 결제 대기 */
    public List<AppRow> paymentDueSoon(Instant now, Instant until, long afterId, int limit) {
        return jdbc.query("SELECT * FROM biz_applications WHERE status = 'AWAITING_PAYMENT' AND payment_deadline_at > ? AND payment_deadline_at <= ? "
                + "AND payment_due_notified_at IS NULL AND id > ? ORDER BY id LIMIT ?", (rs, i) -> app(rs), ts(now), ts(until), afterId, limit);
    }

    /** 시작이 {@code until} 안인 예약(아직 알리지 않음) */
    public List<AppRow> startingSoon(Instant now, Instant until, long afterId, int limit) {
        return jdbc.query("SELECT * FROM biz_applications WHERE status = 'SCHEDULED' AND start_at > ? AND start_at <= ? AND start_notified_at IS NULL AND id > ? "
                + "ORDER BY id LIMIT ?", (rs, i) -> app(rs), ts(now), ts(until), afterId, limit);
    }

    /** 끝이 {@code until} 안인 진행 중 예약(아직 알리지 않음) */
    public List<AppRow> endingSoon(Instant now, Instant until, long afterId, int limit) {
        return jdbc.query("SELECT * FROM biz_applications WHERE status = 'SCHEDULED' AND start_at <= ? AND end_at > ? AND end_at <= ? AND end_notified_at IS NULL "
                + "AND id > ? ORDER BY id LIMIT ?", (rs, i) -> app(rs), ts(now), ts(now), ts(until), afterId, limit);
    }

    // ── 자리·금액 줄 ────────────────────────────────────────────────────────

    public void insertItem(ItemRow item, Instant now) {
        jdbc.update("INSERT INTO biz_application_items (id, application_id, product, unit_price, units, supply, start_at, end_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                item.id(), item.applicationId(), item.product().name(), item.unitPrice(), item.units(), item.supply(), ts(item.startAt()), ts(item.endAt()), ts(now));
    }

    /** 줄을 통째로 바꾼다(승인 때 기간·금액을 다시 계산) */
    public void replaceItems(long applicationId, List<ItemRow> items, Instant now) {
        jdbc.update("DELETE FROM biz_application_items WHERE application_id = ?", applicationId);
        for (ItemRow item : items) {
            insertItem(item, now);
        }
    }

    public List<ItemRow> items(long applicationId) {
        return jdbc.query("SELECT * FROM biz_application_items WHERE application_id = ? ORDER BY product", (rs, i) -> item(rs), applicationId);
    }

    public Map<Long, List<ItemRow>> items(Collection<Long> applicationIds) {
        Map<Long, List<ItemRow>> out = new java.util.HashMap<>();
        if (applicationIds.isEmpty()) {
            return out;
        }
        named.query("SELECT * FROM biz_application_items WHERE application_id IN (:ids) ORDER BY product", new MapSqlParameterSource("ids", applicationIds), rs -> {
            ItemRow row = item(rs);
            out.computeIfAbsent(row.applicationId(), k -> new ArrayList<>()).add(row);
        });
        return out;
    }

    /** 끝 시각만 바꾼다(되돌리기 연장) */
    public void extendItems(long applicationId, Instant end) {
        jdbc.update("UPDATE biz_application_items SET end_at = ? WHERE application_id = ?", ts(end), applicationId);
    }

    // ── 소재 버전 ───────────────────────────────────────────────────────────

    public void insertCreative(long id, long applicationId, int version, Map<String, Object> content, BizActorType byType, Long by, Instant now) {
        jdbc.update("INSERT INTO biz_creative_versions (id, application_id, version, content, created_by_type, created_by, created_at) VALUES (?, ?, ?, ?::jsonb, ?, ?, ?)",
                id, applicationId, version, JSON.writeValueAsString(content), byType.name(), by, ts(now));
    }

    public Optional<CreativeRow> latestCreative(long applicationId) {
        return jdbc.query("SELECT * FROM biz_creative_versions WHERE application_id = ? ORDER BY version DESC LIMIT 1", (rs, i) -> creative(rs), applicationId).stream()
                .findFirst();
    }

    public Map<Long, CreativeRow> latestCreatives(Collection<Long> applicationIds) {
        Map<Long, CreativeRow> out = new java.util.HashMap<>();
        if (applicationIds.isEmpty()) {
            return out;
        }
        named.query("SELECT DISTINCT ON (application_id) * FROM biz_creative_versions WHERE application_id IN (:ids) ORDER BY application_id, version DESC",
                new MapSqlParameterSource("ids", applicationIds), rs -> {
                    CreativeRow row = creative(rs);
                    out.put(row.applicationId(), row);
                });
        return out;
    }

    /** 승인 때 지금 소재 버전을 고정한다(A2) */
    public void approveLatestCreative(long applicationId, Instant now) {
        jdbc.update("UPDATE biz_creative_versions SET approved_at = ? WHERE id = (SELECT id FROM biz_creative_versions WHERE application_id = ? ORDER BY version DESC LIMIT 1)",
                ts(now), applicationId);
    }

    // ── 처리 기록 ───────────────────────────────────────────────────────────

    public void insertEvent(long id, long applicationId, BizEventAction action, BizActorType actorType, Long actorId, String note, Instant now) {
        jdbc.update("INSERT INTO biz_application_events (id, application_id, action, actor_type, actor_id, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", id,
                applicationId, action.name(), actorType.name(), actorId, note == null ? null : cut(note, 300), ts(now));
    }

    /** 처리 기록(오래된 것 먼저) — 행위자 이름은 운영자 이름·회원 닉네임 */
    public List<EventRow> events(long applicationId) {
        return jdbc.query("SELECT e.created_at, e.action, e.actor_type, e.actor_id, CASE WHEN e.actor_type = 'OPERATOR' THEN o.operator_name "
                        + "WHEN e.actor_type = 'MEMBER' THEN p.nickname END, e.note FROM biz_application_events e "
                        + "LEFT JOIN operators o ON e.actor_type = 'OPERATOR' AND o.id = e.actor_id "
                        + "LEFT JOIN profiles p ON e.actor_type = 'MEMBER' AND p.user_id = e.actor_id "
                        + "WHERE e.application_id = ? ORDER BY e.created_at, e.id LIMIT 300",
                (rs, i) -> new EventRow(instant(rs.getTimestamp(1)), rs.getString(2), BizActorType.valueOf(rs.getString(3)), (Long) rs.getObject(4, Long.class),
                        rs.getString(5), rs.getString(6)), applicationId);
    }

    public int countEvents(long applicationId, BizEventAction action) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM biz_application_events WHERE application_id = ? AND action = ?", Integer.class, applicationId, action.name());
        return n == null ? 0 : n;
    }

    // ── 입금 확인 ───────────────────────────────────────────────────────────

    public void upsertDeposit(long applicationId, long amount, LocalDate depositedOn, String depositorName, String mismatchReason, long operatorId, Instant now) {
        jdbc.update("INSERT INTO biz_deposits (application_id, amount, deposited_on, depositor_name, mismatch_reason, confirmed_by, confirmed_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (application_id) DO UPDATE SET amount = EXCLUDED.amount, deposited_on = EXCLUDED.deposited_on, depositor_name = EXCLUDED.depositor_name, "
                        + "mismatch_reason = EXCLUDED.mismatch_reason, confirmed_by = EXCLUDED.confirmed_by, confirmed_at = EXCLUDED.confirmed_at",
                applicationId, amount, Date.valueOf(depositedOn), depositorName, mismatchReason, operatorId, ts(now));
    }

    public Optional<DepositRow> deposit(long applicationId) {
        return jdbc.query("SELECT d.*, o.operator_name FROM biz_deposits d LEFT JOIN operators o ON o.id = d.confirmed_by WHERE d.application_id = ?",
                (rs, i) -> new DepositRow(rs.getLong("application_id"), rs.getLong("amount"), rs.getDate("deposited_on").toLocalDate(), rs.getString("depositor_name"),
                        rs.getString("mismatch_reason"), rs.getLong("confirmed_by"), rs.getString("operator_name"), instant(rs.getTimestamp("confirmed_at"))),
                applicationId).stream().findFirst();
    }

    // ── 이름 ────────────────────────────────────────────────────────────────

    /** 운영자 이름(없으면 null) */
    public String operatorName(Long operatorId) {
        if (operatorId == null) {
            return null;
        }
        return jdbc.query("SELECT operator_name FROM operators WHERE id = ?", (rs, i) -> rs.getString(1), operatorId).stream().findFirst().orElse(null);
    }

    /** 회원 닉네임(없으면 null) */
    public String nickname(Long userId) {
        if (userId == null) {
            return null;
        }
        return jdbc.query("SELECT nickname FROM profiles WHERE user_id = ?", (rs, i) -> rs.getString(1), userId).stream().findFirst().orElse(null);
    }

    // ── 광고 이미지(files — 용도 BIZ_AD_IMAGE) ─────────────────────────────

    /** 회원이 올린 광고 이미지 — 주소 키·이름(올린 사람이 다르거나 없으면 비어 있다) */
    public record AdImage(long id, String storageKey, String contentType, long sizeBytes) {
    }

    public Optional<AdImage> adImage(long fileId, long userId) {
        return jdbc.query("SELECT id, storage_key, content_type, size_bytes FROM files WHERE id = ? AND uploaded_by_user = ? AND purpose = 'BIZ_AD_IMAGE'",
                (rs, i) -> new AdImage(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4)), fileId, userId).stream().findFirst();
    }

    /** 쓰는 중으로(올리다 만 파일 정리에서 빠진다) */
    public void markImageUsed(long fileId, Instant now) {
        jdbc.update("UPDATE files SET status = 'IN_USE', used_at = coalesce(used_at, ?) WHERE id = ?", ts(now), fileId);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private static AppRow app(ResultSet rs) throws SQLException {
        return new AppRow(rs.getLong("id"), rs.getString("application_no"), BizApplicationKind.valueOf(rs.getString("kind")),
                BizApplicationStatus.valueOf(rs.getString("status")), BizSource.valueOf(rs.getString("source")), (Long) rs.getObject("business_profile_id", Long.class),
                (Long) rs.getObject("user_id", Long.class), rs.getString("advertiser_name"), rs.getString("company_name"), rs.getString("biz_no_last4"),
                rs.getString("title"), instant(rs.getTimestamp("start_at")), instant(rs.getTimestamp("end_at")), BizPaymentMethod.valueOf(rs.getString("preferred_method")),
                enumOrNull(BizPaymentMethod.class, rs.getString("payment_method")), new Amount(rs.getLong("quote_supply"), rs.getLong("quote_vat"), rs.getLong("quote_total")),
                rs.getObject("approved_total") == null ? null : new Amount(rs.getLong("approved_supply"), rs.getLong("approved_vat"), rs.getLong("approved_total")),
                rs.getString("adjust_reason"), instant(rs.getTimestamp("approved_at")), (Long) rs.getObject("approved_by", Long.class),
                instant(rs.getTimestamp("payment_deadline_at")), instant(rs.getTimestamp("deposit_deadline_at")), instant(rs.getTimestamp("paid_at")),
                (Long) rs.getObject("payment_order_id", Long.class), rs.getInt("active_contract_count"), instant(rs.getTimestamp("deposit_confirmed_at")),
                enumOrNull(BizRejectCode.class, rs.getString("reject_code")), rs.getString("reject_text"), instant(rs.getTimestamp("taken_down_at")),
                enumOrNull(BizTakedownCause.class, rs.getString("takedown_cause")), rs.getString("takedown_text"), rs.getString("ad_policy_version"),
                rs.getString("refund_policy_version"), instant(rs.getTimestamp("agreed_at")), instant(rs.getTimestamp("closed_at")), rs.getInt("version"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));
    }

    private static ItemRow item(ResultSet rs) throws SQLException {
        return new ItemRow(rs.getLong("id"), rs.getLong("application_id"), BizProduct.valueOf(rs.getString("product")), rs.getLong("unit_price"), rs.getInt("units"),
                rs.getLong("supply"), instant(rs.getTimestamp("start_at")), instant(rs.getTimestamp("end_at")));
    }

    private static CreativeRow creative(ResultSet rs) throws SQLException {
        return new CreativeRow(rs.getLong("id"), rs.getLong("application_id"), rs.getInt("version"), JSON.readValue(rs.getString("content"), MAP),
                rs.getString("created_by_type"), (Long) rs.getObject("created_by", Long.class), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("approved_at")));
    }

    static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    static List<String> names(Collection<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).toList();
    }

    static Timestamp ts(Instant at) {
        return at == null ? null : Timestamp.from(at);
    }

    static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
