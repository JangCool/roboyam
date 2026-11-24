package com.community.aimember.service;

import com.community.aimember.dto.response.AiSettingsView;
import com.community.aimember.repository.master.MemberAiOptOutStore;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 AI 제외 설정 — 「내 글·댓글을 AI 회원에게 보내지 않기」 한 스위치(기본 꺼짐, docs/features/ai-members-contract.md 「회원의 AI 제외 설정 (2026-11-24)」).
 * 켜면 그 회원의 글은 AI 기자 뉴스 소재·AI 회원 첫 댓글 대상에서, 댓글은 AI 답글 대상·답글 때 함께 읽는 같은 줄기 댓글에서 빠진다(실행기 {@code AiDraftGenerator} · {@code AiMemberQueries}).
 * 켜기 전에 만들어 둔 생성물은 등록기가 {@code MEMBER_OPTED_OUT} 으로 남기고 올리지 않는다. 쪽지 받기 설정({@code DmService#updateSettings})과 같은 모양.
 */
@Service
public class MyAiSettingsService {

    private final MemberAiOptOutStore store;

    public MyAiSettingsService(MemberAiOptOutStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public AiSettingsView mine(long userId) {
        return new AiSettingsView(store.optedOut(userId));
    }

    /** {@code excludeFromAi} 가 불리언이 아니면 400 {@code fields.excludeFromAi} */
    @Transactional
    public AiSettingsView save(long userId, Object excludeFromAi) {
        if (!(excludeFromAi instanceof Boolean exclude)) {
            throw ValidationException.of("excludeFromAi", new MessageRef("aiOptOut.excludeFromAi.invalid"));
        }
        if (exclude) {
            store.optOut(userId, Instant.now());
        } else {
            store.optIn(userId);
        }
        return new AiSettingsView(exclude);
    }
}
