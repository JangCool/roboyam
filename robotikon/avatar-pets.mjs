// 프로필 아바타 — 진돗개 백구 · 삼색 고양이(로보티콘 그림 방식, src/parts.mjs 부품 그대로)
// 흰 스티커 외곽선, 평면 색. m/f 는 로보티콘 규칙(setVariant: f = 안테나 리본 + 속눈썹)
//  - animal-jindo-{m,f}  : 얼굴 전체 흰 털, 윙크 + 크림빛 점 눈썹 + 볼터치 + 흰 주둥이·검은 코, 말린 꼬리
//  - animal-calico-{m,f} : 남색 화면에 부탁하는 큰 눈(장화 신은 고양이 느낌) + 걱정 눈썹, 흰 수염 패드·분홍 코,
//                          수염 한쪽 4가닥(아래로 갈수록 짧게), 짧은 귀(왼 주황·오른 검정), 흰 바탕 주황·검정 얼룩 꼬리
// 사용: node avatar-pets.mjs → apps/{user-web,admin-web}/public/avatars/irobit/animal-{jindo,calico}-{m,f}.svg
// 카탈로그 등록: server/shared/src/main/resources/avatars/manifest.json · shared V192 (상점 품목)
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { body, sparkle, C, V, setVariant, OUTLINE as O, resetIds } from './src/parts.mjs'

const APPS = ['user-web', 'admin-web'].map((app) => join(import.meta.dirname, '..', 'apps', app, 'public', 'avatars', 'irobit'))
const NAVY = C.navy
const NS = `stroke="${NAVY}" stroke-width="3.5" stroke-linejoin="round"`
// 흰 스티커 외곽선 + 남색 선 + 채움
const stk = (d, fill) => `<path d="${d}" fill="#fff" stroke="#fff" stroke-width="${O * 2 + 3.5}" stroke-linejoin="round"/><path d="${d}" fill="${fill}" ${NS}/>`
// 굵은 선 꼬리(흰 스티커 외곽선 + 겹) — layers: [[색, 굵기]]
const strokeTail = (d, layers) => `<path d="${d}" fill="none" stroke="#fff" stroke-width="${layers[0][1] + O * 2}" stroke-linecap="round" stroke-linejoin="round"/>`
  + layers.map(([c, w]) => `<path d="${d}" fill="none" stroke="${c}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"/>`).join('')
const HEAD = () => `x="28" y="62" width="144" height="90" rx="${V.headRx}"`
const headLine = () => `<rect ${HEAD()} fill="none" ${NS}/>`
// 귀·꼬리가 머리 밖으로 나오므로 다른 아바타(-8 -6 216 216)보다 조금 넓게 잡는다
const wrap = (inner) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-12 -8 224 224">${inner}</svg>`

// ═════════ 진돗개 백구 ═════════
function jindo(g) {
  setVariant(g); resetIds()
  const W = '#FFF9EE', CREAM = '#F1D29C', PINK = '#FFB8B0'
  // 선 귀: 두툼한 세모, 끝은 크림빛
  const ear = (flip) => `<g${flip ? ' transform="matrix(-1 0 0 1 200 0)"' : ''}>${stk('M36 86 C 30 60 32 32 40 13 Q 44 5 52 10 C 68 26 82 44 90 62 Z', W)}<path d="M41 17 Q 44 7 52 11 C 56 15 59 18 62 22 C 54 21 47 22 41 25 Z" fill="${CREAM}"/><path d="M46 76 C 42 56 43 36 47 24 C 60 36 70 48 76 60 Z" fill="${PINK}"/></g>`
  // 말린 꼬리 + 살랑 선
  const tail = strokeTail('M156 128 C 192 128 206 100 196 80 C 188 64 166 66 168 82 C 170 94 186 92 185 83', [[NAVY, 22], [W, 15]])
    + `<path d="M178 50 q8 -2 13 5 M200 58 q6 4 6 12" fill="none" stroke="${NAVY}" stroke-width="3.5" stroke-linecap="round"/>`
  // 얼굴 전체 흰 털: 왼눈 윙크, 오른눈 남색 알약 눈, 크림빛 눈썹 점, 볼터치, 흰 주둥이 + 검은 코
  const face = `<path d="M70 102 Q 80 88 90 102" fill="none" stroke="${NAVY}" stroke-width="6" stroke-linecap="round"/>`
    + `<rect x="111" y="86" width="18" height="24" rx="9" fill="${NAVY}"/><circle cx="123.5" cy="92.5" r="3.6" fill="#fff"/><circle cx="116" cy="104" r="1.6" fill="#fff"/>`
    + (V.lashes ? `<path d="M69 99 L 61 94 M131 91 L 139 85" stroke="${NAVY}" stroke-width="3.5" stroke-linecap="round"/>` : '')
    + `<ellipse cx="80" cy="79" rx="6.5" ry="3.6" transform="rotate(-12 80 79)" fill="#E8BF7A"/><ellipse cx="120" cy="79" rx="6.5" ry="3.6" transform="rotate(12 120 79)" fill="#E8BF7A"/>`
    + `<ellipse cx="58" cy="118" rx="9" ry="5" fill="#FFB3B3"/><ellipse cx="142" cy="118" rx="9" ry="5" fill="#FFB3B3"/>`
    + `<path d="M74 142 C 74 122 86 113 100 113 C 114 113 126 122 126 142 Z" fill="#FFFFFF" stroke="#EBD3A8" stroke-width="3"/><path d="M86 116 Q 100 110 114 116" fill="none" stroke="${CREAM}" stroke-width="4" stroke-linecap="round"/>`
    + `<path d="M90 120 Q 100 114.5 110 120 Q 110 127.5 100 130.5 Q 90 127.5 90 120 Z" fill="#1E1B24"/><ellipse cx="95.5" cy="119.5" rx="3.2" ry="1.5" fill="#fff"/><path d="M100 130.5 v3.5 M91.5 135 q4.25 4.5 8.5 0 q4.25 4.5 8.5 0" fill="none" stroke="${NAVY}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>`
  const front = face + headLine() + sparkle(28, 30, 9, C.yellow, 0)
  return wrap(`${tail}${body({ head: W, screen: W, face: '', back: ear(false) + ear(true), front })}`)
}

// ═════════ 삼색이 ═════════
function calico(g) {
  setVariant(g); resetIds()
  const W = '#FFFFFF', OR = '#FF9A3C', BK = '#2E2A35', PINK = '#FFB3C3'
  const id = `animal-calico-${g}`
  // 짧은 귀: 왼쪽 주황, 오른쪽 검정
  const ears = stk('M30 84 L 37 32 Q 39 23 48 27 L 88 60 Z', OR) + `<path d="M41 74 L 45 40 L 73 60 Z" fill="${PINK}"/>`
    + stk('M170 84 L 163 32 Q 161 23 152 27 L 112 60 Z', BK) + `<path d="M159 74 L 155 40 L 127 60 Z" fill="${PINK}"/>`
  // 꼬리: 흰 바탕에 주황·검정 얼룩이 불규칙하게(줄무늬 아님)
  const tD = 'M156 142 C 186 146 198 118 190 96 C 186 84 190 74 199 71'
  const tail = strokeTail(tD, [[NAVY, 20]])
    + `<defs><mask id="${id}-tail" maskUnits="userSpaceOnUse" x="140" y="56" width="80" height="110"><path d="${tD}" fill="none" stroke="#fff" stroke-width="13" stroke-linecap="round" stroke-linejoin="round"/></mask></defs>`
    + `<g mask="url(#${id}-tail)"><rect x="140" y="56" width="80" height="110" fill="${W}"/><path d="M158 136 C 166 132 176 136 178 144 C 176 152 162 152 156 148 Z" fill="${OR}"/><path d="M182 118 C 192 116 200 124 196 132 C 190 136 180 132 178 126 Z" fill="${BK}"/><path d="M186 98 C 194 94 200 102 196 110 C 190 112 184 106 186 98 Z" fill="${OR}"/><path d="M184 84 C 190 80 196 86 194 92 C 190 94 184 90 184 84 Z" fill="${BK}"/><path d="M190 66 C 202 64 208 74 202 80 C 196 82 188 76 190 66 Z" fill="${BK}"/></g>`
    + `<path d="M204 86 q6 6 2 14" fill="none" stroke="${NAVY}" stroke-width="3.5" stroke-linecap="round"/>`
  const scr = `x="46" y="76" width="108" height="62" rx="${V.screenRx}"`
  // 부탁하는 큰 눈: 연두 홍채 + 크게 열린 동공 + 반짝임, 촉촉한 아래 눈가
  const eye = (cx, side) => `<circle cx="${cx}" cy="101" r="18.5" fill="url(#${id}-iris)"/><circle cx="${cx}" cy="102" r="15.2" fill="#07060B"/><path d="M${cx - 10} 110.5 Q ${cx} 115.5 ${cx + 10} 110.5 Q ${cx} 112.5 ${cx - 10} 110.5 Z" fill="#9FD3FF" opacity=".55"/><circle cx="${cx + 6}" cy="93" r="6.6" fill="#fff"/><circle cx="${cx - 6}" cy="107" r="3.6" fill="#fff"/><circle cx="${cx + 9.5}" cy="104" r="1.6" fill="#fff"/><circle cx="${cx - 9.5}" cy="96" r="1.3" fill="#fff"/><path d="M${cx - 15} 112 Q ${cx} 125 ${cx + 15} 112" fill="none" stroke="#BFE6FF" stroke-width="2.4" stroke-linecap="round"/>`
    + (V.lashes ? `<path d="M${cx + side * 17} 88 l${side * 7} -5 M${cx + side * 19} 94 l${side * 8} -2" stroke="${C.yellow}" stroke-width="3" stroke-linecap="round"/>` : '')
  // 안쪽이 올라간 걱정 눈썹 + 흰 수염 패드 · 분홍 코 · ω 입
  const face = eye(78, -1) + eye(122, 1) + `<path d="M69 85.5 Q 77 82 87 77.5 M131 85.5 Q 123 82 113 77.5" fill="none" stroke="${C.yellow}" stroke-width="4.6" stroke-linecap="round"/>`
    + `<ellipse cx="90" cy="129" rx="12" ry="9" fill="${W}"/><ellipse cx="110" cy="129" rx="12" ry="9" fill="${W}"/><path d="M94 117 h12 l-6 6.5 Z" fill="#FF8FA3" stroke="${NAVY}" stroke-width="2" stroke-linejoin="round"/><path d="M100 123.5 v3 M92 127.5 q4 4.5 8 0 q4 4.5 8 0" fill="none" stroke="${NAVY}" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/><g fill="${NAVY}"><circle cx="84" cy="128" r="1.3"/><circle cx="87" cy="132" r="1.3"/><circle cx="116" cy="128" r="1.3"/><circle cx="113" cy="132" r="1.3"/></g>`
  // 머리 얼룩(왼쪽 위 주황, 오른쪽 위 검정)을 깐 뒤 남색 화면과 얼굴을 다시 그린다
  const patches = `<defs><radialGradient id="${id}-iris" cx=".5" cy=".6" r=".55"><stop offset="0" stop-color="#F6F79A"/><stop offset=".7" stop-color="#B8D84A"/><stop offset="1" stop-color="#6E9A24"/></radialGradient><clipPath id="${id}-head"><rect ${HEAD()}/></clipPath><clipPath id="${id}-screen"><rect ${scr}/></clipPath></defs>`
    + `<g clip-path="url(#${id}-head)"><path d="M20 50 L 94 50 C 94 72 76 88 60 102 C 48 112 36 122 20 128 Z" fill="${OR}"/><path d="M106 50 L 184 50 L 184 122 C 174 112 166 98 156 88 C 140 74 118 70 106 50 Z" fill="${BK}"/><ellipse cx="160" cy="144" rx="11" ry="6" fill="${OR}"/><ellipse cx="42" cy="145" rx="9" ry="5" fill="${BK}"/></g>`
    + `<rect ${scr} fill="${NAVY}"/><g clip-path="url(#${id}-screen)">${face}</g>`
  // 수염: 한쪽 4가닥, 위가 가장 길고 아래로 갈수록 짧게
  const wd = 'M80 123 Q 58 113 32 110 M80 127 Q 58 122 35 123 M80 131 Q 60 132 40 137 M81 135 Q 64 140 48 148 M120 123 Q 142 113 168 110 M120 127 Q 142 122 165 123 M120 131 Q 140 132 160 137 M119 135 Q 136 140 152 148'
  const whiskers = `<path d="${wd}" fill="none" stroke="${NAVY}" stroke-width="4.6" stroke-linecap="round"/><path d="${wd}" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"/>`
  const front = patches + headLine() + whiskers + sparkle(172, 44, 9, C.yellow, 0)
  return wrap(`${tail}${body({ head: W, face: '', back: ears, front })}`)
}

for (const g of ['m', 'f']) {
  for (const [name, draw] of [['jindo', jindo], ['calico', calico]]) {
    const svg = draw(g).replace(/\n/g, '')
    for (const dir of APPS) writeFileSync(join(dir, `animal-${name}-${g}.svg`), svg)
  }
}
console.log('animal-jindo/calico-{m,f}.svg → user-web·admin-web')
