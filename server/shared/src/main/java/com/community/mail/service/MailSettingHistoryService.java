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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 메일 서버 설정 변경 이력({@code mail_setting_history}, V54) 쓰기 — 설치 마법사(api, 출처 {@code INSTALL})와 관리자 화면(admin, 출처 {@code ADMIN})이 같이 쓴다.
 * <b>설정을 쓰는 트랜잭션에 참여한다</b>(설정 저장이 되돌아가면 이력도 남지 않는다). 바뀐 칸이 없으면 아무것도 쓰지 않는다.
 * 비밀번호는 값 없이 {@code SET}·{@code CHANGED}·{@code CLEARED} 만 남긴다({@link MailSettingChange#password}).
 */
@Service
public class MailSettingHistoryService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final MailSettingHistoryRepository history;
    private final SnowflakeIdGenerator ids;

    public MailSettingHistoryService(MailSettingHistoryRepository history, SnowflakeIdGenerator ids) {
        this.history = history;
        this.ids = ids;
    }

    /** 설치 마법사의 첫 저장 — 넣은 칸을 모두 「없음 → 값」으로 남긴다(CREATE · INSTALL) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInstall(MailValues values, long operatorId, Instant now) {
        List<MailSettingChange> changes = new ArrayList<>();
        changes.add(new MailSettingChange(MailSettingChange.HOST, null, values.host()));
        changes.add(new MailSettingChange(MailSettingChange.PORT, null, String.valueOf(values.port())));
        changes.add(new MailSettingChange(MailSettingChange.SECURITY, null, values.security().name()));
        if (values.username() != null) {
            changes.add(new MailSettingChange(MailSettingChange.USERNAME, null, values.username()));
        }
        if (values.password() != null) {
            changes.add(MailSettingChange.password(MailSettingChange.PASSWORD_SET));
        }
        changes.add(new MailSettingChange(MailSettingChange.FROM_ADDRESS, null, values.from()));
        record(MailSettingHistoryEntity.ACTION_CREATE, MailSettingHistorySource.INSTALL, operatorId, now, changes);
    }

    /** 바뀐 칸을 한 줄로 남긴다. 비어 있으면 쓰지 않는다 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, MailSettingHistorySource source, long operatorId, Instant now, List<MailSettingChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        history.persist(MailSettingHistoryEntity.create(ids.nextId(), action, source, operatorId, now, MAPPER.writeValueAsString(changes)));
    }

    /** 이력의 {@code changes} JSON 을 읽는다. 읽을 수 없으면 빈 목록(옛 줄의 모양이 달라도 조회가 멈추면 안 된다) */
    public static List<MailSettingChange> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(MAPPER.readValue(json, MailSettingChange[].class));
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
