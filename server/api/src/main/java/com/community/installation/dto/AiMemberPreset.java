package com.community.installation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 설치 구성의 <b>AI 기본 계정</b> 파일({@code install-presets/<구성>.ai-members.json}, 예: {@code irobit.ai-members.json}) 의 모양.
 * 원천은 데모 원고 {@code seed/demo/ai-members.json} 의 계정 정의이고 {@code scripts/build-ai-member-preset.mjs} 가 만든다(샘플 글·소모임·모델 이력 연출은 담지 않는다).
 * 정본은 docs/features/install-contract.md 「AI 기본 계정 (2026-11-24)」. 시작 때 모양을 검사한다({@code BoardPresetCatalog}).
 *
 * @param format   형식 — 1
 * @param preset   이 파일을 가리키는 구성 아이디
 * @param accounts 만들 계정(차례대로)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiMemberPreset(int format, String preset, List<Account> accounts) {

    /**
     * 계정 하나 — 관리자 「AI 회원」 만들기와 같은 칸.
     *
     * @param model        제공사의 모델 아이디(이름). 이 DB 에 켜져 있지 않으면 그 제공사 기본 모델, 그것도 없으면 건너뛴다
     * @param avatarId     카탈로그 아바타 ID — 카탈로그에 없으면 아바타 없이 만든다
     * @param voice        글 방식 {@code NORMAL} · {@code DOG} · {@code CAT}(비우면 NORMAL)
     * @param boards       활동 게시판 주소 — 이 DB 에 없거나 AI 가 쓸 수 없는 게시판은 뺀다(하나도 남지 않으면 건너뛴다)
     * @param relation     다른 기본 계정과의 관계(양쪽 모두 이번에 만들어졌을 때만 잇는다) — 없으면 null
     * @param dailyPostMax 계정별 하루 새 글 상한 — 비우면 공통 설정
     * @param newsRatio    계정별 뉴스 글 비율 % — 비우면 공통 설정
     * @param extraPrompt  운영자 추가 지시 — 비우면 없음
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(String nickname, String provider, String model, String avatarId, String voice, Persona persona, String bio, Windows windows,
                          List<String> boards, Relation relation, Integer dailyPostMax, Integer newsRatio, String extraPrompt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Persona(String gender, String ageBand, String personality, String tone, String interests) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Windows(Window morning, Window afternoon) {
    }

    /** {@code HH:mm} 두 개 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Window(String start, String end) {
    }

    /** {@code kind} = {@code AiRelationKind}, {@code partner} = 상대 기본 계정의 닉네임 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Relation(String kind, String partner) {
    }
}
