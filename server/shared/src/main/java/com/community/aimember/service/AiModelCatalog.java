package com.community.aimember.service;

import com.community.aimember.entity.AiModelEntity;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.repository.master.AiModelRepository;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고를 수 있는 모델(정본 = 표 {@code ai_models}, shared V189 — 옛 배포 설정 {@code roboyam.ai-member.models.*} 대신). 관리자 저장 검사 · 목록의 쉬는 이유 · 실행기가 같이 본다.
 * 「사용」이 꺼졌거나 표에 없는 모델을 쓰는 계정은 쉰다({@link AiMemberDirectory#MODEL_UNAVAILABLE} — 관리자가 모델을 다시 골라야 돈다).
 * 캐시하지 않는다(행 몇십 개 — 바꾸면 바로 반영).
 */
@Service
@Transactional(readOnly = true)
public class AiModelCatalog {

    private final AiModelRepository models;

    public AiModelCatalog(AiModelRepository models) {
        this.models = models;
    }

    /** 지금 켜진 모델 한 벌(한 질의) — 여러 계정을 볼 때 */
    public Enabled enabled() {
        Set<String> keys = new HashSet<>();
        for (AiModelEntity m : models.findByEnabledTrue()) {
            keys.add(key(m.getProvider(), m.getModelId()));
        }
        return new Enabled(Set.copyOf(keys));
    }

    /** 그 제공사에서 고를 수 있는 모델인가(한 계정만 볼 때) */
    public boolean allows(AiProvider provider, String model) {
        return enabled().allows(provider, model);
    }

    /** 켜진 모델 한 벌 */
    public record Enabled(Set<String> keys) {

        public boolean allows(AiProvider provider, String model) {
            return provider != null && model != null && keys.contains(key(provider, model));
        }
    }

    private static String key(AiProvider provider, String model) {
        return provider.name() + "/" + model;
    }
}
