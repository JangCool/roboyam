package com.community.terms.service;

import com.community.cache.definition.CacheNames;
import org.springframework.cache.CacheManager;
import com.community.terms.dto.PublicTermsSnapshot;
import com.community.terms.dto.TermsVersionMeta;
import com.community.terms.dto.response.PublicTermsCurrent;
import com.community.terms.dto.response.PublicTermsHistoryItem;
import com.community.terms.dto.response.PublicTermsPageResponse;
import com.community.terms.dto.response.PublicTermsUpcoming;
import com.community.terms.dto.response.PublicTermsVersionResponse;
import com.community.terms.dto.response.TermsCurrentBrief;
import com.community.terms.dto.response.TermsNoticeView;
import com.community.terms.dto.response.TermsSummaryKind;
import com.community.terms.dto.response.TermsSummaryResponse;
import com.community.terms.entity.TermsKind;
import com.community.terms.entity.TermsVersionEntity;
import com.community.terms.repository.master.TermsVersionRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 공개 사이트가 읽는 약관 — 요약({@code /api/terms/summary})과 종류별 화면({@code /api/terms/{kind}})은 <b>짧은 캐시 사본</b>({@link PublicTermsSnapshot})에서 낸다.
 * 요청마다 DB 를 읽지 않고 캐시 {@code terms.public}(기본 20초)에 둔다 — 관리자 게시·옵션 저장은 세대 번호로 몇 초 안에 닿는다({@code PublicSiteService} 와 같은 틀).
 * 지난·예정 버전 본문({@code …/versions/{version}})은 드물게 읽혀 사본에 두지 않고 그때 읽는다.
 */
@Service
public class PublicTermsService {


    private final TermsService terms;
    private final TermsVersionRepository versions;
    private final CacheManager caches;

    public PublicTermsService(TermsService terms, TermsVersionRepository versions, CacheManager caches) {
        this.caches = caches;
        this.terms = terms;
        this.versions = versions;
    }

    public TermsSummaryResponse summary() {
        return snapshot().summary();
    }

    /** 종류별 화면 — 모르는 종류는 404 */
    public PublicTermsPageResponse page(String kindPath) {
        return snapshot().pages().get(kindOf(kindPath));
    }

    /** 게시본 한 버전(지난·지금·예정) — 없으면 404 */
    public PublicTermsVersionResponse version(String kindPath, int version) {
        TermsKind kind = kindOf(kindPath);
        TermsVersionEntity entity = versions.findByKindAndVersion(kind, version).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND));
        String status = terms.timeline(kind).statusOf(version).name();
        return new PublicTermsVersionResponse(entity.getVersion(), entity.getEffectiveDate().toString(), entity.getPublishedAt(), entity.getChangeNote(), status,
                entity.getBody());
    }

    /** 지금 쓸 수 있는 사본 — 캐시 {@code terms.public} */
    private PublicTermsSnapshot snapshot() {
        return caches.getCache(CacheNames.TERMS_PUBLIC).get(CacheNames.SINGLE, this::load);
    }

    private PublicTermsSnapshot load() {
        Map<TermsKind, TermsTimeline> timelines = terms.timelines();
        List<TermsSummaryKind> kinds = new ArrayList<>();
        Map<TermsKind, PublicTermsPageResponse> pages = new EnumMap<>(TermsKind.class);
        for (Map.Entry<TermsKind, TermsTimeline> entry : timelines.entrySet()) {
            TermsKind kind = entry.getKey();
            TermsTimeline timeline = entry.getValue();
            pages.put(kind, page(kind, timeline));
            if (!kind.signup()) {
                continue; // 로보티콘 상품화 계약 — 화면({@code /api/terms/robotikon_contract})은 있고, 가입·로그인 링크·변경 안내(요약)에는 넣지 않는다
            }
            TermsCurrentBrief currentBrief = timeline.current().map(v -> new TermsCurrentBrief(v.version(), v.effectiveDate().toString())).orElse(null);
            TermsNoticeView notice = timeline.notice()
                    .map(v -> new TermsNoticeView(v.version(), v.effectiveDate().toString(), v.effectiveDate().isAfter(timeline.today()), v.changeNote()))
                    .orElse(null);
            kinds.add(new TermsSummaryKind(kind.name(), currentBrief, notice));
        }
        return new PublicTermsSnapshot(new TermsSummaryResponse(terms.linksVisible(), List.copyOf(kinds)), pages);
    }

    private PublicTermsPageResponse page(TermsKind kind, TermsTimeline timeline) {
        PublicTermsCurrent current = timeline.current()
                .flatMap(meta -> versions.findByKindAndVersion(kind, meta.version()))
                .map(v -> new PublicTermsCurrent(v.getVersion(), v.getEffectiveDate().toString(), v.getPublishedAt(), v.getChangeNote(), v.getBody()))
                .orElse(null);
        PublicTermsUpcoming upcoming = timeline.upcoming()
                .map(v -> new PublicTermsUpcoming(v.version(), v.effectiveDate().toString(), v.changeNote()))
                .orElse(null);
        List<PublicTermsHistoryItem> history = timeline.versions().stream()
                .map((TermsVersionMeta v) -> new PublicTermsHistoryItem(v.version(), v.effectiveDate().toString(), timeline.statusOf(v.version()).name()))
                .toList();
        return new PublicTermsPageResponse(kind.name(), current, upcoming, history);
    }

    private static TermsKind kindOf(String path) {
        return TermsKind.ofPath(path).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND));
    }

}
