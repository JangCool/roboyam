package com.community.emoticon.controller;

import com.community.emoticon.dto.request.EmoticonPayoutAccountRequest;
import com.community.emoticon.dto.request.EmoticonStudioSaveRequest;
import com.community.emoticon.dto.response.EmoticonSettlementView;
import com.community.emoticon.dto.response.EmoticonStudioView;
import com.community.emoticon.service.EmoticonContractService;
import com.community.emoticon.service.EmoticonPayoutService;
import com.community.emoticon.service.EmoticonSettlementService;
import com.community.emoticon.service.EmoticonStudioService;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로보티콘 스튜디오(작가 — user-web {@code useEmoticonStudioApi} 의 S1 부분). 로그인 필요(401), {@code no-store}. 쓰기는 정상 회원 · 이메일 인증(403)·CSRF.
 * <ul>
 *   <li>{@code GET} 요약(내 팩 · 받은 수 · 보상 · 최근 보상 · 심사 대기 수 · 이번 달 판매 요약 = 이번 달 정산 줄의 예정 값) · {@code GET /packs/{id}} 편집 모양(남의 팩 404)</li>
 *   <li>{@code POST /images} multipart {@code file} · {@code kind=EMOTICON|COVER} → 201 올린 그림 · {@code 400 fields.file}(이유 코드)</li>
 *   <li>{@code POST /packs}(새 팩 임시 저장, 201) · {@code PUT /packs/{id}} 임시 저장 · {@code POST /packs/submit}(새 팩 심사 요청, 201) · {@code POST /packs/{id}/submit}</li>
 *   <li>{@code GET /contract} 로보티콘 상품화 계약(지금 판 · 제목 · 요약 · 평문 전문 · 내 동의 — 게시된 계약이 없으면 {@code data} 없음, S2). 동의는 유료 희망 심사 요청의 {@code agreeContractVersion}</li>
 *   <li>{@code GET·PUT /payout-account} 정산 정보(가린 값만 — S2). 저장은 작가 자격 · 본인인증 {@code 403 IDENTITY_REQUIRED} · 인증 이름 없음 {@code 409 EMOTICON_IDENTITY_NAME_MISSING} · {@code 400 fields.*}</li>
 * </ul>
 * <ul>
 *   <li>{@code GET /settlements} 내 월 정산 내역(S4 — {@code { items }} 최근 달 먼저, 예정 달은 조회 때 계산)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/me/emoticon-studio")
public class EmoticonStudioController {

    private final EmoticonStudioService studio;
    private final EmoticonContractService contracts;
    private final EmoticonPayoutService payouts;
    private final Messages messages;
    private final EmoticonSettlementService settlements;

    public EmoticonStudioController(EmoticonStudioService studio, EmoticonContractService contracts, EmoticonPayoutService payouts, Messages messages,
                                    EmoticonSettlementService settlements) {
        this.settlements = settlements;
        this.studio = studio;
        this.contracts = contracts;
        this.payouts = payouts;
        this.messages = messages;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<EmoticonStudioView.Overview>> overview(Authentication authentication) {
        return EmoticonController.noStore(studio.overview(EmoticonController.userId(authentication)));
    }

    @GetMapping("/packs/{id}")
    public ResponseEntity<ApiResponse<EmoticonStudioView.Pack>> pack(@PathVariable String id, Authentication authentication) {
        return EmoticonController.noStore(studio.pack(EmoticonController.userId(authentication), id));
    }

    @PostMapping("/images")
    public ResponseEntity<ApiResponse<EmoticonStudioView.Image>> upload(@RequestParam(value = "file", required = false) MultipartFile file,
                                                                       @RequestParam(value = "kind", required = false) String kind, Authentication authentication) {
        return created(studio.upload(EmoticonController.userId(authentication), file, kind));
    }

    @PostMapping("/packs")
    public ResponseEntity<ApiResponse<EmoticonStudioView.Pack>> create(@RequestBody(required = false) EmoticonStudioSaveRequest body, Authentication authentication) {
        return created(studio.save(EmoticonController.userId(authentication), null, body));
    }

    @PutMapping("/packs/{id}")
    public ResponseEntity<ApiResponse<EmoticonStudioView.Pack>> save(@PathVariable String id, @RequestBody(required = false) EmoticonStudioSaveRequest body,
                                                                    Authentication authentication) {
        return EmoticonController.noStore(studio.save(EmoticonController.userId(authentication), id, body));
    }

    @PostMapping("/packs/submit")
    public ResponseEntity<ApiResponse<EmoticonStudioView.Pack>> submitNew(@RequestBody(required = false) EmoticonStudioSaveRequest body, Authentication authentication) {
        return created(studio.submit(EmoticonController.userId(authentication), null, body));
    }

    @PostMapping("/packs/{id}/submit")
    public ResponseEntity<ApiResponse<EmoticonStudioView.Pack>> submit(@PathVariable String id, @RequestBody(required = false) EmoticonStudioSaveRequest body,
                                                                      Authentication authentication) {
        return EmoticonController.noStore(studio.submit(EmoticonController.userId(authentication), id, body));
    }

    /** 상품화 계약 — 본문에 제목이 없으면 기본 문구(요청 언어) */
    @GetMapping("/contract")
    public ResponseEntity<ApiResponse<EmoticonStudioView.Contract>> contract(Authentication authentication) {
        EmoticonStudioView.Contract c = contracts.creatorView(EmoticonController.userId(authentication));
        if (c != null && c.title() == null) {
            c = new EmoticonStudioView.Contract(c.version(), c.effectiveDate(), messages.get("emoticon.contract.title"), c.summary(), c.body(), c.agreements());
        }
        return EmoticonController.noStore(c);
    }

    @GetMapping("/payout-account")
    public ResponseEntity<ApiResponse<EmoticonStudioView.PayoutAccount>> payoutAccount(Authentication authentication) {
        return EmoticonController.noStore(payouts.view(EmoticonController.userId(authentication)));
    }

    @PutMapping("/payout-account")
    public ResponseEntity<ApiResponse<EmoticonStudioView.PayoutAccount>> savePayoutAccount(@RequestBody(required = false) EmoticonPayoutAccountRequest body,
                                                                                         Authentication authentication) {
        return EmoticonController.noStore(payouts.save(EmoticonController.userId(authentication), body));
    }

    /** 내 정산 내역(S4) — 처음 달 ~ 이번 달, 최근 달 먼저(예정 달 포함). 로그인만 */
    @GetMapping("/settlements")
    public ResponseEntity<ApiResponse<EmoticonSettlementView.StudioList>> settlements(Authentication authentication) {
        return EmoticonController.noStore(settlements.studio(EmoticonController.userId(authentication)));
    }

    private static <T> ResponseEntity<ApiResponse<T>> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
