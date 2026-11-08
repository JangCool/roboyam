package com.community.board.dto.request;

import com.community.board.dto.BoardPolicyValues;
import java.util.List;

/**
 * {@code PUT /api/admin/boards/{id}} — 기본 정보 + 정책 + 말머리를 한 트랜잭션으로 저장한다(admin-web {@code BoardSaveRequest}).
 * 바뀐 부분의 권한만 본다: 기본 정보·말머리 {@code board.manage}, 정책(잠금·안내문·가젯 노출 금지 포함) {@code board.settings}, 매니저·스텝 한도 칸 {@code board.staff.limit}.
 *
 * @param gadgetExclusions 「가젯 노출 금지」로 고를 가젯 이름({@code BoardGadget}) — null 이면 바꾸지 않는다, 중복은 하나로, 모르는 값은 400 {@code fields.gadgetExclusions}
 * @param writeTemplate    글 양식(새 글 본문에 미리 채우는 평문, 2,000자까지 — V153) — null 이면 바꾸지 않는다, 빈 글자면 지운다
 * @param adsHidden        「이 게시판에 광고 숨김」 — null 이면 바꾸지 않는다(정책 묶음 — {@code board.settings}). 19금 게시판은 광고 자리 설정 {@code adult.enabled} 가 꺼져 있으면 이 칸과 상관없이 광고가 없다
 * @param secretMode       비밀글 방식 OFF · OPTIONAL · ALWAYS(V166) — null 이면 바꾸지 않는다(정책 묶음 — {@code board.settings}), 모르는 값 400 {@code fields.secretMode}.
 *                         일반 게시판만 켤 수 있다(그 밖 400). 바꿔도 이미 쓴 글의 비밀 여부는 그대로
 * @param statusEnabled    처리 상태를 쓰는가(V166) — null 이면 바꾸지 않는다(정책 묶음), 일반 게시판만 켤 수 있다(그 밖 400 {@code fields.statusEnabled})
 */
public record BoardSaveRequest(String name, String nameEn, String description, String layout, Boolean viewToggle, String color, Boolean allowAdult,
                               Boolean investmentNotice, String writeLock, String introText, String writeGuideText, String writeTemplate, BoardPolicyValues policy,
                               List<BoardCommonPrefixSaveRequest> commonPrefixes, List<BoardPrefixSaveRequest> prefixes,
                               List<String> gadgetExclusions, Boolean adsHidden, String secretMode, Boolean statusEnabled) {

    /** 운영 게시판 칸 없이(그대로) */
    public BoardSaveRequest(String name, String nameEn, String description, String layout, Boolean viewToggle, String color, Boolean allowAdult,
                            Boolean investmentNotice, String writeLock, String introText, String writeGuideText, String writeTemplate, BoardPolicyValues policy,
                            List<BoardCommonPrefixSaveRequest> commonPrefixes, List<BoardPrefixSaveRequest> prefixes,
                            List<String> gadgetExclusions, Boolean adsHidden) {
        this(name, nameEn, description, layout, viewToggle, color, allowAdult, investmentNotice, writeLock, introText, writeGuideText, writeTemplate, policy,
                commonPrefixes, prefixes, gadgetExclusions, adsHidden, null, null);
    }
}
