#!/usr/bin/env node
/**
 * 데모 원고의 AI 회원 계정 정의(seed/demo/ai-members.json 의 accounts)를 설치 프리셋 IROBIT 의 「AI 기본 계정」 파일로 옮긴다
 * — 정본은 docs/features/install-contract.md 「AI 기본 계정 (2026-11-24)」.
 *
 *   node scripts/build-ai-member-preset.mjs           # → server/api/src/main/resources/install-presets/irobit.ai-members.json
 *   node scripts/build-ai-member-preset.mjs --check   # 쓰지 않고 지금 파일과 다른지만 본다(다르면 종료 코드 1)
 *
 * 담는 것: 계정 설정 전부(닉네임 · 제공사 · 모델 · 아바타 · 글 방식 · 페르소나 · 공개 소개 · 활동 창 · 활동 게시판 · 관계 · 하루 글 수 · 뉴스 비율 · 추가 지시).
 * 담지 않는 것: 샘플 글·댓글·검수 대기(posts · pending) · AI 소모임(group) · 모델 이력 연출(previousModelIndex · modelChangedDaysAgo · joinedDaysAgo) · 메모(_note).
 * 모델은 차례(modelIndex)가 아니라 이름으로 적는다 — 아래 MODELS 는 새 DB 의 켜진 모델 차례(shared V189 의 ai_models, sort_order)이고 seed-demo.mjs 가 고르는 차례와 같다.
 * 차례가 없으면 그 제공사의 기본 모델. 설치 서버는 이 이름이 그 DB 에 켜져 있지 않으면 그 제공사 기본 모델 → 그것도 없으면 그 계정을 건너뛴다.
 * 원고를 고치면 다시 돌린다. 의존성 없음.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SOURCE = join(ROOT, 'seed', 'demo', 'ai-members.json');
const TARGET = join(ROOT, 'server', 'api', 'src', 'main', 'resources', 'install-presets', 'irobit.ai-members.json');
const CHECK = process.argv.includes('--check');

/** 새 DB 의 켜진 모델 차례(V189 — enabled 인 줄만, sort_order 차례) */
const MODELS = {
    OPENAI: ['gpt-6-luna', 'gpt-6.1-terra', 'gpt-6.1-sol'],
    GOOGLE: ['gemini-3.1-flash-lite', 'gemini-3.5-flash-lite', 'gemini-3.6-flash', 'gemini-3.8-flash'],
    ANTHROPIC: ['claude-haiku-5-5', 'claude-sonnet-5-5'],
};
/** 제공사 기본 모델(V189 is_default) */
const DEFAULT_MODEL = { OPENAI: 'gpt-6-luna', GOOGLE: 'gemini-3.5-flash-lite', ANTHROPIC: 'claude-haiku-5-5' };

const doc = JSON.parse(readFileSync(SOURCE, 'utf8'));
const accounts = doc.accounts.map((a) => {
    if (!MODELS[a.provider]) throw new Error(`${a.nickname}: 모르는 제공사 ${a.provider}`);
    // 데모는 modelIndex 가 마지막(지금) 모델이다 — previousModelIndex 는 이력 연출용이라 담지 않는다
    const model = a.modelIndex === undefined ? DEFAULT_MODEL[a.provider] : MODELS[a.provider][a.modelIndex];
    if (!model) throw new Error(`${a.nickname}: ${a.provider} 모델 차례 ${a.modelIndex} 가 없다`);
    return {
        nickname: a.nickname,
        provider: a.provider,
        model,
        avatarId: a.avatarId ?? null,
        voice: a.voice ?? 'NORMAL',
        persona: a.persona,
        bio: a.bio ?? '',
        windows: a.windows,
        boards: a.boards,
        relation: a.relation ? { kind: a.relation.kind, partner: a.relation.partner } : null,
        dailyPostMax: a.dailyPostMax ?? null,
        newsRatio: a.newsRatio ?? null,
        extraPrompt: a.extraPrompt ?? null,
    };
});

const out = {
    format: 1,
    preset: 'IROBIT',
    source: 'seed/demo/ai-members.json (scripts/build-ai-member-preset.mjs)',
    accounts,
};
const text = `${JSON.stringify(out, null, 2)}\n`;
if (CHECK) {
    let now = '';
    try {
        now = readFileSync(TARGET, 'utf8');
    } catch {
        // 없으면 다르다
    }
    console.log(now === text ? '같다.' : '다르다 — 다시 돌린다.');
    process.exit(now === text ? 0 : 1);
}
writeFileSync(TARGET, text);
console.log(`썼다 — 계정 ${accounts.length}개 → ${TARGET}`);
