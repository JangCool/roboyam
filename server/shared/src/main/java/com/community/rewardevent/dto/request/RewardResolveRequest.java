package com.community.rewardevent.dto.request;

import java.util.List;

/** 붙여 넣은 줄(회원 번호·닉네임·이메일) — 같은 줄은 한 번, 500줄까지 */
public record RewardResolveRequest(List<String> lines) {
}
