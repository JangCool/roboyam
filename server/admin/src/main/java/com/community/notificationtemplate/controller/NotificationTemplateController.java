package com.community.notificationtemplate.controller;

import com.community.notificationtemplate.dto.request.ChatTemplateSaveRequest;
import com.community.notificationtemplate.dto.request.MailTemplateSaveRequest;
import com.community.notificationtemplate.dto.response.ChatTemplateListItemView;
import com.community.notificationtemplate.dto.response.ChatTemplateView;
import com.community.notificationtemplate.dto.response.KakaoTemplateListItemView;
import com.community.notificationtemplate.dto.response.KakaoTemplateView;
import com.community.notificationtemplate.dto.response.MailTemplateListItemView;
import com.community.notificationtemplate.dto.response.MailTemplateView;
import com.community.notificationtemplate.dto.response.TemplateHistoryPage;
import com.community.notificationtemplate.service.ChatTemplateAdminService;
import com.community.notificationtemplate.service.KakaoTemplateAdminService;
import com.community.notificationtemplate.service.MailTemplateAdminService;
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
 * 알림 템플릿(도구별 겉모양) API — 메일 · Slack · Mattermost(만들기·고치기·지우기·이력) · 카카오톡(준비 중 — 읽기만).
 * admin-web {@code useMailTemplateApi} · {@code useChatTemplateApi} · {@code useKakaoTemplateApi} 의 주소·모양 그대로. 읽기 {@code template.read}, 쓰기 {@code template.manage}.
 * Slack · Mattermost 는 주소 조각({@code slack} · {@code mattermost})이 종류다 — 고정 경로 {@code mail} · {@code kakao} 가 먼저 맞는다.
 */
@RestController
@RequestMapping("/api/admin/notification-templates")
public class NotificationTemplateController {

    private final MailTemplateAdminService mail;
    private final ChatTemplateAdminService chat;
    private final KakaoTemplateAdminService kakao;

    public NotificationTemplateController(MailTemplateAdminService mail, ChatTemplateAdminService chat, KakaoTemplateAdminService kakao) {
        this.mail = mail;
        this.chat = chat;
        this.kakao = kakao;
    }

    // ── 메일 ────────────────────────────────────────────────────────────────

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/mail")
    public ApiResponse<List<MailTemplateListItemView>> mailList(@RequestParam(required = false) String audience) {
        return ApiResponse.success(mail.list(audience));
    }

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/mail/{id}")
    public ApiResponse<MailTemplateView> mailGet(@PathVariable String id) {
        return ApiResponse.success(mail.get(id));
    }

    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @PostMapping("/mail")
    public ResponseEntity<ApiResponse<MailTemplateView>> mailCreate(@RequestBody MailTemplateSaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(mail.create(actor, body)));
    }

    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @PutMapping("/mail/{id}")
    public ApiResponse<MailTemplateView> mailUpdate(@PathVariable String id, @RequestBody MailTemplateSaveRequest body, AdminActor actor) {
        return ApiResponse.success(mail.update(actor, id, body));
    }

    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @DeleteMapping("/mail/{id}")
    public ResponseEntity<Void> mailDelete(@PathVariable String id, AdminActor actor) {
        mail.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/mail/{id}/history")
    public ApiResponse<TemplateHistoryPage> mailHistory(@PathVariable String id, @RequestParam(required = false) String page,
                                                        @RequestParam(required = false) String size) {
        return ApiResponse.success(mail.history(id, page, size));
    }

    // ── 카카오톡(준비 중 — 읽기만) ──────────────────────────────────────────

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/kakao")
    public ApiResponse<List<KakaoTemplateListItemView>> kakaoList() {
        return ApiResponse.success(kakao.list());
    }

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/kakao/{id}")
    public ApiResponse<KakaoTemplateView> kakaoGet(@PathVariable String id) {
        return ApiResponse.success(kakao.get(id));
    }

    // ── Slack · Mattermost ──────────────────────────────────────────────────

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/{tool:slack|mattermost}")
    public ApiResponse<List<ChatTemplateListItemView>> chatList(@PathVariable String tool) {
        return ApiResponse.success(chat.list(ChatTemplateAdminService.tool(tool)));
    }

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/{tool:slack|mattermost}/{id}")
    public ApiResponse<ChatTemplateView> chatGet(@PathVariable String tool, @PathVariable String id) {
        return ApiResponse.success(chat.get(ChatTemplateAdminService.tool(tool), id));
    }

    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @PostMapping("/{tool:slack|mattermost}")
    public ResponseEntity<ApiResponse<ChatTemplateView>> chatCreate(@PathVariable String tool, @RequestBody ChatTemplateSaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(chat.create(actor, ChatTemplateAdminService.tool(tool), body)));
    }

    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @PutMapping("/{tool:slack|mattermost}/{id}")
    public ApiResponse<ChatTemplateView> chatUpdate(@PathVariable String tool, @PathVariable String id, @RequestBody ChatTemplateSaveRequest body, AdminActor actor) {
        return ApiResponse.success(chat.update(actor, ChatTemplateAdminService.tool(tool), id, body));
    }

    @RequirePermission(Permission.TEMPLATE_MANAGE)
    @DeleteMapping("/{tool:slack|mattermost}/{id}")
    public ResponseEntity<Void> chatDelete(@PathVariable String tool, @PathVariable String id, AdminActor actor) {
        chat.delete(actor, ChatTemplateAdminService.tool(tool), id);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.TEMPLATE_READ)
    @GetMapping("/{tool:slack|mattermost}/{id}/history")
    public ApiResponse<TemplateHistoryPage> chatHistory(@PathVariable String tool, @PathVariable String id, @RequestParam(required = false) String page,
                                                        @RequestParam(required = false) String size) {
        return ApiResponse.success(chat.history(ChatTemplateAdminService.tool(tool), id, page, size));
    }
}
