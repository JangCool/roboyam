package com.community.title.service;

import com.community.title.dto.request.RepresentativeTitleRequest;
import com.community.title.dto.response.MyTitlesView;
import com.community.title.dto.response.OwnedTitleView;
import com.community.title.entity.MemberTitleEntity;
import com.community.title.entity.MemberTitlePrefEntity;
import com.community.title.entity.TitleApplicationEntity;
import com.community.title.entity.TitleApplicationStatus;
import com.community.title.entity.TitleEntity;
import com.community.title.entity.TitleMethod;
import com.community.title.entity.TitleTier;
import com.community.title.exception.TitleException;
import com.community.title.repository.master.MemberTitlePrefRepository;
import com.community.title.repository.master.MemberTitleRepository;
import com.community.title.repository.master.TitleApplicationRepository;
import com.community.title.repository.master.TitleQueries;
import com.community.title.repository.master.TitleRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 칭호(member-title-contract 「화면」 내 정보 › 칭호 · 「구현 결정」 4·7) — {@code GET /api/me/titles}·{@code PUT …/representative}·{@code POST …/{key}/applications}·
 * {@code POST …/seen}.
 *
 * <ul>
 *   <li>대표 칭호: 가진(살아 있는·켜진) 칭호만 고를 수 있다(409 {@code TITLE_NOT_OWNED}), {@code key} null = 기본, {@code hidden} = 「안 보이기」.</li>
 *   <li>신청: 신청형이고 켜진 칭호만(409 {@code TITLE_NOT_APPLICABLE}) · AI 회원·운영팀 계정 409 {@code TITLE_MEMBER_EXCLUDED} · 이미 가짐 409 {@code TITLE_ALREADY_GRANTED} ·
 *       대기 중 409 {@code TITLE_APPLICATION_PENDING} · 거절 뒤 다시 신청할 수 있는 날 전 409 {@code TITLE_REAPPLY_TOO_SOON} · 조건을 다 채우지 않음 409 {@code TITLE_CONDITIONS_UNMET}.</li>
 *   <li>축하 창: 받은 줄 중 {@code seen_at} 이 빈 것({@code unseen}), 닫으면 모두 본 것으로.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class MyTitleService {

    private final TitleRepository titles;
    private final TitleDisplayService display;
    private final TitleEligibility eligibility;
    private final TitleTexts texts;
    private final TitleQueries queries;
    private final TitleGrantService grants;
    private final TitleSettingsReader settings;
    private final MemberTitleRepository memberTitles;
    private final MemberTitlePrefRepository prefs;
    private final TitleApplicationRepository applications;
    private final SnowflakeIdGenerator ids;

    public MyTitleService(TitleRepository titles, TitleDisplayService display, TitleEligibility eligibility, TitleTexts texts, TitleQueries queries, TitleGrantService grants,
                          TitleSettingsReader settings, MemberTitleRepository memberTitles, MemberTitlePrefRepository prefs, TitleApplicationRepository applications,
                          SnowflakeIdGenerator ids) {
        this.titles = titles;
        this.display = display;
        this.eligibility = eligibility;
        this.texts = texts;
        this.queries = queries;
        this.grants = grants;
        this.settings = settings;
        this.memberTitles = memberTitles;
        this.prefs = prefs;
        this.applications = applications;
        this.ids = ids;
    }

    public MyTitlesView mine(long userId) {
        Map<Long, TitleEntity> enabled = display.enabledTitles();
        List<MemberTitleEntity> rows = queries.activeOf(List.of(userId)).stream().filter(r -> enabled.containsKey(r.getTitleId())).toList();
        MemberTitlePrefEntity pref = prefs.findById(userId).orElse(null);
        // 고른 대표 칭호(지금도 가진·켜진 것) — 고르지 않았으면(기본 = 가진 작가 칭호 중 가장 높은 것) null
        String chosen = pref == null || pref.isHidden() || pref.getRepresentativeTitleId() == null ? null
                : rows.stream().anyMatch(r -> r.getTitleId().equals(pref.getRepresentativeTitleId())) ? enabled.get(pref.getRepresentativeTitleId()).getTitleKey() : null;
        List<OwnedTitleView> owned = display.owned(userId);
        Instant now = grants.now();
        boolean excluded = queries.excluded(userId);

        List<MyTitlesView.ProgressView> progress = new ArrayList<>();
        List<MyTitlesView.ApplicableView> applicable = new ArrayList<>();
        for (TitleEntity t : enabled.values()) {
            List<MemberTitleEntity> mine = rows.stream().filter(r -> r.getTitleId().equals(t.getId())).toList();
            if (t.getMethod() == TitleMethod.AUTO) {
                progress(t, mine, userId).ifPresent(progress::add);
            } else if (t.getMethod() == TitleMethod.APPLY && mine.isEmpty()) {
                applicable.add(applicable(t, userId, now, excluded));
            }
        }

        Map<Long, Long> holders = queries.holders();
        long base = queries.memberBase();
        List<MyTitlesView.UnseenView> unseen = rows.stream().filter(r -> r.getSeenAt() == null).map(r -> {
            TitleEntity t = enabled.get(r.getTitleId());
            return new MyTitlesView.UnseenView(t.getTitleKey(), TitleLabels.label(t, r.getPeriod()), TitleLabels.labelEn(t, r.getPeriod()), t.getIcon(),
                    r.getTier() == null ? null : r.getTier().name(), t.getChip(), PublicTitleService.ratio(holders.getOrDefault(t.getId(), 0L), base));
        }).toList();

        Map<Long, TitleEntity> all = new java.util.HashMap<>();
        titles.findAll().forEach(t -> all.put(t.getId(), t));
        List<MyTitlesView.ApplicationView> apps = applications.findTop50ByUserIdOrderByCreatedAtDescIdDesc(userId).stream().filter(a -> all.containsKey(a.getTitleId()))
                .map(a -> new MyTitlesView.ApplicationView(Long.toString(a.getId()), all.get(a.getTitleId()).getTitleKey(), all.get(a.getTitleId()).getName(), a.getStatus().name(),
                        a.getReason(), a.getReapplyAt(), a.getCreatedAt())).toList();

        return new MyTitlesView(chosen == null ? null : new MyTitlesView.RepresentativeView(chosen), pref != null && pref.isHidden(), owned, progress, applicable, apps, unseen);
    }

    /** 자동 칭호의 다음 목표 — 다 받았으면 빈 값 */
    private java.util.Optional<MyTitlesView.ProgressView> progress(TitleEntity t, List<MemberTitleEntity> mine, long userId) {
        if (BuiltinTitles.HONORARY_WRITER.equals(t.getTitleKey())) {
            if (!mine.isEmpty()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(view(t, null, eligibility.metric(t, userId), settings.current().honoraryCount()));
        }
        if (t.isTiered()) {
            TitleTier have = mine.stream().map(MemberTitleEntity::getTier).filter(Objects::nonNull).reduce(null, TitleTier::max);
            if (have == TitleTier.GOLD) {
                return java.util.Optional.empty();
            }
            TitleTier next = have == null ? TitleTier.BRONZE : TitleTier.values()[have.ordinal() + 1];
            return java.util.Optional.of(view(t, next, eligibility.metric(t, userId), t.thresholdOf(next)));
        }
        Integer target = TitleEligibility.driverTarget(t);
        if (!mine.isEmpty() || target == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(view(t, null, eligibility.metric(t, userId), target));
    }

    private static MyTitlesView.ProgressView view(TitleEntity t, TitleTier tier, long current, long target) {
        return new MyTitlesView.ProgressView(t.getTitleKey(), t.getName(), t.getNameEn(), t.getIcon(), t.getChip(), tier == null ? null : tier.name(), current, target);
    }

    private MyTitlesView.ApplicableView applicable(TitleEntity t, long userId, Instant now, boolean excluded) {
        List<TitleEligibility.Check> checks = eligibility.check(t, userId);
        List<MyTitlesView.ConditionView> conditions = conditions(t, checks);
        boolean pending = applications.findFirstByUserIdAndTitleIdAndStatus(userId, t.getId(), TitleApplicationStatus.PENDING).isPresent();
        Instant reapplyAt = reapplyAt(userId, t.getId(), now);
        boolean canApply = !excluded && !pending && reapplyAt == null && checks.stream().allMatch(TitleEligibility.Check::met);
        return new MyTitlesView.ApplicableView(t.getTitleKey(), t.getName(), t.getNameEn(), t.getIcon(), t.getChip(), conditions, canApply, reapplyAt, pending);
    }

    /** 조건별 한 줄(신청 화면·매니저 심사 화면) */
    public List<MyTitlesView.ConditionView> conditions(TitleEntity t, List<TitleEligibility.Check> checks) {
        List<MyTitlesView.ConditionView> out = new ArrayList<>();
        List<com.community.title.dto.TitleCondition> defs = TitleConditions.read(t.getConditions());
        for (int i = 0; i < checks.size() && i < defs.size(); i++) {
            TitleEligibility.Check c = checks.get(i);
            boolean counted = c.type().counted();
            out.add(new MyTitlesView.ConditionView(c.type().name(), texts.condition(defs.get(i)), c.met(), counted ? c.current() : null,
                    counted ? (long) (c.value() == null ? 1 : c.value()) : null));
        }
        return out;
    }

    /** 마지막 신청이 거절이고 다시 신청할 수 있는 날이 아직이면 그날, 아니면 null */
    private Instant reapplyAt(long userId, long titleId, Instant now) {
        return applications.findFirstByUserIdAndTitleIdOrderByCreatedAtDescIdDesc(userId, titleId)
                .filter(a -> a.getStatus() == TitleApplicationStatus.REJECTED && a.getReapplyAt() != null && a.getReapplyAt().isAfter(now))
                .map(TitleApplicationEntity::getReapplyAt).orElse(null);
    }

    @Transactional
    public MyTitlesView representative(long userId, RepresentativeTitleRequest body) {
        boolean hidden = body != null && Boolean.TRUE.equals(body.hidden());
        String key = body == null || body.key() == null || body.key().isBlank() ? null : body.key().strip();
        Long titleId = null;
        if (!hidden && key != null) {
            TitleEntity t = titles.findByTitleKey(key).filter(TitleEntity::isEnabled).orElseThrow(TitleException::notOwned);
            if (memberTitles.findByUserIdAndTitleIdAndRevokedAtIsNull(userId, t.getId()).isEmpty()) {
                throw TitleException.notOwned();
            }
            titleId = t.getId();
        }
        Instant now = grants.now();
        MemberTitlePrefEntity pref = prefs.findById(userId).orElse(null);
        if (pref == null) {
            prefs.persist(MemberTitlePrefEntity.create(userId, titleId, hidden, now));
        } else {
            pref.change(titleId, hidden, now);
        }
        prefs.flush();
        return mine(userId);
    }

    @Transactional
    public MyTitlesView.ApplicationView apply(long userId, String key) {
        TitleEntity t = titles.findByTitleKey(key == null ? "" : key).orElseThrow(TitleException::notFound);
        if (t.getMethod() != TitleMethod.APPLY || !t.isEnabled()) {
            throw TitleException.notApplicable();
        }
        grants.requireReceivable(userId);
        if (!memberTitles.findByUserIdAndTitleIdAndRevokedAtIsNull(userId, t.getId()).isEmpty()) {
            throw TitleException.alreadyGranted();
        }
        if (applications.findFirstByUserIdAndTitleIdAndStatus(userId, t.getId(), TitleApplicationStatus.PENDING).isPresent()) {
            throw TitleException.applicationPending();
        }
        Instant now = grants.now();
        if (reapplyAt(userId, t.getId(), now) != null) {
            throw TitleException.reapplyTooSoon();
        }
        if (!eligibility.allMet(t, userId)) {
            throw TitleException.conditionsUnmet();
        }
        TitleApplicationEntity app = TitleApplicationEntity.apply(ids.nextId(), userId, t.getId(), now);
        try {
            applications.persist(app);
            applications.flush(); // 대기는 하나 — 동시 신청의 유일 색인 위반은 여기서
        } catch (DuplicateKeyException e) {
            throw TitleException.applicationPending();
        }
        return new MyTitlesView.ApplicationView(Long.toString(app.getId()), t.getTitleKey(), t.getName(), app.getStatus().name(), null, null, app.getCreatedAt());
    }

    @Transactional
    public void seen(long userId) {
        Instant now = grants.now();
        memberTitles.findByUserIdAndRevokedAtIsNull(userId).stream().filter(r -> r.getSeenAt() == null).forEach(r -> r.markSeen(now));
    }
}
