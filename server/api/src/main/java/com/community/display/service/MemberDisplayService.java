package com.community.display.service;

import com.community.display.dto.request.MyDisplayRequest;
import com.community.display.dto.response.DisplayEffectiveResponse;
import com.community.display.dto.response.DisplayValuesView;
import com.community.display.dto.response.MyDisplayResponse;
import com.community.site.dto.request.DisplayValuesRequest;
import com.community.site.dto.response.SiteDisplayDefaultsView;
import com.community.site.dto.response.SiteInfoResponse;
import com.community.site.service.DisplayValuesPolicy;
import com.community.site.service.PublicSiteService;
import com.community.user.entity.MemberDisplayPreferenceEntity;
import com.community.user.repository.master.MemberDisplayPreferenceRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 사이트 표시 값(언어·시간대·시간 형식·날짜 형식)의 <b>층을 합치는 한 곳</b> — 개인 값({@code member_display_preferences}) → 사이트 표시 기본값 → 처음 값.
 * 규약은 {@code docs/features/display-contract.md}(원본 community {@code docs/api-display.md}).
 *
 * <ul>
 *   <li>사이트 표시 기본값은 {@code /api/site/info} 의 {@code displayDefaults} 와 같은 사본({@link PublicSiteService}, 20초)에서 읽는다 — 두 응답이 서로 어긋나지 않는다.
 *       설정 행이 없으면(사이트 이름 없음 — 설치 전·시험) 처음 값이고 {@code from=FALLBACK}.</li>
 *   <li>개인 값은 네 칸을 통째로 덮는다(칸별 상속 없음). 기본값과 같은 값을 저장해도 행이 생기고, 되돌리기는 행 삭제다(행이 없어도 성공).</li>
 *   <li>값 허용 범위와 칸별 문구는 사이트 표시 기본값·운영자 내 표시 형식과 같은 {@link DisplayValuesPolicy} 다. 언어는 빼도 된다 — 빼면 지금 개인 값의 언어(없으면 사이트 기본 언어).</li>
 *   <li>저장은 회원 행을 잠근 뒤 만들거나 고친다(같은 회원의 동시 저장을 줄 세운다). 탈퇴한 계정이면 {@code 401}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class MemberDisplayService {

    /** 서비스 시간대(서비스 하루의 기준) — 표시 값과 무관하다 */
    static final String SERVICE_ZONE_ID = "Asia/Seoul";

    private final MemberDisplayPreferenceRepository preferences;
    private final UserRepository users;
    private final PublicSiteService site;

    public MemberDisplayService(MemberDisplayPreferenceRepository preferences, UserRepository users, PublicSiteService site) {
        this.preferences = preferences;
        this.users = users;
        this.site = site;
    }

    /** 지금 사람이 볼 값 — 비로그인({@code null})은 사이트 기본값 */
    public DisplayEffectiveResponse effective(Long userId) {
        if (userId != null) {
            Optional<MemberDisplayPreferenceEntity> own = preferences.findById(userId);
            if (own.isPresent()) {
                return new DisplayEffectiveResponse(view(own.get()), "USER", SERVICE_ZONE_ID);
            }
        }
        SiteInfoResponse info = site.snapshot().info();
        return new DisplayEffectiveResponse(defaults(info), info.siteName() == null ? "FALLBACK" : "DEFAULT", SERVICE_ZONE_ID);
    }

    /** 내 값·기본값·효과값 — 행을 만들지 않는다 */
    public MyDisplayResponse mine(long userId) {
        return response(preferences.findById(userId).orElse(null));
    }

    /** 개인 값을 만들거나 고친다 — 틀린 칸은 한꺼번에 {@code 400 VALIDATION}(아무것도 저장하지 않는다) */
    @Transactional
    public MyDisplayResponse save(long userId, MyDisplayRequest request) {
        DisplayValuesRequest values = request == null ? null : request.values();
        String language = values == null || values.language() == null ? currentLanguage(userId) : values.language();
        DisplayValuesPolicy.Checked checked = DisplayValuesPolicy.check(values == null
                ? new DisplayValuesRequest(language, null, null, null)
                : new DisplayValuesRequest(language, values.zoneId(), values.timeFormat(), values.dateFormat()));

        users.findWithLockById(userId).filter(user -> !user.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        Instant now = Instant.now();
        MemberDisplayPreferenceEntity preference = preferences.findById(userId).orElse(null);
        if (preference == null) {
            preference = MemberDisplayPreferenceEntity.create(userId, checked.language(), checked.zoneId(), checked.timeFormat(), checked.dateFormat(), now);
            preferences.persist(preference);
        } else {
            preference.change(checked.language(), checked.zoneId(), checked.timeFormat(), checked.dateFormat(), now);
        }
        return response(preference);
    }

    /** 기본값으로 되돌리기 — 행을 지운다. 행이 없어도 성공 */
    @Transactional
    public MyDisplayResponse reset(long userId) {
        preferences.findById(userId).ifPresent(preferences::delete);
        return response(null);
    }

    private String currentLanguage(long userId) {
        return preferences.findById(userId).map(MemberDisplayPreferenceEntity::getLanguage)
                .orElseGet(() -> site.snapshot().info().displayDefaults().language());
    }

    private MyDisplayResponse response(MemberDisplayPreferenceEntity preference) {
        DisplayValuesView defaults = defaults(site.snapshot().info());
        DisplayValuesView own = preference == null ? null : view(preference);
        return new MyDisplayResponse(own, defaults, own == null ? defaults : own);
    }

    private static DisplayValuesView defaults(SiteInfoResponse info) {
        SiteDisplayDefaultsView d = info.displayDefaults();
        return new DisplayValuesView(d.language(), d.zoneId(), d.timeFormat(), d.dateFormat());
    }

    private static DisplayValuesView view(MemberDisplayPreferenceEntity p) {
        return new DisplayValuesView(p.getLanguage(), p.getZoneId(), p.getTimeFormat(), p.getDateFormat());
    }
}
