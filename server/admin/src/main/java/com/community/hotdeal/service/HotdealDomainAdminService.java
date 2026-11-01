package com.community.hotdeal.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.hotdeal.dto.request.HotdealAdminRequest;
import com.community.hotdeal.dto.response.HotdealRuleView;
import com.community.hotdeal.entity.DealDomainEntity;
import com.community.hotdeal.exception.HotdealDomainDuplicateException;
import com.community.hotdeal.exception.HotdealDomainNotFoundException;
import com.community.hotdeal.repository.master.AdminDealQueries;
import com.community.hotdeal.repository.master.DealDomainRepository;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 링크 규칙({@code deal_domains}) — 조회 {@code hotdeal.read}, 추가·고치기·지우기 {@code hotdeal.domain.manage}. hotdeal-contract 「링크 판정」·서버 규칙 2·3·7.
 *
 * <ul>
 *   <li><b>도메인</b>: 서버가 정규화한다({@link DealHosts#normalizeDomain} — 스킴·{@code www.}·포트·경로를 떼고 소문자·ASCII). 형식이 틀리면 {@code 400 fields.domain}, 다른 줄에 이미 있으면
 *       {@code 409 HOTDEAL_DOMAIN_DUPLICATE} + {@code fields.domain}.</li>
 *   <li><b>규칙</b>: BLOCK · AFFILIATE · null(대형몰 표시만 — 그러면 {@code common} 필수, 아니면 {@code 400 fields.common} — DB CHECK 와 같은 뜻). 모르는 값 {@code 400 fields.rule}.</li>
 *   <li><b>keyParams</b>: 같은 딜 키에 남길 쿼리 이름 — 이름 형식(영숫자·{@code _ - . []} 1~40자) · 최대 {@value DealDomainEntity#KEY_PARAMS_MAX}개 · 추적 파라미터 거절
 *       ({@link DealUrlKey#isTracking} — 같은 딜 키가 늘 지우는 목록과 같은 원천) · 같은 이름은 한 번. 어기면 {@code 400 fields.keyParams}.</li>
 *   <li><b>메모</b> 0~100자({@code 400 fields.note}). 단축 주소 표시·쇼핑몰 이름(시드 칸)은 화면이 보내지 않으므로 그대로 둔다(규칙이 차단이 아니게 바뀌면 단축 주소 표시는 꺼진다).</li>
 *   <li><b>저장 뒤</b>: 이 서버의 규칙 사본({@link DealRuleBook})을 커밋 뒤 버린다 — 공개 서버는 사본 수명(15초) 안에 따라온다. 이미 올라간 글은 그대로다(규칙 7).</li>
 *   <li><b>영향 미리 보기</b>({@link #impact}): 상품·최종 호스트가 그 도메인(하위 도메인 포함)인 진행 중(ACTIVE·FLAGGED) 공개 글 수 + 최근 3개 — 저장하지 않는다.</li>
 *   <li>감사 {@code HOTDEAL_DOMAIN_CREATE}·{@code UPDATE}(바뀐 칸 before/after)·{@code DELETE}, 대상 HOTDEAL_DOMAIN.</li>
 * </ul>
 */
@Service
public class HotdealDomainAdminService {

    private static final Logger log = LoggerFactory.getLogger(HotdealDomainAdminService.class);

    static final String TARGET = "HOTDEAL_DOMAIN";
    public static final int NOTE_MAX = 100;
    static final int IMPACT_SAMPLES = 3;
    /** 쿼리 이름 1~40자(영숫자 · {@code _} · {@code -} · {@code .} · {@code []}) — admin-web {@code KEY_PARAM} 과 같다 */
    static final Pattern KEY_PARAM = Pattern.compile("^[A-Za-z0-9_.\\[\\]-]{1,40}$");
    static final List<String> RULE_FILTERS = List.of("ALL", DealDomainEntity.BLOCK, DealDomainEntity.AFFILIATE, "NONE", "COMMON");

    private final DealDomainRepository domains;
    private final AdminDealQueries queries;
    private final DealRuleBook rules;
    private final DealSettingsReader settings;
    private final OperatorRepository operators;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;

    public HotdealDomainAdminService(DealDomainRepository domains, AdminDealQueries queries, DealRuleBook rules, DealSettingsReader settings,
                                     OperatorRepository operators, AuditLogService audit, SnowflakeIdGenerator ids) {
        this.domains = domains;
        this.queries = queries;
        this.rules = rules;
        this.settings = settings;
        this.operators = operators;
        this.audit = audit;
        this.ids = ids;
    }

    // ── 읽기 ──────────────────────────────────────────────────────────────

    /** 목록(도메인 차례, 쪽 없음) — {@code rule} ALL·BLOCK·AFFILIATE·NONE(규칙 없음)·COMMON(대형몰), {@code q} 도메인·메모·쿼리 이름 부분 일치 */
    @Transactional(readOnly = true)
    public HotdealRuleView.DomainList list(String ruleRaw, String q) {
        String rule = HotdealAdminQueryService.trim(ruleRaw) == null ? "ALL" : HotdealAdminQueryService.trim(ruleRaw).toUpperCase(Locale.ROOT);
        String needle = HotdealAdminQueryService.trim(q) == null ? null : HotdealAdminQueryService.trim(q).toLowerCase(Locale.ROOT);
        List<DealDomainEntity> rows = domains.findAllByOrderByDomainAsc().stream().filter(d -> switch (rule) {
            case DealDomainEntity.BLOCK, DealDomainEntity.AFFILIATE -> rule.equals(d.getRule());
            case "NONE" -> d.getRule() == null;
            case "COMMON" -> d.isCommon();
            default -> true;
        }).filter(d -> needle == null || d.getDomain().contains(needle) || (d.getNote() != null && d.getNote().toLowerCase(Locale.ROOT).contains(needle))
                || d.getKeyParams().stream().anyMatch(k -> k.toLowerCase(Locale.ROOT).contains(needle))).toList();
        Map<Long, String> names = operatorNames(rows.stream().map(DealDomainEntity::getUpdatedBy).toList());
        return new HotdealRuleView.DomainList(rows.stream().map(d -> view(d, names)).toList(), settings.current().affiliateMode());
    }

    /** 차단 미리 보기 — 이 도메인(하위 도메인 포함)으로 가는 진행 중 공개 글 */
    @Transactional(readOnly = true)
    public HotdealRuleView.Impact impact(String raw) {
        String domain = DealHosts.normalizeDomain(raw);
        if (domain == null) {
            throw ValidationException.of("domain", new MessageRef("hotdeal.admin.domain.invalid"));
        }
        AdminDealQueries.DomainImpact impact = queries.domainImpact(domain, settings.current().autoExpireDays(), HotdealAdminQueryService.now(), IMPACT_SAMPLES);
        return new HotdealRuleView.Impact(domain, impact.count(),
                impact.samples().stream().map(s -> new HotdealRuleView.ImpactPost(Long.toString(s.postId()), s.title(), s.storeName())).toList());
    }

    // ── 쓰기 ──────────────────────────────────────────────────────────────

    @Transactional
    public HotdealRuleView.Domain create(AdminActor actor, HotdealAdminRequest.Domain body) {
        Checked c = check(body);
        if (domains.findByDomain(c.domain()).isPresent()) {
            throw new HotdealDomainDuplicateException();
        }
        Instant now = now();
        DealDomainEntity d = DealDomainEntity.create(ids.nextId(), c.domain(), c.rule(), c.common(), false, c.keyParams(), null, c.note(), actor.operatorId(), now);
        domains.persist(d);
        domains.flush();
        record(actor, AuditActions.HOTDEAL_DOMAIN_CREATE, d.getId(), null, snapshot(d));
        AfterCommit.run(rules::invalidate);
        log.info("링크 규칙 추가 domain={} rule={} by operatorId={}", d.getDomain(), d.getRule(), actor.operatorId());
        return view(d, operatorNames(List.of(actor.operatorId())));
    }

    @Transactional
    public HotdealRuleView.Domain update(AdminActor actor, String idText, HotdealAdminRequest.Domain body) {
        Checked c = check(body);
        DealDomainEntity d = find(idText);
        if (!d.getDomain().equals(c.domain()) && domains.findByDomain(c.domain()).filter(other -> !other.getId().equals(d.getId())).isPresent()) {
            throw new HotdealDomainDuplicateException();
        }
        Map<String, Object> before = snapshot(d);
        if (!d.getDomain().equals(c.domain())) {
            d.rename(c.domain());
        }
        d.change(c.rule(), c.common(), d.isShortener(), c.keyParams(), d.getStoreName(), c.note(), actor.operatorId(), now());
        domains.flush();
        Map<String, Object> after = snapshot(d);
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();
        for (String key : after.keySet()) {
            if (!Objects.equals(before.get(key), after.get(key))) {
                changedBefore.put(key, before.get(key));
                changedAfter.put(key, after.get(key));
            }
        }
        if (!changedAfter.isEmpty()) {
            record(actor, AuditActions.HOTDEAL_DOMAIN_UPDATE, d.getId(), changedBefore, changedAfter);
        }
        AfterCommit.run(rules::invalidate);
        log.info("링크 규칙 고침 domain={} 칸={} by operatorId={}", d.getDomain(), changedAfter.keySet(), actor.operatorId());
        return view(d, operatorNames(List.of(actor.operatorId())));
    }

    @Transactional
    public void delete(AdminActor actor, String idText) {
        DealDomainEntity d = find(idText);
        Map<String, Object> before = snapshot(d);
        domains.delete(d);
        domains.flush();
        record(actor, AuditActions.HOTDEAL_DOMAIN_DELETE, d.getId(), before, null);
        AfterCommit.run(rules::invalidate);
        log.info("링크 규칙 지움 domain={} by operatorId={}", d.getDomain(), actor.operatorId());
    }

    // ── 검사 ──────────────────────────────────────────────────────────────

    /** 검사한 값 */
    record Checked(String domain, String rule, boolean common, List<String> keyParams, String note) {
    }

    /** 칸 검사 — 도메인 → 규칙 → 대형몰 → 쿼리 이름 → 메모 차례로 모아 한 번에 400 */
    static Checked check(HotdealAdminRequest.Domain body) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String domain = DealHosts.normalizeDomain(body == null ? null : body.domain());
        if (domain == null) {
            invalid.put("domain", new MessageRef("hotdeal.admin.domain.invalid"));
        }
        String ruleRaw = body == null ? null : HotdealAdminQueryService.trim(body.rule());
        String rule = ruleRaw == null ? null : ruleRaw.toUpperCase(Locale.ROOT);
        if ("NONE".equals(rule)) {
            rule = null;
        }
        if (rule != null && !DealDomainEntity.BLOCK.equals(rule) && !DealDomainEntity.AFFILIATE.equals(rule)) {
            invalid.put("rule", new MessageRef("hotdeal.admin.domain.rule"));
        }
        boolean common = body != null && Boolean.TRUE.equals(body.common());
        if (rule == null && !invalid.containsKey("rule") && !common) {
            invalid.put("common", new MessageRef("hotdeal.admin.domain.commonRequired"));
        }
        List<String> keyParams = new ArrayList<>();
        MessageRef keyError = keyParams(body == null ? null : body.keyParams(), keyParams);
        if (keyError != null) {
            invalid.put("keyParams", keyError);
        }
        String note = body == null || body.note() == null ? null : body.note().replace("\u0000", "").strip();
        if (note != null && note.isEmpty()) {
            note = null;
        }
        if (note != null && note.codePointCount(0, note.length()) > NOTE_MAX) {
            invalid.put("note", new MessageRef("hotdeal.admin.note.tooLong", String.valueOf(NOTE_MAX)));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        return new Checked(domain, rule, common, keyParams, note);
    }

    /** 쿼리 이름 검사 — 통과하면 {@code out} 에 채우고 null, 아니면 첫 문제 */
    static MessageRef keyParams(List<String> raw, List<String> out) {
        if (raw == null) {
            return null;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String name : raw) {
            String n = name == null ? "" : name.strip();
            if (!KEY_PARAM.matcher(n).matches()) {
                return new MessageRef("hotdeal.admin.keyParams.invalid");
            }
            if (DealUrlKey.isTracking(n)) {
                return new MessageRef("hotdeal.admin.keyParams.tracking");
            }
            seen.add(n);
        }
        if (seen.size() > DealDomainEntity.KEY_PARAMS_MAX) {
            return new MessageRef("hotdeal.admin.keyParams.tooMany", String.valueOf(DealDomainEntity.KEY_PARAMS_MAX));
        }
        out.addAll(seen);
        return null;
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private DealDomainEntity find(String idText) {
        Long id = HotdealAdminQueryService.parseId(idText);
        return (id == null ? java.util.Optional.<DealDomainEntity>empty() : domains.findById(id)).orElseThrow(HotdealDomainNotFoundException::new);
    }

    private static Map<String, Object> snapshot(DealDomainEntity d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("domain", d.getDomain());
        m.put("rule", d.getRule());
        m.put("common", d.isCommon());
        m.put("keyParams", d.getKeyParams());
        m.put("note", d.getNote());
        return m;
    }

    private void record(AdminActor actor, String action, long id, Map<String, Object> before, Map<String, Object> after) {
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target(TARGET, Long.toString(id));
        if (before != null) {
            event = event.with("before", before);
        }
        if (after != null) {
            event = event.with("after", after);
        }
        audit.record(event);
    }

    private HotdealRuleView.Domain view(DealDomainEntity d, Map<Long, String> names) {
        return new HotdealRuleView.Domain(Long.toString(d.getId()), d.getDomain(), d.getRule(), d.isCommon(), d.getKeyParams(), d.getNote(),
                d.getUpdatedBy() == null ? null : names.get(d.getUpdatedBy()), d.getUpdatedAt());
    }

    private Map<Long, String> operatorNames(List<Long> idList) {
        Map<Long, String> out = new HashMap<>();
        List<Long> distinct = idList.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty()) {
            operators.findAllById(distinct).forEach(o -> out.put(o.getId(), o.getOperatorName()));
        }
        return out;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
