package com.community.dashboard.service;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가젯 데이터 묶음 조회(admin-dashboard-contract 「API 후보」·「권한·정책」) — 화면이 놓인 가젯의 종류만 부른다.
 *
 * <ul>
 *   <li>종류마다 권한(그중 하나라도)을 서버가 본다. 권한이 없는 종류는 그 칸 null 이고, <b>한 종류만</b> 요청했는데 권한이 없으면 {@code 403 PERMISSION_DENIED}
 *       (대시보드가 1분마다 다시 부를 수 있어 거절 감사 기록은 남기지 않는다 — 입구 검사는 로그인만).</li>
 *   <li>이 API 대상이 아닌 종류(TODO · COMMERCE_TODO · MEMO · SHORTCUTS)는 null. 권한은 있으나 제공자가 없는 종류(원천 없음 · 2단계)는 null + {@code unavailable}.</li>
 *   <li>모르는 종류는 {@code 400 VALIDATION fields.types}. {@code limit.<종류>} 는 5|10, {@code days.<종류>} 는 7|30 이고 틀리면 그 질의 이름의 칸 오류다(그 칸이 없는 종류의 값은 버린다).</li>
 *   <li>서버 캐시는 없다(이 저장소에 캐시 방식이 아직 없다) — 질의마다 인덱스로 센 COUNT·10행 이하 목록·집계 표만 읽는다.</li>
 * </ul>
 */
@Service
public class DashboardGadgetService {

    private final Map<DashboardGadgetType, DashboardGadgetProvider> providers = new EnumMap<>(DashboardGadgetType.class);

    public DashboardGadgetService(List<DashboardGadgetProvider> providers) {
        for (DashboardGadgetProvider provider : providers) {
            if (this.providers.put(provider.type(), provider) != null) {
                throw new IllegalStateException("가젯 종류 하나에 제공자가 둘이다: " + provider.type());
            }
        }
    }

    @Transactional(readOnly = true)
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

        Instant now = Instant.now();
        Map<String, DashboardGadgetData> gadgets = new LinkedHashMap<>();
        List<String> unavailable = new ArrayList<>();
        boolean denied = false;
        for (DashboardGadgetType type : types) {
            if (!type.data()) {
                gadgets.put(type.name(), null);
                continue;
            }
            if (!type.allowed(actor)) {
                denied = true;
                gadgets.put(type.name(), null);
                continue;
            }
            DashboardGadgetProvider provider = providers.get(type);
            if (provider == null) {
                unavailable.add(type.name());
                gadgets.put(type.name(), null);
                continue;
            }
            DashboardGadgetQuery query = new DashboardGadgetQuery(actor, limits.getOrDefault(type, DashboardGadgetQuery.DEFAULT_LIMIT),
                    days.getOrDefault(type, DashboardGadgetQuery.DEFAULT_DAYS), now);
            gadgets.put(type.name(), provider.load(query));
        }
        if (denied && types.size() == 1) {
            throw new PermissionDeniedException();
        }
        return new DashboardGadgetsView(now, gadgets, unavailable);
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
