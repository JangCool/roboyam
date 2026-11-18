package com.community.site.service;

import com.community.holiday.repository.master.SiteHolidayStore;
import com.community.site.entity.SiteLogoBundleEntity;
import com.community.site.entity.SiteLogoRuleEntity;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteLogoBundleRepository;
import com.community.site.repository.master.SiteLogoRuleRepository;
import com.community.site.repository.master.SiteSettingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그날 보일 로고 묶음을 고른다 — <b>기념일 → 성별 → 평소</b>(docs/features/site-logo-library-contract.md 「정책」).
 * <ul>
 *   <li>기념일: 켜진 줄 가운데 그날 걸리는 맨 위 줄({@link SiteLogoRules#matches}). 그 줄이 기본 제공 기념일 묶음을 가리키면 평소 로고의 벌(캐릭터·글자)에 맞는 짝을 보인다. 쉬는 날 이름은 쉬는 날 표({@code site_holidays})에서 그날의 이름으로 푼다
 *       (쉬는 날 이름 줄이 있을 때만 읽는다).</li>
 *   <li>성별: 기념일이 안 걸린 날에만 뜻이 있다 — 서버는 성별마다 묶음을 주고, 로그인한 회원의 성별로 고르는 것은 화면이다(사이트 정보는 회원과 무관하게 캐시된다).</li>
 *   <li>평소: 그 밖의 모든 경우. 메일 로고·관리자 상단 로고처럼 「① 칸」을 쓰던 곳은 날짜와 무관하게 평소 로고를 쓴다({@link #defaultBundle}).</li>
 * </ul>
 * 「그날」은 부르는 쪽이 넘긴다(서비스 시간대 날짜 — {@code ServiceTime}).
 */
@Service
public class SiteLogoResolver {

    private final SiteSettingRepository settings;
    private final SiteLogoBundleRepository bundles;
    private final SiteLogoRuleRepository rules;
    private final SiteHolidayStore holidays;

    public SiteLogoResolver(SiteSettingRepository settings, SiteLogoBundleRepository bundles, SiteLogoRuleRepository rules, SiteHolidayStore holidays) {
        this.settings = settings;
        this.bundles = bundles;
        this.rules = rules;
        this.holidays = holidays;
    }

    /**
     * 그날의 로고.
     *
     * @param bundle      성별과 무관하게 보일 묶음 — 기념일이 걸리면 그 묶음, 아니면 평소 로고
     * @param anniversary 기념일이 걸렸는가 — 걸렸으면 성별 묶음은 쓰지 않는다({@code male}·{@code female} 이 null)
     * @param male        남성 회원에게 보일 묶음(정하지 않았거나 기념일이면 null)
     * @param female      여성 회원에게 보일 묶음(정하지 않았거나 기념일이면 null)
     */
    public record Today(SiteLogoBundleEntity bundle, boolean anniversary, SiteLogoBundleEntity male, SiteLogoBundleEntity female) {
    }

    /** 그날 보일 묶음들 — 설정 행이 없으면(설치 전) 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<Today> on(LocalDate date) {
        SiteSettingEntity row = settings.findById(SiteSettingEntity.ROW_ID).orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        SiteLogoBundleEntity usual = defaultBundle(row).orElse(null);
        Optional<SiteLogoBundleEntity> anniversary = anniversaryBundle(date).map(bundle -> matchStyle(bundle, usual));
        if (anniversary.isPresent()) {
            return Optional.of(new Today(anniversary.get(), true, null, null));
        }
        if (usual == null) {
            return Optional.empty();
        }
        return Optional.of(new Today(usual, false, find(row.getLogoMaleBundleId()), find(row.getLogoFemaleBundleId())));
    }

    /** 평소 로고 묶음 — 설정 행이 없으면 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<SiteLogoBundleEntity> defaultBundle() {
        return settings.findById(SiteSettingEntity.ROW_ID).flatMap(this::defaultBundle);
    }

    /** 이미 읽은 설정 행의 평소 로고 묶음 */
    @Transactional(readOnly = true)
    public Optional<SiteLogoBundleEntity> defaultBundle(SiteSettingEntity row) {
        return bundles.findById(row.getLogoDefaultBundleId());
    }

    private Optional<SiteLogoBundleEntity> anniversaryBundle(LocalDate date) {
        List<SiteLogoRuleEntity> enabled = rules.findAllByOrderBySortOrderAsc().stream().filter(SiteLogoRuleEntity::isEnabled).toList();
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        boolean needsHolidays = enabled.stream().anyMatch(rule -> SiteLogoRuleEntity.MODE_HOLIDAY.equals(rule.getMode()));
        Set<String> names = needsHolidays ? holidays.between(date, date).stream().map(SiteHolidayStore.Row::name).collect(Collectors.toSet()) : Set.of();
        return enabled.stream().filter(rule -> SiteLogoRules.matches(rule, date, names)).findFirst().flatMap(rule -> bundles.findById(rule.getBundleId()));
    }

    /**
     * 기념일 로고의 두 벌 가운데 고르기(V251) — 규칙이 가리킨 묶음이 기본 제공 기념일 묶음이면 <b>평소 로고의 벌</b>에 맞는 짝으로 바꾼다:
     * 평소 로고가 글자 로고 계열이면 그날의 글자 벌, 그 밖에는(캐릭터 로고·운영자가 올린 묶음) 캐릭터 벌. 운영자가 올린 묶음을 가리키는 규칙은 짝이 없으니 그대로다.
     */
    private SiteLogoBundleEntity matchStyle(SiteLogoBundleEntity bundle, SiteLogoBundleEntity usual) {
        if (bundle.getDayKey() == null || bundle.getBuiltInKey() == null) {
            return bundle;
        }
        String wanted = usual != null && SiteLogoBundleEntity.STYLE_WORDMARK.equals(usual.getBuiltInKey())
                ? SiteLogoBundleEntity.STYLE_WORDMARK : SiteLogoBundleEntity.STYLE_CHARACTER;
        if (wanted.equals(bundle.getBuiltInKey())) {
            return bundle;
        }
        return bundles.findFirstByBuiltInKeyAndDayKey(wanted, bundle.getDayKey()).orElse(bundle);
    }

    private SiteLogoBundleEntity find(Long bundleId) {
        return bundleId == null ? null : bundles.findById(bundleId).orElse(null);
    }
}
