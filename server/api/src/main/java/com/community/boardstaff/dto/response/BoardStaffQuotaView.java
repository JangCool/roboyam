package com.community.boardstaff.dto.response;

import com.community.sanction.service.BoardSanctionService;

/** 오늘(한국 시간 0시 기준) 처리자 1명·게시판 1개의 제재 한도 — 경고 + 쓰기 제한 합산(user-web {@code BoardStaffQuota}) */
public record BoardStaffQuotaView(int limit, int used, int remaining) {

    public static BoardStaffQuotaView of(BoardSanctionService.Quota quota) {
        return new BoardStaffQuotaView(quota.limit(), quota.used(), quota.remaining());
    }
}
