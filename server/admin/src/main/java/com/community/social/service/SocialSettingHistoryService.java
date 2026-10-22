package com.community.social.service;

import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.social.dto.SocialSettingChange;
import com.community.social.dto.request.SocialSettingHistoryRequest;
import com.community.social.dto.response.SocialSettingHistoryChangeView;
import com.community.social.dto.response.SocialSettingHistoryPage;
import com.community.social.dto.response.SocialSettingHistoryView;
import com.community.social.entity.SocialProvider;
import com.community.social.entity.SocialProviderSettingHistoryEntity;
import com.community.social.repository.master.SocialProviderSettingHistoryRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 공급자 설정 변경 이력 조회({@code GET /api/admin/social-providers/history}) — 최신이 위. 읽기만 있다(이력은 고치거나 지우지 않는다).
 * 운영자 이름은 지금 이름이고, 운영자를 찾을 수 없으면 요청 언어의 「삭제된 운영자」다.
 */
@Service
@Transactional(readOnly = true)
public class SocialSettingHistoryService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;
    private static final String LABEL_KEY_PREFIX = "settingHistory.field.";

    private final SocialProviderSettingHistoryRepository history;
    private final OperatorRepository operators;
    private final Messages messages;

    public SocialSettingHistoryService(SocialProviderSettingHistoryRepository history, OperatorRepository operators, Messages messages) {
        this.history = history;
        this.operators = operators;
        this.messages = messages;
    }

    public SocialSettingHistoryPage search(SocialSettingHistoryRequest request) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        SocialProvider provider = null;
        String providerName = request == null ? null : request.provider();
        if (providerName != null && !providerName.isBlank()) {
            provider = SocialProvider.from(providerName).orElse(null);
            if (provider == null) {
                fields.put("provider", new MessageRef("socialSetting.history.provider.invalid"));
            }
        }
        MessageRef range = new MessageRef("socialSetting.history.page.range", String.valueOf(MAX_SIZE));
        Integer page = integer(request == null ? null : request.page(), 0, 0, Integer.MAX_VALUE / MAX_SIZE);
        if (page == null) {
            fields.put("page", range);
        }
        Integer size = integer(request == null ? null : request.size(), DEFAULT_SIZE, 1, MAX_SIZE);
        if (size == null) {
            fields.put("size", range);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("changedAt"), Sort.Order.desc("id")));
        Page<SocialProviderSettingHistoryEntity> rows = provider == null ? history.findAll(pageable) : history.findByProvider(provider, pageable);
        List<Long> operatorIds = rows.getContent().stream().map(SocialProviderSettingHistoryEntity::getOperatorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = operators.findAllById(operatorIds).stream()
                .collect(Collectors.toMap(OperatorEntity::getId, OperatorEntity::getOperatorName, (a, b) -> a));
        List<SocialSettingHistoryView> items = rows.getContent().stream().map(row -> view(row, names)).toList();
        return new SocialSettingHistoryPage(items, rows.getTotalElements(), page, size);
    }

    private SocialSettingHistoryView view(SocialProviderSettingHistoryEntity row, Map<Long, String> names) {
        String name = row.getOperatorId() == null ? null : names.get(row.getOperatorId());
        boolean deleted = name == null;
        List<SocialSettingHistoryChangeView> changes = SocialSettingsHistory.parse(row.getChanges()).stream()
                .map(change -> new SocialSettingHistoryChangeView(change.field(), LABEL_KEY_PREFIX + change.field(), change.before(), change.after()))
                .toList();
        return new SocialSettingHistoryView(String.valueOf(row.getId()), row.getProvider().name(), messages.get(row.getProvider().displayNameKey()), row.getAction(),
                row.getSource(), row.getOperatorId() == null ? null : String.valueOf(row.getOperatorId()),
                deleted ? messages.get("socialSetting.history.operatorDeleted") : name, deleted, row.getChangedAt(), changes);
    }

    /** 비었으면 기본값, 정수가 아니거나 범위 밖이면 null */
    private static Integer integer(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            return value < min || value > max ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
