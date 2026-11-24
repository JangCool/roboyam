package com.community.installation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.installation.dto.AiMemberPreset;
import com.community.installation.repository.master.InstallationRepositoryQDSL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 설치 구성의 AI 기본 계정(install-contract 「AI 기본 계정 (2026-11-24)」) — 파일은 데모 원고와 같은 11계정이고, 만들 때 꺼진 채 · 모델은 이름(없으면 그 제공사 기본 모델) ·
 * 이 DB 에 없는 게시판은 빼고(하나도 없으면 건너뜀) · 카탈로그에 없는 아바타는 없이 · 같은 닉네임이 있으면 건너뛰고 · 관계는 양쪽에 적는다.
 */
class AiMemberPresetSeedTest extends CommerceApiTestBase {

    @Autowired
    private BoardPresetCatalog presets;
    @Autowired
    private AiMemberPresetInstaller installer;
    @Autowired
    private InstallationRepositoryQDSL installation;

    @Test
    void IROBIT_구성의_AI_기본_계정은_데모와_같은_11계정이다() {
        AiMemberPreset file = presets.aiMembersOf("IROBIT").orElseThrow();
        assertThat(file.accounts()).hasSize(11);
        assertThat(file.accounts()).extracting(AiMemberPreset.Account::nickname).contains("백구진돗개", "삼색이냥", "아이로빗기자");
        AiMemberPreset.Account reporter = file.accounts().stream().filter(a -> a.nickname().equals("아이로빗기자")).findFirst().orElseThrow();
        assertThat(reporter.dailyPostMax()).isEqualTo(10);
        assertThat(reporter.newsRatio()).isEqualTo(100);
        assertThat(reporter.boards()).containsExactly("news");
        assertThat(presets.aiMembersOf("NEUTRAL")).isEmpty();
        // 설치 행의 새 칸(V266)이 엔티티에 맞는다
        tx.executeWithoutResult(s -> installation.lockRow().orElseThrow().getAiMembersSeededAt());
    }

    @Test
    void 꺼진_채로_만들고_모델_게시판_아바타_닉네임_관계를_규칙대로_다룬다() {
        long root = newOperator();
        long boardId = ids.nextId();
        String tag = Long.toString(boardId, 36);
        String sfx = tag.substring(tag.length() - 6);
        String slug = "am" + tag;
        boardFixture.board(boardId, slug, "AI " + sfx, 993);
        long taken = newMember();
        memberFixture.profile(taken, "있음" + sfx);

        AiMemberPreset.Persona persona = new AiMemberPreset.Persona("MALE", "THIRTIES", "차분하다", "존댓말", "로봇");
        AiMemberPreset.Windows windows = new AiMemberPreset.Windows(new AiMemberPreset.Window("07:00", "10:00"), new AiMemberPreset.Window("13:00", "18:00"));
        AiMemberPreset file = new AiMemberPreset(1, "IROBIT", List.of(
                new AiMemberPreset.Account("갑" + sfx, "OPENAI", "gpt-6-luna", "no-such-avatar", null, persona, "소개", windows, List.of(slug, "no-such-board"),
                        new AiMemberPreset.Relation("SPOUSE", "을" + sfx), null, null, null),
                new AiMemberPreset.Account("을" + sfx, "ANTHROPIC", "not-a-model", null, "DOG", persona, "", windows, List.of(slug),
                        new AiMemberPreset.Relation("SPOUSE", "갑" + sfx), 10, 100, "뉴스 먼저"),
                new AiMemberPreset.Account("병" + sfx, "GOOGLE", "gemini-3.5-flash-lite", null, null, persona, "", windows, List.of("no-such-board"), null, null, null, null),
                new AiMemberPreset.Account("있음" + sfx, "GOOGLE", "gemini-3.5-flash-lite", null, null, persona, "", windows, List.of(slug), null, null, null, null)));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        AiMemberPresetInstaller.Outcome outcome = tx.execute(s -> installer.createAll(file, root, now));

        assertThat(outcome).isEqualTo(new AiMemberPresetInstaller.Outcome(2, 2));
        String sql = """
                SELECT a.id, a.enabled, a.provider, a.model, a.voice, a.board_ids::text AS boards, a.partner_id, a.relation_kind, a.daily_post_max, a.news_ratio,
                       a.extra_prompt, a.created_by, p.profile_image, p.bio, u.email
                  FROM ai_member_profiles a JOIN profiles p ON p.user_id = a.user_id JOIN users u ON u.id = a.user_id WHERE p.nickname = ?""";
        Map<String, Object> gap = jdbc.queryForMap(sql, "갑" + sfx);
        Map<String, Object> eul = jdbc.queryForMap(sql, "을" + sfx);
        assertThat(gap).containsEntry("enabled", false).containsEntry("provider", "OPENAI").containsEntry("model", "gpt-6-luna").containsEntry("voice", "NORMAL")
                .containsEntry("boards", "{" + boardId + "}").containsEntry("relation_kind", "SPOUSE").containsEntry("created_by", root).containsEntry("bio", "소개");
        assertThat(gap.get("profile_image")).isNull(); // 카탈로그에 없는 아바타 — 없이
        assertThat(gap.get("email")).isNull(); // 로그인할 수 없는 AI 계정
        assertThat(eul).containsEntry("enabled", false).containsEntry("provider", "ANTHROPIC").containsEntry("model", "claude-haiku-5-5") // 없는 모델 → 제공사 기본 모델
                .containsEntry("voice", "DOG").containsEntry("extra_prompt", "뉴스 먼저");
        assertThat(((Number) eul.get("daily_post_max")).intValue()).isEqualTo(10);
        assertThat(((Number) eul.get("news_ratio")).intValue()).isEqualTo(100);
        assertThat(gap.get("partner_id")).isEqualTo(eul.get("id"));
        assertThat(eul.get("partner_id")).isEqualTo(gap.get("id"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_member_model_history WHERE ai_member_id = ?", Integer.class, gap.get("id"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profiles WHERE nickname = ?", Integer.class, "병" + sfx)).isZero(); // 게시판이 하나도 없어 건너뜀
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profiles WHERE nickname = ?", Integer.class, "있음" + sfx)).isEqualTo(1); // 이미 있던 회원 그대로
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'AI_MEMBER_CREATE' AND target_id = ? AND detail::text LIKE '%INSTALL_PRESET%'",
                Integer.class, String.valueOf(gap.get("id")))).isEqualTo(1);
    }
}
