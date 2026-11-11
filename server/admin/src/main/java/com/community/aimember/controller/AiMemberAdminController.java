package com.community.aimember.controller;

import com.community.aimember.dto.AiMemberRequests;
import com.community.aimember.dto.AiMemberViews;
import com.community.aimember.service.AiDraftAdminService;
import com.community.aimember.service.AiMemberAdminService;
import com.community.aimember.service.AiMemberSettingsAdminService;
import com.community.aimember.service.AiModelAdminService;
import com.community.aimember.service.AiPromptAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
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
 * AI 회원 관리 API(관리자 「AI 회원」 다섯 화면 — docs/features/ai-members-contract.md). 읽기 {@code ai-member.read}, 쓰기 {@code ai-member.manage},
 * 제공사 키 저장 {@code ai-member.secret}. 응답은 {@code Cache-Control: no-store}. 입력 검사는 서비스가 한다.
 */
@RestController
@RequestMapping("/api/admin/ai-members")
public class AiMemberAdminController {

    private final AiMemberAdminService accounts;
    private final AiDraftAdminService drafts;
    private final AiMemberSettingsAdminService settings;
    private final AiModelAdminService models;
    private final AiPromptAdminService prompts;

    public AiMemberAdminController(AiMemberAdminService accounts, AiDraftAdminService drafts, AiMemberSettingsAdminService settings, AiModelAdminService models,
                                   AiPromptAdminService prompts) {
        this.models = models;
        this.prompts = prompts;
        this.accounts = accounts;
        this.drafts = drafts;
        this.settings = settings;
    }

    // ── 계정 ──

    @RequirePermission(Permission.AI_MEMBER_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<AiMemberViews.ListView>> list() {
        return noStore(accounts.list());
    }

    @RequirePermission(Permission.AI_MEMBER_READ)
    @GetMapping("/options")
    public ResponseEntity<ApiResponse<AiMemberViews.FormOptions>> options() {
        return noStore(accounts.options());
    }

    /** 새 AI 회원(로그인할 수 없는 회원 계정을 서버가 만든다) — 201 · 400 {@code VALIDATION}(nickname · 고치기와 같은 칸) · 409 {@code NICKNAME_TAKEN} */
    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<AiMemberViews.Detail>> create(@RequestBody(required = false) AiMemberRequests.Create body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(accounts.create(actor, body)));
    }

    /** 상세 — 404 {@code AI_MEMBER_NOT_FOUND} */
    @RequirePermission(Permission.AI_MEMBER_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AiMemberViews.Detail>> detail(@PathVariable String id) {
        return noStore(accounts.detail(id));
    }

    /** 고치기 — 404 {@code AI_MEMBER_NOT_FOUND} · 400 {@code VALIDATION}(model · persona.* · bio · windows.* · voice · boards · groups · relation · avatarId) */
    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AiMemberViews.Detail>> save(@PathVariable String id, @RequestBody(required = false) AiMemberRequests.Save body, AdminActor actor) {
        return noStore(accounts.save(actor, id, body));
    }

    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PutMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<AiMemberViews.Row>> enabled(@PathVariable String id, @RequestBody(required = false) AiMemberRequests.Enabled body,
                                                                  AdminActor actor) {
        return noStore(accounts.setEnabled(actor, id, body == null ? null : body.enabled()));
    }

    /** AI 전용 소모임 지정·해제(목업에 없던 API — 소모임을 AI 전용으로 만드는 곳) · 404 {@code GROUP_NOT_FOUND} */
    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PutMapping("/groups/{groupId}")
    public ResponseEntity<ApiResponse<AiMemberViews.GroupAiOnly>> groupAiOnly(@PathVariable String groupId,
                                                                              @RequestBody(required = false) AiMemberRequests.GroupAiOnly body, AdminActor actor) {
        return noStore(accounts.setGroupAiOnly(actor, groupId, body == null ? null : body.aiOnly()));
    }

    // ── 대기함 ──

    @RequirePermission(Permission.AI_MEMBER_READ)
    @GetMapping("/drafts")
    public ResponseEntity<ApiResponse<AiMemberViews.DraftList>> drafts(@RequestParam(required = false) String kind,
                                                                       @RequestParam(required = false) String aiMemberId) {
        return noStore(drafts.drafts(kind, aiMemberId));
    }

    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PostMapping("/drafts/publish")
    public ResponseEntity<ApiResponse<AiMemberViews.BatchResult>> publish(@RequestBody(required = false) AiMemberRequests.Ids body, AdminActor actor) {
        return noStore(drafts.publish(actor, body == null ? null : body.ids()));
    }

    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PostMapping("/drafts/discard")
    public ResponseEntity<ApiResponse<AiMemberViews.BatchResult>> discard(@RequestBody(required = false) AiMemberRequests.Ids body, AdminActor actor) {
        return noStore(drafts.discard(actor, body == null ? null : body.ids()));
    }

    /** 수정 후 게시 — 404 {@code AI_DRAFT_NOT_FOUND} · 400 {@code VALIDATION}(title · body) · 400 {@code BLOCKED_WORD} */
    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PostMapping("/drafts/{id}/publish")
    public ResponseEntity<ApiResponse<AiMemberViews.BatchResult>> publishEdited(@PathVariable String id, @RequestBody(required = false) AiMemberRequests.Edit body,
                                                                                AdminActor actor) {
        return noStore(drafts.publishEdited(actor, id, body));
    }

    // ── 설정 ──

    @RequirePermission(Permission.AI_MEMBER_READ)
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<AiMemberViews.Settings>> settings() {
        return noStore(settings.view());
    }

    /** 400 {@code VALIDATION}(instantScope · instantAccountIds · dailyCallLimit · quietDays · busyIntervalDays · dailyPostMax) */
    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<AiMemberViews.Settings>> saveSettings(@RequestBody(required = false) AiMemberRequests.SettingsSave body, AdminActor actor) {
        return noStore(settings.save(actor, body));
    }

    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PutMapping("/settings/stopped")
    public ResponseEntity<ApiResponse<AiMemberViews.Settings>> stopped(@RequestBody(required = false) AiMemberRequests.Stopped body, AdminActor actor) {
        return noStore(settings.stop(actor, body == null ? null : body.stopped()));
    }

    /** 제공사 키 — 400 {@code fields.key} · 404(모르는 제공사) · 503 {@code CRYPTO_KEY_MISSING}. 값은 다시 내려 주지 않는다 */
    @RequirePermission(Permission.AI_MEMBER_SECRET)
    @PutMapping("/settings/keys/{provider}")
    public ResponseEntity<ApiResponse<AiMemberViews.Settings>> key(@PathVariable String provider, @RequestBody(required = false) AiMemberRequests.Key body,
                                                                   AdminActor actor) {
        return noStore(settings.setKey(actor, provider, body));
    }

    // ── 모델 목록(표 ai_models — V189) ──

    @RequirePermission(Permission.AI_MEMBER_READ)
    @GetMapping("/models")
    public ResponseEntity<ApiResponse<AiMemberViews.ModelList>> models() {
        return noStore(models.list());
    }

    /** 모델 직접 추가 — 201 · 400 {@code VALIDATION}(provider · modelId(형식·중복) · label · priceInput · priceOutput) */
    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PostMapping("/models")
    public ResponseEntity<ApiResponse<AiMemberViews.ModelList>> createModel(@RequestBody(required = false) AiMemberRequests.ModelCreate body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(models.create(actor, body)));
    }

    /** 사용 켜기·끄기 — 404 {@code AI_MODEL_NOT_FOUND} · 409 {@code AI_MODEL_DEFAULT_LOCKED}(기본 모델 끄기) */
    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PutMapping("/models/{id}/enabled")
    public ResponseEntity<ApiResponse<AiMemberViews.ModelList>> modelEnabled(@PathVariable String id, @RequestBody(required = false) AiMemberRequests.ModelEnabled body,
                                                                             AdminActor actor) {
        return noStore(models.setEnabled(actor, id, body == null ? null : body.enabled()));
    }

    /** 기본 정하기(제공사당 하나 — 꺼진 모델이면 함께 켠다) — 404 {@code AI_MODEL_NOT_FOUND} */
    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PutMapping("/models/{id}/default")
    public ResponseEntity<ApiResponse<AiMemberViews.ModelList>> modelDefault(@PathVariable String id, AdminActor actor) {
        return noStore(models.setDefault(actor, id));
    }

    /** 지우기 — 404 {@code AI_MODEL_NOT_FOUND} · 409 {@code AI_MODEL_DEFAULT_LOCKED} · 409 {@code AI_MODEL_IN_USE}(쓰는 계정이 있다 — 대신 끄기) */
    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @DeleteMapping("/models/{id}")
    public ResponseEntity<ApiResponse<AiMemberViews.ModelList>> deleteModel(@PathVariable String id, AdminActor actor) {
        return noStore(models.delete(actor, id));
    }

    // ── 프롬프트(표 ai_prompt_settings — V190) ──

    @RequirePermission(Permission.AI_MEMBER_READ)
    @GetMapping("/prompts")
    public ResponseEntity<ApiResponse<AiMemberViews.PromptSettings>> prompts() {
        return noStore(prompts.view());
    }

    /** 저장 — 400 {@code VALIDATION}(common · post · news · comment · reply · dog · cat · moodChance · moods · moods[i].name · moods[i].prompt · newsRatio · newsKeyword · newsDays · paragraphSentences) */
    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PutMapping("/prompts")
    public ResponseEntity<ApiResponse<AiMemberViews.PromptSettings>> savePrompts(@RequestBody(required = false) AiMemberRequests.PromptSave body, AdminActor actor) {
        return noStore(prompts.save(actor, body));
    }

    /** 미리 보기(저장 전 화면 값 · 제공사 호출 없음 · 읽기 권한) — 404 {@code AI_MEMBER_NOT_FOUND} · 400 {@code VALIDATION}(aiMemberId · kind · mood · settings.*) */
    @RequirePermission(Permission.AI_MEMBER_READ)
    @PostMapping("/prompts/preview")
    public ResponseEntity<ApiResponse<AiMemberViews.PromptPreview>> previewPrompt(@RequestBody(required = false) AiMemberRequests.PromptPreview body) {
        return noStore(prompts.preview(body));
    }

    // ── 기록 ──

    @RequirePermission(Permission.AI_MEMBER_READ)
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<AiMemberViews.LogPage>> logs(@RequestParam(required = false) String aiMemberId, @RequestParam(required = false) String result,
                                                                   @RequestParam(required = false) String kind, @RequestParam(required = false) String page,
                                                                   @RequestParam(required = false) String size) {
        return noStore(drafts.logs(aiMemberId, result, kind, page, size));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
