package com.community.viewsetting.service;

import com.community.viewsetting.dto.request.ViewSettingsRequest;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 보기 설정 세 칸의 허용 값·기본값과 검사 — user-web {@code UtilViewSettings}(THEMES·TEXT_SCALES·COMMENT_ORDERS·DEFAULTS)·V73 CHECK 와 같다.
 * 위반은 칸마다 한꺼번에 돌려준다({@code fields.theme|textScale|commentOrder}). 빠진 칸·타입이 틀린 칸도 같은 칸 오류다.
 */
public final class ViewSettingsPolicy {

    static final List<String> THEMES = List.of("AUTO", "LIGHT", "DARK");
    static final List<Integer> TEXT_SCALES = List.of(90, 100, 110, 125);
    static final List<String> COMMENT_ORDERS = List.of("OLDEST", "LATEST");

    static final String DEFAULT_THEME = "AUTO";
    static final int DEFAULT_TEXT_SCALE = 100;
    static final String DEFAULT_COMMENT_ORDER = "OLDEST";

    /** 검사를 마친 세 칸 */
    public record Checked(String theme, int textScale, String commentOrder) {
    }

    private ViewSettingsPolicy() {
    }

    /**
     * @param request 요청 본문. null 이면 세 칸 모두 오류
     * @throws ValidationException 하나라도 어긋났을 때(칸마다 문구)
     */
    public static Checked check(ViewSettingsRequest request) {
        Object theme = request == null ? null : request.theme();
        Object textScale = request == null ? null : request.textScale();
        Object commentOrder = request == null ? null : request.commentOrder();

        Map<String, MessageRef> errors = new LinkedHashMap<>();
        if (!(theme instanceof String t) || !THEMES.contains(t)) {
            errors.put("theme", new MessageRef("viewSettings.theme.invalid"));
        }
        // JSON 정수만 — Jackson 은 정수를 Integer(큰 수는 Long·BigInteger), 소수를 Double 로 읽는다
        if (!(textScale instanceof Integer s) || !TEXT_SCALES.contains(s)) {
            errors.put("textScale", new MessageRef("viewSettings.textScale.invalid"));
        }
        if (!(commentOrder instanceof String o) || !COMMENT_ORDERS.contains(o)) {
            errors.put("commentOrder", new MessageRef("viewSettings.commentOrder.invalid"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Checked((String) theme, (Integer) textScale, (String) commentOrder);
    }
}
