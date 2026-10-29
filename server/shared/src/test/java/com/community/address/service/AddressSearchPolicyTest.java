package com.community.address.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.zconfiguration.message.MessageRef;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;

/** 주소 검색의 검색어·설정 칸 규칙(address-search-contract 「필드·검증」) — DB 없이 */
class AddressSearchPolicyTest {

    @Test
    void 검색어는_낱말로_나누고_괄호와_쉼표는_띄어쓰기로_본다() {
        assertThat(AddressSearchPolicy.terms("  테헤란로   152 ")).containsExactly("테헤란로", "152");
        assertThat(AddressSearchPolicy.terms("서울특별시 강남구 테헤란로 152 (역삼동, 강남파이낸스센터)"))
                .containsExactly("서울특별시", "강남구", "테헤란로", "152", "역삼동", "강남파이낸스센터");
    }

    @Test
    void 같은_낱말은_한_번만_낱말은_여섯_개까지() {
        assertThat(AddressSearchPolicy.terms("역삼동 역삼동 테헤란로")).containsExactly("역삼동", "테헤란로");
        assertThat(AddressSearchPolicy.terms("a1 b2 c3 d4 e5 f6 g7 h8")).hasSize(AddressSearchPolicy.MAX_TERMS).containsExactly("a1", "b2", "c3", "d4", "e5", "f6");
    }

    @Test
    void 띄어쓰기를_뺀_글자가_두_자보다_적으면_낱말이_없다() {
        assertThat(AddressSearchPolicy.terms("가")).isEmpty();
        assertThat(AddressSearchPolicy.terms(" ( 가 ) ")).isEmpty();
        assertThat(AddressSearchPolicy.terms(null)).isEmpty();
        assertThat(AddressSearchPolicy.terms("가 나")).containsExactly("가", "나");
    }

    @Test
    void 검색어_문제는_비었음_너무_김_너무_짧음_순서() {
        assertThat(AddressSearchPolicy.keywordProblem(null).key()).isEqualTo("address.search.keywordRequired");
        assertThat(AddressSearchPolicy.keywordProblem("   ").key()).isEqualTo("address.search.keywordRequired");
        assertThat(AddressSearchPolicy.keywordProblem("가").key()).isEqualTo("address.search.keywordTooShort");
        MessageRef tooLong = AddressSearchPolicy.keywordProblem("가".repeat(AddressSearchPolicy.KEYWORD_MAX + 1));
        assertThat(tooLong.key()).isEqualTo("address.search.keywordTooLong");
        assertThat(tooLong.args()).containsExactly(AddressSearchPolicy.KEYWORD_MAX);
        assertThat(AddressSearchPolicy.keywordProblem("가".repeat(AddressSearchPolicy.KEYWORD_MAX))).isNull();
        assertThat(AddressSearchPolicy.keywordProblem("테헤란로")).isNull();
    }

    @Test
    void 검색어의_LIKE_특수문자는_글자_그대로_찾는다() {
        assertThat(AddressSearchPolicy.containsPattern("테헤란로")).isEqualTo("%테헤란로%");
        assertThat(AddressSearchPolicy.containsPattern("100%")).isEqualTo("%100\\%%");
        assertThat(AddressSearchPolicy.containsPattern("a_b")).isEqualTo("%a\\_b%");
        assertThat(AddressSearchPolicy.containsPattern("a\\b")).isEqualTo("%a\\\\b%");
    }

    @Test
    void 스크립트_주소는_비우면_공식_주소_https_만_500자까지() {
        assertThat(AddressSearchPolicy.scriptUrlProblem(null)).isNull();
        assertThat(AddressSearchPolicy.scriptUrlProblem("  ")).isNull();
        assertThat(AddressSearchPolicy.scriptUrlProblem("https://cdn.example.com/postcode.v2.js")).isNull();
        assertThat(AddressSearchPolicy.scriptUrlProblem("http://cdn.example.com/postcode.v2.js").key()).isEqualTo("address.settings.scriptUrlNotHttps");
        assertThat(AddressSearchPolicy.scriptUrlProblem("https://").key()).isEqualTo("address.settings.scriptUrlNotHttps");
        assertThat(AddressSearchPolicy.scriptUrlProblem("https://cdn.example.com/a b.js").key()).isEqualTo("address.settings.scriptUrlNotHttps");
        String longUrl = "https://" + "a".repeat(AddressSearchPolicy.SCRIPT_URL_MAX);
        assertThat(AddressSearchPolicy.scriptUrlProblem(longUrl).key()).isEqualTo("address.settings.scriptUrlTooLong");

        assertThat(AddressSearchPolicy.storedScriptUrl("  ")).isNull();
        assertThat(AddressSearchPolicy.storedScriptUrl(" https://x.example/p.js ")).isEqualTo("https://x.example/p.js");
        assertThat(AddressSearchPolicy.effectiveScriptUrl(null)).isEqualTo(AddressSearchPolicy.DEFAULT_SCRIPT_URL);
        assertThat(AddressSearchPolicy.effectiveScriptUrl("https://x.example/p.js")).isEqualTo("https://x.example/p.js");
    }

    @Test
    void 갱신_권고_일수는_7에서_365() {
        assertThat(AddressSearchPolicy.staleDaysProblem(null).key()).isEqualTo("address.settings.staleDaysRequired");
        assertThat(AddressSearchPolicy.staleDaysProblem(6).key()).isEqualTo("address.settings.staleDaysOutOfRange");
        assertThat(AddressSearchPolicy.staleDaysProblem(366).key()).isEqualTo("address.settings.staleDaysOutOfRange");
        assertThat(AddressSearchPolicy.staleDaysProblem(7)).isNull();
        assertThat(AddressSearchPolicy.staleDaysProblem(365)).isNull();
    }

    @Test
    void 질의_시간_한도로_끊긴_것만_더_자세히로_답한다() {
        assertThat(AddressSearchService.timedOut(new QueryTimeoutException("timeout"))).isTrue();
        assertThat(AddressSearchService.timedOut(new DataAccessResourceFailureException("x", new SQLException("canceling statement", "57014")))).isTrue();
        assertThat(AddressSearchService.timedOut(new DataAccessResourceFailureException("x", new SQLException("connection", "08006")))).isFalse();
    }
}
