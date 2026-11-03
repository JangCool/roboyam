package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardGroupBuyQnaView;
import com.community.dashboard.repository.master.DashboardCommerceStore;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import org.springframework.stereotype.Component;

/**
 * 답변 대기 공구 Q&A — 답변 대기 수 + 오래된 차례 limit 건(관리자 Q&A 「답변 대기」 탭과 같은 조건: OPEN · 숨기지 않음 · 지우지 않음).
 *
 * <p>비밀글(작성자가 고르거나 개인정보 모양이라 서버가 바꾼 글)은 본문 대신 서버 문구({@code dashboard.gadget.qna.secret})를 싣는다 — 비밀글 본문 열람은 개인정보 열람 감사
 * ({@code GROUPBUY_QNA_VIEW})가 남는 Q&A 화면에서만 한다. 공개 질문 본문은 회원 화면에도 보이는 글이라 앞부분({@value #PREVIEW_MAX}자, 공백은 한 칸으로)만 싣고 감사를 남기지 않는다.
 */
@Component
public class GroupBuyQnaGadget implements DashboardGadgetProvider {

    static final int PREVIEW_MAX = 100;

    private final DashboardCommerceStore store;
    private final Messages messages;

    public GroupBuyQnaGadget(DashboardCommerceStore store, Messages messages) {
        this.store = store;
        this.messages = messages;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.GROUPBUY_QNA;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        long pending = store.openQuestionCount();
        String secret = messages.get(new MessageRef("dashboard.gadget.qna.secret"));
        return new DashboardGroupBuyQnaView(pending, store.oldestOpenQuestions(query.limit()).stream()
                .map(q -> new DashboardGroupBuyQnaView.Oldest(Long.toString(q.id()), q.groupBuyTitle(), q.secret() ? secret : preview(q.body()), q.askedAt())).toList());
    }

    /** 공개 질문 본문 앞부분 — 줄바꿈·공백 묶음을 한 칸으로, 넘치면 잘라 「…」 */
    static String preview(String body) {
        String flat = body == null ? "" : body.strip().replaceAll("\\s+", " ");
        if (flat.codePointCount(0, flat.length()) <= PREVIEW_MAX) {
            return flat;
        }
        return flat.substring(0, flat.offsetByCodePoints(0, PREVIEW_MAX)) + "…";
    }
}
