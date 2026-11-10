package com.community.terms.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.SiteOperatorValues;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.service.SiteOperatorReader;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.terms.dto.TermsOperatorSnapshot;
import com.community.terms.dto.TermsStatus;
import com.community.terms.dto.TermsVersionMeta;
import com.community.terms.dto.request.TermsDraftRequest;
import com.community.terms.dto.request.TermsPublishRequest;
import com.community.terms.dto.request.TermsSettingsRequest;
import com.community.terms.dto.response.TermsAdminOverviewResponse;
import com.community.terms.dto.response.TermsDraftView;
import com.community.terms.dto.response.TermsKindDetailResponse;
import com.community.terms.dto.response.TermsKindOverview;
import com.community.terms.dto.response.TermsOperatorSnapshotView;
import com.community.terms.dto.response.TermsSettingsResponse;
import com.community.terms.dto.response.TermsVersionBrief;
import com.community.terms.dto.response.TermsVersionDetailResponse;
import com.community.terms.dto.response.TermsVersionRow;
import com.community.terms.entity.TermsDraftEntity;
import com.community.terms.entity.TermsKind;
import com.community.terms.entity.TermsVersionEntity;
import com.community.terms.repository.master.TermsDraftRepository;
import com.community.terms.repository.master.TermsVersionRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 약관 화면 — 개요·종류별 초안과 이력·게시본 보기, 초안 저장·삭제, 새 버전 게시, 「보기」 링크 옵션 저장. 입구 권한은 컨트롤러가 선언한다
 * (읽기 {@code terms.read}, 쓰기 {@code terms.manage}). 칸별 문구 경로({@code fields.body} 등)가 계약이라 서비스가 검사한다.
 *
 * <p>게시본은 고치지 않는다. 게시는 초안 행을 잠가 종류마다 한 번에 하나씩 줄 세우고, 번호는 마지막 버전 + 1 이다({@code (kind, version)} 유일 제약이 한 번 더 막는다).
 */
@Service
public class TermsAdminService {

    private static final Logger log = LoggerFactory.getLogger(TermsAdminService.class);

    /** 변경 요지 상한(글자) */
    static final int CHANGE_NOTE_MAX = 200;
    /** 개정의 최소 공지 기간(일) — 시행일은 오늘 + 7일 이후 */
    static final int REVISION_NOTICE_DAYS = 7;
    /** 회원에게 불리한 개정의 최소 공지 기간(일) — 시행일은 오늘 + 30일 이후 */
    static final int UNFAVORABLE_NOTICE_DAYS = 30;
    /** 정화 전 입력 상한 — 터무니없이 큰 입력을 정화기에 넣지 않는다(정화 후 상한의 5배) */
    private static final int RAW_BODY_MAX = TermsHtml.BODY_MAX * 5;
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final TermsService terms;
    private final TermsVersionRepository versions;
    private final TermsDraftRepository drafts;
    private final SiteSettingRepository settings;
    private final OperatorRepository operators;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final SiteOperatorReader operatorReader;
    private final TermsChangeMails changeMails;

    public TermsAdminService(TermsService terms, TermsVersionRepository versions, TermsDraftRepository drafts, SiteSettingRepository settings,
            OperatorRepository operators, AuditLogService audit, SnowflakeIdGenerator ids, SiteOperatorReader operatorReader, TermsChangeMails changeMails) {
        this.terms = terms;
        this.versions = versions;
        this.drafts = drafts;
        this.settings = settings;
        this.operators = operators;
        this.audit = audit;
        this.ids = ids;
        this.operatorReader = operatorReader;
        this.changeMails = changeMails;
    }

    @Transactional(readOnly = true)
    public TermsAdminOverviewResponse overview() {
        Map<TermsKind, TermsTimeline> timelines = terms.timelines();
        Map<String, TermsDraftEntity> draftByKind = drafts.findAll().stream().collect(Collectors.toMap(d -> d.getKind().name(), d -> d));
        List<TermsKindOverview> kinds = timelines.entrySet().stream().map(entry -> {
            TermsTimeline timeline = entry.getValue();
            TermsDraftEntity draft = draftByKind.get(entry.getKey().name());
            return new TermsKindOverview(entry.getKey().name(), timeline.current().map(TermsAdminService::brief).orElse(null),
                    timeline.upcoming().map(TermsAdminService::brief).orElse(null), draft == null ? null : draft.getUpdatedAt(), timeline.versions().size());
        }).toList();
        return new TermsAdminOverviewResponse(terms.linksVisible(), kinds);
    }

    @Transactional(readOnly = true)
    public TermsKindDetailResponse detail(String kindPath) {
        TermsKind kind = kindOf(kindPath);
        TermsTimeline timeline = terms.timeline(kind);
        Optional<TermsDraftEntity> draft = drafts.findById(kind.name());
        Set<Long> operatorIds = timeline.versions().stream().map(TermsVersionMeta::publishedBy).filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        draft.map(TermsDraftEntity::getUpdatedBy).ifPresent(operatorIds::add);
        Map<Long, String> names = operatorNames(operatorIds);
        List<TermsVersionRow> rows = timeline.versions().stream()
                .map(v -> new TermsVersionRow(v.version(), v.effectiveDate().toString(), v.publishedAt(), nameOf(names, v.publishedBy()), v.changeNote(),
                        timeline.statusOf(v.version()).name(), v.unfavorable(), snapshotView(v.operatorSnapshot())))
                .toList();
        return new TermsKindDetailResponse(kind.name(), draft.map(d -> draftView(d, names)).orElse(null), rows);
    }

    @Transactional(readOnly = true)
    public TermsVersionDetailResponse version(String kindPath, int version) {
        TermsKind kind = kindOf(kindPath);
        TermsVersionEntity entity = versions.findByKindAndVersion(kind, version).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND));
        TermsStatus status = terms.timeline(kind).statusOf(version);
        return versionView(entity, status, operatorNames(entity.getPublishedBy() == null ? Set.of() : Set.of(entity.getPublishedBy())));
    }

    /** 초안 저장 — 본문을 정화해 넣는다. 감사 기록은 남기지 않는다(계약) */
    @Transactional
    public TermsDraftView saveDraft(AdminActor actor, String kindPath, TermsDraftRequest request) {
        TermsKind kind = kindOf(kindPath);
        String body = cleanBody(request == null ? null : request.body());
        Instant now = Instant.now();
        TermsDraftEntity draft = drafts.findWithLockByKind(kind.name()).orElse(null);
        if (draft == null) {
            draft = TermsDraftEntity.create(kind, body, actor.operatorId(), now);
            drafts.persist(draft);
        } else {
            draft.changeBody(body, actor.operatorId(), now);
        }
        log.info("약관 초안 저장 kind={} operatorId={}", kind, actor.operatorId());
        return draftView(draft, operatorNames(Set.of(actor.operatorId())));
    }

    /** 초안 삭제 — 없어도 성공 */
    @Transactional
    public void deleteDraft(AdminActor actor, String kindPath) {
        TermsKind kind = kindOf(kindPath);
        drafts.findWithLockByKind(kind.name()).ifPresent(draft -> {
            drafts.delete(draft);
            log.info("약관 초안 삭제 kind={} operatorId={}", kind, actor.operatorId());
        });
    }

    /**
     * 초안 본문으로 새 버전을 게시하고 초안을 지운다(terms-operator-contract).
     *
     * <ol>
     *   <li><b>최소 시행일</b>(사이트 시간대): 그 종류의 첫 게시본 = 내일 이후, 개정 = 7일 뒤 이후, 회원에게 불리한 변경({@code unfavorable}) = 30일 뒤 이후. 마지막 게시본 시행일 이후(같은 날 허용)는 그대로.</li>
     *   <li><b>자리표시</b>: 초안(자리표시 그대로)을 지금 운영 주체 값·사이트 이름·시행일로 바꾼 뒤({@link TermsPlaceholders}) {@link TermsHtml} 로 다시 정화해 버전 본문으로 저장한다.
     *       초안은 자리표시 그대로다(게시하면 지운다). 모르는 자리표시가 있거나, 쓴 자리표시의 값이 운영 주체 설정에 없으면 거절({@code fields.draft}).</li>
     *   <li><b>사본</b>: 게시 당시 운영 주체 값을 버전에 함께 남긴다 — 나중에 단계를 바꿔도 지난 판은 그대로다.</li>
     *   <li><b>불리한 변경 메일</b>: 이용약관·개인정보처리방침·운영정책을 {@code unfavorable} 로 게시하면 가입 회원 전체에게 메일을 대기열로 넣는다({@link TermsChangeMails} — 나눠 보낸다).</li>
     * </ol>
     */
    @Transactional
    public TermsVersionDetailResponse publish(AdminActor actor, String kindPath, TermsPublishRequest request) {
        TermsKind kind = kindOf(kindPath);
        Optional<TermsDraftEntity> draft = drafts.findWithLockByKind(kind.name());
        TermsTimeline timeline = terms.timeline(kind);
        LocalDate today = timeline.today();
        boolean unfavorable = request != null && Boolean.TRUE.equals(request.unfavorable());

        Map<String, MessageRef> errors = new LinkedHashMap<>();
        LocalDate effectiveDate = parseDate(request == null ? null : request.effectiveDate(), errors);
        Optional<TermsVersionMeta> latest = timeline.latest();
        if (effectiveDate != null) {
            LocalDate min = minEffectiveDate(today, latest.isPresent(), unfavorable);
            if (effectiveDate.isBefore(min)) {
                // 첫 게시본은 「내일부터」(오늘 시행은 받지 않는다) · 개정은 7일 뒤 · 불리한 변경은 30일 뒤부터
                errors.put("effectiveDate", unfavorable ? new MessageRef("terms.effectiveDate.unfavorable", min.toString())
                        : latest.isPresent() ? new MessageRef("terms.effectiveDate.revision", min.toString()) : new MessageRef("terms.effectiveDate.past"));
            } else if (latest.isPresent() && effectiveDate.isBefore(latest.get().effectiveDate())) {
                errors.put("effectiveDate", new MessageRef("terms.effectiveDate.beforeLast", latest.get().effectiveDate().toString()));
            }
        }
        String changeNote = request == null || request.changeNote() == null ? null : request.changeNote().strip();
        if (changeNote != null && changeNote.isEmpty()) {
            changeNote = null;
        }
        if (changeNote != null && changeNote.codePointCount(0, changeNote.length()) > CHANGE_NOTE_MAX) {
            errors.put("changeNote", new MessageRef("terms.changeNote.max", CHANGE_NOTE_MAX));
        }
        // 자리표시 바꾸기 — 모르는 이름은 날짜 칸 오류와 함께 한꺼번에 알린다(이름 판정은 시행일과 무관하다)
        SiteOperatorValues operator = operatorReader.current();
        String serviceName = settings.findById(SiteSettingEntity.ROW_ID).map(SiteSettingEntity::getSiteName).orElse("");
        String body = null;
        if (draft.isEmpty()) {
            errors.put("draft", new MessageRef("terms.draft.missing"));
        } else {
            TermsPlaceholders.Result replaced = TermsPlaceholders.apply(TermsPlaceholders.unsplitBraces(draft.get().getBody()),
                    TermsPlaceholders.valuesOf(operator, serviceName, effectiveDate == null ? today : effectiveDate));
            if (!replaced.unknown().isEmpty()) {
                errors.put("draft", new MessageRef("terms.draft.unknownPlaceholder", replaced.unknown().stream().map(n -> "{{" + n + "}}").collect(Collectors.joining(", "))));
            } else if (!replaced.empty().isEmpty()) {
                // 운영 주체 설정에 값이 없는 칸 — 그대로 게시하면 게시본에 빈칸이 남는다
                errors.put("draft", new MessageRef("terms.draft.emptyPlaceholder", replaced.empty().stream().map(n -> "{{" + n + "}}").collect(Collectors.joining(", "))));
            } else {
                body = TermsHtml.sanitize(replaced.html());
                if (!TermsHtml.hasVisibleText(body)) {
                    errors.put("draft", new MessageRef("terms.body.required"));
                } else if (body.length() > TermsHtml.BODY_MAX) {
                    errors.put("draft", new MessageRef("terms.body.max", TermsHtml.BODY_MAX));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        int version = latest.map(TermsVersionMeta::version).orElse(0) + 1;
        Instant now = Instant.now();
        TermsVersionEntity entity = TermsVersionEntity.publish(ids.nextId(), kind, version, body, effectiveDate, changeNote, unfavorable,
                TermsOperatorSnapshot.of(operator, serviceName).toJson(), actor.operatorId(), now);
        versions.persist(entity);
        drafts.delete(draft.get());

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("version", version);
        after.put("effectiveDate", effectiveDate.toString());
        after.put("unfavorable", unfavorable);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.TERMS_PUBLISH, AuditResult.SUCCESS).target("TERMS", kind.name())
                .with("after", after));
        if (unfavorable && kind.memberNotice()) {
            changeMails.enqueue(kind, version, effectiveDate, changeNote);
        }
        log.info("약관 게시 kind={} version={} effectiveDate={} unfavorable={} stage={} operatorId={}", kind, version, effectiveDate, unfavorable, operator.stage(),
                actor.operatorId());

        TermsStatus status = effectiveDate.isAfter(today) ? TermsStatus.UPCOMING : TermsStatus.CURRENT;
        return versionView(entity, status, operatorNames(Set.of(actor.operatorId())));
    }

    /** 「보기」 링크 옵션 저장 — 바뀐 때만 감사 기록 */
    @Transactional
    public TermsSettingsResponse saveSettings(AdminActor actor, TermsSettingsRequest request) {
        boolean showLinks = Boolean.TRUE.equals(request.showLinks());
        SiteSettingEntity row = settings.findWithLockById(SiteSettingEntity.ROW_ID)
                .orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        boolean before = row.isTermsLinksVisible();
        if (before != showLinks) {
            row.changeTermsLinksVisible(showLinks, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.TERMS_LINKS_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("before", Map.of("showLinks", before)).with("after", Map.of("showLinks", showLinks)));
            log.info("약관 보기 링크 표시 {} operatorId={}", showLinks ? "켬" : "끔", actor.operatorId());
        }
        return new TermsSettingsResponse(row.isTermsLinksVisible());
    }

    // ── 도우미 ─────────────────────────────────────────────────────────────

    /** 주소의 {@code {kind}}(terms·privacy) → 종류. 모르면 404 */
    private static TermsKind kindOf(String path) {
        return TermsKind.ofPath(path).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND));
    }

    /** 본문 검사·정화 — 위반은 {@code fields.body} */
    private static String cleanBody(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ValidationException.of("body", new MessageRef("terms.body.required"));
        }
        if (raw.length() > RAW_BODY_MAX) {
            throw ValidationException.of("body", new MessageRef("terms.body.max", TermsHtml.BODY_MAX));
        }
        String safe = TermsHtml.sanitize(raw);
        if (!TermsHtml.hasVisibleText(safe)) {
            throw ValidationException.of("body", new MessageRef("terms.body.required"));
        }
        if (safe.length() > TermsHtml.BODY_MAX) {
            throw ValidationException.of("body", new MessageRef("terms.body.max", TermsHtml.BODY_MAX));
        }
        return safe;
    }

    /** {@code YYYY-MM-DD} → 날짜. 위반은 {@code errors} 의 {@code effectiveDate} 에 넣고 null */
    private static LocalDate parseDate(String raw, Map<String, MessageRef> errors) {
        String value = raw == null ? null : raw.strip();
        if (value == null || value.isEmpty()) {
            errors.put("effectiveDate", new MessageRef("terms.effectiveDate.required"));
            return null;
        }
        if (!DATE.matcher(value).matches()) {
            errors.put("effectiveDate", new MessageRef("terms.effectiveDate.invalid"));
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            errors.put("effectiveDate", new MessageRef("terms.effectiveDate.invalid"));
            return null;
        }
    }

    private static TermsVersionBrief brief(TermsVersionMeta meta) {
        return new TermsVersionBrief(meta.version(), meta.effectiveDate().toString(), meta.publishedAt());
    }

    private static TermsDraftView draftView(TermsDraftEntity draft, Map<Long, String> names) {
        return new TermsDraftView(draft.getBody(), draft.getUpdatedAt(), nameOf(names, draft.getUpdatedBy()));
    }

    private static TermsVersionDetailResponse versionView(TermsVersionEntity entity, TermsStatus status, Map<Long, String> names) {
        return new TermsVersionDetailResponse(entity.getVersion(), entity.getEffectiveDate().toString(), entity.getPublishedAt(),
                nameOf(names, entity.getPublishedBy()), entity.getChangeNote(), status.name(), entity.getBody(), entity.isUnfavorable(),
                snapshotView(entity.getOperatorSnapshot()));
    }

    /** 게시 당시 운영 주체 — 응답에는 단계·운영자 이름·상호만(사본이 없는 옛 판은 null) */
    private static TermsOperatorSnapshotView snapshotView(String json) {
        TermsOperatorSnapshot snapshot = TermsOperatorSnapshot.parse(json);
        return snapshot == null ? null : new TermsOperatorSnapshotView(snapshot.stage(), snapshot.operatorName(), snapshot.businessName());
    }

    /** 최소 시행일 — 첫 게시본 내일 · 개정 7일 뒤 · 불리한 변경 30일 뒤(사이트 시간대의 오늘 기준) */
    static LocalDate minEffectiveDate(LocalDate today, boolean revision, boolean unfavorable) {
        if (unfavorable) {
            return today.plusDays(UNFAVORABLE_NOTICE_DAYS);
        }
        return today.plusDays(revision ? REVISION_NOTICE_DAYS : 1);
    }

    private static String nameOf(Map<Long, String> names, Long operatorId) {
        return operatorId == null ? null : names.get(operatorId);
    }

    /** 운영자 표시 이름 — 지워진 운영자는 결과에 없다(이름 null) */
    private Map<Long, String> operatorNames(Collection<Long> operatorIds) {
        Map<Long, String> out = new HashMap<>();
        if (operatorIds.isEmpty()) {
            return out;
        }
        for (OperatorEntity operator : operators.findAllById(operatorIds)) {
            out.put(operator.getId(), operator.getOperatorName());
        }
        return out;
    }
}
