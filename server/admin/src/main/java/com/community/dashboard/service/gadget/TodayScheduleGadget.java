package com.community.dashboard.service.gadget;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.BatchCatalog;
import com.community.batch.entity.BatchJobEntity;
import com.community.batch.repository.master.BatchJobRepository;
import com.community.batch.service.BatchCronPolicy;
import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardTodayScheduleView;
import com.community.dashboard.repository.master.DashboardCommerceStore;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 오늘·내일 일정 — 지금부터 내일(한국 날짜) 끝까지, 항목마다 그 종류 조회 권한이 있을 때만(시각 차례, 최대 10).
 *
 * <ul>
 *   <li>광고 시작·종료(ad.read) — 예약된 광고 건(ix_ba_scheduled).</li>
 *   <li>포인트 상점 판매 시작·끝(pointshop.read) — 사용 중 품목(품목 표는 작다).</li>
 *   <li>예약 배치(batch.read) — 켜 둔 Java 배치의 다음 실행 시각(cron 계산, DB 조회 없음). 하루에 여러 번 도는 배치({@link #BATCH_MIN_INTERVAL} 보다 자주)는 목록을 덮어 빼고,
 *       DB 배치(pg_cron)는 아직 넣지 않는다.</li>
 *   <li>공구 마감(commerce.product.read) — 모집 중 공구의 지금 마감 시각(연장 중이면 연장 끝 — ix_group_buys_recruiting_end 범위, 최대 10). {@code id} = {@code GROUPBUY_CLOSE-<공구 ID>}.</li>
 * </ul>
 */
@Component
public class TodayScheduleGadget implements DashboardGadgetProvider {

    static final int MAX = 10;
    static final Duration BATCH_MIN_INTERVAL = Duration.ofHours(6);

    private final DashboardGadgetStore store;
    private final DashboardCommerceStore commerce;
    private final BatchJobRepository jobs;
    private final BatchCronPolicy cron;
    private final BatchProperties batchProperties;
    private final Messages messages;

    public TodayScheduleGadget(DashboardGadgetStore store, DashboardCommerceStore commerce, BatchJobRepository jobs, BatchCronPolicy cron, BatchProperties batchProperties,
                               Messages messages) {
        this.store = store;
        this.commerce = commerce;
        this.jobs = jobs;
        this.cron = cron;
        this.batchProperties = batchProperties;
        this.messages = messages;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.TODAY_SCHEDULE;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        AdminActor actor = query.actor();
        Instant from = query.now();
        Instant to = DashboardGadgetQuery.startOf(query.today().plusDays(2));
        List<DashboardTodayScheduleView.Item> items = new ArrayList<>();
        if (actor.can(Permission.AD_READ)) {
            for (DashboardGadgetStore.AdBooking b : store.adBookings(from, to)) {
                if (AdScheduleGadget.within(b.startAt(), from, to)) {
                    items.add(new DashboardTodayScheduleView.Item("AD_START-" + b.id(), "AD_START", b.title(), null, b.startAt()));
                }
                if (AdScheduleGadget.within(b.endAt(), from, to)) {
                    items.add(new DashboardTodayScheduleView.Item("AD_END-" + b.id(), "AD_END", b.title(), null, b.endAt()));
                }
            }
        }
        if (actor.can(Permission.COMMERCE_PRODUCT_READ)) {
            for (DashboardCommerceStore.Recruiting g : commerce.closingGroupBuys(from, to, MAX)) {
                items.add(new DashboardTodayScheduleView.Item("GROUPBUY_CLOSE-" + g.id(), "GROUPBUY_CLOSE", g.title(), null, g.endsAt()));
            }
        }
        if (actor.can(Permission.POINTSHOP_READ)) {
            for (DashboardGadgetStore.ShopSale s : store.shopSales(from, to)) {
                if (AdScheduleGadget.within(s.startAt(), from, to)) {
                    items.add(new DashboardTodayScheduleView.Item("POINTSHOP_START-" + s.id(), "POINTSHOP_START", s.name(), s.nameEn(), s.startAt()));
                }
                if (AdScheduleGadget.within(s.endAt(), from, to)) {
                    items.add(new DashboardTodayScheduleView.Item("POINTSHOP_END-" + s.id(), "POINTSHOP_END", s.name(), s.nameEn(), s.endAt()));
                }
            }
        }
        if (actor.can(Permission.BATCH_READ)) {
            ZonedDateTime start = from.atZone(batchProperties.zoneId());
            for (BatchJobEntity job : jobs.findAll()) {
                BatchCatalog definition = BatchCatalog.ofKey(job.getJobKey());
                if (definition == null || !definition.isJava() || !job.isEnabled()) {
                    continue;
                }
                ZonedDateTime next = cron.next(job.getCron(), start);
                if (next == null || !next.toInstant().isBefore(to)) {
                    continue;
                }
                ZonedDateTime after = cron.next(job.getCron(), next);
                if (after != null && Duration.between(next, after).compareTo(BATCH_MIN_INTERVAL) < 0) {
                    continue;
                }
                items.add(new DashboardTodayScheduleView.Item("BATCH-" + job.getJobKey(), "BATCH", messages.get(new MessageRef(definition.nameKey())), null,
                        next.toInstant()));
            }
        }
        return new DashboardTodayScheduleView(items.stream()
                .sorted(Comparator.comparing(DashboardTodayScheduleView.Item::at).thenComparing(DashboardTodayScheduleView.Item::id)).limit(MAX).toList());
    }
}
