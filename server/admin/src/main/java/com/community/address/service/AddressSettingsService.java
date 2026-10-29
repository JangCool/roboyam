package com.community.address.service;

import com.community.address.dto.AddressProvider;
import com.community.address.dto.request.AddressSettingsSaveRequest;
import com.community.address.dto.response.AddressDbStateView;
import com.community.address.dto.response.AddressSettingsView;
import com.community.address.entity.AddressDbLoadEntity;
import com.community.address.entity.AddressLoadStatus;
import com.community.address.entity.AddressSearchSettingsEntity;
import com.community.address.repository.master.AddressDbLoadRepository;
import com.community.address.repository.master.AddressSearchSettingsRepository;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 설정 › 주소 검색의 제공처 구획(docs/features/address-search-contract.md) — 조회 {@code site.read}, 저장 {@code site.manage}(컨트롤러가 선언).
 * 저장은 세 칸을 모두 받아 틀린 칸을 모아 400 {@code VALIDATION} — 하나라도 틀리면 아무것도 저장하지 않는다. 바뀐 칸만 감사 {@code before}/{@code after},
 * 바뀐 것이 없으면 쓰지 않는다. 공개 서버(api)는 회원이 우편번호 찾기를 열 때마다 DB 를 읽으므로 바로 따라온다.
 */
@Service
public class AddressSettingsService {

    public static final String TARGET_ID = "settings";

    private final AddressSearchSettingsRepository settings;
    private final AddressDbLoadRepository loads;
    private final OperatorRepository operators;
    private final AuditLogService audit;

    public AddressSettingsService(AddressSearchSettingsRepository settings, AddressDbLoadRepository loads, OperatorRepository operators, AuditLogService audit) {
        this.settings = settings;
        this.loads = loads;
        this.operators = operators;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AddressSettingsView view() {
        return view(row(), Instant.now());
    }

    @Transactional
    public AddressSettingsView save(AdminActor actor, AddressSettingsSaveRequest body) {
        AddressSettingsSaveRequest request = body == null ? new AddressSettingsSaveRequest(null, null, null) : body;
        AddressProvider provider = check(request);
        String scriptUrl = AddressSearchPolicy.storedScriptUrl(request.scriptUrl());
        int days = request.staleWarnDays();

        AddressSearchSettingsEntity row = settings.findWithLockById(AddressSearchSettingsEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("address_search_settings 에 id = 1 줄이 없다(V109)"));
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        changed(before, after, "provider", row.getProvider().name(), provider.name());
        changed(before, after, "scriptUrl", row.getScriptUrl(), scriptUrl);
        changed(before, after, "staleWarnDays", row.getStaleWarnDays(), days);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (!after.isEmpty()) {
            row.change(provider, scriptUrl, days, actor.operatorId(), now);
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.ADDRESS_SEARCH_SETTINGS_UPDATE, AuditResult.SUCCESS)
                    .target(AddressImportWorker.TARGET_TYPE, TARGET_ID).with("before", before).with("after", after));
        }
        return view(row, now);
    }

    /** 세 칸 검사 — 틀린 칸을 모아 400. 맞으면 제공처 */
    static AddressProvider check(AddressSettingsSaveRequest request) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        AddressProvider provider = AddressProvider.ofName(request.provider());
        if (provider == null) {
            fields.put("provider", new MessageRef("address.settings.providerUnknown"));
        }
        MessageRef url = AddressSearchPolicy.scriptUrlProblem(request.scriptUrl());
        if (url != null) {
            fields.put("scriptUrl", url);
        }
        MessageRef days = AddressSearchPolicy.staleDaysProblem(request.staleWarnDays());
        if (days != null) {
            fields.put("staleWarnDays", days);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return provider;
    }

    /** 지금 주소 DB 가 갱신 권고 일수보다 오래됐는가 — 올린 적이 없어도 true */
    static boolean stale(Instant loadedAt, int staleWarnDays, Instant now) {
        return loadedAt == null || loadedAt.isBefore(now.minus(Duration.ofDays(staleWarnDays)));
    }

    private AddressSearchSettingsEntity row() {
        return settings.findById(AddressSearchSettingsEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("address_search_settings 에 id = 1 줄이 없다(V109)"));
    }

    private AddressSettingsView view(AddressSearchSettingsEntity row, Instant now) {
        AddressDbLoadEntity last = loads.findFirstByStatusOrderByFinishedAtDesc(AddressLoadStatus.DONE).orElse(null);
        AddressDbStateView db = last == null ? new AddressDbStateView(null, null, null, null, true)
                : new AddressDbStateView(last.getFinishedAt(), String.join(", ", last.getFileNames()), last.getEntryCount(), last.getEnglishCount(),
                        stale(last.getFinishedAt(), row.getStaleWarnDays(), now));
        String by = row.getUpdatedBy() == null ? null : operators.findById(row.getUpdatedBy()).map(OperatorEntity::getOperatorName).orElse(null);
        return new AddressSettingsView(row.getProvider(), row.getScriptUrl(), AddressSearchPolicy.DEFAULT_SCRIPT_URL, row.getStaleWarnDays(),
                AddressSearchPolicy.STALE_DAYS_MIN, AddressSearchPolicy.STALE_DAYS_MAX, db, row.getUpdatedAt(), by);
    }

    private static void changed(Map<String, Object> before, Map<String, Object> after, String name, Object from, Object to) {
        if (!Objects.equals(from, to)) {
            before.put(name, from);
            after.put(name, to);
        }
    }
}
