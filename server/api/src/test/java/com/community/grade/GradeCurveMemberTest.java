package com.community.grade;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.grade.dto.response.GradeRequirementView;
import com.community.grade.dto.response.MyGradeResponse;
import com.community.grade.service.PublicGradeService;
import com.community.grade.service.XpService;
import com.community.point.entity.PointReason;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 레벨 곡선 지수(grade-level-contract 6절)의 회원 쪽 — 내 등급({@code GET /api/me/grade} 의 서비스)의 {@code next.requirements[]} LEVEL 줄 {@code xpLeft}
 * (다음 등급 최소 레벨이 시작하는 경험치 − 내 경험치, 0 이상 — 다른 종류는 null)와, 경험치가 바뀔 때 레벨을 지금 곡선(지수 2.0)으로 매기고 레벨이 오르면 즉시 승급하는 흐름.
 * 실제 PostgreSQL(V245 뒤 기본 사다리: 견습 = 최소 레벨 5 · 3일).
 */
class GradeCurveMemberTest extends CommerceApiTestBase {

    @Autowired
    private PublicGradeService grades;
    @Autowired
    private XpService xp;

    /** 가입 10일 된 새싹 회원 — 경험치와 레벨을 바로 둔다 */
    private long member(long xpValue, int level) {
        long id = newMember();
        String base36 = Long.toString(id, 36);
        memberFixture.profile(id, "g" + base36.substring(Math.max(0, base36.length() - 11)));
        // SQL 로 남김: 가입 시각·경험치·레벨을 바로 두는 도메인 메서드가 없다(경험치·레벨은 프로필 엔티티의 읽기 전용 칸)
        jdbc.update("UPDATE users SET created_at = now() - interval '10 days' WHERE id = ?", id);
        jdbc.update("UPDATE profiles SET xp = ?, level = ? WHERE user_id = ?", xpValue, level, id);
        return id;
    }

    private int rank(long userId) {
        return jdbc.queryForObject("SELECT g.level FROM profiles p JOIN grades g ON g.grade_key = p.grade_key WHERE p.user_id = ?", Integer.class, userId);
    }

    private int level(long userId) {
        return jdbc.queryForObject("SELECT level FROM profiles WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    void 내_등급의_레벨_조건은_남은_경험치를_싣고_다른_조건은_싣지_않는다() {
        assertThat(jdbc.queryForList("SELECT min_level FROM grades WHERE level IN (1, 2, 3) ORDER BY level", Integer.class)).as("V245 기본 사다리").containsExactly(1, 5, 10);
        long member = member(100, 4); // 지수 2.0: Lv.4 = 90 XP, Lv.5 = 160 XP

        MyGradeResponse mine = grades.mine(member);
        assertThat(mine.level()).isEqualTo(4);
        assertThat(mine.xp()).isEqualTo(100);
        assertThat(mine.levelXp()).isEqualTo(90);
        assertThat(mine.nextLevelXp()).isEqualTo(160);
        List<GradeRequirementView> requirements = mine.next().requirements();
        assertThat(requirements).extracting(GradeRequirementView::kind).containsExactly("LEVEL", "DAYS");
        assertThat(requirements.get(0)).isEqualTo(new GradeRequirementView("LEVEL", 4, 5, false, 60L)); // 160 − 100
        assertThat(requirements.get(1)).isEqualTo(new GradeRequirementView("DAYS", 10, 3, true, null));

        // 이미 채웠으면 0(음수가 아니다)
        jdbc.update("UPDATE profiles SET xp = 200, level = 5 WHERE user_id = ?", member);
        assertThat(grades.mine(member).next().requirements().get(0)).isEqualTo(new GradeRequirementView("LEVEL", 5, 5, true, 0L));
    }

    @Test
    void 경험치가_바뀌면_지금_곡선으로_레벨을_매기고_레벨이_오르면_즉시_승급한다() {
        long member = member(150, 4);
        assertThat(rank(member)).isEqualTo(1);

        tx.executeWithoutResult(status -> xp.onLedgerLine(member, PointReason.POST, 20)); // 170 XP → Lv.5(160) — 견습 조건(최소 레벨 5 · 3일)

        assertThat(jdbc.queryForObject("SELECT xp FROM profiles WHERE user_id = ?", Long.class, member)).isEqualTo(170);
        assertThat(level(member)).isEqualTo(5);
        assertThat(rank(member)).as("커밋 뒤 즉시 승급").isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT kind FROM member_grade_changes WHERE user_id = ?", String.class, member)).containsExactly("AUTO");

        tx.executeWithoutResult(status -> xp.onLedgerLine(member, PointReason.POST_REVOKE, -20)); // 150 XP → Lv.4 — 등급은 내리지 않는다

        assertThat(level(member)).isEqualTo(4);
        assertThat(rank(member)).isEqualTo(2);
    }

    /** 데모 설치 끝의 손질({@code DemoDataInstaller} → {@code XpService#recomputeAllFromLedger}) — 팩의 프로필이 옛 곡선(지수 1.5)으로 매긴 레벨을 싣고 있어도 지금 곡선으로 맞춘다 */
    @Test
    void 원장에서_다시_세면_경험치가_같아도_옛_곡선의_레벨을_지금_곡선으로_고친다() {
        long stale = member(170, 7);   // 원장 합계와 경험치는 같고 레벨만 옛 곡선 값(지수 1.5: 170 XP = Lv.7)
        long drifted = member(999, 9); // 원장에 없는 경험치
        // SQL 로 남김: 팩이 원장 줄을 통째로 넣은 모양을 만든다(적립 서비스를 거치면 경험치가 이미 맞는다)
        jdbc.update("INSERT INTO point_ledger (id, user_id, kind, reason, delta, free_delta, paid_delta, balance_after, ref_type, ref_id, created_at) "
                + "VALUES (?, ?, 'EARN', 'POST', 170, 170, 0, 170, 'POST', ?, now())", ids.nextId(), stale, ids.nextId());

        tx.executeWithoutResult(status -> xp.recomputeAllFromLedger());

        assertThat(jdbc.queryForObject("SELECT xp FROM profiles WHERE user_id = ?", Long.class, stale)).isEqualTo(170);
        assertThat(level(stale)).as("지수 2.0: 160 <= 170 < 250").isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT xp FROM profiles WHERE user_id = ?", Long.class, drifted)).isZero();
        assertThat(level(drifted)).isEqualTo(1);
    }
}
