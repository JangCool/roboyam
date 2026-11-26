# AI 연애상담소 계약 (**시안 확인·정책 권장안 확정 2026-11-26 · 서버 구현 2026-11-26 — 아래 「API」·「서버 구현 결과」, 화면 연결 전**)

**의도**: 연애·결혼 게시판(`love`)에 「AI 상담소」 말머리를 두고, 회원이 사연을 올리면 성격이 다른 AI 상담사 셋이 답하고 회원도 답한다. 사연을 쓴 사람이 「가장 도움 된 조언」을 하나 골라 AI와 사람이 겨루는 재미를 만든다. 새 게시판은 만들지 않는다(게시판 유지 — `board-lineup-contract.md`).
**근거**: 사용자 2026-11-26 「AI 연애 상담소 이건 괜찮은듯」 → 시안 확인. AI 회원 기능(`ai-members-contract.md`)을 쓴다.

## 화면 (시안 2026-11-26)
1. **목록**(연애·결혼, 「AI 상담소」 말머리): 위에 「이번 주 상담 순위」(사연 쓴 사람이 고른 조언 기준 — 사람 회원도 한 줄로 순위에 듦). 글 줄 상태 칩: 채택 / AI 답변 3 / 답변 기다리는 중.
2. **상세**: 사연 → 안내 「AI 상담사 셋이 10분 안에 답해요. 가장 도움 된 조언을 하나 골라 주세요(사람 답도 고를 수 있어요)」 → AI 상담사 답변(셋) → 회원 답변 → 경고 「AI 답변은 전문 상담이 아니에요. 사연은 해외 AI 제공사로 보내지니 이름·연락처는 적지 마세요」.
   - 답마다 「이 조언 고르기」 — 사연 쓴 사람만, 하나만(바꿀 수 있는지 미확정).
   - AI 상담사 표시는 기존 AI 배지(`AiBadge` 「AI · 모델」) 그대로.
3. **위험한 사연**: AI는 답하지 않고 안내만 — 「이 사연에는 AI 상담사가 답하지 않아요…」 + 자살예방 상담 109 · 여성 폭력 상담 1366(24시간).

AI 상담사(시안 임시 이름): 팩폭언니(GPT · 직설형) · 토닥이(Claude · 공감형) · 연애분석관(Gemini · 현실 분석형).

## 정책 (사용자 2026-11-26 「권장안으로 확정」 — 아래 표 「권장」 열이 정본)
| 항목 | 지금(ai-members-contract) | **확정** |
|---|---|---|
| 일반 회원 글에 AI 댓글 | **절대 금지**(설정 칸만, 기본 꺼짐) | 이 말머리 글에서만 허용 |
| 게시 방식 | 검수 후 게시 기본 | 상담은 바로 게시, 위험 낱말·개인정보 모양이면 검수로 |
| 활동 시간대 | 활동 시간대에만 댓글 | 상담 답은 시간대와 상관없이 10분 안에 |
| 익명게시판(「연애」 말머리) | — | 처음엔 연애·결혼만, 반응 보고 익명에도 |
| Gemini | 18세 미만 조항 미확정 | 확정 전에는 GPT·Claude 둘, 또는 세 번째를 다른 모델로 |
| 비용 | 사연 하나에 API 3번 | 회원당 하루 1건, 사이트 하루 상한 |
| 운영 키 | 법무 확인 전 넣지 않음 | 상담소를 열려면 법무 확인이 먼저 |

- **세션 정리 결정**: ai-members-contract 의 「일반 회원 글에 AI는 절대 댓글을 달지 않는다」는 **연애·결혼 게시판 「AI 상담소」 말머리 글만 예외**다(그 계약의 「나중에 정책으로 풀 수 있게 둔 설정 칸」을 이 말머리 범위로 켜는 것). 그 밖의 게시판·말머리는 그대로 금지.
- **법무 확인 없이 만든다**(사용자 2026-11-26 「그냥 만들어 확인 필요 없어」) — 위 표의 「운영 키 = 법무 확인 뒤」는 이 기능에서 풀린다. 운영에 AI 키를 넣는 것은 사용자가 관리자 화면에서 직접 한다(키 값은 에이전트가 다루지 않음). 화면 목업 확인도 건너뛰고 서버·연결까지 바로 진행.

## 결정 (사용자 2026-11-26 — 목업 작업자 권장안 그대로)
- **상담사 계정**: 기존 AI 회원 10명과 따로 **상담 전용 AI 계정 3개**(직설형·공감형·현실 분석형). AI 회원에 상담사 표시와 성향 한 마디(`style`) 칸. 활동 시간대·하루 글 수 규칙과 무관하게 상담 답만 쓴다.
- **다시 고르기**: 허용하지 않음(확인창만). 위험 사연·지운 글·내 답은 고를 수 없음. 회원 답은 원댓글만 고름, AI 답에는 답글 없음.
- **순위 집계**: 고를 때 주차(KST 월 00시~일 24시)·상담사별(사람 회원은 한 줄) 카운트 +1 — 배치 없음. percent 는 합 100 이 되게 서버가 반올림. 인기글 점수의 댓글 수에서 상담사 AI 댓글은 뺀다.
- **위험 판정**: 1차 서버 낱말 목록(즉시), 2차 생성 전 AI 분류. 걸리면 AI 답을 만들지 않고 `RISK`, 관리자 「확인할 일」에 알림. 상담 프롬프트에도 위기 대응 지침.
- **10분 안 답**: 실행은 admin 서버(배치 규칙 — api 에 @Scheduled 금지). 기존 AI 회원 실행기(admin `AiMemberScheduler`, 1분)에 상담 답 차례를 더하는 방식 권장.
- **화면 API**(목업이 만든 타입 그대로 — `apps/user-web/app/composables/counsel/api/useCounselApi.types.ts`): 말머리 `PrefixView.counsel`, 글 줄 `PostRow.counsel { status: WAITING|ANSWERED|PICKED|RISK, aiAnswerCount }`, `GET /api/posts/{id}/counsel`, `PUT /api/posts/{id}/counsel/pick { commentId }`, `GET /api/boards/{slug}/counsel/ranking`. 상담사 AI 댓글은 일반 댓글 목록에서 빼고 상담 칸 응답에만.

## API (서버 구현 2026-11-26 — 미커밋, 컴파일만 확인)
마이그레이션 shared **V274**`__ai_love_counsel.sql`(V273 은 board-recommend 몫 — 건너뜀) + 반복 `R__batch_24_popular_rank.sql` 수정. admin 스트림 변경 없음.
- **말머리**: `board_prefixes.counsel`(고유 말머리만 — CHECK). 공개 `PrefixView.counsel` · `PostPrefixRef.counsel`(글 줄·글 보기·프로필 글). 관리자 게시판 저장은 이 칸을 건드리지 않는다.
- **글 줄** `PostRow.counsel = { status, aiAnswerCount } | null` — 게시판 글 목록만(섞인 목록·프로필은 null). 상태: 위험 `RISK` → 고름 `PICKED` → 상담사 답 1개 이상 `ANSWERED` → 그 밖(판정 전 포함) `WAITING`. 숨긴 글은 null.
- `GET /api/posts/{id}/counsel` → `PostCounselView`(글 보기와 같은 열기 판정 — 404 `POST_NOT_FOUND` · 403 `POST_HIDDEN`·`SECRET_POST` 등, 상담 글 아님 404 `COUNSEL_NOT_FOUND`).
  ```json
  { "status": "ANSWERED", "canPick": true, "pickedCommentId": null, "answerDueAt": "2026-11-26T03:10:00Z",
    "counselors": [ { "counselorId": "1201", "name": "팩폭언니", "style": "직설형", "ai": { "provider": "OPENAI", "model": "gpt-6-luna" }, "comment": { "id": "…", "...": "CommentView 그대로" } },
                    { "counselorId": "1202", "name": "토닥이", "style": "공감형", "ai": { "provider": "ANTHROPIC", "model": "claude-haiku-5-5" }, "comment": null } ] }
  ```
  위험 사연: `{ "status": "RISK", "canPick": false, "pickedCommentId": null, "answerDueAt": null, "counselors": [] }`. `counselors` = 판정 전이면 지금 켜진 상담사, 판정 뒤면 그때 답하기로 한 상담사(답을 끝내 못 한 상담사는 글 + 60분 뒤 빠진다). `ai` = 답이 있으면 그 답을 만든 모델, 아직이면 계정의 지금 모델. `canPick` = 사연 쓴 사람 · 판정을 마친 위험 아닌 사연(판정 전 1분 안은 false) · 아직 안 고름.
- `PUT /api/posts/{id}/counsel/pick` `{ "commentId": "…" }` → 바뀐 `PostCounselView`. 로그인 필요(401) + CSRF. 차례: 400 `VALIDATION fields.commentId`(빈 값) → 글 열기 판정 → 403 `COUNSEL_PICK_FORBIDDEN`(사연 쓴 사람 아님) → 409 `COUNSEL_PICK_UNAVAILABLE`(위험·판정 전) → 409 `COUNSEL_ALREADY_PICKED` → 404 `COMMENT_NOT_FOUND`(그 글의 댓글 아님) → 409 `COUNSEL_PICK_UNAVAILABLE`(지운·숨긴 댓글 · 답글 · 상담사가 아닌 AI 의 댓글) → 409 `COUNSEL_PICK_OWN`. 고르면 같은 트랜잭션에서 `counsel_weekly_picks`(그 주 · 게시판 · 상담사, 사람 회원 = 0) +1. 다시 고르기 없음.
- `GET /api/boards/{slug}/counsel/ranking` → `{ "weekStart": "2026-11-23", "weekEnd": "2026-11-29", "totalPicks": 7, "rows": [ { "rank": 1, "kind": "HUMAN", "name": null, "ai": null, "picks": 3, "percent": 43 }, { "rank": 2, "kind": "AI", "name": "토닥이", "ai": { "provider": "ANTHROPIC", "model": "claude-haiku-5-5" }, "picks": 2, "percent": 29 }, … ] }`. 줄 = 사람 회원 한 줄 + 지금 상담사 전부(+ 이번 주 고른 적 있는 옛 상담사), 많은 순(같으면 사람 → 상담사 만든 차례), 비율은 큰 나머지 반올림으로 합 100 — **고른 것이 없으면 모두 0**(목업은 이때 1·1·1·1). 그 게시판에 상담소 말머리가 없으면 404 `COUNSEL_NOT_FOUND`.
- **일반 댓글 목록**: 상담 글이면 상담사 계정의 원댓글(과 그 아래 답글)을 `threads`·`best` 에서 빼고 `total` 도 그만큼 뺀다(상담 칸에만).
- **글쓰기**(새 글 · 다른 말머리에서 「AI 상담소」로 고칠 때): 400 `VALIDATION fields.prefixId` — `counsel.prefix.memberOnly`(비회원) · `counsel.prefix.optedOut`(AI 제외 설정 켬 — 사연이 제공사로 가므로) · `counsel.prefix.dailyLimit`(회원 하루 상한, 지운 글 포함) · `counsel.prefix.siteLimit`(사이트 하루 상한). 한국 날짜 기준.
- **관리자**(화면 없음 — 이 API 로 켠다, 읽기 `ai-member.read` · 저장 `ai-member.manage`): `GET /api/admin/ai-members/counsel` → `{ memberDailyLimit, siteDailyLimit, memberDailyRange, siteDailyRange, todayPosts, prefixes: [{prefixId, boardId, boardSlug, boardName, name, active, counsel}], counselors: [{aiMemberId, userId, nickname, provider, model, enabled, counselor, style, restingReason}], counselorsMax: 5, styleMax: 20 }` · `PUT …/counsel` `{ memberDailyLimit: 0~10, siteDailyLimit: 0~5000, counselPrefixIds: ["…"], counselors: [{aiMemberId, style}] }`(넘긴 것만 상담소·상담사 — 빠진 것은 푼다) → 같은 모양, 400 `VALIDATION`(칸 이름 그대로 · `counselors[i].aiMemberId`·`counselors[i].style`), 감사 `AI_COUNSEL_SETTINGS_UPDATE`(대상 `AI_MEMBER_SETTINGS`/`COUNSEL`, before/after), 말머리가 바뀌면 게시판 목록 캐시 갱신.
- **관리자 알림** `AI_COUNSEL_RISK`(CONTENT · WARNING · `post.manage` 보유자 · 이메일 기본 켬 · 링크 `/posts?post={postId}` · 사연마다 한 건). 「확인할 일」 공통 구조가 없어 알림 서랍으로 연결했다.

## 서버 구현 결과 (2026-11-26)
- **켜는 법**: 공개 서버가 뜰 때(설치 직후·데모 뒤·기동 — `CounselPresetInstaller`, `installation.counsel_seeded_at` 으로 한 번) 연애·결혼 게시판에 「AI 상담소」 말머리(**꺼진 채**)와 상담사 3계정(**꺼진 채**)을 넣는다. 운영자는 ① AI 회원 설정에서 OpenAI·Anthropic 키 ② AI 회원 목록에서 팩폭언니·토닥이·연애분석관 켜기 ③ 관리자 게시판 화면에서 연애·결혼의 「AI 상담소」 말머리 켜기. 다른 말머리·상담사·상한은 `PUT /api/admin/ai-members/counsel`.
- **상담사 3계정**: 팩폭언니(OPENAI `gpt-6-luna` · 직설형) · 토닥이(ANTHROPIC `claude-haiku-5-5` · 공감형) · 연애분석관(OPENAI `gpt-6.1-terra` · 현실 분석형 — Gemini 18세 미만 조항 미확정이라 GPT 로, 원하는 모델이 꺼져 있으면 그 제공사 기본 모델). 쓸 곳 = 연애·결혼, 새 글 상한 0, 아바타 없음, 소개에 「AI 회원·성격은 설정·전문 상담 아님」. 모델·성향은 관리자에서 바꾼다(계정 고치기는 상담사 표시를 건드리지 않는다). 같은 닉네임 회원이 있으면 그 계정은 건너뛴다.
- **실행기**(admin `AiMemberScheduler` 1분 · 잠금 `ai-member-runner` — 승인 줄 등록 → **상담소**(`CounselRunner`) → 보통 AI 회원): 시간대·「글 쓰는 때」·하루 호출 상한·`calls-per-tick` 과 무관(상담소 한 바퀴 호출 상한 12). 전체 정지 중에는 쉰다. 보통 AI 회원 실행기는 상담사 계정을 건너뛰고, 상담 글에 첫 댓글을 달지 않는다(「회원 글 댓글 허용」을 켜도).
  1. 새 사연(24시간 안 · 회원 글 · 비밀글·소모임 아님 · AI 제외 끈 회원) → 1차 낱말 목록(`CounselRules.RISK_WORDS` — 자살·자해·폭력·스토킹·성폭력 등, 공백 무시) 걸리면 `RISK`(WORD) + 알림. 사이트 하루 상한을 넘으면 다음 날까지 판정을 미룬다.
  2. AI 분류(쉬지 않는 첫 상담사의 모델, RISK·SAFE 한 낱말, 생성 줄은 남기지 않음) → RISK 면 `RISK`(AI) + 알림, SAFE 면 답할 상담사 = 지금 쉬지 않는 상담사. 3번 실패하면 낱말 목록만으로 통과.
  3. 상담사마다 원댓글 하나(사연 인용 — `AiPiiMasker`, 닉네임 없음 · 페르소나 · 성향 지침 · 상담 규칙(위기 시 109·1366 안내) · 고정 규칙). 답에 위험 낱말·개인정보 모양이 있으면 검수 대기함(`PENDING`), 아니면 바로 게시. 실패 2번이면 그 상담사는 그만, 운영자가 버린 답은 다시 만들지 않는다. 키 없음·결제 부족·키 오류는 보통 AI 회원과 같은 처리. 모두 답했거나 글 + 60분이면 끝.
- **인기글**: `popular_rank_candidates` 의 댓글 수 = 상담 글이면 `comment_count − 상담사의 지우지 않은 원댓글 수`(최소 댓글 조건에도).
- **판단한 것**: 「확인할 일」 = 관리자 알림 · 하루 상한은 글쓰기에서 막음(답 없는 글이 쌓이지 않게) · 비회원·AI 제외 회원은 상담 말머리 불가 · 판정 전에는 고르기 불가 · AI 답에 단 답글은 서버가 막지 않음(화면이 단추를 안 줌) · 상담사 답 수의 셈은 댓글 표에서 바로(지운 답 제외).

## 미확정
- 상담사 이름(시안 임시 이름 그대로 넣음) · 세 번째 상담사 모델(Gemini 조항 확정 뒤 바꿀 수 있음).
- 관리자 화면(상담소 설정 · 상담사 표시) — admin-web 게시판 화면 작업이 끝난 뒤.
- 익명 게시판 「연애」 말머리 확대(반응 보고).
