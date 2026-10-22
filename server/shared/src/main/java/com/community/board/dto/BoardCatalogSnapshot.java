package com.community.board.dto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 게시판 목록·유효 정책·기본 설정 하위 화면 값·등급 목록의 한 시점 사본({@link com.community.board.service.BoardCatalog}).
 *
 * @param boards        모든 게시판(꺼진 것 포함, 정렬 순서대로)
 * @param grades        등급 키 → 등급(서열·이름)
 * @param firstGradeKey 첫 등급(서열이 가장 낮은 등급)의 키 — 「새 회원」 규칙의 대상. 등급이 없으면 null
 */
public record BoardCatalogSnapshot(List<EffectiveBoard> boards, Map<Long, EffectiveBoard> byId, Map<String, EffectiveBoard> bySlug,
                                   BoardDefaultsValues defaults, BoardSecurityValues security, BoardReportValues report, Map<String, GradeInfo> grades,
                                   String firstGradeKey) {

    /** 등급 하나 */
    public record GradeInfo(String key, int level, String label) {
    }

    public Optional<EffectiveBoard> board(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<EffectiveBoard> board(String slug) {
        return slug == null ? Optional.empty() : Optional.ofNullable(bySlug.get(slug));
    }

    /** 등급 이름 — 등급 키가 아니면(GUEST·STAFF·OFF·지워진 등급) null */
    public String gradeLabel(String key) {
        GradeInfo grade = key == null ? null : grades.get(key);
        return grade == null ? null : grade.label();
    }

    /** 등급 서열 — 모르는 키면 null */
    public Integer gradeLevel(String key) {
        GradeInfo grade = key == null ? null : grades.get(key);
        return grade == null ? null : grade.level();
    }
}
