package com.community.notificationtemplate.catalog;

import java.util.List;
import java.util.Optional;

/**
 * 대상이 나가는 채널 하나 — 그 채널에서 쓸 수 있는 변수와 조건. 기본 문구가 있는가는 채널이 정한다(카카오톡만 없다 — 알림톡 템플릿을 카카오에 먼저 등록해야 한다).
 */
public record MessageChannelDef(MessageChannel channel, List<MessageVariable> variables, List<MessageCondition> conditions) {

    public MessageChannelDef {
        variables = List.copyOf(variables);
        conditions = List.copyOf(conditions);
    }

    /** 기본(builtin) 문구가 있는가 */
    public boolean hasBuiltin() {
        return channel != MessageChannel.KAKAO;
    }

    public Optional<MessageVariable> variable(String name) {
        return variables.stream().filter(variable -> variable.name().equals(name)).findFirst();
    }

    public Optional<MessageCondition> condition(String key) {
        return conditions.stream().filter(condition -> condition.key().equals(key)).findFirst();
    }
}
