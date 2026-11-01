package com.community.hotdeal.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.hotdeal.dto.request.HotdealAdminRequest;
import com.community.hotdeal.dto.response.HotdealRuleView;
import com.community.hotdeal.entity.DealSiteAffiliateEntity;
import com.community.hotdeal.exception.HotdealSiteAffiliateDuplicateException;
import com.community.hotdeal.exception.HotdealSiteAffiliateNotFoundException;
import com.community.hotdeal.repository.master.DealSiteAffiliateRepository;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 사이트 제휴 몰({@code deal_site_affiliates} — D5) — 조회·추가·고치기·지우기 {@code hotdeal.manage}. 설정 저장과 따로 바로 저장한다.
 *
 * <ul>
 *   <li><b>도메인</b>: 링크 규칙과 같은 정규화({@link DealHosts#normalizeDomain}) — 형식 {@code 400 fields.domain}, 이미 있으면 {@code 409 HOTDEAL_SITE_AFFILIATE_DUPLICATE} + {@code fields.domain}.</li>
 *   <li><b>프로그램 이름</b> 1~{@value DealSiteAffiliateEntity#PROGRAM_NAME_MAX}자({@code 400 fields.programName}).</li>
 *   <li><b>TEMPLATE</b>: {@value DealSiteAffiliateEntity#TEMPLATE_MAX}자까지 · {@code http(s)://호스트} 로 시작(호스트는 글자 그대로) · {@code {url}} 자리표시자 <b>정확히 1개</b>
 *       ({@code 400 fields.template}). <b>QUERY</b>: {@value DealSiteAffiliateEntity#QUERY_MAX}자까지 · {@code k=v&…}(이름은 쿼리 이름 규칙, 값은 공백·{@code & = #} 없음, 한 쌍 이상 —
 *       앞의 {@code ?}·{@code &} 는 뗀다, {@code 400 fields.query}). 변환 종류가 틀리면 {@code 400 fields.transform}.</li>
 *   <li>없는 몰 {@code 404 HOTDEAL_SITE_AFFILIATE_NOT_FOUND}. 저장 뒤 이 서버의 규칙 사본({@link DealRuleBook} — {@code SiteAffiliateLinker} 가 읽는 몰 표)을 커밋 뒤 버린다.</li>
 *   <li>감사 {@code HOTDEAL_SITE_AFFILIATE_CREATE}·{@code UPDATE}(바뀐 칸 before/after)·{@code DELETE}, 대상 HOTDEAL_SITE_AFFILIATE.</li>
 * </ul>
 */
@Service
public class HotdealSiteAffiliateAdminService {

    private static final Logger log = LoggerFactory.getLogger(HotdealSiteAffiliateAdminService.class);

    static final String TARGET = "HOTDEAL_SITE_AFFILIATE";
    /** 링크 틀의 머리 — http(s) 주소, 호스트는 글자 그대로(자리표시자 아님) */
    static final Pattern TEMPLATE_HEAD = Pattern.compile("^https?://[^\\s/?#{}]+", Pattern.CASE_INSENSITIVE);
    /** 붙일 쿼리 한 쌍 — 이름(쿼리 이름 규칙) = 값(공백·& = # 없음) */
    static final Pattern QUERY_PAIR = Pattern.compile("^[A-Za-z0-9_.\\[\\]-]{1,40}=[^\\s&=#]+$");

    private final DealSiteAffiliateRepository stores;
    private final DealRuleBook rules;
    private final OperatorRepository operators;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;

    public HotdealSiteAffiliateAdminService(DealSiteAffiliateRepository stores, DealRuleBook rules, OperatorRepository operators, AuditLogService audit,
                                            SnowflakeIdGenerator ids) {
        this.stores = stores;
        this.rules = rules;
        this.operators = operators;
        this.audit = audit;
        this.ids = ids;
    }

    @Transactional(readOnly = true)
    public HotdealRuleView.SiteStoreList list() {
        List<DealSiteAffiliateEntity> rows = stores.findAllByOrderByDomainAsc();
        Map<Long, String> names = names(rows.stream().map(DealSiteAffiliateEntity::getUpdatedBy).toList());
        return new HotdealRuleView.SiteStoreList(rows.stream().map(s -> view(s, names)).toList());
    }

    @Transactional
    public HotdealRuleView.SiteStore create(AdminActor actor, HotdealAdminRequest.SiteStore body) {
        Checked c = check(body);
        if (stores.findByDomain(c.domain()).isPresent()) {
            throw new HotdealSiteAffiliateDuplicateException();
        }
        DealSiteAffiliateEntity s = DealSiteAffiliateEntity.create(ids.nextId(), c.domain(), c.programName(), c.type(), c.template(), c.query(), c.enabled(),
                actor.operatorId(), now());
        stores.persist(s);
        stores.flush();
        record(actor, AuditActions.HOTDEAL_SITE_AFFILIATE_CREATE, s.getId(), null, snapshot(s));
        AfterCommit.run(rules::invalidate);
        log.info("사이트 제휴 몰 추가 domain={} by operatorId={}", s.getDomain(), actor.operatorId());
        return view(s, names(List.of(actor.operatorId())));
    }

    @Transactional
    public HotdealRuleView.SiteStore update(AdminActor actor, String idText, HotdealAdminRequest.SiteStore body) {
        Checked c = check(body);
        DealSiteAffiliateEntity s = find(idText);
        if (stores.findByDomain(c.domain()).filter(other -> !other.getId().equals(s.getId())).isPresent()) {
            throw new HotdealSiteAffiliateDuplicateException();
        }
        Map<String, Object> before = snapshot(s);
        s.change(c.domain(), c.programName(), c.type(), c.template(), c.query(), c.enabled(), actor.operatorId(), now());
        stores.flush();
        Map<String, Object> after = snapshot(s);
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();
        for (String key : after.keySet()) {
            if (!Objects.equals(before.get(key), after.get(key))) {
                changedBefore.put(key, before.get(key));
                changedAfter.put(key, after.get(key));
            }
        }
        if (!changedAfter.isEmpty()) {
            record(actor, AuditActions.HOTDEAL_SITE_AFFILIATE_UPDATE, s.getId(), changedBefore, changedAfter);
        }
        AfterCommit.run(rules::invalidate);
        log.info("사이트 제휴 몰 고침 domain={} 칸={} by operatorId={}", s.getDomain(), changedAfter.keySet(), actor.operatorId());
        return view(s, names(List.of(actor.operatorId())));
    }

    @Transactional
    public void delete(AdminActor actor, String idText) {
        DealSiteAffiliateEntity s = find(idText);
        Map<String, Object> before = snapshot(s);
        stores.delete(s);
        stores.flush();
        record(actor, AuditActions.HOTDEAL_SITE_AFFILIATE_DELETE, s.getId(), before, null);
        AfterCommit.run(rules::invalidate);
        log.info("사이트 제휴 몰 지움 domain={} by operatorId={}", s.getDomain(), actor.operatorId());
    }

    // ── 검사 ──────────────────────────────────────────────────────────────

    record Checked(String domain, String programName, String type, String template, String query, boolean enabled) {
    }

    /** 도메인 → 프로그램 이름 → 변환(틀 · 쿼리) 차례로 모아 한 번에 400. 켬/끔이 빠지면 켬(새 몰은 켠 채로) */
    static Checked check(HotdealAdminRequest.SiteStore body) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String domain = DealHosts.normalizeDomain(body == null ? null : body.domain());
        if (domain == null) {
            invalid.put("domain", new MessageRef("hotdeal.admin.domain.invalid"));
        }
        String program = body == null || body.programName() == null ? null : body.programName().replace("\u0000", "").strip();
        int programLength = program == null ? 0 : program.codePointCount(0, program.length());
        if (programLength < 1 || programLength > DealSiteAffiliateEntity.PROGRAM_NAME_MAX) {
            invalid.put("programName", new MessageRef("hotdeal.admin.siteAffiliate.programName", String.valueOf(DealSiteAffiliateEntity.PROGRAM_NAME_MAX)));
        }
        HotdealAdminRequest.Transform t = body == null ? null : body.transform();
        String type = t == null || t.type() == null ? null : t.type().strip().toUpperCase(Locale.ROOT);
        String template = null;
        String query = null;
        if (DealSiteAffiliateEntity.TEMPLATE.equals(type)) {
            template = t.template() == null ? null : t.template().strip();
            if (!templateOk(template)) {
                invalid.put("template", new MessageRef("hotdeal.admin.siteAffiliate.template", String.valueOf(DealSiteAffiliateEntity.TEMPLATE_MAX),
                        DealSiteAffiliateEntity.URL_PLACEHOLDER));
            }
        } else if (DealSiteAffiliateEntity.QUERY.equals(type)) {
            query = t.query() == null ? null : t.query().strip().replaceFirst("^[?&]+", "");
            if (!queryOk(query)) {
                invalid.put("query", new MessageRef("hotdeal.admin.siteAffiliate.query", String.valueOf(DealSiteAffiliateEntity.QUERY_MAX)));
            }
        } else {
            invalid.put("transform", new MessageRef("hotdeal.admin.siteAffiliate.transform"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        return new Checked(domain, program, type, template, query, body.enabled() == null || body.enabled());
    }

    /** 링크 틀 — 비지 않음 · 1000자 · http(s) 호스트로 시작 · {@code {url}} 정확히 1개 */
    static boolean templateOk(String template) {
        if (template == null || template.isEmpty() || template.length() > DealSiteAffiliateEntity.TEMPLATE_MAX) {
            return false;
        }
        if (!TEMPLATE_HEAD.matcher(template).find()) {
            return false;
        }
        int first = template.indexOf(DealSiteAffiliateEntity.URL_PLACEHOLDER);
        return first >= 0 && template.indexOf(DealSiteAffiliateEntity.URL_PLACEHOLDER, first + 1) < 0;
    }

    /** 붙일 쿼리 — 비지 않음 · 300자 · {@code k=v} 쌍들을 {@code &} 로 이음 */
    static boolean queryOk(String query) {
        if (query == null || query.isEmpty() || query.length() > DealSiteAffiliateEntity.QUERY_MAX) {
            return false;
        }
        for (String pair : query.split("&", -1)) {
            if (!QUERY_PAIR.matcher(pair).matches()) {
                return false;
            }
        }
        return true;
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private DealSiteAffiliateEntity find(String idText) {
        Long id = HotdealAdminQueryService.parseId(idText);
        return (id == null ? java.util.Optional.<DealSiteAffiliateEntity>empty() : stores.findById(id)).orElseThrow(HotdealSiteAffiliateNotFoundException::new);
    }

    private static Map<String, Object> snapshot(DealSiteAffiliateEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("domain", s.getDomain());
        m.put("programName", s.getProgramName());
        m.put("type", s.getTransformType());
        m.put("template", s.getTemplate());
        m.put("query", s.getQuery());
        m.put("enabled", s.isEnabled());
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

    private HotdealRuleView.SiteStore view(DealSiteAffiliateEntity s, Map<Long, String> names) {
        HotdealRuleView.Transform transform = DealSiteAffiliateEntity.TEMPLATE.equals(s.getTransformType())
                ? new HotdealRuleView.Transform(DealSiteAffiliateEntity.TEMPLATE, s.getTemplate(), null)
                : new HotdealRuleView.Transform(DealSiteAffiliateEntity.QUERY, null, s.getQuery());
        return new HotdealRuleView.SiteStore(Long.toString(s.getId()), s.getDomain(), s.getProgramName(), transform, s.isEnabled(),
                s.getUpdatedBy() == null ? null : names.get(s.getUpdatedBy()), s.getUpdatedAt());
    }

    private Map<Long, String> names(List<Long> idList) {
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
