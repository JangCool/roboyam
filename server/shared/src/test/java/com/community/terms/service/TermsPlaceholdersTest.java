package com.community.terms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.site.dto.SiteOperatorValues;
import com.community.site.entity.SiteOperatorStage;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 약관 자리표시 바꾸기(terms-operator-contract 「규칙」 — 정본은 admin-web {@code util-terms-placeholder.ts} 머리 주석 규칙 1~5) — 관리자 화면 [미리보기]와 같은 결과인지.
 */
class TermsPlaceholdersTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 7);

    private static SiteOperatorValues individual() {
        return new SiteOperatorValues(SiteOperatorStage.INDIVIDUAL, "홍길동", "help@roboyam.example", null, true, null, null, null, null, null);
    }

    private static SiteOperatorValues corporation() {
        return new SiteOperatorValues(SiteOperatorStage.CORPORATION, "홍길동", "help@roboyam.example", "02-000-0000", false, "김보호", "privacy@roboyam.example", "AWS",
                "Amazon SES", new SiteOperatorValues.Business("로보얌 주식회사", "홍길동", "123-45-67891", "서울특별시 강남구 1", "2026-서울강남-0001", "서울특별시 강남구", "110111-0000000"));
    }

    @Test
    void 개인_단계는_호칭이_운영자이고_운영자정보는_운영자_연락_이메일만_빈_전화는_뺀다() {
        TermsPlaceholders.Values v = TermsPlaceholders.valuesOf(individual(), "로보얌", DAY);
        TermsPlaceholders.Result r = TermsPlaceholders.apply("<p>{{운영자}}는 {{서비스이름}}을 운영해요. 시행일 {{시행일}}</p><p>{{ 보호책임자 }} · {{보호책임자연락처}} · {{연락이메일}}</p>", v);
        assertThat(r.unknown()).isEmpty();
        assertThat(r.html()).isEqualTo("<p>운영자는 로보얌을 운영해요. 시행일 2026년 12월 7일</p><p>홍길동 · help@roboyam.example · help@roboyam.example</p>");
        assertThat(v.operatorInfo()).extracting(TermsPlaceholders.Row::label).containsExactly("운영자", "연락 이메일");
    }

    @Test
    void 법인은_호칭이_회사_보호책임자는_따로_적은_사람_목록은_회사명과_법인등록번호와_신고_기관() {
        TermsPlaceholders.Values v = TermsPlaceholders.valuesOf(corporation(), "로보얌", DAY);
        assertThat(v.operatorAlias()).isEqualTo("회사");
        assertThat(v.privacyOfficer()).isEqualTo("김보호");
        assertThat(v.privacyOfficerContact()).as("「같음」이 아니면 운영자 전화를 붙이지 않는다").isEqualTo("privacy@roboyam.example");
        assertThat(v.operatorInfo()).extracting(TermsPlaceholders.Row::label)
                .containsExactly("회사명", "대표자", "사업자등록번호", "법인등록번호", "사업장 주소", "전화", "이메일", "통신판매업 신고번호");
        assertThat(v.operatorInfo().getLast().value()).isEqualTo("2026-서울강남-0001 (서울특별시 강남구)");
    }

    @Test
    void 개인사업자는_상호이고_법인등록번호를_넣지_않는다_같음이면_운영자_전화를_붙인다() {
        SiteOperatorValues sole = new SiteOperatorValues(SiteOperatorStage.SOLE_PROPRIETOR, "홍길동", "help@x.example", "010-0000-0000", true, null, null, null, null,
                new SiteOperatorValues.Business("로보얌", "홍길동", "123-45-67891", "서울 1", null, null, "무시"));
        TermsPlaceholders.Values v = TermsPlaceholders.valuesOf(sole, "로보얌", DAY);
        assertThat(v.operatorInfo()).extracting(TermsPlaceholders.Row::label).containsExactly("상호", "대표자", "사업자등록번호", "사업장 주소", "전화", "이메일");
        assertThat(v.privacyOfficerContact()).isEqualTo("help@x.example · 010-0000-0000");
    }

    @Test
    void 운영자이름은_개인이면_운영자_이름_사업자면_상호_회사명_호스팅과_메일_발송_사업자도_바꾼다() {
        TermsPlaceholders.Result r = TermsPlaceholders.apply("<p>{{운영자이름}}(이하 「{{운영자}}」) · {{호스팅사업자}} · {{메일발송사업자}}</p>",
                TermsPlaceholders.valuesOf(corporation(), "로보얌", DAY));
        assertThat(r.unknown()).isEmpty();
        assertThat(r.empty()).isEmpty();
        assertThat(r.html()).isEqualTo("<p>로보얌 주식회사(이하 「회사」) · AWS · Amazon SES</p>");
        assertThat(TermsPlaceholders.valuesOf(individual(), "로보얌", DAY).operatorName()).isEqualTo("홍길동");
    }

    @Test
    void 값이_빈_자리표시는_처음_나온_차례로_중복_없이_돌려준다() {
        TermsPlaceholders.Result r = TermsPlaceholders.apply("<p>{{메일발송사업자}} {{운영자}} {{호스팅사업자}} {{ 메일발송사업자 }}</p><p>{{운영자정보}}</p>",
                TermsPlaceholders.valuesOf(SiteOperatorValues.INITIAL, "로보얌", DAY));
        assertThat(r.empty()).containsExactly("메일발송사업자", "호스팅사업자", "운영자정보");
        assertThat(r.html()).isEqualTo("<p> 운영자  </p>");
    }

    @Test
    void 운영자정보가_문단_하나를_차지하면_목록_글_중간이면_한_줄() {
        TermsPlaceholders.Values v = TermsPlaceholders.valuesOf(individual(), "로보얌", DAY);
        TermsPlaceholders.Result r = TermsPlaceholders.apply("<p> {{운영자정보}} </p><p>연락처: {{운영자정보}}</p>", v);
        assertThat(r.html()).isEqualTo("<ul><li>운영자: 홍길동</li><li>연락 이메일: help@roboyam.example</li></ul>"
                + "<p>연락처: 운영자 홍길동, 연락 이메일 help@roboyam.example</p>");
    }

    @Test
    void 바꿔_넣는_값은_HTML_이스케이프한다() {
        SiteOperatorValues tricky = new SiteOperatorValues(SiteOperatorStage.INDIVIDUAL, "<b>\"홍'&\"</b>", "a@b.example", null, true, null, null, null, null, null);
        TermsPlaceholders.Result r = TermsPlaceholders.apply("<p>{{보호책임자}}</p><p>{{운영자정보}}</p>", TermsPlaceholders.valuesOf(tricky, "A&B", DAY));
        assertThat(r.html()).isEqualTo("<p>&lt;b&gt;&quot;홍&#39;&amp;&quot;&lt;/b&gt;</p>"
                + "<ul><li>운영자: &lt;b&gt;&quot;홍&#39;&amp;&quot;&lt;/b&gt;</li><li>연락 이메일: a@b.example</li></ul>");
        assertThat(TermsPlaceholders.apply("{{서비스이름}}", TermsPlaceholders.valuesOf(tricky, "A&B", DAY)).html()).isEqualTo("A&amp;B");
    }

    @Test
    void 모르는_이름은_그대로_두고_처음_나온_차례로_중복_없이_돌려준다_서식으로_쪼개진_것은_자리표시가_아니다() {
        TermsPlaceholders.Result r = TermsPlaceholders.apply("<p>{{회사이름}} {{운영자}} {{ 전화 }} {{회사이름}} {{운영<strong>자</strong>}}</p>",
                TermsPlaceholders.valuesOf(individual(), "로보얌", DAY));
        assertThat(r.unknown()).containsExactly("회사이름", "전화");
        assertThat(r.html()).isEqualTo("<p>{{회사이름}} 운영자 {{ 전화 }} {{회사이름}} {{운영<strong>자</strong>}}</p>");
    }

    @Test
    void 정화된_초안의_쪼개진_중괄호를_되돌린_뒤_바꾼다() {
        String sanitized = TermsHtml.sanitize("<p>{{운영자}}와 {{시행일}}</p><p>{{운영자정보}}</p>");
        assertThat(sanitized).as("정화기는 {{ 를 쪼갠다").doesNotContain("{{");
        TermsPlaceholders.Result r = TermsPlaceholders.apply(TermsPlaceholders.unsplitBraces(sanitized), TermsPlaceholders.valuesOf(individual(), "로보얌", DAY));
        assertThat(r.unknown()).isEmpty();
        assertThat(TermsHtml.sanitize(r.html())).contains("운영자와 2026년 12월 7일").contains("<ul><li>운영자: 홍길동</li>");
    }
}
