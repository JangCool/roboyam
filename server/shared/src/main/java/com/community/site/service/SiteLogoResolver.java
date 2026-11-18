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
 * 그날 보일 로고 묶음을 고른다(docs/features/site-logo-library-contract.md 「정책」) — 평소에는 <b>성별 로고 → 기본 로고</b>,
 * 기념일에는 <b>그 회원에게 보일 로고(성별 로고가 있으면 그것, 없으면 기본 로고)의 유형에 맞는 기념일 벌</b>.
 * <ul>
 *   <li>기념일: 켜진 줄 가운데 그날 걸리는 맨 위 줄({@link SiteLogoRules#matches}). 그 줄이 기본 제공 기념일 묶음을 가리키면 「기념일 로고 유형」 설정(V252)에 맞는 짝(캐릭터·글자)을 보인다 —
 *       {@code AUTO}(기본)면 방문자·성별 로고가 없는 회원은 기본 로고의 유형, 성별 로고가 정해진 성별의 회원은 그 성별 로고의 유형에 맞는 벌,
 *       {@code CHARACTER}·{@code WORDMARK} 면 모두에게 그 벌. 운영자가 올린 묶음을 가리키는 줄은 성별과 무관하게 그 묶음 그대로다.
 *       쉬는 날 이름은 쉬는 날 표({@code site_holidays})에서 그날의 이름으로 푼다(쉬는 날 이름 줄이 있을 때만 읽는다).</li>
 *   <li>성별: 서버는 성별마다 묶음을 주고, 로그인한 회원의 성별로 고르는 것은 화면이다(사이트 정보는 회원과 무관하게 캐시된다). 기념일에는 성별 무관 묶음과 다를 때만 준다.</li>
 *   <li>기본 로고: 그 밖의 모든 경우. 메일 로고·관리자 상단 로고처럼 「① 칸」을 쓰던 곳은 날짜와 무관하게 기본 로고를 쓴다({@link #defaultBundle}).</li>
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
     * @param bundle      성별과 무관하게 보일 묶음(방문자·성별 로고가 없는 회원·메일처럼 로그인 맥락이 없는 곳) — 기념일이 걸리면 그 묶음(기본 로고의 유형에 맞는 벌), 아니면 기본 로고
     * @param anniversary 기념일이 걸렸는가
     * @param male        남성 회원에게 보일 묶음 — 평소에는 남성 로고(정하지 않았으면 null). 기념일에는 남성 로고의 유형에 맞는 그날의 벌이
     *                    {@code bundle} 과 <b>다른 묶음일 때만</b> 그 벌(같으면 null — {@code bundle} 을 쓴다)
     * @param female      여성 회원에게 보일 묶음 — {@code male} 과 같은 규칙
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
        SiteLogoBundleEntity male = find(row.getLogoMaleBundleId());
        SiteLogoBundleEntity female = find(row.getLogoFemaleBundleId());
        SiteLogoBundleEntity pointed = anniversaryBundle(date).orElse(null);
        if (pointed != null) {
            String style = row.getHolidayLogoStyle();
            SiteLogoBundleEntity neutral = matchStyle(pointed, usual, style);
            return Optional.of(new Today(neutral, true, genderHoliday(pointed, male, style, neutral), genderHoliday(pointed, female, style, neutral)));
        }
        if (usual == null) {
            return Optional.empty();
        }
        return Optional.of(new Today(usual, false, male, female));
    }

    /**
     * 기념일에 그 성별 회원에게 보일 벌 — 성별 로고가 정해져 있으면 그 묶음의 유형에 맞는 그날의 벌({@link #matchStyle}). 성별 무관 묶음과 같으면 null
     * (성별 로고가 없을 때, 유형 설정이 한쪽 벌로 고정일 때, 규칙이 운영자가 올린 묶음을 가리킬 때가 모두 여기에 든다).
     */
    private SiteLogoBundleEntity genderHoliday(SiteLogoBundleEntity pointed, SiteLogoBundleEntity genderBundle, String style, SiteLogoBundleEntity neutral) {
        if (genderBundle == null) {
            return null;
        }
        SiteLogoBundleEntity matched = matchStyle(pointed, genderBundle, style);
        return matched.getId().equals(neutral.getId()) ? null : matched;
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
     * 기념일 로고의 두 벌 가운데 고르기(V251·V252) — 규칙이 가리킨 묶음이 기본 제공 기념일 묶음이면 「기념일 로고 유형」 설정에 맞는 짝으로 바꾼다:
     * {@code CHARACTER}·{@code WORDMARK} 면 늘 그 벌, {@code AUTO}(기본)면 <b>기준 로고의 벌</b> — 기준 로고의 유효 유형({@link SiteLogoBundleEntity#effectiveStyle()} —
     * 기본 제공은 벌, 올린 묶음은 운영자가 고른 유형·없으면 캐릭터)이 글자면 그날의 글자 벌, 아니면 캐릭터 벌. 짝이 없으면 원래 묶음. 운영자가 올린 묶음을 가리키는 규칙은 짝이 없으니 그대로다.
     *
     * @param usual 기준 로고 — 그 사람에게 평소 보일 묶음(기본 로고 또는 그 성별의 성별 로고)
     */
    private SiteLogoBundleEntity matchStyle(SiteLogoBundleEntity bundle, SiteLogoBundleEntity usual, String holidayStyle) {
        if (bundle.getDayKey() == null || bundle.getBuiltInKey() == null) {
            return bundle;
        }
        String wanted;
        if (SiteLogoBundleEntity.STYLE_CHARACTER.equals(holidayStyle) || SiteLogoBundleEntity.STYLE_WORDMARK.equals(holidayStyle)) {
            wanted = holidayStyle;
        } else {
            // 기준 로고의 유효 유형(V255) — 올린 묶음이어도 운영자가 글자로 골랐으면 글자 벌
            wanted = usual != null && SiteLogoBundleEntity.STYLE_WORDMARK.equals(usual.effectiveStyle())
                    ? SiteLogoBundleEntity.STYLE_WORDMARK : SiteLogoBundleEntity.STYLE_CHARACTER;
        }
        if (wanted.equals(bundle.getBuiltInKey())) {
            return bundle;
        }
        return bundles.findFirstByBuiltInKeyAndDayKey(wanted, bundle.getDayKey()).orElse(bundle);
    }

    private SiteLogoBundleEntity find(Long bundleId) {
        return bundleId == null ? null : bundles.findById(bundleId).orElse(null);
    }
}
