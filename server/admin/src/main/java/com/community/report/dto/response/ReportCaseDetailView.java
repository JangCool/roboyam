package com.community.report.dto.response;

import com.community.post.dto.response.AdminBodyEmoticonView;
import com.community.post.dto.response.AdminBodyJjalView;
import com.community.sanction.dto.response.SanctionLineView;
import java.util.List;

/**
 * 사건 상세 — 계약의 {@code case} 칸(자바 예약어라 이름만 {@code caseItem} 으로 두고 JSON 은 {@code case}).
 *
 * @param publicUrl      공개 사이트의 대상 주소 — 글·댓글 사건은 그 글({@code /posts/{id}}), 소모임 사건은 그 소모임({@code /groups/{id}}). 사이트 공개 주소가 없으면 null
 * @param group          소모임 사건(V95)의 대상 소모임 — 글·댓글 사건이거나 소모임 행이 없으면 null
 * @param issuedSanction 조치({@code resolve})에서 이 사건으로 준 작성자 제재(member-sanction-contract 결정 4) — 조치 응답에만, 그 밖 null
 * @param emoticons      글·댓글 사건의 지금 본문({@code content.body})과 신고 당시 내용({@code content.snapshot.body})에 나온 로보티콘 토큰의 그림(합쳐 겹치면 한 번,
 *                       판매 중단 팩 포함 — 관리자 글 상세와 같은 규칙). 짤·소모임 사건이거나 토큰이 없으면 빈 목록
 * @param jjals          같은 본문들에 나온 짤 토큰의 표시 정보(내려간 짤은 {@code url null}·{@code blocked true})
 */
public record ReportCaseDetailView(@com.fasterxml.jackson.annotation.JsonProperty("case") ReportCaseItemView caseItem, ReportContentView content,
                                   List<ReportLineView> reports, ReportAuthorCardView authorCard, List<ReportEventView> events, String publicUrl,
                                   ReportGroupView group, SanctionLineView issuedSanction, List<AdminBodyEmoticonView> emoticons, List<AdminBodyJjalView> jjals) {

    /** 조치 응답 — 이 사건에서 준 제재를 곁들인다 */
    public ReportCaseDetailView withIssuedSanction(SanctionLineView sanction) {
        return new ReportCaseDetailView(caseItem, content, reports, authorCard, events, publicUrl, group, sanction, emoticons, jjals);
    }
}
