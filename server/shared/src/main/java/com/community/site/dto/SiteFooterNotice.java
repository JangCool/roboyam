package com.community.site.dto;

import com.community.site.entity.SiteFooterNoticeScope;
import java.util.Arrays;
import java.util.List;

/**
 * 푸터 안내 문구(V177, terms-operator-contract 「푸터 안내 문구」) — 운영 주체 행에 함께 산다. 줄은 앞뒤 공백을 뗀 빈 줄 없는 목록(비면 안내 숨김).
 * {@code {사이트}} 와 뒤 조사는 그대로 둔다 — 사이트 이름으로 바꾸는 곳은 공개 화면(user-web 푸터) 한 곳이다.
 *
 * @param lines 안내 줄(0~{@value #LINES_MAX}줄, 줄마다 {@value #LINE_MAX}자 이하 — 검사는 저장하는 쪽)
 * @param scope 보이기 — 개인 단계에서만 · 늘
 * @param businessApply 푸터 「고객 지원」 끝의 「사업자 신청」 링크를 보이는가(V182, 기본 켬 — 사용자 2026-11-10)
 */
public record SiteFooterNotice(List<String> lines, SiteFooterNoticeScope scope, boolean businessApply) {

    /** 최대 줄 수 */
    public static final int LINES_MAX = 3;
    /** 줄마다 글자 수 상한(코드 포인트) */
    public static final int LINE_MAX = 120;

    /** 저장한 적 없을 때(행이 없을 때)의 값 — 마이그레이션 기본값과 같다 */
    public static final SiteFooterNotice INITIAL = new SiteFooterNotice(
            List.of("{사이트}은 개인이 운영하는 커뮤니티입니다.", "사이트 운영을 위해 광고가 표시될 수 있습니다."), SiteFooterNoticeScope.INDIVIDUAL_ONLY, true);

    public SiteFooterNotice {
        lines = lines == null ? List.of() : List.copyOf(lines);
        scope = scope == null ? SiteFooterNoticeScope.INDIVIDUAL_ONLY : scope;
    }

    /** 글(줄바꿈으로 이은 것) → 줄 — 줄마다 앞뒤 공백을 떼고 빈 줄은 버린다. null 이면 빈 목록 */
    public static List<String> split(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R")).map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    /** DB 에 둘 글 — 줄을 {@code \n} 으로 잇는다. 줄이 없으면 null(안내 숨김) */
    public String stored() {
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    /** 공개·관리자 응답의 줄 — 없으면 null */
    public List<String> linesOrNull() {
        return lines.isEmpty() ? null : lines;
    }
}
