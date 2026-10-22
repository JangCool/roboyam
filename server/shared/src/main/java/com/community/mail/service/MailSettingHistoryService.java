package com.community.mail.service;

import com.community.mail.dto.MailSettingChange;
import com.community.mail.dto.MailValues;
import com.community.mail.entity.MailSettingHistoryEntity;
import com.community.mail.entity.MailSettingHistorySource;
import com.community.mail.repository.master.MailSettingHistoryRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 메일 서버 설정 변경 이력 쓰기 — 설치 마법사(api, 출처 {@code INSTALL})와 관리자 화면(admin, 출처 {@code ADMIN})이 같이 쓴다.
 * <b>설정을 쓰는 트랜잭션 안에서 부른다</b>(설정 저장이 되돌아가면 이력도 남지 않는다). 바뀐 칸이 없으면 아무것도 쓰지 않는다.
 * 비밀번호는 값 없이 {@code SET}·{@code CHANGED}·{@code CLEARED} 만 남긴다.
 */
@Service
public class MailSettingHistoryService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<MailSettingChange>> CHANGES = new TypeReference<>() {
    };

    private final MailSettingHistoryRepository history;
    private final SnowflakeIdGenerator ids;

    public MailSettingHistoryService(MailSettingHistoryRepository history, SnowflakeIdGenerator ids) {
        this.history = history;
        this.ids = ids;
    }

    /** 설치 마법사의 첫 저장 — 넣은 칸을 모두 「없음 → 값」으로 남긴다 */
    @Transactional
    public void recordInstall(MailValues values, long operatorId, Instant now) {
        List<MailSettingChange> changes = new ArrayList<>();
        changes.add(MailSettingChange.value(MailSettingChange.HOST, null, values.host()));
        changes.add(MailSettingChange.value(MailSettingChange.PORT, null, String.valueOf(values.port())));
        changes.add(MailSettingChange.value(MailSettingChange.SECURITY, null, values.security().name()));
        if (values.username() != null) {
            changes.add(MailSettingChange.value(MailSettingChange.USERNAME, null, values.username()));
        }
        if (values.password() != null) {
            changes.add(MailSettingChange.password(MailSettingChange.PASSWORD_SET));
        }
        changes.add(MailSettingChange.value(MailSettingChange.FROM_ADDRESS, null, values.from()));
        record(MailSettingHistorySource.INSTALL, operatorId, now, changes);
    }

    /** 바뀐 칸을 한 줄로 남긴다. 비어 있으면 쓰지 않는다 */
    @Transactional
    public void record(MailSettingHistorySource source, long operatorId, Instant now, List<MailSettingChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        history.persist(MailSettingHistoryEntity.create(ids.nextId(), now, operatorId, source, MAPPER.writeValueAsString(changes)));
    }

    /** 저장된 JSON 을 읽는다. 읽지 못하면 빈 목록(이력 조회가 깨지지 않게) */
    public static List<MailSettingChange> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, CHANGES);
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
