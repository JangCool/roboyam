package com.community.aimember.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.board.dto.BoardPrefixInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI 뉴스 글의 말머리 고르기({@link AiNewsSources#pickPrefix}, home-news-contract 「AI 회원 뉴스」) — 순수 함수, DB 없음.
 * ① 소재가 뉴스 게시판 글이면 같은 말머리 ② 아니면 소재 말머리 이름 「AI」·「로봇」·「게임」(대소문자 무시) → 뉴스 게시판의 그 말머리 ③ 없으면 없음.
 * 꺼진 · 운영진 전용 · 성인 전용 말머리는 고르지 않는다.
 */
class AiNewsPrefixTest {

    private static final BoardPrefixInfo AI = prefix(10, "AI");
    private static final BoardPrefixInfo ROBOT = prefix(11, "로봇");
    private static final BoardPrefixInfo IT = prefix(12, "IT");
    private static final BoardPrefixInfo GAME = prefix(18, "게임");
    private static final BoardPrefixInfo NOTICE = new BoardPrefixInfo(4, "공지", true, false, "NOTICE", null, true, 4);
    private static final BoardPrefixInfo QUESTION = new BoardPrefixInfo(1, "질문", false, false, "QUESTION", null, false, 1);
    private static final List<BoardPrefixInfo> NEWS = List.of(QUESTION, NOTICE, AI, ROBOT, IT, GAME);

    private static BoardPrefixInfo prefix(long id, String name) {
        return new BoardPrefixInfo(id, name, false, false, null, null, true, (int) id);
    }

    @Test
    void 뉴스_게시판_글이면_같은_말머리() {
        assertThat(AiNewsSources.pickPrefix(NEWS, true, IT)).isEqualTo(12L);
        assertThat(AiNewsSources.pickPrefix(NEWS, true, ROBOT)).isEqualTo(11L);
    }

    @Test
    void 뉴스_게시판_글이라도_꺼진_말머리면_이름으로_고르고_없으면_없음() {
        BoardPrefixInfo offAi = new BoardPrefixInfo(10, "AI", false, false, null, null, false, 10);
        List<BoardPrefixInfo> news = List.of(offAi, ROBOT, GAME);
        assertThat(AiNewsSources.pickPrefix(news, true, offAi)).isNull(); // 이름 규칙의 「AI」 도 꺼져 있다
        assertThat(AiNewsSources.pickPrefix(news, true, QUESTION)).isNull(); // 꺼진 공통 말머리 — 이름 규칙에도 안 걸린다
    }

    @Test
    void 운영진_전용_말머리는_고르지_않는다() {
        assertThat(AiNewsSources.pickPrefix(NEWS, true, NOTICE)).isNull();
    }

    @Test
    void 다른_게시판_말머리는_이름으로_고른다_대소문자_무시() {
        assertThat(AiNewsSources.pickPrefix(NEWS, false, prefix(900, "AI 뉴스·이슈"))).isEqualTo(10L);
        assertThat(AiNewsSources.pickPrefix(NEWS, false, prefix(901, "ai 소식"))).isEqualTo(10L);
        assertThat(AiNewsSources.pickPrefix(NEWS, false, prefix(902, "로봇 뉴스·이슈"))).isEqualTo(11L);
        assertThat(AiNewsSources.pickPrefix(NEWS, false, prefix(903, "게임 뉴스"))).isEqualTo(18L);
    }

    @Test
    void 이름_규칙은_AI_로봇_게임_차례() {
        assertThat(AiNewsSources.pickPrefix(NEWS, false, prefix(904, "로봇·AI 뉴스"))).isEqualTo(10L);
        assertThat(AiNewsSources.pickPrefix(NEWS, false, prefix(905, "게임 로봇 뉴스"))).isEqualTo(11L);
    }

    @Test
    void 앞_규칙의_말머리를_쓸_수_없으면_다음_규칙() {
        BoardPrefixInfo staffAi = new BoardPrefixInfo(10, "AI", true, false, null, null, true, 10);
        assertThat(AiNewsSources.pickPrefix(List.of(staffAi, ROBOT), false, prefix(906, "AI 로봇 뉴스"))).isEqualTo(11L);
    }

    @Test
    void 성인_전용_말머리는_고르지_않는다() {
        BoardPrefixInfo adultGame = new BoardPrefixInfo(18, "게임", false, true, null, null, true, 18);
        assertThat(AiNewsSources.pickPrefix(List.of(AI, adultGame), false, prefix(907, "게임 뉴스"))).isNull();
        assertThat(AiNewsSources.pickPrefix(List.of(AI, adultGame), true, adultGame)).isNull();
    }

    @Test
    void 맞는_이름이_없거나_소재_말머리가_없으면_없음() {
        assertThat(AiNewsSources.pickPrefix(NEWS, false, prefix(908, "뉴스"))).isNull();
        assertThat(AiNewsSources.pickPrefix(NEWS, false, null)).isNull();
        assertThat(AiNewsSources.pickPrefix(NEWS, true, null)).isNull();
    }

    @Test
    void 뉴스_게시판_글인데_그_게시판_목록에_없는_말머리는_이름으로만() {
        BoardPrefixInfo stray = prefix(999, "IT 소식");
        assertThat(AiNewsSources.pickPrefix(NEWS, true, stray)).isNull();
    }
}
