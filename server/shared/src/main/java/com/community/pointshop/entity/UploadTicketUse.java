package com.community.pointshop.entity;

/**
 * 올리기 변경권이 쓰이는 곳(commerce-motion-avatar-contract 규칙 0) — {@code user_items.ref_type}(V157 {@code ck_user_items_ref})과 같은 이름. 신청 하나에 변경권 한 장이 예약된다.
 */
public enum UploadTicketUse {

    /** 닉네임 이미지 신청({@code nickname_image_requests}) */
    NICKNAME_IMAGE(PointShopItemKind.NICKNAME_IMAGE_TICKET, "nickname-image-ticket", "NICKNAME_IMAGE_TICKET_REQUIRED"),
    /** 아바타 올리기 신청({@code avatar_upload_requests}) */
    AVATAR_UPLOAD(PointShopItemKind.AVATAR_UPLOAD_TICKET, "avatar-upload-ticket", "AVATAR_UPLOAD_TICKET_REQUIRED");

    private final PointShopItemKind kind;
    private final String seedCode;
    private final String requiredCode;

    UploadTicketUse(PointShopItemKind kind, String seedCode, String requiredCode) {
        this.kind = kind;
        this.seedCode = seedCode;
        this.requiredCode = requiredCode;
    }

    /** 그 변경권 품목 종류 */
    public PointShopItemKind kind() {
        return kind;
    }

    /** V157 이 넣은 품목 코드(파는 품목이 없을 때 값의 출처) */
    public String seedCode() {
        return seedCode;
    }

    /** 변경권이 없을 때의 오류 코드(409) */
    public String requiredCode() {
        return requiredCode;
    }
}
