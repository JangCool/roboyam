package com.community.member.dto.response;

import java.util.List;

/** 회원 목록 한 쪽 — {@code page} 는 0 부터 */
public record MemberPage(List<MemberView> items, long total, int page, int size) {
}
