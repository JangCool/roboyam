package com.community.dashboard.service;

import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionSettings;
import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.DashboardGadgetType.Option;
import com.community.dashboard.dto.request.DashboardGadgetsRequest;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardGadgetsView;
import com.community.dashboard.service.gadget.DashboardGadgetProvider;
import com.community.permission.dto.AdminActor;
import com.community.permission.exception.PermissionDeniedException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가젯 데이터 묶음 조회(admin-dashboard-contract 「API 후보」·「권한·정책」) — 화면이 놓인 가젯의 종류만 부른다.
 *
 * <ul>
 *   <li>종류마다 권한(그중 하나라도)을 서버가 본다. 권한이 없는 종류는 그 칸 null 이고, <b>한 종류만</b> 요청했는데 권한이 없으면 {@code 403 PERMISSION_DENIED}
 *       (대시보드가 1분마다 다시 부를 수 있어 거절 감사 기록은 남기지 않는다 — 입구 검사는 로그인만).</li>
 *   <li>이 API 대상이 아닌 종류(TODO · COMMERCE_TODO · MEMO · SHORTCUTS)는 null. 권한은 있으나 제공자가 없는 종류(원천 없음 · 2단계)는 null + {@code unavailable}.</li>
 *   <li>권한은 있으나 「통계 수집」에서 그 항목이 꺼진 종류(방문 현황 · 인기 페이지 · 유입 경로 · 알림 상태 · 글 활동 · 활발한 게시판 · 가입 추이 — {@link #collectionOff})는
 *       null + {@code disabled}(site-analytics-contract). 설정은 30초 사본이고 캐시보다 먼저 본다.</li>
 *   <li>모르는 종류는 {@code 400 VALIDATION fields.types}. {@code limit.<종류>} 는 5|10, {@code days.<종류>} 는 7|30 이고 틀리면 그 질의 이름의 칸 오류다(그 칸이 없는 종류의 값은 버린다).</li>
 *   <li><b>서버 캐시</b>(사용자 결정 2026-11-03 「서버 부하가 최소화되어야 한다」): 종류별 결과를 서버 메모리에 짧게(숫자·목록 60초, 기간 합·추이 300초) 둔다 —
 *       키·수명·한 번만 계산·크기 상한은 {@link DashboardGadgetCache}. 권한 검사는 캐시 앞에서 매 요청 한다. 캐시에 없는 종류가 있을 때만 읽기 전용 트랜잭션 하나를 열어
 *       계산하므로 모두 캐시에서 나오는 요청은 DB 연결을 잡지 않는다(그래서 메서드 트랜잭션을 두지 않는다). 계산 자체는 여전히 인덱스로 센 COUNT·10행 이하 목록·집계 표만 읽는다.</li>
 *   <li>{@code generatedAt} = 응답에 든 종류 결과 중 <b>가장 오래된 계산 시각</b>(화면 「HH:mm 기준」이 캐시된 값보다 새로워 보이지 않게). 든 결과가 없으면 지금.</li>
 *   <li>관리자 서버가 여러 대면 캐시는 서버마다 따로다 — 공유할 상태가 아니라 괜찮다(값이 수명 안에서 서버마다 조금 다를 수 있을 뿐).</li>
 * </ul>
 */
@Service
public class DashboardGadgetService {

    private final Map<DashboardGadgetType, DashboardGadgetProvider> providers = new EnumMap<>(DashboardGadgetType.class);
    private final DashboardGadgetCache cache = new DashboardGadgetCache(Clock.systemUTC());
    private final TransactionTemplate readOnly;
    private final Messages messages;
    private final StatsCollectionSettings statsSettings;

    public DashboardGadgetService(List<DashboardGadgetProvider> providers, PlatformTransactionManager transactions, Messages messages,
                                  StatsCollectionSettings statsSettings) {
        this.statsSettings = statsSettings;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
        this.messages = messages;
        for (DashboardGadgetProvider provider : providers) {
            if (this.providers.put(provider.type(), provider) != null) {
                throw new IllegalStateException("가젯 종류 하나에 제공자가 둘이다: " + provider.type());
            }
        }
    }

    public DashboardGadgetsView load(AdminActor actor, DashboardGadgetsRequest request) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Set<DashboardGadgetType> types = types(request, fields);
        Map<DashboardGadgetType, Integer> limits = new EnumMap<>(DashboardGadgetType.class);
        Map<DashboardGadgetType, Integer> days = new EnumMap<>(DashboardGadgetType.class);
        for (DashboardGadgetType type : types) {
            if (type.has(Option.LIMIT)) {
                choice(request.limits().get(type.name()), Set.of(5, 10), "limit." + type.name(), "dashboard.gadget.config.limit", fields).ifPresent(v -> limits.put(type, v));
            }
            if (type.has(Option.DAYS)) {
                choice(request.days().get(type.name()), Set.of(7, 30), "days." + type.name(), "dashboard.gadget.config.days", fields).ifPresent(v -> days.put(type, v));
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }

        Map<String, DashboardGadgetData> gadgets = new LinkedHashMap<>();
        Map<DashboardGadgetType, DashboardGadgetCache.Key> wanted = new EnumMap<>(DashboardGadgetType.class);
        List<String> unavailable = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        Locale locale = messages.locale();
        StatsCollection collection = statsSettings.current(); // 30초 사본 — 요청마다 DB 를 읽지 않는다
        boolean denied = false;
        for (DashboardGadgetType type : types) {
            gadgets.put(type.name(), null); // 요청 차례를 지킨다 — 값은 아래에서 채운다
            if (!type.data()) {
                continue;
            }
            if (!type.allowed(actor)) { // 볼 수 있나 — 캐시 앞에서 매 요청 본다
                denied = true;
                continue;
            }
            if (collectionOff(type, collection)) { // 수집이 꺼진 종류 — 값 null + disabled(캐시·집계 표를 보지 않는다)
                disabled.add(type.name());
                continue;
            }
            if (!providers.containsKey(type)) {
                unavailable.add(type.name());
                continue;
            }
            wanted.put(type, DashboardGadgetCache.key(type, limits.getOrDefault(type, DashboardGadgetQuery.DEFAULT_LIMIT),
                    days.getOrDefault(type, DashboardGadgetQuery.DEFAULT_DAYS), locale, actor));
        }
        if (denied && types.size() == 1) {
            throw new PermissionDeniedException();
        }

        Map<DashboardGadgetType, DashboardGadgetCache.Computed> results = new EnumMap<>(DashboardGadgetType.class);
        List<DashboardGadgetType> misses = new ArrayList<>();
        wanted.forEach((type, key) -> cache.peek(key).ifPresentOrElse(c -> results.put(type, c), () -> misses.add(type)));
        if (!misses.isEmpty()) {
            // 캐시에 없는 종류만 이 읽기 전용 트랜잭션 안에서 계산한다(모두 캐시에서 나오면 트랜잭션도 DB 연결도 잡지 않는다)
            readOnly.executeWithoutResult(status -> {
                for (DashboardGadgetType type : misses) {
                    DashboardGadgetCache.Key key = wanted.get(type);
                    DashboardGadgetProvider provider = providers.get(type);
                    results.put(type, cache.get(key, at -> provider.load(new DashboardGadgetQuery(actor, key.limit(), key.days(), at))));
                }
            });
        }
        Instant generatedAt = null;
        for (Map.Entry<DashboardGadgetType, DashboardGadgetCache.Computed> e : results.entrySet()) {
            gadgets.put(e.getKey().name(), e.getValue().data());
            if (generatedAt == null || e.getValue().computedAt().isBefore(generatedAt)) {
                generatedAt = e.getValue().computedAt(); // 가장 오래된 계산 시각 — 「HH:mm 기준」이 거짓말하지 않게
            }
        }
        return new DashboardGadgetsView(generatedAt == null ? Instant.now() : generatedAt, gadgets, unavailable, disabled);
    }

    /**
     * 그 종류의 데이터를 만드는 「통계 수집」 항목이 꺼져 있나(site-analytics-contract — admin-web 종류표의 {@code collect} 칸과 같다). 수집과 무관한 종류는 늘 false.
     * 방문 하위 항목은 방문 수집 전체({@code enabled})가 꺼져도 꺼진 것이다.
     */
    static boolean collectionOff(DashboardGadgetType type, StatsCollection s) {
        return switch (type) {
            case VISITORS -> !s.visitsOn();
            case POPULAR_PAGES -> !s.rawEventsOn();
            case REFERRERS -> !s.referrersOn();
            case NOTIFICATION_HEALTH -> !s.notificationSends();
            case POST_ACTIVITY, BOARD_RANKING, SIGNUP_TREND -> !s.boardActivity();
            default -> false;
        };
    }

    /** 요청한 종류(요청 차례, 겹침 없음) — 비었거나 모르는 종류가 있으면 {@code types} 칸 오류 */
    private static Set<DashboardGadgetType> types(DashboardGadgetsRequest request, Map<String, MessageRef> fields) {
        Set<DashboardGadgetType> out = new LinkedHashSet<>();
        boolean unknown = false;
        if (request.types() != null) {
            for (String token : request.types().split(",")) {
                String name = token.strip();
                if (name.isEmpty()) {
                    continue;
                }
                DashboardGadgetType type = DashboardGadgetType.of(name);
                if (type == null) {
                    unknown = true;
                } else {
                    out.add(type);
                }
            }
        }
        if (unknown || out.isEmpty()) {
            fields.put("types", new MessageRef("dashboard.gadgets.types"));
        }
        return out;
    }

    /** 고를 수 있는 정수 값 — 없으면 빈 값, 틀리면 칸 오류 */
    private static Optional<Integer> choice(String raw, Set<Integer> allowed, String field, String key, Map<String, MessageRef> fields) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            int value = Integer.parseInt(raw.strip());
            if (allowed.contains(value)) {
                return Optional.of(value);
            }
        } catch (NumberFormatException e) {
            // 아래에서 칸 오류로 알린다
        }
        fields.put(field, new MessageRef(key));
        return Optional.empty();
    }
}
