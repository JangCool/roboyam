package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardNotificationHealthView;
import com.community.dashboard.repository.master.DashboardAnalyticsStore;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 알림 상태(notification.read · 수집 항목 {@code notificationSends}) — 오늘의 채널별 발송 합({@code notification_send_daily} PK) 한 번 + 최근 실패 3건(실패 시각 인덱스) 한 번.
 * 실패 문구는 사유 코드의 요청 언어 문구(알림 도구 연결 시험과 같은 {@code notification.channel.test.<코드>}) — 캐시 키에 언어가 있다.
 */
@Component
public class NotificationHealthGadget implements DashboardGadgetProvider {

    static final int RECENT_FAILURES = 3;

    private final DashboardAnalyticsStore store;
    private final Messages messages;

    public NotificationHealthGadget(DashboardAnalyticsStore store, Messages messages) {
        this.store = store;
        this.messages = messages;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.NOTIFICATION_HEALTH;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        List<DashboardNotificationHealthView.Channel> channels = store.notificationDay(query.today()).stream()
                .map(c -> new DashboardNotificationHealthView.Channel(c.channel(), c.sent(), c.failed())).toList();
        long sent = channels.stream().mapToLong(DashboardNotificationHealthView.Channel::sent).sum();
        List<DashboardNotificationHealthView.RecentFailure> failures = store.recentFailures(RECENT_FAILURES).stream()
                .map(f -> new DashboardNotificationHealthView.RecentFailure(Long.toString(f.id()), f.channel(), summary(f.reasonCode()), f.failedAt())).toList();
        return new DashboardNotificationHealthView(sent, channels, failures);
    }

    private String summary(String code) {
        String text = messages.find(new MessageRef("notification.channel.test." + code));
        return text != null ? text : messages.get(new MessageRef("notification.channel.test.ERROR"));
    }
}
