package com.community.aimember.service;

import com.community.aimember.dto.AiMemberRequests;
import com.community.aimember.dto.AiMemberViews;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiModelEntity;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.exception.AiModelDefaultLockedException;
import com.community.aimember.exception.AiModelInUseException;
import com.community.aimember.exception.AiModelNotFoundException;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiModelRepository;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모델 목록 관리(관리자 AI 회원 설정 「모델 목록」 — ai-members-contract 「모델 목록·결제 부족 (2026-11-11)」, 표 {@code ai_models} V189).
 * <ul>
 *   <li><b>사용</b>: 켠 모델만 계정 상세에서 고를 수 있고 실행기가 부른다. 끄면 그 모델을 쓰는 계정은 쉰다({@code MODEL_UNAVAILABLE} — 다시 골라야 돈다). 기본 모델은 끌 수 없다(409
 *       {@code AI_MODEL_DEFAULT_LOCKED}).</li>
 *   <li><b>기본</b>: 제공사당 하나 — 새 계정에 처음 들어가는 모델. 꺼진 모델을 기본으로 정하면 함께 켠다.</li>
 *   <li><b>추가</b>: 모델 ID 영숫자·{@code .}·{@code -}·{@code _} 1~{@value #MODEL_ID_MAX}자, 제공사 안에서 중복 금지. 설명 {@value #LABEL_MAX}자 · 가격 0~{@value #PRICE_MAX}(선택).
 *       켜진 채로 목록 맨 뒤에 붙는다.</li>
 *   <li><b>지우기</b>: 쓰는 계정이 있으면 409 {@code AI_MODEL_IN_USE}(대신 끄기), 기본이면 409 {@code AI_MODEL_DEFAULT_LOCKED}. 생성 기록·모델 이력은 모델명 글자만 들고 있어 그대로 남는다.</li>
 * </ul>
 * 바꾸기는 그 제공사의 줄을 모두 잠그고 한다(기본 옮기기·동시 추가가 서로 줄 선다). 권한은 컨트롤러(읽기 {@code ai-member.read} · 쓰기 {@code ai-member.manage}), 감사 {@code AI_MODEL_*}.
 */
@Service
@Transactional(readOnly = true)
public class AiModelAdminService {

    static final String AUDIT_TARGET = "AI_MODEL";
    static final int MODEL_ID_MAX = 64;
    static final int LABEL_MAX = 100;
    static final int PRICE_MAX = 9999;
    private static final Pattern MODEL_ID = Pattern.compile("^[A-Za-z0-9._-]{1," + MODEL_ID_MAX + "}$");

    private final AiModelRepository models;
    private final AiMemberProfileRepository accounts;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;

    public AiModelAdminService(AiModelRepository models, AiMemberProfileRepository accounts, SnowflakeIdGenerator ids, AuditLogService audit) {
        this.models = models;
        this.accounts = accounts;
        this.ids = ids;
        this.audit = audit;
    }

    public AiMemberViews.ModelList list() {
        Map<String, Long> used = new HashMap<>();
        for (AiMemberProfileEntity a : accounts.findAll()) {
            used.merge(a.getProvider().name() + "/" + a.getModel(), 1L, Long::sum);
        }
        return new AiMemberViews.ModelList(models.findAllByOrderByProviderAscSortOrderAscIdAsc().stream()
                .map(m -> new AiMemberViews.ModelRow(Long.toString(m.getId()), m.getProvider().name(), m.getModelId(), m.getLabel(), m.getPriceInput(), m.getPriceOutput(),
                        m.isFreeTier(), m.isEnabled(), m.isDefault(), used.getOrDefault(m.getProvider().name() + "/" + m.getModelId(), 0L), m.getUpdatedAt()))
                .toList());
    }

    /** 모델 직접 추가 — 400 {@code VALIDATION}(provider · modelId · label · priceInput · priceOutput) */
    @Transactional
    public AiMemberViews.ModelList create(AdminActor actor, AiMemberRequests.ModelCreate body) {
        AiMemberRequests.ModelCreate b = body == null ? new AiMemberRequests.ModelCreate(null, null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        AiProvider provider = AiMemberAdminService.parseEnum(AiProvider.class, b.provider());
        if (provider == null) {
            fields.put("provider", new MessageRef("aiMember.provider.unknown"));
        }
        String modelId = b.modelId() == null ? "" : b.modelId().strip();
        if (!MODEL_ID.matcher(modelId).matches()) {
            fields.put("modelId", new MessageRef("aiMember.modelId.invalid"));
        }
        String label = b.label() == null || b.label().isBlank() ? null : b.label().strip().replaceAll("\\s+", " ");
        if (label != null && label.codePointCount(0, label.length()) > LABEL_MAX) {
            fields.put("label", new MessageRef("aiMember.modelLabel.max", LABEL_MAX));
        }
        BigDecimal priceInput = price(b.priceInput(), "priceInput", fields);
        BigDecimal priceOutput = price(b.priceOutput(), "priceOutput", fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        List<AiModelEntity> rows = models.findWithLockByProviderOrderBySortOrderAscIdAsc(provider);
        if (rows.stream().anyMatch(m -> m.getModelId().equals(modelId))) {
            throw ValidationException.of("modelId", new MessageRef("aiMember.modelId.duplicate"));
        }
        int sortOrder = rows.stream().mapToInt(AiModelEntity::getSortOrder).max().orElse(0) + 10;
        Instant now = now();
        AiModelEntity m = AiModelEntity.added(ids.nextId(), provider, modelId, label, priceInput, priceOutput, Boolean.TRUE.equals(b.freeTier()), sortOrder,
                actor.operatorId(), now);
        models.persist(m);
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MODEL_CREATE, AuditResult.SUCCESS).target(AUDIT_TARGET, Long.toString(m.getId()))
                .with("provider", provider.name()).with("modelId", modelId).with("freeTier", m.isFreeTier());
        // 감사 detail 은 null 값을 받지 않는다 — 없는 칸은 넣지 않는다
        if (label != null) {
            event = event.with("label", label);
        }
        if (priceInput != null) {
            event = event.with("priceInput", priceInput.toPlainString());
        }
        if (priceOutput != null) {
            event = event.with("priceOutput", priceOutput.toPlainString());
        }
        audit.record(event);
        models.flush();
        return list();
    }

    /** 사용 켜기·끄기 — 404 {@code AI_MODEL_NOT_FOUND} · 409 {@code AI_MODEL_DEFAULT_LOCKED}(기본 모델 끄기) */
    @Transactional
    public AiMemberViews.ModelList setEnabled(AdminActor actor, String idText, Boolean enabled) {
        if (enabled == null) {
            throw ValidationException.of("enabled", new MessageRef("aiMember.modelEnabled.required"));
        }
        AiModelEntity m = lockOne(idText);
        if (!enabled && m.isDefault()) {
            throw new AiModelDefaultLockedException();
        }
        boolean from = m.isEnabled();
        if (from != enabled) {
            m.enable(enabled, actor.operatorId(), now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MODEL_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, Long.toString(m.getId()))
                    .with("provider", m.getProvider().name()).with("modelId", m.getModelId()).with("change", "ENABLED").with("from", from).with("to", enabled));
            models.flush();
        }
        return list();
    }

    /** 기본 정하기(제공사당 하나) — 꺼진 모델이면 함께 켠다 · 404 {@code AI_MODEL_NOT_FOUND} */
    @Transactional
    public AiMemberViews.ModelList setDefault(AdminActor actor, String idText) {
        AiModelEntity m = lockOne(idText);
        if (m.isDefault()) {
            return list();
        }
        Instant now = now();
        String fromModel = null;
        for (AiModelEntity other : models.findWithLockByProviderOrderBySortOrderAscIdAsc(m.getProvider())) {
            if (other.isDefault()) {
                fromModel = other.getModelId();
                other.markDefault(false, actor.operatorId(), now);
            }
        }
        models.flush(); // 부분 유일 색인(제공사당 기본 하나) — 옛 기본을 먼저 내린다
        boolean enabledToo = !m.isEnabled();
        if (enabledToo) {
            m.enable(true, actor.operatorId(), now);
        }
        m.markDefault(true, actor.operatorId(), now);
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MODEL_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, Long.toString(m.getId()))
                .with("provider", m.getProvider().name()).with("modelId", m.getModelId()).with("change", "DEFAULT").with("to", m.getModelId()).with("enabledToo", enabledToo);
        audit.record(fromModel == null ? event : event.with("from", fromModel));
        models.flush();
        return list();
    }

    /** 지우기 — 404 {@code AI_MODEL_NOT_FOUND} · 409 {@code AI_MODEL_DEFAULT_LOCKED} · 409 {@code AI_MODEL_IN_USE} */
    @Transactional
    public AiMemberViews.ModelList delete(AdminActor actor, String idText) {
        AiModelEntity m = lockOne(idText);
        if (m.isDefault()) {
            throw new AiModelDefaultLockedException();
        }
        if (accounts.countByProviderAndModel(m.getProvider(), m.getModelId()) > 0) {
            throw new AiModelInUseException();
        }
        models.delete(m);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MODEL_DELETE, AuditResult.SUCCESS).target(AUDIT_TARGET, Long.toString(m.getId()))
                .with("provider", m.getProvider().name()).with("modelId", m.getModelId()));
        models.flush();
        return list();
    }

    /** 그 줄을 찾고 같은 제공사의 줄을 모두 잠근다 */
    private AiModelEntity lockOne(String idText) {
        Long id = AiMemberAdminService.parseLong(idText);
        AiModelEntity found = id == null ? null : models.findById(id).orElse(null);
        if (found == null) {
            throw new AiModelNotFoundException();
        }
        return models.findWithLockByProviderOrderBySortOrderAscIdAsc(found.getProvider()).stream().filter(m -> m.getId().equals(id)).findFirst()
                .orElseThrow(AiModelNotFoundException::new);
    }

    /** 가격 — 비우면 null, 0~9999 · 소수 넷째 자리까지(반올림) */
    private static BigDecimal price(BigDecimal raw, String field, Map<String, MessageRef> fields) {
        if (raw == null) {
            return null;
        }
        if (raw.signum() < 0 || raw.compareTo(BigDecimal.valueOf(PRICE_MAX)) > 0) {
            fields.put(field, new MessageRef("aiMember.modelPrice.invalid"));
            return null;
        }
        return raw.setScale(4, RoundingMode.HALF_UP);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
