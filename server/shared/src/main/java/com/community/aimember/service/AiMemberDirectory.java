package com.community.aimember.service;

import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiMemberSettingsEntity;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiProviderKeyStatus;
import com.community.aimember.entity.AiRelationKind;
import com.community.aimember.repository.master.AiMemberModelHistoryRepository;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 회원 찾기 — 사이트(작성자 「AI · 모델」 배지 · 공개 프로필 · 소모임), 집계 제외(포인트·보상 이벤트·하이라이트), 관리자 화면이 같이 쓴다.
 * AI 표시는 숨기는 옵션이 없다(정책 3) — 계정이 꺼져 있거나 쉬어도 배지는 붙는다. 글·댓글의 모델은 그것을 만든 생성 줄의 모델({@link #postModels}·{@link #commentModels}),
 * 생성 줄이 없으면(사람이 직접 썼거나 옛 글) 계정의 지금 모델이다. 공개 프로필은 계정의 지금 모델.
 */
@Service
@Transactional(readOnly = true)
public class AiMemberDirectory {

    /** 쉬는 이유(켜져 있어도 쉴 수 있다) — 차례: 꺼짐 → 전체 정지 → 제공사 키 없음 → 제공사 쉬는 중(결제·크레딧 부족 · 키 오류) → 모델 없음 → 사이트 제재 */
    public static final String DISABLED = "DISABLED";
    public static final String GLOBAL_STOP = "GLOBAL_STOP";
    public static final String NO_PROVIDER_KEY = "NO_PROVIDER_KEY";
    /** 제공사가 결제·크레딧 부족으로 거절해 그 제공사를 쉬게 했다(V189 키 상태) — 결제한 뒤 키를 다시 저장하면 돈다 */
    public static final String BILLING_REQUIRED = "BILLING_REQUIRED";
    /** 제공사가 키를 받지 않아 그 제공사를 쉬게 했다(V189 키 상태) — 키를 다시 저장하면 돈다 */
    public static final String KEY_INVALID = "KEY_INVALID";
    /** 계정의 모델이 모델 목록(표 {@code ai_models} — {@link AiModelCatalog})에서 꺼졌거나 없다 — 관리자가 모델을 다시 골라야 돈다 */
    public static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";
    public static final String SANCTIONED = "SANCTIONED";

    /** 공개 프로필 소개 한 줄의 관심사 부분 최대 글자 */
    static final int INTRO_INTERESTS_MAX = 40;

    private final AiMemberQueries queries;
    private final AiMemberProfileRepository profiles;
    private final Messages messages;
    private final AiMemberModelHistoryRepository history;

    public AiMemberDirectory(AiMemberQueries queries, AiMemberProfileRepository profiles, Messages messages, AiMemberModelHistoryRepository history) {
        this.history = history;
        this.queries = queries;
        this.profiles = profiles;
        this.messages = messages;
    }

    /** 작성자 배지의 제공사·모델 */
    public record AiAuthor(AiProvider provider, String model) {
    }

    /** 이 회원들 중 AI 회원 — 회원 ID → 제공사·모델(한 질의) */
    public Map<Long, AiAuthor> authorsOf(Collection<Long> userIds) {
        Map<Long, AiAuthor> out = new HashMap<>();
        queries.authorsOf(userIds).forEach((id, row) -> out.put(id, new AiAuthor(row.provider(), row.model())));
        return out;
    }

    /** 게시된 글 번호 → 그 글을 만든 제공사·모델(정책 3 — 실제로 부른 모델, 생성 줄이 없으면 빠진다) */
    public Map<Long, AiAuthor> postModels(Collection<Long> postIds) {
        Map<Long, AiAuthor> out = new HashMap<>();
        queries.postModels(postIds).forEach((id, m) -> out.put(id, new AiAuthor(m.provider(), m.model())));
        return out;
    }

    /** 게시된 댓글 번호 → 그 댓글을 만든 제공사·모델 */
    public Map<Long, AiAuthor> commentModels(Collection<Long> commentIds) {
        Map<Long, AiAuthor> out = new HashMap<>();
        queries.commentModels(commentIds).forEach((id, m) -> out.put(id, new AiAuthor(m.provider(), m.model())));
        return out;
    }

    public boolean isAi(Long userId) {
        return userId != null && queries.isAi(userId);
    }

    /** 모든 AI 회원의 회원 ID */
    public Set<Long> allUserIds() {
        return queries.allUserIds();
    }

    /** 공개 프로필의 AI 칸 */
    public record ProfileAi(AiProvider provider, String model, String intro, Relation relation, java.util.List<ModelSince> modelHistory) {
    }

    /** 모델 이력 한 줄(공개 — 바꾼 사람 없음) */
    public record ModelSince(AiProvider provider, String model, java.time.Instant since) {
    }

    /** 관계 상대 — 회원 ID(탈퇴 등으로 없으면 null)·닉네임 */
    public record Relation(AiRelationKind kind, Long partnerUserId, String partnerNickname) {
    }

    /** 그 회원이 AI 회원이면 공개 프로필의 AI 칸(요청 언어로 나이대를 붙인 소개 한 줄) */
    public Optional<ProfileAi> profileOf(long userId) {
        return profiles.findByUserId(userId).map(a -> {
            Relation relation = null;
            if (a.getPartnerId() != null) {
                AiMemberProfileEntity partner = profiles.findById(a.getPartnerId()).orElse(null);
                if (partner != null) {
                    String nickname = queries.nicknames(java.util.List.of(partner.getUserId())).get(partner.getUserId());
                    relation = new Relation(a.getRelationKind(), nickname == null ? null : partner.getUserId(), nickname == null ? "" : nickname);
                }
            }
            java.util.List<ModelSince> modelHistory = history.findTop20ByAiMemberIdOrderBySinceDescIdDesc(a.getId()).stream()
                    .map(h -> new ModelSince(h.getProvider(), h.getModel(), h.getSince())).toList();
            return new ProfileAi(a.getProvider(), a.getModel(), intro(a), relation, modelHistory);
        });
    }

    /** 「40대 · DIY 목공·로봇청소기 개조」 — 나이대(요청 언어) + 관심사 앞부분 */
    public String intro(AiMemberProfileEntity a) {
        String band = messages.get(new MessageRef("aiMember.ageBand." + a.getAgeBand().name()));
        String interests = a.getInterests() == null ? "" : a.getInterests().strip().replaceAll("\s+", " ");
        if (interests.codePointCount(0, interests.length()) > INTRO_INTERESTS_MAX) {
            interests = interests.substring(0, interests.offsetByCodePoints(0, INTRO_INTERESTS_MAX)) + "…";
        }
        return interests.isEmpty() ? band : band + " · " + interests;
    }

    /**
     * 쉬는 이유(쉬지 않으면 null) — 꺼짐 → 전체 정지 → 제공사 키 없음 → 제공사 쉬는 중(결제·크레딧 부족 · 키 오류) → 모델이 꺼졌거나 목록에 없음
     * → 사이트 제재(정지·영구 정지·탈퇴 진행) 차례(정책 12·13).
     *
     * @param usableKeys      키가 저장되고 풀 수 있는 제공사
     * @param pausedProviders 쉬는 중인 제공사 → 키 상태({@link AiProviderKeys#paused})
     * @param modelAvailable  계정의 모델이 지금 모델 목록에서 켜져 있는가({@link AiModelCatalog})
     * @param restricted      회원 계정이 활동을 멈춰야 하는가
     */
    public static String restingReason(AiMemberProfileEntity a, AiMemberSettingsEntity settings, Set<AiProvider> usableKeys,
                                       Map<AiProvider, AiProviderKeyStatus> pausedProviders, boolean modelAvailable, boolean restricted) {
        if (!a.isEnabled()) {
            return DISABLED;
        }
        if (settings.isStopped()) {
            return GLOBAL_STOP;
        }
        if (!usableKeys.contains(a.getProvider())) {
            return NO_PROVIDER_KEY;
        }
        AiProviderKeyStatus paused = pausedProviders == null ? null : pausedProviders.get(a.getProvider());
        if (paused == AiProviderKeyStatus.BILLING_REQUIRED) {
            return BILLING_REQUIRED;
        }
        if (paused == AiProviderKeyStatus.KEY_INVALID) {
            return KEY_INVALID;
        }
        if (!modelAvailable) {
            return MODEL_UNAVAILABLE;
        }
        if (restricted) {
            return SANCTIONED;
        }
        return null;
    }
}
