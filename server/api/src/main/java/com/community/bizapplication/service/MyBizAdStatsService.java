package com.community.bizapplication.service;

import com.community.ad.dto.AdStatsView;
import com.community.ad.service.AdStatsService;
import com.community.bizapplication.dto.BizAdStatsView;
import com.community.bizapplication.dto.BizMemberView;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.CreativeRow;
import com.community.bizapplication.service.BizApplicationReader.Snapshot;
import com.community.bizapplication.service.BizApplicationReader.Summary;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업자 센터 광고 성과(ad-performance-contract 규칙 7·8) — 본인 건만(신청 건 상세와 같은 본인 검사 — 남의 건·없는 건 404 {@code BIZ_APPLICATION_NOT_FOUND}).
 * 계산은 shared {@link AdStatsService}. 운영자 등록 건({@code user_id} 없음)은 사업자 화면에 나타나지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class MyBizAdStatsService {

    /** 요약 최대 건 수 · 최근 날 수 */
    static final int SUMMARY_MAX = 5;
    static final int RECENT_DAYS = 7;
    /** 요약에 드는 내림 까닭 — 정지 중 · 긴급 내림 · 사업자(서비스) 정지. 광고주 중단은 뺀다 */
    static final Set<BizTakedownCause> SUMMARY_CAUSES = EnumSet.of(BizTakedownCause.ADVERTISER_PAUSE, BizTakedownCause.EMERGENCY, BizTakedownCause.SUSPENDED);

    private final BizApplicationReader reader;
    private final AdStatsService stats;

    public MyBizAdStatsService(BizApplicationReader reader, AdStatsService stats) {
        this.reader = reader;
        this.stats = stats;
    }

    /** 한 건 — 노출 자리 줄이 없는 본인 건은 빈 결과 */
    public AdStatsView.Application application(long userId, long id, String from, String to) {
        Snapshot s = reader.snapshotOwned(id, userId);
        return stats.application(s.app(), s.items(), from, to);
    }

    /**
     * 「진행 중 광고」 — 본인 건 중 노출 자리 줄이 있고 시작 ≤ 지금 &lt; 끝이며 예약({@code SCHEDULED}) 또는 내림 까닭 정지·긴급·사업자 정지인 건, 시작 늦은 차례 최대 5.
     */
    public BizAdStatsView.Summary summary(long userId) {
        Instant now = stats.now();
        LocalDate today = ServiceTime.dateOf(now);
        LocalDate from = today.minusDays(RECENT_DAYS - 1L);
        List<Summary> picked = reader.summaries(reader.byUser(userId)).stream()
                .filter(s -> BizApplicationReader.capacityProduct(s.items()) != null && included(s.app(), now))
                .sorted(Comparator.comparing((Summary s) -> s.app().startAt()).reversed().thenComparing(s -> s.app().id(), Comparator.reverseOrder()))
                .limit(SUMMARY_MAX).toList();
        List<AppRow> apps = picked.stream().map(Summary::app).toList();
        Map<Long, List<AdStatsView.Day>> recent = stats.recent(apps, from, today);
        Map<Long, CreativeRow> creatives = reader.creatives(apps.stream().map(AppRow::id).toList());
        List<BizAdStatsView.Item> items = picked.stream().map(s -> {
            AppRow a = s.app();
            BizMemberView.Summary sum = MyBizApplicationService.summary(a, s.items(), creatives.get(a.id()), s.refund(), now);
            List<AdStatsView.Day> spark = recent.get(a.id());
            AdStatsView.Counts last7 = AdStatsView.Counts.ZERO;
            for (AdStatsView.Day d : spark) {
                last7 = last7.plus(d.impressions(), d.clicks());
            }
            return new BizAdStatsView.Item(sum.id(), sum.title(), sum.kind(), sum.adType(), sum.placement(), sum.status(), sum.phase(), sum.takedownCause(),
                    sum.startDate(), sum.endDate(), last7, spark);
        }).toList();
        return new BizAdStatsView.Summary(now, items);
    }

    /** 규칙 8 — 시작 ≤ 지금 &lt; 끝 · 예약 또는 정지·긴급·사업자 정지 내림(광고주 중단·끝난 건·취소 제외) */
    static boolean included(AppRow a, Instant now) {
        if (a.startAt().isAfter(now) || !a.endAt().isAfter(now)) {
            return false;
        }
        return a.status() == BizApplicationStatus.SCHEDULED || (a.status() == BizApplicationStatus.TAKEN_DOWN && SUMMARY_CAUSES.contains(a.takedownCause()));
    }
}
