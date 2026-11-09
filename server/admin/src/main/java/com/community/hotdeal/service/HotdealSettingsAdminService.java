package com.community.hotdeal.service;

import com.community.cache.service.CacheRefresher;
import com.community.cache.service.CacheSaveSource;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.dto.request.HotdealAdminRequest;
import com.community.hotdeal.dto.response.HotdealSettingsView;
import com.community.hotdeal.repository.master.AdminDealQueries;
import com.community.hotdeal.repository.master.DealSettingsStore;
import com.community.hotdeal.repository.master.DealSiteAffiliateRepository;
import com.community.hotdeal.service.SiteAffiliateLinker.SiteStore;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.point.dto.PointRules;
import com.community.point.service.PointPolicy;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 핫딜 설정({@code deal_settings} 한 행) — 조회 {@code hotdeal.read}, 저장 {@code hotdeal.manage}. 계약 「핫딜 설정」 표(범위·기본값)가 원천이고 DB CHECK 와 같다.
 *
 * <ul>
 *   <li><b>저장</b>(통째로): 숫자 17칸은 표의 범위 안 정수(빠졌거나 범위 밖이면 {@code 400 fields.<칸>} — 표 차례로 모아 한 번에), {@code ogFetchEnabled} 켬/끔 필수,
 *       {@code affiliateMode} MEMBER·SITE({@code 400 fields.affiliateMode}). 바뀐 칸이 없으면 쓰지 않는다(마지막 저장 시각도 그대로).</li>
 *   <li><b>감사</b> {@code HOTDEAL_SETTINGS_UPDATE}(대상 HOTDEAL_SETTINGS · 1) — 바뀐 칸만 {@code before}/{@code after}(제휴 링크 방식 전환도 이 칸으로 남는다).</li>
 *   <li>저장 뒤 이 서버의 설정 사본({@link DealSettingsReader})을 커밋 뒤 버린다 — 공개 서버는 사본 수명(15초) 안에 따라온다.</li>
 *   <li>보상 포인트·하루 지급 횟수는 포인트 정책 소유라 읽기만 한다({@code rewardPoints}·{@code rewardDailyLimit}).</li>
 *   <li><b>제휴 방식 전환 영향</b>({@link #affiliateImpact}): 진행 중(ACTIVE·FLAGGED) 공개 글 가운데 개인 제휴 글 수 · 그중 켠 사이트 제휴 몰(최종 호스트 우선·접미사·긴 도메인 우선)로
 *       가는 글 수 · 켠 몰로 가는 전체 글 수. 저장하지 않는다(몰 표는 지금 DB 값).</li>
 * </ul>
 */
@Service
public class HotdealSettingsAdminService {

    private static final Logger log = LoggerFactory.getLogger(HotdealSettingsAdminService.class);

    static final String TARGET = "HOTDEAL_SETTINGS";

    /** 숫자 칸 하나 — 이름 · 범위 · 기본값(계약 표 차례) */
    record Spec(String field, long min, long max, long initial, Function<DealSettings, Long> current, Function<HotdealAdminRequest.Settings, Number> requested) {
    }

    static final List<Spec> SPECS = List.of(
            new Spec("endRequestThreshold", 1, 20, 3, s -> (long) s.endRequestThreshold(), HotdealAdminRequest.Settings::endRequestThreshold),
            new Spec("endGraceHours", 0, 168, 12, s -> (long) s.endGraceHours(), HotdealAdminRequest.Settings::endGraceHours),
            new Spec("endRequestDailyLimit", 1, 100, 20, s -> (long) s.endRequestDailyLimit(), HotdealAdminRequest.Settings::endRequestDailyLimit),
            new Spec("endRequestPerAuthorLimit", 0, 20, 3, s -> (long) s.endRequestPerAuthorLimit(), HotdealAdminRequest.Settings::endRequestPerAuthorLimit),
            new Spec("endRequestPerAuthorDays", 1, 30, 7, s -> (long) s.endRequestPerAuthorDays(), HotdealAdminRequest.Settings::endRequestPerAuthorDays),
            new Spec("keepMaxCount", 0, 10, 2, s -> (long) s.keepMaxCount(), HotdealAdminRequest.Settings::keepMaxCount),
            new Spec("memberDailyPostLimit", 0, 50, 5, s -> (long) s.memberDailyPostLimit(), HotdealAdminRequest.Settings::memberDailyPostLimit),
            new Spec("sameStoreIntervalMinutes", 0, 1440, 10, s -> (long) s.sameStoreIntervalMinutes(), HotdealAdminRequest.Settings::sameStoreIntervalMinutes),
            new Spec("autoExpireDays", 0, 365, 30, s -> (long) s.autoExpireDays(), HotdealAdminRequest.Settings::autoExpireDays),
            new Spec("priceMaxWon", 1_000_000, 1_000_000_000, 100_000_000, DealSettings::priceMaxWon, HotdealAdminRequest.Settings::priceMaxWon),
            new Spec("discountWarnPercent", 50, 99, 90, s -> (long) s.discountWarnPercent(), HotdealAdminRequest.Settings::discountWarnPercent),
            new Spec("hotLikeThreshold", 1, 100, 10, s -> (long) s.hotLikeThreshold(), HotdealAdminRequest.Settings::hotLikeThreshold),
            new Spec("suspicionMinPosts", 0, 50, 3, s -> (long) s.suspicionMinPosts(), HotdealAdminRequest.Settings::suspicionMinPosts),
            new Spec("suspicionWindowDays", 1, 90, 7, s -> (long) s.suspicionWindowDays(), HotdealAdminRequest.Settings::suspicionWindowDays),
            new Spec("storeSuspicionMembers", 0, 20, 3, s -> (long) s.storeSuspicionMembers(), HotdealAdminRequest.Settings::storeSuspicionMembers),
            new Spec("storeSuspicionWindowDays", 1, 90, 30, s -> (long) s.storeSuspicionWindowDays(), HotdealAdminRequest.Settings::storeSuspicionWindowDays),
            new Spec("storeSuspicionOtherRatioPercent", 0, 100, 20, s -> (long) s.storeSuspicionOtherRatioPercent(),
                    HotdealAdminRequest.Settings::storeSuspicionOtherRatioPercent));

    private final DealSettingsStore store;
    private final DealSettingsReader reader;
    private final CacheRefresher refresher;
    private final PointPolicy points;
    private final AdminDealQueries queries;
    private final DealSiteAffiliateRepository siteStores;
    private final OperatorRepository operators;
    private final AuditLogService audit;

    public HotdealSettingsAdminService(DealSettingsStore store, DealSettingsReader reader, PointPolicy points, AdminDealQueries queries,
                                       DealSiteAffiliateRepository siteStores, OperatorRepository operators, AuditLogService audit, CacheRefresher refresher) {
        this.refresher = refresher;
        this.store = store;
        this.reader = reader;
        this.points = points;
        this.queries = queries;
        this.siteStores = siteStores;
        this.operators = operators;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public HotdealSettingsView get() {
        return view(store.read());
    }

    @Transactional
    public HotdealSettingsView save(AdminActor actor, HotdealAdminRequest.Settings body) {
        DealSettings wanted = check(body);
        DealSettingsStore.Stored current = store.lock();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        Map<String, Object> was = values(current.settings());
        Map<String, Object> now = values(wanted);
        for (Map.Entry<String, Object> e : now.entrySet()) {
            if (!Objects.equals(was.get(e.getKey()), e.getValue())) {
                before.put(e.getKey(), was.get(e.getKey()));
                after.put(e.getKey(), e.getValue());
            }
        }
        if (after.isEmpty()) {
            return view(current);
        }
        Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
        store.update(wanted, actor.operatorId(), at);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.HOTDEAL_SETTINGS_UPDATE, AuditResult.SUCCESS).target(TARGET, "1")
                .with("before", before).with("after", after));
        refresher.onSave(CacheSaveSource.HOTDEAL_SETTINGS_SAVE, null, actor.operatorId());
        log.info("핫딜 설정 저장 칸={} by operatorId={}", after.keySet(), actor.operatorId());
        return view(store.read());
    }

    /** 제휴 방식을 {@code mode} 로 바꾸면 영향받는 진행 중 공개 글 수(저장하지 않는다) */
    @Transactional(readOnly = true)
    public HotdealSettingsView.AffiliateImpact affiliateImpact(String modeRaw) {
        String mode = modeRaw == null ? null : modeRaw.strip().toUpperCase(Locale.ROOT);
        if (!DealSettings.MEMBER.equals(mode) && !DealSettings.SITE.equals(mode)) {
            throw ValidationException.of("mode", new MessageRef("hotdeal.admin.settings.affiliateMode"));
        }
        List<SiteStore> stores = siteStores.findAll().stream().map(s -> new SiteStore(s.getId(), s.getDomain(), s.getProgramName(), s.getTransformType(),
                s.getTemplate(), s.getQuery(), s.isEnabled())).toList();
        long member = 0;
        long matched = 0;
        long siteActive = 0;
        for (AdminDealQueries.LiveDeal d : queries.liveDeals(reader.current().autoExpireDays(), Instant.now())) {
            boolean site = SiteAffiliateLinker.match(stores, d.productHost(), d.finalHost()).isPresent();
            if (d.affiliate()) {
                member++;
                if (site) {
                    matched++;
                }
            }
            if (site) {
                siteActive++;
            }
        }
        return new HotdealSettingsView.AffiliateImpact(mode, member, matched, siteActive);
    }

    // ── 검사 ──────────────────────────────────────────────────────────────

    /** 칸 검사 — 표 차례로 모아 한 번에 400(빠짐·범위 밖·정수 아님) */
    static DealSettings check(HotdealAdminRequest.Settings body) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        long[] v = new long[SPECS.size()];
        for (int i = 0; i < SPECS.size(); i++) {
            Spec spec = SPECS.get(i);
            Number n = body == null ? null : spec.requested().apply(body);
            if (n == null || n.longValue() < spec.min() || n.longValue() > spec.max()) {
                invalid.put(spec.field(), new MessageRef("hotdeal.admin.settings.range", String.valueOf(spec.min()), String.valueOf(spec.max())));
            } else {
                v[i] = n.longValue();
            }
        }
        if (body == null || body.ogFetchEnabled() == null) {
            invalid.put("ogFetchEnabled", new MessageRef("hotdeal.admin.settings.ogFetchEnabled"));
        }
        String mode = body == null || body.affiliateMode() == null ? null : body.affiliateMode().strip().toUpperCase(Locale.ROOT);
        if (!DealSettings.MEMBER.equals(mode) && !DealSettings.SITE.equals(mode)) {
            invalid.put("affiliateMode", new MessageRef("hotdeal.admin.settings.affiliateMode"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        return new DealSettings((int) v[0], (int) v[1], (int) v[2], (int) v[3], (int) v[4], (int) v[5], (int) v[6], (int) v[7], (int) v[8], v[9], (int) v[10],
                (int) v[11], (int) v[12], (int) v[13], (int) v[14], (int) v[15], (int) v[16], body.ogFetchEnabled(), mode);
    }

    /** 칸 이름 → 값(감사 비교 — 표 차례 + 켬/끔 + 방식) */
    static Map<String, Object> values(DealSettings s) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Spec spec : SPECS) {
            out.put(spec.field(), spec.current().apply(s));
        }
        out.put("ogFetchEnabled", s.ogFetchEnabled());
        out.put("affiliateMode", s.affiliateMode());
        return out;
    }

    private HotdealSettingsView view(DealSettingsStore.Stored stored) {
        DealSettings s = stored.settings();
        PointRules rules = points.current();
        String by = stored.updatedBy() == null ? null : operators.findById(stored.updatedBy()).map(o -> o.getOperatorName()).orElse(null);
        return new HotdealSettingsView(s.endRequestThreshold(), s.endGraceHours(), s.endRequestDailyLimit(), s.endRequestPerAuthorLimit(),
                s.endRequestPerAuthorDays(), s.keepMaxCount(), s.memberDailyPostLimit(), s.sameStoreIntervalMinutes(), s.autoExpireDays(), s.priceMaxWon(),
                s.discountWarnPercent(), s.hotLikeThreshold(), s.suspicionMinPosts(), s.suspicionWindowDays(), s.storeSuspicionMembers(),
                s.storeSuspicionWindowDays(), s.storeSuspicionOtherRatioPercent(), s.ogFetchEnabled(), s.affiliateMode(), rules.dealEndRequestPoints(),
                rules.dealEndRequestDailyLimit(), stored.updatedAt(), by);
    }
}
