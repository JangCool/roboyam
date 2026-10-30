package com.community.business.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.business.dto.BusinessAdminRequest;
import com.community.business.dto.BusinessAdminView;
import com.community.business.dto.BusinessSuspendPlan;
import com.community.business.entity.BusinessProfileEntity;
import com.community.business.entity.BusinessRejectCode;
import com.community.business.entity.BusinessServiceEntity;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.entity.BusinessServiceStatus;
import com.community.business.entity.BusinessStatus;
import com.community.business.entity.BusinessSuspendCode;
import com.community.business.entity.NtsBizStatus;
import com.community.business.exception.BusinessException;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.business.repository.master.BusinessServiceRepository;
import com.community.business.repository.master.BusinessStore;
import com.community.business.repository.master.BusinessStore.DocumentRow;
import com.community.business.repository.master.BusinessStore.NtsRow;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사업자 심사(admin-web {@code /businesses}, docs/features/business-contract.md) — 목록·상세(가린 값)·국세청 다시 조회·승인·반려·정지·정지 해제·평문 열람·등록증 사본.
 * 클래스 트랜잭션을 두지 않는다(국세청 조회는 외부 호출). 처리는 신원 줄을 잠그고 상태를 다시 본다 — 이미 바뀌었으면 409 {@code BUSINESS_STATE_CHANGED}.
 *
 * <ul>
 *   <li>승인(B1): 가장 최근 국세청 조회가 계속사업자면 그대로, 조회 불가면 사유 5~200자(감사 {@code BUSINESS_NTS_OVERRIDE}), 휴·폐업·없는 번호는 409 {@code BUSINESS_NTS_NOT_ACTIVE},
 *       조회한 적이 없으면 409 {@code BUSINESS_NTS_NOT_CHECKED}.</li>
 *   <li>정지(H2): 진행 건 처리 선택을 저장·감사하고 {@link BusinessApplicationHook#onSuspended} 를 같은 트랜잭션에서 부른다(신청 건 백엔드가 처리).</li>
 *   <li>평문·사본 열람(B2·B3): 열람 기록(운영자·IP·사유)·처리 기록·감사를 같은 트랜잭션에서 남긴 뒤에만 값을 준다. 평문은 10분 뒤 화면이 가린다.</li>
 * </ul>
 * 회원 역할 「사업자 회원」은 붙이지 않는다 — 계약 「서버 구현 결정」(기존 역할이 글쓰기·쪽지를 막는 별도 계정용이다).
 */
@Service
public class BusinessAdminService {

    /** 평문을 화면에 두는 시간(B3) */
    static final Duration REVEAL_TTL = Duration.ofMinutes(10);
    static final int PAGE_SIZE_MAX = 100;

    private final BusinessProfileRepository profiles;
    private final BusinessServiceRepository services;
    private final BusinessStore store;
    private final BusinessPii pii;
    private final BusinessNtsService nts;
    private final BusinessServiceRows serviceRows;
    private final BusinessNotices notices;
    private final ObjectProvider<BusinessApplicationHook> hooks;
    private final AuditLogService audit;
    private final Messages messages;
    private final SnowflakeIdGenerator ids;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;
    private final Clock clock;

    public BusinessAdminService(BusinessProfileRepository profiles, BusinessServiceRepository services, BusinessStore store, BusinessPii pii, BusinessNtsService nts,
                                BusinessServiceRows serviceRows, BusinessNotices notices, ObjectProvider<BusinessApplicationHook> hooks, AuditLogService audit,
                                Messages messages, SnowflakeIdGenerator ids, DataSource dataSource, PlatformTransactionManager transactions,
                                ObjectProvider<Clock> clocks) {
        this.profiles = profiles;
        this.services = services;
        this.store = store;
        this.pii = pii;
        this.nts = nts;
        this.serviceRows = serviceRows;
        this.notices = notices;
        this.hooks = hooks;
        this.audit = audit;
        this.messages = messages;
        this.ids = ids;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(transactions);
        this.readTx = new TransactionTemplate(transactions);
        this.readTx.setReadOnly(true);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    /** 상태·서비스·검색(상호·닉네임·등록번호 10자리·끝 4자리) — 상태별 건수는 상태 거르기를 뺀 같은 조건 */
    public BusinessAdminView.Page list(String statusText, String serviceText, String q, int page, int size) {
        BusinessStatus status = optionalEnum(BusinessStatus.class, statusText, "status");
        BusinessServiceKind service = optionalEnum(BusinessServiceKind.class, serviceText, "service");
        int limit = Math.min(Math.max(size, 1), PAGE_SIZE_MAX);
        int offset = Math.max(page, 0) * limit;
        MapSqlParameterSource args = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" FROM business_profiles bp LEFT JOIN profiles pr ON pr.user_id = bp.user_id WHERE 1 = 1");
        if (service != null) {
            where.append(" AND EXISTS (SELECT 1 FROM business_services s WHERE s.profile_id = bp.id AND s.service = :service AND s.status <> 'OFF')");
            args.addValue("service", service.name());
        }
        String term = q == null ? "" : q.strip();
        if (!term.isEmpty()) {
            String digits = BusinessFields.digits(term);
            where.append(" AND (bp.company_name ILIKE :like OR pr.nickname ILIKE :like");
            args.addValue("like", "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            if (digits.length() == 10 && pii.isIndexReady()) {
                where.append(" OR bp.biz_no_hash = :hash");
                args.addValue("hash", pii.bizNoHash(digits));
            } else if (digits.length() == 4 && digits.equals(term)) {
                where.append(" OR bp.biz_no_last4 = :last4");
                args.addValue("last4", digits);
            }
            where.append(")");
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (BusinessStatus s : BusinessStatus.values()) {
            counts.put(s.name(), 0L);
        }
        jdbc.query("SELECT bp.status, count(*)" + where + " GROUP BY bp.status", args, rs -> {
            counts.put(rs.getString(1), rs.getLong(2));
        });
        String filtered = where + (status == null ? "" : " AND bp.status = :status");
        if (status != null) {
            args.addValue("status", status.name());
        }
        Long total = jdbc.queryForObject("SELECT count(*)" + filtered, args, Long.class);
        args.addValue("limit", limit).addValue("offset", offset);
        List<RowBase> rows = jdbc.query("SELECT bp.id, bp.company_name, bp.biz_no_last4, bp.status, bp.re_review, bp.user_id, pr.nickname, bp.submitted_at" + filtered
                        + " ORDER BY bp.submitted_at DESC, bp.id DESC LIMIT :limit OFFSET :offset", args,
                (rs, i) -> new RowBase(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getLong(6), rs.getString(7),
                        rs.getTimestamp(8).toInstant()));
        List<Long> idList = rows.stream().map(RowBase::id).toList();
        Map<Long, List<BusinessServiceEntity>> serviceMap = idList.isEmpty() ? Map.of()
                : services.findByProfileIdIn(idList).stream().collect(Collectors.groupingBy(BusinessServiceEntity::getProfileId));
        Map<Long, NtsRow> ntsMap = store.latestNtsChecks(idList);
        List<BusinessAdminView.Row> items = rows.stream().map(r -> new BusinessAdminView.Row(Long.toString(r.id()), r.name(), BusinessFields.maskBizNo(r.last4()),
                r.status(), r.reReview(), serviceRows(serviceMap.getOrDefault(r.id(), List.of())), new BusinessAdminView.Applicant(Long.toString(r.userId()), r.nickname()),
                r.submittedAt(), ntsView(ntsMap.get(r.id()), r.submittedAt()))).toList();
        return new BusinessAdminView.Page(items, total == null ? 0 : total, counts);
    }

    private record RowBase(long id, String name, String last4, String status, boolean reReview, long userId, String nickname, Instant submittedAt) {
    }

    // ── 상세 ────────────────────────────────────────────────────────────────

    public BusinessAdminView.Detail detail(String idText) {
        long id = parseId(idText);
        return readTx.execute(status -> detailOf(profiles.findById(id).orElseThrow(BusinessException::notFound)));
    }

    // ── 국세청 다시 조회(B1) ────────────────────────────────────────────────

    public BusinessAdminView.Detail recheckNts(AdminActor actor, String idText) {
        long id = parseId(idText);
        nts.check(id, actor.operatorId(), actor.ip());
        return detail(idText);
    }

    // ── 승인·반려 ───────────────────────────────────────────────────────────

    public BusinessAdminView.Detail approve(AdminActor actor, String idText, BusinessAdminRequest.Approve body) {
        long id = parseId(idText);
        String override = body == null ? null : strip(body.ntsOverrideReason());
        return tx.execute(status -> {
            Instant now = now();
            BusinessProfileEntity p = lockPending(id);
            NtsRow check = store.latestNtsCheck(id).orElseThrow(BusinessException::ntsNotChecked);
            String reason = null;
            switch (check.status()) {
                case CONTINUING -> {
                }
                case UNAVAILABLE -> {
                    if (override == null || override.length() < 5 || override.length() > 200) {
                        throw ValidationException.of("ntsOverrideReason", new MessageRef("business.reason.invalid", 5, 200));
                    }
                    reason = override;
                }
                default -> throw BusinessException.ntsNotActive();
            }
            boolean wasReReview = p.isReReview();
            p.approve(actor.operatorId(), reason, now);
            serviceRows.sync(id, BusinessServiceStatus.APPROVED, actor.operatorId(), now);
            store.insertHistory(ids.nextId(), id, BusinessStore.APPROVED, BusinessStore.OPERATOR, actor.operatorId(), reason == null ? null : "NTS_OVERRIDE", now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("companyName", p.getCompanyName());
            detail.put("ntsStatus", check.status().name());
            detail.put("reReview", wasReReview);
            record(actor, AuditActions.BUSINESS_APPROVE, id, detail);
            if (reason != null) {
                record(actor, AuditActions.BUSINESS_NTS_OVERRIDE, id, Map.of("reason", reason));
            }
            notices.member(p.getUserId(), BusinessNotices.APPROVED, p.getCompanyName(), null);
            return detailOf(p);
        });
    }

    public BusinessAdminView.Detail reject(AdminActor actor, String idText, BusinessAdminRequest.Reject body) {
        long id = parseId(idText);
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        BusinessRejectCode code = enumOrError(BusinessRejectCode.class, body == null ? null : body.code(), "code", errors);
        String text = text(body == null ? null : body.text(), "text", errors);
        throwIf(errors);
        return tx.execute(status -> {
            Instant now = now();
            BusinessProfileEntity p = lockPending(id);
            p.reject(actor.operatorId(), code, text, now);
            serviceRows.sync(id, BusinessServiceStatus.REJECTED, actor.operatorId(), now);
            store.insertHistory(ids.nextId(), id, BusinessStore.REJECTED, BusinessStore.OPERATOR, actor.operatorId(), code.name(), now);
            record(actor, AuditActions.BUSINESS_REJECT, id, Map.of("code", code.name(), "text", text));
            notices.member(p.getUserId(), BusinessNotices.REJECTED, p.getCompanyName(), text);
            return detailOf(p);
        });
    }

    // ── 정지·해제(H2) ───────────────────────────────────────────────────────

    public BusinessAdminView.Detail suspend(AdminActor actor, String idText, BusinessAdminRequest.Suspend body) {
        long id = parseId(idText);
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        BusinessSuspendCode code = enumOrError(BusinessSuspendCode.class, body == null ? null : body.code(), "code", errors);
        String text = text(body == null ? null : body.text(), "text", errors);
        BusinessSuspendPlan.Pending pending = enumOrError(BusinessSuspendPlan.Pending.class, body == null ? null : body.pendingAction(), "pendingAction", errors);
        BusinessSuspendPlan.Scheduled scheduled = enumOrError(BusinessSuspendPlan.Scheduled.class, body == null ? null : body.scheduledAction(), "scheduledAction", errors);
        BusinessSuspendPlan.Running running = enumOrError(BusinessSuspendPlan.Running.class, body == null ? null : body.runningAction(), "runningAction", errors);
        throwIf(errors);
        BusinessSuspendPlan plan = new BusinessSuspendPlan(pending, scheduled, running);
        return tx.execute(status -> {
            Instant now = now();
            BusinessProfileEntity p = profiles.findWithLockById(id).orElseThrow(BusinessException::notFound);
            if (!p.suspendable()) {
                throw BusinessException.stateChanged();
            }
            p.suspend(actor.operatorId(), code, text, plan, now);
            serviceRows.sync(id, BusinessServiceStatus.SUSPENDED, actor.operatorId(), now);
            hook().onSuspended(id, plan, actor.operatorId());
            store.insertHistory(ids.nextId(), id, BusinessStore.SUSPENDED, BusinessStore.OPERATOR, actor.operatorId(), code.name(), now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("code", code.name());
            detail.put("text", text);
            detail.put("pendingAction", pending.name());
            detail.put("scheduledAction", scheduled.name());
            detail.put("runningAction", running.name());
            record(actor, AuditActions.BUSINESS_SUSPEND, id, detail);
            notices.member(p.getUserId(), BusinessNotices.SUSPENDED, p.getCompanyName(), text);
            return detailOf(p);
        });
    }

    public BusinessAdminView.Detail unsuspend(AdminActor actor, String idText, BusinessAdminRequest.Unsuspend body) {
        long id = parseId(idText);
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        BusinessSuspendPlan.Restore restore = enumOrError(BusinessSuspendPlan.Restore.class, body == null ? null : body.restore(), "restore", errors);
        String note = body == null ? null : strip(body.note());
        if (note != null && note.length() > 200) {
            errors.put("note", new MessageRef("business.text.invalid", 1, 200));
        }
        throwIf(errors);
        return tx.execute(status -> {
            Instant now = now();
            BusinessProfileEntity p = profiles.findWithLockById(id).orElseThrow(BusinessException::notFound);
            if (p.getStatus() != BusinessStatus.SUSPENDED) {
                throw BusinessException.stateChanged();
            }
            p.unsuspend(now);
            serviceRows.move(id, EnumSet.of(BusinessServiceStatus.SUSPENDED), BusinessServiceStatus.APPROVED, actor.operatorId(), now);
            hook().onUnsuspended(id, restore, actor.operatorId());
            store.insertHistory(ids.nextId(), id, BusinessStore.UNSUSPENDED, BusinessStore.OPERATOR, actor.operatorId(), restore.name(), now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("restore", restore.name());
            if (note != null) {
                detail.put("note", note);
            }
            record(actor, AuditActions.BUSINESS_UNSUSPEND, id, detail);
            notices.member(p.getUserId(), BusinessNotices.UNSUSPENDED, p.getCompanyName(), null);
            return detailOf(p);
        });
    }

    // ── 평문·사본 열람(B2·B3) ───────────────────────────────────────────────

    public BusinessAdminView.Reveal reveal(AdminActor actor, String idText, BusinessAdminRequest.Reveal body) {
        long id = parseId(idText);
        String reason = body == null ? null : strip(body.reason());
        if (reason == null || reason.length() < 5 || reason.length() > 200) {
            throw ValidationException.of("reason", new MessageRef("business.reason.invalid", 5, 200));
        }
        return tx.execute(status -> {
            Instant now = now();
            BusinessProfileEntity p = profiles.findById(id).orElseThrow(BusinessException::notFound);
            BusinessPii.Plain plain = pii.plain(p);
            if (plain == null) {
                throw BusinessException.stateChanged();
            }
            store.insertView(ids.nextId(), id, "REVEAL", actor.operatorId(), reason, actor.ip(), now);
            store.insertHistory(ids.nextId(), id, BusinessStore.REVEALED, BusinessStore.OPERATOR, actor.operatorId(), reason, now);
            record(actor, AuditActions.BUSINESS_REVEAL, id, Map.of("reason", reason));
            String address = "(" + plain.postcode() + ") " + plain.address1() + (plain.address2() == null ? "" : " " + plain.address2());
            return new BusinessAdminView.Reveal(BusinessFields.formatBizNo(plain.bizNo()), plain.ownerName(), address, BusinessFields.formatPhone(plain.phone()),
                    plain.email(), now.plus(REVEAL_TTL));
        });
    }

    public BusinessAdminView.CertificateFile certificate(AdminActor actor, String idText) {
        long id = parseId(idText);
        return tx.execute(status -> {
            Instant now = now();
            BusinessProfileEntity p = profiles.findById(id).orElseThrow(BusinessException::notFound);
            DocumentRow doc = p.getCertificateId() == null ? null : store.document(p.getCertificateId()).filter(d -> d.erasedAt() == null).orElse(null);
            byte[] content = doc == null ? null : store.documentContent(doc.id()).orElse(null);
            if (content == null) {
                throw BusinessException.certificateNotFound();
            }
            byte[] plain = pii.decryptFile(content);
            store.insertView(ids.nextId(), id, "CERTIFICATE", actor.operatorId(), null, actor.ip(), now);
            store.insertHistory(ids.nextId(), id, BusinessStore.CERT_VIEWED, BusinessStore.OPERATOR, actor.operatorId(), null, now);
            record(actor, AuditActions.BUSINESS_CERT_VIEW, id, Map.of("fileName", doc.fileName()));
            return new BusinessAdminView.CertificateFile("data:" + doc.contentType() + ";base64," + Base64.getEncoder().encodeToString(plain), doc.contentType(),
                    doc.fileName());
        });
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private BusinessAdminView.Detail detailOf(BusinessProfileEntity p) {
        long id = p.getId();
        BusinessApplicationHook hook = hook();
        List<BusinessServiceEntity> rows = services.findByProfileId(id);
        String nickname = jdbc.query("SELECT nickname FROM profiles WHERE user_id = :id", new MapSqlParameterSource("id", p.getUserId()), (rs, i) -> rs.getString(1))
                .stream().findFirst().orElse(null);
        BusinessPii.Plain plain = pii.isReady() ? pii.plain(p) : null;
        BusinessAdminView.Masked masked = plain == null ? new BusinessAdminView.Masked("", "", "", "")
                : new BusinessAdminView.Masked(BusinessFields.maskName(plain.ownerName()), BusinessFields.maskAddress(plain.address1()),
                BusinessFields.maskPhone(plain.phone()), BusinessFields.maskEmail(plain.email()));
        DocumentRow doc = p.getCertificateId() == null ? null : store.document(p.getCertificateId()).filter(d -> d.erasedAt() == null).orElse(null);
        BusinessAdminView.Certificate cert = doc == null ? null : store.lastView(id, "CERTIFICATE")
                .map(v -> new BusinessAdminView.Certificate(doc.fileName(), doc.contentType(), doc.sizeBytes(), doc.createdAt(), v.operatorName(), v.at()))
                .orElseGet(() -> new BusinessAdminView.Certificate(doc.fileName(), doc.contentType(), doc.sizeBytes(), doc.createdAt(), null, null));
        List<BusinessAdminView.ServiceCard> cards = sorted(rows).stream().map(s -> {
            BusinessApplicationHook.ServiceCounts c = hook.counts(id, s.getService());
            return new BusinessAdminView.ServiceCard(s.getService().name(), s.getStatus().name(), c.liveCount(), c.pendingCount());
        }).toList();
        BusinessApplicationHook.SuspendImpact impact = hook.impact(id);
        BusinessAdminView.Rejection rejection = p.getStatus() == BusinessStatus.REJECTED && p.getRejectCode() != null
                ? new BusinessAdminView.Rejection(p.getRejectCode().name(), p.getRejectText()) : null;
        BusinessAdminView.Suspension suspension = p.getStatus() == BusinessStatus.SUSPENDED && p.getSuspendCode() != null
                ? new BusinessAdminView.Suspension(p.getSuspendCode().name(), p.getSuspendText(), p.getSuspendedAt()) : null;
        List<BusinessAdminView.History> history = store.history(id).stream().map(h -> new BusinessAdminView.History(h.at(), historyAction(h.action()),
                actorName(h.actorType(), h.actorName()), historyNote(h.action(), h.note()))).toList();
        return new BusinessAdminView.Detail(Long.toString(id), p.getCompanyName(), BusinessFields.maskBizNo(p.getBizNoLast4()), p.getStatus().name(), p.isReReview(),
                serviceRows(rows), new BusinessAdminView.Applicant(Long.toString(p.getUserId()), nickname), p.getSubmittedAt(),
                ntsView(store.latestNtsCheck(id).orElse(null), p.getSubmittedAt()), masked, cert, cards,
                new BusinessAdminView.Impact(impact.awaitingPayment(), impact.scheduled(), impact.running()), rejection, suspension, history);
    }

    private BusinessProfileEntity lockPending(long id) {
        BusinessProfileEntity p = profiles.findWithLockById(id).orElseThrow(BusinessException::notFound);
        if (p.getStatus() != BusinessStatus.PENDING) {
            throw BusinessException.stateChanged();
        }
        return p;
    }

    private BusinessApplicationHook hook() {
        return hooks.getIfAvailable(() -> BusinessApplicationHook.NONE);
    }

    private BusinessAdminView.Nts ntsView(NtsRow row, Instant appliedAt) {
        if (row == null) {
            return new BusinessAdminView.Nts(NtsBizStatus.UNAVAILABLE.name(), appliedAt, null, null, messages.get(new MessageRef("business.nts.message.NOT_CHECKED")));
        }
        String message = row.message() == null ? null : messages.get(new MessageRef("business.nts.message." + row.message()));
        return new BusinessAdminView.Nts(row.status().name(), row.checkedAt(), row.taxType(), row.closedOn(), message);
    }

    private static List<BusinessAdminView.ServiceRow> serviceRows(List<BusinessServiceEntity> rows) {
        return sorted(rows).stream().map(s -> new BusinessAdminView.ServiceRow(s.getService().name(), s.getStatus().name())).toList();
    }

    private static List<BusinessServiceEntity> sorted(List<BusinessServiceEntity> rows) {
        List<BusinessServiceEntity> out = new ArrayList<>(rows);
        out.sort(Comparator.comparing(BusinessServiceEntity::getService));
        return out;
    }

    /** 화면의 처리 기록 행위 — 서버의 {@code UNSUSPENDED}·{@code WITHDRAWN} 도 그대로 보낸다(화면은 모르는 값을 이름 없이 보인다) */
    private static String historyAction(String action) {
        return action;
    }

    /** 처리 기록의 메모 — 국세청 조회 결과·사유 코드는 읽는 사람 언어로 */
    private String historyNote(String action, String note) {
        if (note == null) {
            return null;
        }
        return switch (action) {
            case BusinessStore.NTS_CHECKED -> messages.get(new MessageRef("business.history.nts." + note));
            case BusinessStore.REJECTED -> messages.get(new MessageRef("business.rejectCode." + note));
            case BusinessStore.SUSPENDED -> messages.get(new MessageRef("business.suspendCode." + note));
            case BusinessStore.UNSUSPENDED -> messages.get(new MessageRef("business.restore." + note));
            case BusinessStore.APPROVED, BusinessStore.UPDATED -> messages.get(new MessageRef("business.history.note." + note));
            default -> note;
        };
    }

    private String actorName(String actorType, String name) {
        if (BusinessStore.SYSTEM.equals(actorType)) {
            return messages.get(new MessageRef("business.history.actor.SYSTEM"));
        }
        return name == null ? messages.get(new MessageRef("business.history.actor.UNKNOWN")) : name;
    }

    private void record(AdminActor actor, String action, long id, Map<String, Object> detail) {
        audit.record(new AuditEvent(AuditActorType.OPERATOR, actor.operatorId(), action, AuditResult.SUCCESS, BusinessNtsService.AUDIT_TARGET, Long.toString(id), actor.ip(),
                detail));
    }

    private static long parseId(String text) {
        try {
            return Long.parseLong(text == null ? "" : text.strip());
        } catch (NumberFormatException e) {
            throw new CommonException(ErrorCode.NOT_FOUND);
        }
    }

    private static <E extends Enum<E>> E optionalEnum(Class<E> type, String text, String field) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        E value = enumOrError(type, text, field, errors);
        throwIf(errors);
        return value;
    }

    private static <E extends Enum<E>> E enumOrError(Class<E> type, String text, String field, Map<String, MessageRef> errors) {
        Map<String, E> byName = java.util.Arrays.stream(type.getEnumConstants()).collect(Collectors.toMap(Enum::name, Function.identity()));
        E value = text == null ? null : byName.get(text.strip());
        if (value == null) {
            errors.put(field, new MessageRef("business.value.invalid"));
        }
        return value;
    }

    /** 사유 글 1~200자 */
    private static String text(String value, String field, Map<String, MessageRef> errors) {
        String s = strip(value);
        if (s == null || s.length() > 200) {
            errors.put(field, new MessageRef("business.text.invalid", 1, 200));
        }
        return s;
    }

    private static void throwIf(Map<String, MessageRef> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static String strip(String value) {
        if (value == null) {
            return null;
        }
        String s = value.strip();
        return s.isEmpty() ? null : s;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
