package com.community.board.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 기본 설정 하위 화면 「보안」 — 관리자 API JSON 모양 그대로(admin-web {@code BoardSecuritySettings}). 금지어는 저장만 하고 적용 지점은 후속이다 */
public record BoardSecurityValues(Integer viewCountDedupHours, Boolean blockedWordsEnabled, String blockedWordsAction, List<String> blockedWords,
                                  Integer newMemberLinkLimit, Integer newMemberImageLimit, Integer duplicatePostWindowMinutes, Boolean externalLinkNofollow,
                                  Boolean externalLinkWarn, Boolean guestCaptchaRequired, Integer guestNameMin, Integer guestNameMax, Integer guestPasswordMin,
                                  Integer guestLinkLimit, Boolean guestImageUploadAllowed) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("viewCountDedupHours", viewCountDedupHours);
        map.put("blockedWordsEnabled", blockedWordsEnabled);
        map.put("blockedWordsAction", blockedWordsAction);
        map.put("blockedWords", blockedWords);
        map.put("newMemberLinkLimit", newMemberLinkLimit);
        map.put("newMemberImageLimit", newMemberImageLimit);
        map.put("duplicatePostWindowMinutes", duplicatePostWindowMinutes);
        map.put("externalLinkNofollow", externalLinkNofollow);
        map.put("externalLinkWarn", externalLinkWarn);
        map.put("guestCaptchaRequired", guestCaptchaRequired);
        map.put("guestNameMin", guestNameMin);
        map.put("guestNameMax", guestNameMax);
        map.put("guestPasswordMin", guestPasswordMin);
        map.put("guestLinkLimit", guestLinkLimit);
        map.put("guestImageUploadAllowed", guestImageUploadAllowed);
        return map;
    }
}
