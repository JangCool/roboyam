package com.community.aimember.dto;

import java.util.List;

/**
 * AI 회원 관리 API 의 요청 모양 — admin-web {@code useAiMemberAdminApi.types.ts}. 칸 검사는 서비스가 한다({@code 400 VALIDATION} 칸 경로 {@code persona.tone} 등).
 */
public final class AiMemberRequests {

    private AiMemberRequests() {
    }

    public record Window(String start, String end) {
    }

    public record Windows(Window morning, Window afternoon) {
    }

    public record Persona(String gender, String ageBand, String personality, String tone, String interests) {
    }

    public record Relation(String kind, String partnerAiMemberId) {
    }

    /**
     * 고치기 — {@code avatarId} 는 목업 타입에 없던 칸(카탈로그 아바타 ID — 빠지거나 null 이면 그대로)
     */
    public record Save(String provider, String model, Persona persona, String bio, Relation relation, Windows windows, Integer dailyPosts, List<String> boards,
                       List<String> groups, String avatarId) {
    }

    /** 만들기 — 고치기 칸 + 닉네임(서버가 로그인할 수 없는 회원 계정을 새로 만든다) */
    public record Create(String nickname, String avatarId, String provider, String model, Persona persona, String bio, Relation relation, Windows windows,
                         Integer dailyPosts, List<String> boards, List<String> groups) {

        public Save save() {
            return new Save(provider, model, persona, bio, relation, windows, dailyPosts, boards, groups, avatarId);
        }
    }

    public record Enabled(Boolean enabled) {
    }

    public record Ids(List<String> ids) {
    }

    public record Edit(String title, String body) {
    }

    public record SettingsSave(Boolean instantPublish, String instantScope, List<String> instantAccountIds, Integer dailyCallLimit, Boolean commentOnMemberPosts) {
    }

    public record Stopped(Boolean stopped) {
    }

    /** 키 — 값은 로그·감사에 싣지 않는다 */
    public record Key(String key) {

        @Override
        public String toString() {
            return "Key[***]";
        }
    }

    public record GroupAiOnly(Boolean aiOnly) {
    }
}
