package com.community.terms.service;

import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.terms.dto.TermsVersionMeta;
import com.community.terms.entity.TermsKind;
import com.community.terms.repository.master.TermsVersionRepositoryQDSL;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 약관 조회의 공통 부분(api·admin) — 사이트 표시 시간대의 오늘, 종류별 게시본 나누기({@link TermsTimeline}), 가입 동의 버전 문자열, 「보기」 링크 옵션.
 */
@Service
@Transactional(readOnly = true)
public class TermsService {

    private static final Logger log = LoggerFactory.getLogger(TermsService.class);

    private final SiteSettingRepository settings;
    private final TermsVersionRepositoryQDSL versionQueries;

    public TermsService(SiteSettingRepository settings, TermsVersionRepositoryQDSL versionQueries) {
        this.settings = settings;
        this.versionQueries = versionQueries;
    }

    /**
     * 가입 동의 버전 문자열 {@code T{이용약관 버전}.P{개인정보처리방침 버전}} — 지금 약관(시행일이 오늘 이전인 가장 큰 버전) 기준, 게시본이 없으면 그 종류는 0.
     * 예 {@code T3.P2}, 새 설치 {@code T0.P0}. {@code users.terms_version}(varchar 16)에 넣는다.
     */
    public String currentAgreementVersion() {
        Map<TermsKind, TermsTimeline> timelines = timelines();
        StringBuilder out = new StringBuilder();
        for (TermsKind kind : TermsKind.values()) {
            if (!out.isEmpty()) {
                out.append('.');
            }
            out.append(kind.agreementPrefix()).append(timelines.get(kind).currentVersionOrZero());
        }
        return out.toString();
    }

    /** 모든 종류의 게시본을 오늘 기준으로 나눈 것(종류 선언 차례, 게시본이 없는 종류도 빈 것으로 있다) */
    public Map<TermsKind, TermsTimeline> timelines() {
        LocalDate today = today();
        Map<TermsKind, List<TermsVersionMeta>> byKind = versionQueries.allMeta().stream()
                .collect(Collectors.groupingBy(TermsVersionMeta::kind, () -> new EnumMap<>(TermsKind.class), Collectors.toList()));
        Map<TermsKind, TermsTimeline> out = new EnumMap<>(TermsKind.class);
        for (TermsKind kind : TermsKind.values()) {
            out.put(kind, TermsTimeline.of(byKind.getOrDefault(kind, List.of()), today));
        }
        return out;
    }

    /** 한 종류의 게시본을 오늘 기준으로 나눈 것 */
    public TermsTimeline timeline(TermsKind kind) {
        return TermsTimeline.of(versionQueries.metaOf(kind), today());
    }

    /** 가입·로그인 화면에 약관 「보기」 링크를 보이는가(사이트 설정, 행이 없으면 끔) */
    public boolean linksVisible() {
        return settings.findById(SiteSettingEntity.ROW_ID).map(SiteSettingEntity::isTermsLinksVisible).orElse(false);
    }

    /** 사이트 표시 시간대의 오늘 */
    public LocalDate today() {
        return LocalDate.now(siteZone());
    }

    /** 사이트 표시 시간대({@code site_settings.display_zone_id}). 행이 없거나 읽을 수 없는 이름이면 처음 값({@code Asia/Seoul}) */
    public ZoneId siteZone() {
        String zone = settings.findById(SiteSettingEntity.ROW_ID).map(SiteSettingEntity::getDisplayZoneId).orElse(SiteSettingEntity.DEFAULT_ZONE_ID);
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            log.warn("사이트 표시 시간대를 읽을 수 없어 처음 값을 쓴다: {}", zone);
            return ZoneId.of(SiteSettingEntity.DEFAULT_ZONE_ID);
        }
    }
}
