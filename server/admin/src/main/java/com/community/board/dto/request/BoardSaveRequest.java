package com.community.board.dto.request;

import com.community.board.dto.BoardPolicyValues;
import java.util.List;

/**
 * {@code PUT /api/admin/boards/{id}} — 기본 정보 + 정책 + 말머리를 한 트랜잭션으로 저장한다(admin-web {@code BoardSaveRequest}).
 * 바뀐 부분의 권한만 본다: 기본 정보·말머리 {@code board.manage}, 정책(잠금·안내문 포함) {@code board.settings}, 매니저·스텝 한도 칸 {@code board.staff.limit}.
 */
public record BoardSaveRequest(String name, String nameEn, String description, String layout, Boolean viewToggle, String color, Boolean allowAdult,
                               Boolean investmentNotice, String writeLock, String introText, String writeGuideText, BoardPolicyValues policy,
                               List<BoardCommonPrefixSaveRequest> commonPrefixes, List<BoardPrefixSaveRequest> prefixes) {
}
