package com.community.notificationtemplate.controller;

import com.community.mail.dto.response.MailTestResultView;
import com.community.notificationtemplate.dto.request.ChatMessageTestRequest;
import com.community.notificationtemplate.dto.request.MailMessageTestRequest;
import com.community.notificationtemplate.dto.request.MailPreviewRequest;
import com.community.notificationtemplate.dto.request.MessageSaveRequest;
import com.community.notificationtemplate.dto.request.MessageTargetSaveRequest;
import com.community.notificationtemplate.dto.response.ChatMessageTestResultView;
import com.community.notificationtemplate.dto.response.MailPreviewView;
import com.community.notificationtemplate.dto.response.MessageListItemView;
import com.community.notificationtemplate.dto.response.MessageTargetDetailView;
import com.community.notificationtemplate.dto.response.MessageTargetView;
import com.community.notificationtemplate.dto.response.MessageView;
import com.community.notificationtemplate.dto.response.TemplateHistoryPage;
import com.community.notificationtemplate.service.MessagePreviewService;
import com.community.notificationtemplate.service.NotificationMessageAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 문구 API(docs/features/message-template-contract.md 「개정 4」 · 「서버 구현 결과」 — admin-web {@code useNotificationMessageApi} 의 주소·모양 그대로).
 * 읽기·미리보기 {@code template.read}, 대상 저장·문구 만들기·고치기·지우기·시험 {@code template.manage}.
 */
@RestController
@RequestMapping("/api/admin/notification-messages")
public class NotificationMessageController {

    private final NotificationMessageAdminService service;
    private final MessagePreviewService preview;

    public NotificationMessageController(NotificationMessageAdminService service, MessagePreviewService preview) {
        this.service = service;
        this.preview = preview;
    }

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/targets")
    public ApiResponse<List<MessageTargetView>> targets(@RequestParam(required = false) String type, @RequestParam(required = false) String audience) {
        return ApiResponse.success(service.targets(type, audience));
    }

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/targets/{type}/{code}")
    public ApiResponse<MessageTargetDetailView> target(@PathVariable String type, @PathVariable String code) {
        return ApiResponse.success(service.target(type, code));
    }

    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @PutMapping("/targets/{type}/{code}")
    public ApiResponse<MessageTargetDetailView> saveTarget(@PathVariable String type, @PathVariable String code, @RequestBody MessageTargetSaveRequest body,
                                                           AdminActor actor) {
        return ApiResponse.success(service.saveTarget(actor, type, code, body));
    }

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/messages")
    public ApiResponse<List<MessageListItemView>> messages() {
        return ApiResponse.success(service.messages());
    }

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/messages/{id}")
    public ApiResponse<MessageView> message(@PathVariable String id) {
        return ApiResponse.success(service.message(id));
    }

    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<MessageView>> create(@RequestBody MessageSaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(actor, body)));
    }

    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @PutMapping("/messages/{id}")
    public ApiResponse<MessageView> update(@PathVariable String id, @RequestBody MessageSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.update(actor, id, body));
    }

    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, AdminActor actor) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/messages/{id}/history")
    public ApiResponse<TemplateHistoryPage> history(@PathVariable String id, @RequestParam(required = false) String page,
                                                    @RequestParam(required = false) String size) {
        return ApiResponse.success(service.history(id, page, size));
    }

    /** 이메일 미리보기 — 견본 값 + 메일 템플릿(저장된 것 또는 저장 전 값) */
    @RequirePermission(Permission.TEMPLATE_READ)
    @PostMapping("/preview")
    public ApiResponse<MailPreviewView> preview(@RequestBody MailPreviewRequest body) {
        return ApiResponse.success(preview.preview(body));
    }

    /** 이메일 시험 발송 — 실패도 200 {@code succeeded=false} */
    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @PostMapping("/test")
    public ApiResponse<MailTestResultView> test(@RequestBody MailMessageTestRequest body, AdminActor actor) {
        return ApiResponse.success(preview.test(actor, body));
    }

    /** Slack · Mattermost 시험 게시 — 실패도 200 {@code ok=false} */
    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @PostMapping("/chat-test")
    public ApiResponse<ChatMessageTestResultView> chatTest(@RequestBody ChatMessageTestRequest body, AdminActor actor) {
        return ApiResponse.success(preview.chatTest(actor, body));
    }
}
