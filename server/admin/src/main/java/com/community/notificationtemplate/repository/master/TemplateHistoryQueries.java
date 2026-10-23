package com.community.notificationtemplate.repository.master;

import com.community.notificationtemplate.entity.NotificationChatTemplateHistoryEntity;
import com.community.notificationtemplate.entity.NotificationMailTemplateHistoryEntity;
import com.community.notificationtemplate.entity.NotificationMessageHistoryEntity;
import com.community.notificationtemplate.entity.QNotificationChatTemplateHistoryEntity;
import com.community.notificationtemplate.entity.QNotificationMailTemplateHistoryEntity;
import com.community.notificationtemplate.entity.QNotificationMessageHistoryEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 문구 · 메일 템플릿 · Slack/Mattermost 템플릿 변경 이력 조회 — 한 대상의 줄, 최신순(시각, 같으면 id 큰 쪽 먼저), 페이징은 DB 에서 */
@Repository
public class TemplateHistoryQueries extends RoboyamRepositoryQDSL {

    private static final QNotificationMessageHistoryEntity MESSAGE = QNotificationMessageHistoryEntity.notificationMessageHistoryEntity;
    private static final QNotificationMailTemplateHistoryEntity MAIL = QNotificationMailTemplateHistoryEntity.notificationMailTemplateHistoryEntity;
    private static final QNotificationChatTemplateHistoryEntity CHAT = QNotificationChatTemplateHistoryEntity.notificationChatTemplateHistoryEntity;

    public TemplateHistoryQueries(JPAQueryFactory query) {
        super(query);
    }

    public List<NotificationMessageHistoryEntity> messagePage(long messageId, int page, int size) {
        return query.selectFrom(MESSAGE).where(MESSAGE.messageId.eq(messageId)).orderBy(MESSAGE.changedAt.desc(), MESSAGE.id.desc())
                .offset((long) page * size).limit(size).fetch();
    }

    public long messageCount(long messageId) {
        Long total = query.select(MESSAGE.count()).from(MESSAGE).where(MESSAGE.messageId.eq(messageId)).fetchOne();
        return total == null ? 0 : total;
    }

    public List<NotificationMailTemplateHistoryEntity> mailPage(long templateId, int page, int size) {
        return query.selectFrom(MAIL).where(MAIL.templateId.eq(templateId)).orderBy(MAIL.changedAt.desc(), MAIL.id.desc())
                .offset((long) page * size).limit(size).fetch();
    }

    public long mailCount(long templateId) {
        Long total = query.select(MAIL.count()).from(MAIL).where(MAIL.templateId.eq(templateId)).fetchOne();
        return total == null ? 0 : total;
    }

    public List<NotificationChatTemplateHistoryEntity> chatPage(long templateId, int page, int size) {
        return query.selectFrom(CHAT).where(CHAT.templateId.eq(templateId)).orderBy(CHAT.changedAt.desc(), CHAT.id.desc())
                .offset((long) page * size).limit(size).fetch();
    }

    public long chatCount(long templateId) {
        Long total = query.select(CHAT.count()).from(CHAT).where(CHAT.templateId.eq(templateId)).fetchOne();
        return total == null ? 0 : total;
    }
}
