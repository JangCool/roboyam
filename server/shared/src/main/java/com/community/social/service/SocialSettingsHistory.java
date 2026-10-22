package com.community.social.service;

import com.community.social.dto.SocialSettingChange;
import com.community.social.entity.SocialProvider;
import com.community.social.entity.SocialProviderSettingHistoryEntity;
import com.community.social.repository.master.SocialProviderSettingHistoryRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 소셜 로그인 공급자 설정 변경 이력({@code social_provider_setting_history}, V53)을 남긴다 — 설치 마법사(INSTALL)와 관리자 설정 화면(ADMIN).
 * <b>업무 트랜잭션에 참여한다</b>(저장이 되돌려지면 이력도 없다). 바뀐 칸이 없으면 쓰지 않는다.
 *
 * <p>값은 이력에 남겨도 되는 모양으로만 받는다 — 시크릿은 {@link #SECRET_SET}·{@link #SECRET_CHANGED}·{@link #SECRET_CLEARED} 표시만,
 * 클라이언트 ID 는 {@link #maskClientId}.
 */
@Component
public class SocialSettingsHistory {

    public static final String FIELD_CLIENT_ID = "clientId";
    public static final String FIELD_CLIENT_SECRET = "clientSecret";
    public static final String FIELD_ENABLED = "enabled";
    /** 시크릿을 처음 넣음 */
    public static final String SECRET_SET = "SET";
    /** 시크릿을 바꿈 */
    public static final String SECRET_CHANGED = "CHANGED";
    /** 시크릿을 지움(설정 삭제) */
    public static final String SECRET_CLEARED = "CLEARED";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String MASK = "****";

    private final SocialProviderSettingHistoryRepository history;
    private final SnowflakeIdGenerator ids;

    public SocialSettingsHistory(SocialProviderSettingHistoryRepository history, SnowflakeIdGenerator ids) {
        this.history = history;
        this.ids = ids;
    }

    /** 바뀐 칸이 있으면 한 줄 남긴다 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(SocialProvider provider, String action, String source, Long operatorId, Instant now, List<SocialSettingChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        history.persist(SocialProviderSettingHistoryEntity.create(ids.nextId(), provider, action, source, operatorId, now, MAPPER.writeValueAsString(changes)));
    }

    /** 이력의 {@code changes} JSON 을 읽는다. 읽을 수 없으면 빈 목록(옛 줄의 모양이 달라도 조회가 멈추면 안 된다) */
    public static List<SocialSettingChange> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(MAPPER.readValue(json, SocialSettingChange[].class));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** 클라이언트 ID 를 끝 4자리만 보이게 가린다({@code ****abcd}). 4자 이하면 {@code ****}, 없으면 null. V53 마이그레이션의 첫 줄과 같은 규칙이다 */
    public static String maskClientId(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return null;
        }
        return clientId.length() > 4 ? MASK + clientId.substring(clientId.length() - 4) : MASK;
    }
}
