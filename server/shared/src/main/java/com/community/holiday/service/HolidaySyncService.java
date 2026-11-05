package com.community.holiday.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.holiday.client.SpecialDayApi;
import com.community.holiday.entity.HolidaySource;
import com.community.holiday.repository.master.SiteHolidayStore;
import com.community.publicdata.dto.response.PublicDataSettingsView;
import com.community.publicdata.service.PublicDataEndpointService;
import com.community.publicdata.service.PublicDataSettingsService;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공공데이터에서 공휴일 받기(사용자 지시 2026-11-05) — 한국천문연구원 특일 정보({@link SpecialDayApi}), 인증키는 공공데이터 설정의 일반 인증키 하나({@link PublicDataSettingsService#serviceKey}),
 * 주소는 공공데이터 설정의 API 주소({@link PublicDataEndpointService#holiday}). 예약 배치 {@code holiday-sync}(매월 1일)와 휴일 관리 화면 「지금 받기」가 같이 부른다.
 *
 * <p><b>맞추기 규칙</b>(올해 · 내년, 해마다 12개월):
 * <ul>
 *   <li>한 해를 받으면 그 해의 {@code PUBLIC_DATA} 줄이 응답과 꼭 같아진다 — 새 날 넣기 · 사라진 날 지우기 · 이름(·대체공휴일 여부) 바꾸기. 해 하나는 트랜잭션 하나.</li>
 *   <li>그 해에 {@code SEED} 줄이 있으면 그 해를 처음 받아 반영할 때 지운다.</li>
 *   <li>{@code OPERATOR}(회사 휴무일) 줄은 절대 건드리지 않는다.</li>
 *   <li>그 해 응답이 비어 있으면(내년 자료가 아직 없음) 그 해는 바꾸지 않는다(반영한 해도 아니다).</li>
 *   <li>한 해가 실패하면(한 달이라도) 그 해만 그대로 두고 다른 해는 반영한다 — 결과 PARTIAL. 모두 실패면 FAILED(표는 그대로).</li>
 *   <li>인증키가 없으면 부르지 않는다 — 결과 NO_KEY(실패가 아니라 건너뜀).</li>
 * </ul>
 * 결과는 {@code site_holiday_sync} 1행에 남는다(화면 「마지막으로 받은 때」). 이미 정해진 기한·굳힌 지급일은 다시 계산하지 않는다(값이 저장돼 있다).
 */
@Service
public class HolidaySyncService {

    /** 받기 결과 */
    public enum Outcome {
        OK, PARTIAL, NO_KEY, FAILED
    }

    /**
     * 한 번의 받기.
     *
     * @param appliedYears 응답을 표에 반영한 해(빈 응답·실패한 해는 없다)
     * @param emptyYears   응답이 비어 그대로 둔 해
     * @param failedYears  실패해 그대로 둔 해
     * @param failures     실패한 해 → 사유(KEY_INVALID · TIMEOUT · ERROR)
     * @param changed      바뀐 줄 수(넣기 + 지우기 + 이름 바꾸기 + 지운 기본값)
     */
    public record Result(Outcome outcome, Instant ranAt, List<Integer> appliedYears, List<Integer> emptyYears, List<Integer> failedYears, Map<Integer, String> failures,
                         int changed) {
    }

    /** 받은 줄의 한 해 맞추기 결과 */
    record YearChange(int inserted, int deleted, int renamed, int seedDeleted) {

        int total() {
            return inserted + deleted + renamed + seedDeleted;
        }
    }

    private final SpecialDayApi api;
    private final SiteHolidayStore store;
    private final PublicDataSettingsService keys;
    private final PublicDataEndpointService endpoints;
    private final AuditLogService audit;
    private final TransactionTemplate tx;
    private final ObjectProvider<Clock> clocks;

    public HolidaySyncService(SpecialDayApi api, SiteHolidayStore store, PublicDataSettingsService keys, PublicDataEndpointService endpoints, AuditLogService audit,
                              PlatformTransactionManager txManager, ObjectProvider<Clock> clocks) {
        this.api = api;
        this.store = store;
        this.keys = keys;
        this.endpoints = endpoints;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
        this.clocks = clocks;
    }

    /** 받는 해 — 올해·내년(한국 날짜) */
    public List<Integer> years() {
        int year = ServiceTime.today(clock()).getYear();
        return List.of(year, year + 1);
    }

    /**
     * 받기 — 예약 배치는 {@code operatorId = null}, 「지금 받기」는 운영자(감사 {@code SITE_HOLIDAY_SYNC}). 던지지 않는다(실패는 결과로).
     */
    public Result sync(Long operatorId, String ip) {
        Instant now = now();
        String key = keys.serviceKey();
        Result result;
        if (key == null || key.isBlank()) {
            result = new Result(Outcome.NO_KEY, now, List.of(), List.of(), List.of(), Map.of(), 0);
        } else {
            SpecialDayApi.Endpoint endpoint = endpoints.holiday();
            List<Integer> applied = new ArrayList<>();
            List<Integer> empty = new ArrayList<>();
            List<Integer> failed = new ArrayList<>();
            Map<Integer, String> failures = new LinkedHashMap<>();
            int changed = 0;
            for (int year : years()) {
                SpecialDayApi.Fetch fetch = api.year(key, year, endpoint);
                if (!fetch.ok()) {
                    failed.add(year);
                    failures.put(year, fetch.failure());
                    continue;
                }
                List<SpecialDayApi.Item> items = fetch.items().stream().filter(i -> i.date().getYear() == year).toList();
                if (items.isEmpty()) {
                    empty.add(year);
                    continue;
                }
                YearChange change = Objects.requireNonNull(tx.execute(s -> apply(year, items, now)));
                applied.add(year);
                changed += change.total();
            }
            Outcome outcome = failed.isEmpty() ? Outcome.OK : failed.size() == years().size() ? Outcome.FAILED : Outcome.PARTIAL;
            result = new Result(outcome, now, List.copyOf(applied), List.copyOf(empty), List.copyOf(failed), Map.copyOf(failures), changed);
        }
        Result done = result;
        tx.executeWithoutResult(s -> store.recordSync(done.ranAt(), done.outcome().name(), done.appliedYears(), done.failedYears(), done.changed(), operatorId));
        if (operatorId != null) {
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.SITE_HOLIDAY_SYNC, done.outcome() == Outcome.FAILED ? AuditResult.FAILURE : AuditResult.SUCCESS)
                    .target(SiteHolidayService.AUDIT_TARGET, "sync").with("outcome", done.outcome().name()).with("appliedYears", done.appliedYears())
                    .with("failedYears", done.failedYears()).with("changed", done.changed()));
        }
        return done;
    }

    /**
     * 한 해 맞추기(트랜잭션 안) — 그 해의 {@code PUBLIC_DATA} 줄을 응답과 같게, 기본값 줄은 지운다. 회사 휴무일은 읽지도 않는다.
     * 같은 날의 줄이 한 줄씩이고 이름만 다르면 이름 바꾸기, 그 밖에는 없는 이름 넣기·사라진 이름 지우기.
     */
    YearChange apply(int year, List<SpecialDayApi.Item> items, Instant now) {
        Map<LocalDate, Map<String, Boolean>> fetched = new TreeMap<>();
        for (SpecialDayApi.Item item : items) {
            fetched.computeIfAbsent(item.date(), d -> new LinkedHashMap<>()).merge(item.name(), item.substitute(), (a, b) -> a || b);
        }
        Map<LocalDate, List<SiteHolidayStore.Row>> existing = new TreeMap<>();
        for (SiteHolidayStore.Row row : store.ofYear(year, HolidaySource.PUBLIC_DATA)) {
            existing.computeIfAbsent(row.date(), d -> new ArrayList<>()).add(row);
        }
        int inserted = 0;
        int deleted = 0;
        int renamed = 0;
        java.util.Set<LocalDate> days = new java.util.TreeSet<>(fetched.keySet());
        days.addAll(existing.keySet());
        for (LocalDate day : days) {
            Map<String, Boolean> want = fetched.getOrDefault(day, Map.of());
            List<SiteHolidayStore.Row> have = existing.getOrDefault(day, List.of());
            if (want.size() == 1 && have.size() == 1) {
                Map.Entry<String, Boolean> w = want.entrySet().iterator().next();
                SiteHolidayStore.Row h = have.getFirst();
                if (!h.name().equals(w.getKey()) || h.substitute() != w.getValue()) {
                    store.rename(h.id(), w.getKey(), w.getValue(), now);
                    renamed++;
                }
                continue;
            }
            for (SiteHolidayStore.Row h : have) {
                Boolean sub = want.get(h.name());
                if (sub == null) {
                    store.delete(h.id());
                    deleted++;
                } else if (sub != h.substitute()) {
                    store.rename(h.id(), h.name(), sub, now);
                    renamed++;
                }
            }
            for (Map.Entry<String, Boolean> w : want.entrySet()) {
                if (have.stream().noneMatch(h -> h.name().equals(w.getKey()))) {
                    store.insert(day, w.getKey(), HolidaySource.PUBLIC_DATA, w.getValue(), null, now);
                    inserted++;
                }
            }
        }
        int seed = store.deleteSeedOfYear(year);
        return new YearChange(inserted, deleted, renamed, seed);
    }

    /** 마지막 받기 결과(없으면 null) */
    public SiteHolidayStore.SyncRow last() {
        return store.lastSync().orElse(null);
    }

    /** 인증키가 있는가(화면 안내 — 값은 싣지 않는다) */
    public boolean keyConfigured() {
        String key = keys.serviceKey();
        return key != null && !key.isBlank();
    }

    /**
     * 특일 정보 연결 시험 — 지금 키·주소로 올해 1월 한 달을 부른다(표는 바꾸지 않는다). 결과를 공공데이터 설정의 마지막 시험으로 남긴다. 키가 없으면 부르지 않고 {@code NO_KEY}.
     */
    public PublicDataSettingsView.Test probe(long operatorId) {
        String key = keys.serviceKey();
        PublicDataSettingsView.Result result;
        if (key == null || key.isBlank()) {
            result = PublicDataSettingsView.Result.NO_KEY;
        } else {
            SpecialDayApi.Fetch fetch = api.month(key, years().getFirst(), 1, endpoints.holiday());
            result = fetch.ok() ? PublicDataSettingsView.Result.OK : PublicDataSettingsView.Result.valueOf(fetch.failure());
        }
        endpoints.recordHolidayTest(result.name(), operatorId);
        PublicDataSettingsView.Test saved = endpoints.view().holiday().lastTest();
        return saved != null ? saved : new PublicDataSettingsView.Test(result, now(), null);
    }

    private Clock clock() {
        return clocks.getIfUnique(Clock::systemUTC);
    }

    private Instant now() {
        return clock().instant().truncatedTo(ChronoUnit.MICROS);
    }
}
