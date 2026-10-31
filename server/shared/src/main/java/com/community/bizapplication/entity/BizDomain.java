package com.community.bizapplication.entity;

import com.community.permission.entity.Permission;
import java.util.Arrays;
import java.util.List;

/**
 * 신청 건의 업무(도메인) — 관리자 메뉴·권한·알림을 가르는 단위(docs/plans/biz-ad-hotdeal/04-ia-review.md, 안 C). 서버 엔진 {@code biz_applications} 는 하나이고
 * 종류({@link BizApplicationKind})마다 도메인이 하나다. 조회 권한 · 처리 권한이 도메인마다 다르다(환불은 두 도메인 모두 {@code deal-order.refund}).
 */
public enum BizDomain {
    AD(Permission.AD_READ, Permission.AD_MANAGE),
    HOTDEAL(Permission.HOTDEAL_READ, Permission.HOTDEAL_MANAGE);

    private final Permission read;
    private final Permission manage;

    BizDomain(Permission read, Permission manage) {
        this.read = read;
        this.manage = manage;
    }

    /** 목록·상세·기간 판정을 보는 권한 */
    public Permission readPermission() {
        return read;
    }

    /** 심사·결제 처리(승인·반려·수동 전환·계약서·입금·확정·기한·취소) 권한 */
    public Permission managePermission() {
        return manage;
    }

    /** 이 도메인의 종류들(선언 차례) */
    public List<BizApplicationKind> kinds() {
        return Arrays.stream(BizApplicationKind.values()).filter(k -> k.domain() == this).toList();
    }
}
