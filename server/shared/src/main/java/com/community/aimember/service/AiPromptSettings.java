package com.community.aimember.service;

import com.community.aimember.entity.AiPromptSettingsEntity;
import com.community.aimember.llm.AiPromptConfig;
import com.community.aimember.repository.master.AiPromptSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 프롬프트 설정 읽기(ai-members-contract 「뉴스 글·프롬프트 관리 (2026-11-11)」) — 실행기가 바퀴마다 부른다(캐시하지 않는다 — 저장하면 다음 바퀴부터).
 * 행이 없거나 읽지 못하면(JSON 깨짐 등) 코드 기본값으로 돈다 — 실행기를 멈추지 않는다. <b>트랜잭션 밖에서 부른다</b>(실패가 바깥 트랜잭션을 되돌리지 않게).
 */
@Component
public class AiPromptSettings {

    private static final Logger log = LoggerFactory.getLogger(AiPromptSettings.class);

    private final AiPromptSettingsRepository settings;

    public AiPromptSettings(AiPromptSettingsRepository settings) {
        this.settings = settings;
    }

    public AiPromptConfig current() {
        try {
            return settings.findById(AiPromptSettingsEntity.ID).map(AiPromptSettingsEntity::config).orElseGet(AiPromptConfig::defaults);
        } catch (RuntimeException e) {
            log.warn("AI 프롬프트 설정을 읽지 못해 기본값으로 돈다 원인={}", e.getClass().getSimpleName());
            return AiPromptConfig.defaults();
        }
    }
}
