package com.community.rewardevent.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 409 — 보상 이벤트의 상태가 그 일을 받지 않는다(docs/features/event-admin-contract.md). 코드는 까닭 그대로(문구 키 {@code error.<코드>}):
 * <ul>
 *   <li>{@link #STATE} 상태가 맞지 않다 · {@link #PERIOD_PASSED} 끝 시각이 지나 시작할 수 없다 · {@link #HAS_GRANTS} 지급이 있어 취소할 수 없다(회수를 쓴다)</li>
 *   <li>{@link #LOCKED_FIELD} 고치기 범위 밖의 칸이 바뀌었다({@code fields} = 그 칸 경로)</li>
 *   <li>{@link #TIER_FULL} 등수 자리가 모자라다 · {@link #DRAW_NOT_ENOUGH} 추첨 후보가 모자라다 · {@link #NO_COMMENT_SOURCE} 댓글 추첨 설정이 없다</li>
 *   <li>{@link #RECIPIENT_GRANTED} 이미 지급한 받는 사람은 뺄 수 없다 · {@link #REVOKE_NOTHING} 회수할 받는 사람이 없다 · {@link #DELIVERY_STATE} 전달 대기 중인 보상이 아니다</li>
 * </ul>
 */
public class RewardEventConflictException extends CommonException {

    public static final String STATE = "REWARD_EVENT_STATE";
    public static final String PERIOD_PASSED = "REWARD_EVENT_PERIOD_PASSED";
    public static final String HAS_GRANTS = "REWARD_EVENT_HAS_GRANTS";
    public static final String LOCKED_FIELD = "REWARD_EVENT_LOCKED_FIELD";
    public static final String TIER_FULL = "REWARD_EVENT_TIER_FULL";
    public static final String DRAW_NOT_ENOUGH = "REWARD_DRAW_NOT_ENOUGH";
    public static final String NO_COMMENT_SOURCE = "REWARD_EVENT_NO_COMMENT_SOURCE";
    public static final String RECIPIENT_GRANTED = "REWARD_RECIPIENT_GRANTED";
    public static final String REVOKE_NOTHING = "REWARD_REVOKE_NOTHING";
    public static final String DELIVERY_STATE = "REWARD_DELIVERY_STATE";

    public RewardEventConflictException(String code) {
        super(HttpStatus.CONFLICT, code, new MessageRef("error." + code));
    }

    public RewardEventConflictException(String code, MessageRef message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    /** 고치기 범위 밖의 칸 — {@code fields} 에 그 칸 경로 하나 */
    public static RewardEventConflictException lockedField(String path) {
        return new RewardEventConflictException(path, true);
    }

    private RewardEventConflictException(String lockedPath, boolean locked) {
        super(HttpStatus.CONFLICT, LOCKED_FIELD, new MessageRef("error." + LOCKED_FIELD), Map.of(lockedPath, new MessageRef("rewardEvent.locked")), null);
    }
}
