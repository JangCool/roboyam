package com.community.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 글 태그 글자 규칙(docs/features/post-tags-contract.md 「필드·검증」) — 정리 · 모양 · 비교 열쇠 · 목록(겹침·개수). DB 없이 순수 계산만 본다.
 */
class PostTagsTest {

    @Test
    void 앞뒤_공백과_앞의_샵을_떼고_안쪽_공백은_하나로() {
        assertThat(PostTags.clean("  #AI  ")).isEqualTo("AI");
        assertThat(PostTags.clean("##  휴머노이드 로봇 ")).isEqualTo("휴머노이드 로봇");
        assertThat(PostTags.clean("Tesla   Model  Y")).as("연달은 공백(줄바꿈 아닌 것)은 하나로").isEqualTo("Tesla Model Y");
        assertThat(PostTags.clean(null)).isEmpty();
        assertThat(PostTags.clean(" # ")).isEmpty();
    }

    @Test
    void 모양은_1에서_20자_줄바꿈_제어문자_샵_쉼표_금지() {
        assertThat(PostTags.valid("AI")).isTrue();
        assertThat(PostTags.valid("가".repeat(PostTags.NAME_MAX))).isTrue();
        assertThat(PostTags.valid("가".repeat(PostTags.NAME_MAX + 1))).isFalse();
        assertThat(PostTags.valid("😀".repeat(PostTags.NAME_MAX))).as("글자 수는 코드 포인트로 센다").isTrue();
        assertThat(PostTags.valid("")).isFalse();
        assertThat(PostTags.valid(PostTags.clean("a\nb"))).as("줄바꿈").isFalse();
        assertThat(PostTags.valid(PostTags.clean("a\tb"))).as("탭(제어 문자)").isFalse();
        assertThat(PostTags.valid(PostTags.clean("C#"))).as("안쪽 #").isFalse();
        assertThat(PostTags.valid(PostTags.clean("a,b"))).as("쉼표").isFalse();
    }

    @Test
    void 비교_열쇠는_영문_대소문자를_무시한다() {
        assertThat(PostTags.key("NVIDIA")).isEqualTo(PostTags.key("nvidia"));
        assertThat(PostTags.key("Tesla Model Y")).isEqualTo("tesla model y");
        assertThat(PostTags.key("반도체")).isEqualTo("반도체");
    }

    @Test
    void 목록은_빈칸을_버리고_같은_태그는_처음_모양_하나로_적은_차례() {
        PostTags.Result r = PostTags.normalize(List.of("#AI", " ai ", "", "  ", "반도체", "Nvidia", "NVIDIA"));

        assertThat(r.ok()).isTrue();
        assertThat(r.tags()).containsExactly("AI", "반도체", "Nvidia");
    }

    @Test
    void 겹침을_덜고도_다섯을_넘으면_tooMany() {
        assertThat(PostTags.normalize(List.of("a", "b", "c", "d", "e", "A")).tags()).as("겹침은 하나로 세어 다섯").hasSize(PostTags.MAX_PER_POST);

        PostTags.Result many = PostTags.normalize(List.of("a", "b", "c", "d", "e", "f"));
        assertThat(many.ok()).isFalse();
        assertThat(many.error()).isEqualTo("post.tags.tooMany");
        assertThat(many.tags()).isEmpty();
    }

    @Test
    void 모양이_틀린_태그가_있으면_invalid() {
        PostTags.Result r = PostTags.normalize(List.of("AI", "a,b"));
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).isEqualTo("post.tags.invalid");

        List<String> both = new ArrayList<>(Arrays.asList("1", "2", "3", "4", "5", "6"));
        both.add("가".repeat(PostTags.NAME_MAX + 1));
        assertThat(PostTags.normalize(both).error()).as("모양과 개수가 둘 다 틀리면 모양 먼저").isEqualTo("post.tags.invalid");
    }

    @Test
    void 빈_목록과_null_은_태그_없음() {
        assertThat(PostTags.normalize(null).tags()).isEmpty();
        assertThat(PostTags.normalize(List.of()).ok()).isTrue();
        assertThat(PostTags.normalize(Arrays.asList("#", null, " ")).tags()).isEmpty();
    }

    @Test
    void 개수_상한은_부르는_쪽이_정할_수_있다() {
        List<String> eight = List.of("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(PostTags.normalize(eight, 8).tags()).hasSize(8);
        assertThat(PostTags.normalize(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"), 8).error()).isEqualTo("post.tags.tooMany");
    }

    @Test
    void 거르기_값은_비면_null_틀리면_빈_글자() {
        assertThat(PostTags.filterKey(null)).isNull();
        assertThat(PostTags.filterKey("  ")).isNull();
        assertThat(PostTags.filterKey("#")).as("샵만 — 거르지 않음").isNull();
        assertThat(PostTags.filterKey("#Tesla")).isEqualTo("tesla");
        assertThat(PostTags.filterKey("a,b")).as("모양이 틀리면 맞는 글이 없다").isEmpty();
    }
}
