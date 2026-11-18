// 로고의 마스코트(아이로빗 아바타 머리 + 짧은 A자 다리) — 캐릭터 로고(build.mjs)와 기념일 글자 로고(build-wordmark.mjs)가 같이 쓴다.
// 국경일 변형은 캐릭터를 그대로 두고 그날의 특징과 표정만 얹는다(사용자 2026-11-18). 글자 색은 평소 그대로, 날짜를 작게 붙인다.
//   samil    삼일절(3.1)   **캐릭터 없이** 대나무 깃대의 태극기(건곤감리)와 무궁화 두 송이만(아래 samilFigure)
//   jeheon   제헌절(7.17)  헌법 책 + 안테나 자리에 무궁화 + 반듯하고 차분한 표정
//   gwangbok 광복절(8.15)  **캐릭터 없이** 무궁화 꽃밭 앞에서 펄럭이는 태극기 + 붓글씨 이름(아래 FIGURES.gwangbok)
//   gaecheon 개천절(10.3)  단군의 긴 수염 + 갈라진 구름 사이로 쏟아지는 빛(하늘이 열린 날) + 인자하게 웃는 표정
//   hangeul  한글날(10.9)  ㄱㄴㄷ 팻말 + 눈이 「ㅎㅎ」
//   girim    일본군 '위안부' 피해자 기림의 날(8.14)  **캐릭터 없이** 노란 나비 세 마리
// 국경일은 아니지만 같은 방식으로 만든 기념일
//   gukgun      국군의 날(10.1)        그물 씌운 철모 + 착검한 소총 + 절도 있는 표정
//   gwangbokgun 광복군 창설일(9.17)    군모 + 손 태극기 + 굳은 표정

export const C = { navy: '#0E1F4D', blue: '#2F6BFF', orange: '#FF9F2E', yellow: '#FFC83D', white: '#FFFFFF' }
const TAEGEUK = { red: '#CD2E3A', blue: '#0047A0' }

/** 국경일마다 작게 붙이는 날짜(그 일이 있었던 날) */
export const THEME_DATES = { samil: '1919.3.1', jeheon: '1948.7.17', gwangbok: '1945.8.15', gaecheon: 'B.C.2333', hangeul: '1446.10.9', gukgun: '1950.10.1', gwangbokgun: '1940.9.17' }

/** 기념일 이름 — 한글판에서 날짜 위에 작게 붙인다(포털 기념일 로고처럼 무슨 날인지 글자로도 알린다 — 사용자 2026-11-18) */
export const THEME_NAMES = { samil: '삼일절', jeheon: '제헌절', gwangbok: '광복절', gaecheon: '개천절', hangeul: '한글날', gukgun: '국군의 날', gwangbokgun: '광복군 창설일' }

// 다리: 로보티콘(robotikon/src/parts.mjs body)과 같은 A자 — 꼭짓점 100,42 에서 머리 뒤로 내려가 바닥(46,164 / 154,164 기울기)으로 나온다.
// 안테나 공(100,36 r12)이 꼭짓점에 얹혀 꼭대기를 대부분 가리고, 공과 머리 사이에 A 의 어깨만 살짝 보인다(사용자 2026-11-13 「로보티콘처럼, A 꼭대기는 너무 보이지 않게, 그래도 A 처럼」).
// 머리 몸통 중앙(y=107)을 글자 중앙에 맞추는 정렬은 그대로. 캔버스 높이 40 안에 들도록 바닥 159 이하
const LEG_END = Number(process.env.LEG_END ?? 159)
const legX = (y, side) => +(100 + side * 54 * ((y - 42) / 122)).toFixed(2)
const legs = (attrs) => [-1, 1].map((side) => `<line x1="100" y1="42" x2="${legX(LEG_END, side)}" y2="${LEG_END}" ${attrs}/>`).join('')
const BALL = { cx: 100, cy: 36, r: 12 }

const Y = C.yellow
const eyeArcs = `<path d="M68 112 Q80 94 92 112" stroke="${Y}" stroke-width="8" fill="none" stroke-linecap="round"/><path d="M108 112 Q120 94 132 112" stroke="${Y}" stroke-width="8" fill="none" stroke-linecap="round"/>`
const eyeDots = `<circle cx="81" cy="110" r="6.5" fill="${Y}"/><circle cx="119" cy="110" r="6.5" fill="${Y}"/>`
const line = (d, w = 6) => `<path d="${d}" stroke="${Y}" stroke-width="${w}" fill="none" stroke-linecap="round" stroke-linejoin="round"/>`

const FACES = {
  // 평소: 웃는 눈(^^)
  '': eyeArcs,
  // 삼일절: 웃지 않는다. 살짝만 기운 눈썹 + 또렷한 눈 + 다문 입(눈썹을 많이 기울이면 화난 얼굴이 된다 — 사용자 「너무 화난 표정」)
  samil: `${line('M69 96L91 98.5')}${line('M131 96L109 98.5')}${line('M93 128H107')}<circle cx="81" cy="112" r="6.5" fill="${Y}"/><circle cx="119" cy="112" r="6.5" fill="${Y}"/>`,
  // 제헌절: 반듯한 눈 + 옅은 미소
  jeheon: `${eyeDots}${line('M91 125Q100 130 109 125', 5.5)}`,
  // 광복절: 웃는 눈 + 활짝 벌린 입
  gwangbok: `<g transform="translate(0 -5)">${eyeArcs}</g><path d="M87 118H113Q100 136 87 118Z" fill="${Y}" stroke="${Y}" stroke-width="4" stroke-linejoin="round"/>`,
  // 개천절: 웃는 눈(수염이 입 자리를 덮는다) — 인자한 얼굴
  gaecheon: `<g transform="translate(0 -6)">${eyeArcs}</g><path d="M66 88Q77 81 91 86M134 88Q123 81 109 86" stroke="#FFFFFF" stroke-width="5" fill="none" stroke-linecap="round"/>`,
  // 국군의 날: 또렷한 눈 + 다문 입(절도 있는 얼굴). 눈은 철모 챙 아래로 내린다
  gukgun: `${line('M70 104L91 106')}${line('M130 104L109 106')}<circle cx="81" cy="117" r="6.5" fill="${Y}"/><circle cx="119" cy="117" r="6.5" fill="${Y}"/>${line('M93 131H107')}`,
  // 광복군 창설일: 삼일절과 같은 굳은 얼굴(독립을 위한 결의), 모자 챙 아래로 조금 내린다
  gwangbokgun: `${line('M69 102L91 104.5')}${line('M131 102L109 104.5')}${line('M93 131H107')}<circle cx="81" cy="117" r="6.5" fill="${Y}"/><circle cx="119" cy="117" r="6.5" fill="${Y}"/>`,
  // 한글날: 눈이 ㅎㅎ(꼭지 · 가로 획 · 동그라미) + 미소
  hangeul: [80, 120].map((x) => `${line(`M${x - 4} 93H${x + 4}`, 5)}${line(`M${x - 11} 101H${x + 11}`, 5)}<circle cx="${x}" cy="113" r="6.5" fill="none" stroke="${Y}" stroke-width="5"/>`).join('') + line('M92 129Q100 134 108 129', 5),
}

// ── 손에 든 것·곁들임(아바타 200 좌표, 머리 오른쪽 190..268) — 어두운 바탕에서도 보이게 흰 테를 먼저 깐다 ──
const hand = (cy = 118) => `<circle cx="187" cy="${cy}" r="9" fill="${C.navy}" stroke="#FFFFFF" stroke-width="4"/>`
const pole = `<path d="M186 124L200 30" stroke="#FFFFFF" stroke-width="13"/><path d="M186 124L200 30" stroke="${C.navy}" stroke-width="5"/>`
/** 대나무 깃대 — 손으로 만든 태극기를 대나무에 매어 들었다. 마디 세 개 */
const bamboo = `<path d="M186 124L200 30" stroke="#FFFFFF" stroke-width="14"/><path d="M186 124L200 30" stroke="#6B5320" stroke-width="7.5"/><path d="M186 124L200 30" stroke="#C9A455" stroke-width="4.5"/><path d="M187.6 99L193.4 100M191.3 74L197.1 75M195 49L200.8 50" stroke="#6B5320" stroke-width="2.2"/>`
/** 깃대 꼭대기에 60×40 판을 살짝 기울여 단다 */
const onPole = (board, stick = pole) => `<g stroke-linecap="round" stroke-linejoin="round">${stick}<g transform="translate(200 30) rotate(8)"><rect x="-4" y="-4" width="68" height="48" fill="#FFFFFF"/><rect width="60" height="40" fill="#FFFFFF" stroke="${C.navy}" stroke-width="3"/>${board}</g>${hand()}</g>`

/** 태극기 무늬(60×40 판 안) — 태극은 국기의 대각선 각도(33.69도)로 기운다, 네 귀퉁이 괘는 짧은 막대로 */
function taegeukgi() {
  const cx = 30, cy = 20, r = 11, h = r / 2
  const red = `M${cx - r} ${cy}A${r} ${r} 0 0 1 ${cx + r} ${cy}A${h} ${h} 0 0 0 ${cx} ${cy}A${h} ${h} 0 0 1 ${cx - r} ${cy}Z`
  // 괘: 대각선에 직각인 막대 세 줄. 이어진 막대(true)와 끊어진 막대(false) — 건 ☰(왼쪽 위) · 감 ☵(오른쪽 위) · 리 ☲(왼쪽 아래) · 곤 ☷(오른쪽 아래)
  // 로고 헤더 크기에서는 점으로 뭉치지만 크게 볼 때는 제대로 읽힌다(사용자 2026-11-18 「건곤감리 특징」)
  const gwae = (x, y, a, rows) => `<g transform="translate(${x} ${y}) rotate(${a})" stroke="${C.navy}" stroke-width="2.1" stroke-linecap="butt">${rows.map((solid, k) => { const yy = (k - 1) * 3.3; return solid ? `<path d="M-5 ${yy}H5"/>` : `<path d="M-5 ${yy}H-0.9M0.9 ${yy}H5"/>` }).join('')}</g>`
  return `<g transform="rotate(33.69 ${cx} ${cy})"><circle cx="${cx}" cy="${cy}" r="${r}" fill="${TAEGEUK.blue}"/><path fill="${TAEGEUK.red}" d="${red}"/></g>${gwae(10, 6.7, -56.31, [true, true, true])}${gwae(50, 6.7, 56.31, [false, true, false])}${gwae(10, 33.3, 56.31, [true, false, true])}${gwae(50, 33.3, -56.31, [false, false, false])}`
}

/** 무궁화 — 안테나 공 자리에 꽃잎 다섯 장 */
function mugunghwa() {
  let petals = ''
  for (let i = 0; i < 5; i++) {
    const r = ((-90 + i * 72) * Math.PI) / 180
    petals += `<circle cx="${+(BALL.cx + Math.cos(r) * 9.5).toFixed(1)}" cy="${+(BALL.cy + Math.sin(r) * 9.5).toFixed(1)}" r="8.5"/>`
  }
  return `<g fill="#FFFFFF" stroke="#FFFFFF" stroke-width="6">${petals}</g><g fill="#F08FB0">${petals}</g><circle cx="${BALL.cx}" cy="${BALL.cy}" r="6" fill="#C2305A"/><circle cx="${BALL.cx}" cy="${BALL.cy}" r="2.4" fill="${Y}"/>`
}

/** 헌법 책 — 손에 들어 올린 남색 책(노란 제목 띠, 오른쪽 아래로 종이 두께) */
const book = `<g transform="translate(196 54) rotate(8)" stroke-linejoin="round"><rect x="-5" y="-5" width="62" height="74" rx="6" fill="#FFFFFF"/><rect x="6" y="4" width="46" height="60" rx="3" fill="#FFFFFF" stroke="${C.navy}" stroke-width="3"/><rect width="46" height="60" rx="3" fill="${C.navy}"/><rect x="9" y="12" width="28" height="9" rx="2" fill="${Y}"/><path d="M9 30H37M9 38H30" stroke="#8FA3D9" stroke-width="3.5" stroke-linecap="round"/></g>${hand(120)}`

/**
 * 개천절 — 단군이 하늘을 연 날. 해·구름만 그리면 날씨 그림처럼 보인다(사용자 2026-11-18)
 *   단군: 마스코트에 긴 수염(안테나 공이 상투처럼 보인다)
 *   하늘이 열림: 양쪽으로 갈라진 구름 사이에서 빛이 아래로 쏟아진다
 */
function openSky() {
  // 수염: 아래로 뾰족하게 길게(세로 줄을 넣으면 이빨처럼 보인다)
  // 얼굴에서 시작하는 수염: 코 → 양옆으로 내려가는 콧수염 → 턱 아래로 길게 내려가는 좁은 수염(턱 밑에 세모로 달면 속옷처럼 보인다 — 사용자 2026-11-18)
  const shape = 'M87 123Q100 129 113 123Q116 150 100 177Q84 150 87 123Z'
  const beard = `<path d="${shape}" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="8" stroke-linejoin="round"/><path d="${shape}" fill="#FFFFFF" stroke="${C.navy}" stroke-width="3" stroke-linejoin="round"/><path d="M100 134V166" stroke="#B9C2D4" stroke-width="2.5" stroke-linecap="round"/><path d="M97 119Q84 118 71 129M103 119Q116 118 129 129" stroke="#FFFFFF" stroke-width="6.5" fill="none" stroke-linecap="round"/><ellipse cx="100" cy="113" rx="5.5" ry="6.5" fill="${Y}"/>`
  // 빛: 구름 틈(위)에서 아래로 넓어지는 빛기둥 + 빛살 세 줄
  const beam = `<path d="M221 46H241L268 128H194Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="8" stroke-linejoin="round"/><path d="M221 46H241L268 128H194Z" fill="#FFE39A"/><path d="M226 58L210 122M231 58V124M236 58L252 122" stroke="${C.orange}" stroke-width="4" stroke-linecap="round"/>`
  const cloud = (cx, extra) => [[cx - 12, 46, 10], [cx, 40, 13], [cx + 12, 47, 9]].map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r + extra}"/>`).join('') + `<rect x="${cx - 20 - extra}" y="${44 - extra}" width="${40 + extra * 2}" height="${13 + extra * 2}" rx="${6.5 + extra}"/>`
  const clouds = (extra) => cloud(203, extra) + cloud(259, extra)
  return `${beard}${beam}<g fill="${C.navy}">${clouds(3)}</g><g fill="#FFFFFF">${clouds(0)}</g>`
}

/** 철모 — 머리 윗부분을 덮는다(안테나 공은 철모 꼭지처럼 위로 나온다) */
// 국군의 날 — 1950년 10월 1일 국군(3사단 23연대)이 38선을 처음 넘은 날을 기념해 1956년에 정한 날. 그 무렵 국군의 모습으로 그린다.
// 사용자가 준 참고 사진(2026-11-18) 기준: 위장 그물을 씌운 둥근 철모(턱끈) + 착검한 소총을 옆에 세워 쥔 모습. 철모 앞 표지는 사진에 없어 넣지 않는다.
/** 작은 태극(모표) — 흰 바탕 동그라미 위에 */
const miniTaegeuk = (cx, cy, r) => `<circle cx="${cx}" cy="${cy}" r="${r + 1.6}" fill="#FFFFFF"/><g transform="rotate(33.69 ${cx} ${cy})"><circle cx="${cx}" cy="${cy}" r="${r}" fill="${TAEGEUK.blue}"/><path fill="${TAEGEUK.red}" d="M${cx - r} ${cy}A${r} ${r} 0 0 1 ${cx + r} ${cy}A${r / 2} ${r / 2} 0 0 0 ${cx} ${cy}A${r / 2} ${r / 2} 0 0 1 ${cx - r} ${cy}Z"/></g>`
const DOME = 'M22 83Q18 40 100 40Q182 40 178 83Z'
const RIM = 'M15 80Q100 92 185 80L186 87Q100 100 14 87Z'
/** 철모 그물 — 마름모 그물코를 철모 몸통 안에만 그린다 */
function helmetNet() {
  let d = ''
  for (let x = -40; x <= 200; x += 15) d += `M${x} 90L${x + 52} 38M${x + 52} 90L${x} 38`
  return `<clipPath id="hn"><path d="${DOME}"/></clipPath><path d="${d}" clip-path="url(#hn)" stroke="#2F3B22" stroke-width="2.2" fill="none"/>`
}
/** 철모 앞의 흰 계급 표지(갈매기 무늬 셋) — 사용자가 준 6·25 군복 사진 기준 */
const helmetRank = `<path d="M93 59L100 54L107 59M93 66L100 61L107 66M93 73L100 68L107 73" stroke="#FFFFFF" stroke-width="2.8" fill="none"/>`
/** 그물에 꽂은 나뭇잎 위장 — 철모 위쪽으로 삐져나온다(사용자 2026-11-18 「나뭇잎 같은 위장」) */
function helmetLeaves() {
  const leaf = (x, y, a, s, fill) => `<g transform="translate(${x} ${y}) rotate(${a}) scale(${s})"><path d="M0 2Q8 -10 0 -24Q-8 -10 0 2Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="6"/><path d="M0 2Q8 -10 0 -24Q-8 -10 0 2Z" fill="${fill}"/><path d="M0 0V-18" stroke="#3E5A22" stroke-width="1.6" fill="none"/></g>`
  return leaf(52, 58, -38, 1, '#7DA34A') + leaf(70, 50, -14, 0.9, '#94B85C') + leaf(134, 50, 20, 0.95, '#7DA34A') + leaf(152, 60, 44, 1, '#94B85C') + leaf(118, 46, 4, 0.75, '#6B8F3C')
}
const helmet = `<g stroke-linejoin="round" stroke-linecap="round"><path d="${DOME}" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="10"/><path d="${RIM}" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="9"/><path d="M32 90Q27 130 64 149Q100 160 136 149Q173 130 168 90" fill="none" stroke="#3B4A2B" stroke-width="5"/><path d="${DOME}" fill="#566A3F"/>${helmetNet()}${helmetRank}<path d="${RIM}" fill="#3B4A2B"/>${helmetLeaves()}<rect x="95" y="150" width="10" height="7" rx="2" fill="#C9B26A" stroke="#3B4A2B" stroke-width="2"/></g>`
/** 착검한 소총 — 나무 개머리판·총몸, 검은 총열, 흰 총검. 옆에 세워 오른손으로 쥔다 */
const rifle = `<g stroke-linecap="round" fill="none"><path d="M199 160L213 78L220 22" stroke="#FFFFFF" stroke-width="17"/><path d="M199 160L210 96" stroke="#7A4E2A" stroke-width="12"/><path d="M210 96L215 66" stroke="#8B5A32" stroke-width="8"/><path d="M214 72L217 50" stroke="#2B2F36" stroke-width="5"/><path d="M213 54H221" stroke="#2B2F36" stroke-width="3.5"/><path d="M217 50L220.5 22" stroke="${C.navy}" stroke-width="5.5"/><path d="M217 50L220.5 22" stroke="#EEF1F5" stroke-width="3"/></g><circle cx="205" cy="120" r="9.5" fill="${C.navy}" stroke="#FFFFFF" stroke-width="4"/><g stroke-linecap="round"><path d="M188 126L200 121" stroke="${C.navy}" stroke-width="9"/></g>`

// 광복군 창설일 — 1940년 창설 당시의 한국광복군 모습. 자료(한국민족문화대백과 「광복군 군복」, 한국복식학회 논문)에 따르면 창설기에는
// 중국 국민정부 「육군복제조례」(1936)식 복장 — 푸른색(청회색) 중산복형 제복에 **원통형 군모** — 을 입었고, 모표는 광복군 휘장 규정에 따라 한국의 상징을 넣었다.
// 그래서 위가 넓은 정모·검은 챙(처음 그린 것 — 고증 없이 그렸다)이 아니라 청회색 원통형 군모(같은 천의 짧은 챙, 앞 단추 둘)에 태극 모표를 단다.
// 색은 「푸른빛이 도는 짙은 회색(심회색)」(김정민 2019 박사논문 초록), 군모는 독일군 스키모에서 온 형태(김정민·홍나영 2019)라 앞 단추 둘을 달았다(단추 개수는 스키모의 흔한 모습이고 논문 원문으로 확인한 것은 아니다).
// 색(사용자 2026-11-18, 사용자가 준 사진·실물 군복 사진 기준): 풀빛이 도는 올리브색. 논문 초록의 추정(푸른빛 도는 짙은 회색)과 다르지만 사용자 자료를 따른다.
// 모자 모양은 사용자가 준 대원 사진과 같다: 위가 평평하고 부드러운 원통형, 같은 천의 짧은 챙, 앞에 둥근 모표와 그 아래 단추.
// 일본군과 헷갈리지 않게: 갈색 기운의 황갈색·별 모표·붉은 깃 계급장·목 뒤 가리개 천은 쓰지 않고, 모표는 태극으로 한다.
// 사진과 맞춘 비례(사용자 2026-11-18 「흑백 사진 특징과 비슷하니?」): 머리 폭보다 조금 좁고 높은 몸통, 위 모서리가 둥글게 죽은 부드러운 천,
// 앞 가운데로 올라가는 접은 덮개(스키모의 귀덮개), 모표 아래 **세로로** 놓인 단추 둘, 앞으로 내민 짧은 챙.
const CROWN = 'M38 78L40 41Q42 31 60 29Q100 25 140 29Q158 31 160 41L162 78Z'
const FLAP = 'M38 78L39 63Q72 58 100 50Q128 58 161 63L162 78Z'
const VISOR = 'M50 78H150Q142 92 100 92Q58 92 50 78Z'
const cap = `<g stroke-linejoin="round"><path d="${CROWN}" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="10"/><path d="${VISOR}" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="9"/><path d="${CROWN}" fill="#716D34"/><path d="M50 34Q100 28 150 34" stroke="#8E8A4C" stroke-width="3.5" fill="none" stroke-linecap="round"/><path d="${FLAP}" fill="#605C2B"/><path d="M39 63Q72 58 100 50Q128 58 161 63" stroke="#4D4A22" stroke-width="2" fill="none"/><path d="${VISOR}" fill="#4D4A22"/><path d="M62 82Q100 87 138 82" stroke="#353310" stroke-width="2.5" fill="none" stroke-linecap="round"/><circle cx="100" cy="59" r="3" fill="#C9B26A"/><circle cx="100" cy="69" r="3" fill="#C9B26A"/>${miniTaegeuk(100, 40, 7.5)}</g>`

/** ㄱㄴㄷ 팻말(60×40 판 안) */
const giyeok = `<path d="M9 12H19V28M26 12V28H36M51 12H41V28H51" fill="none" stroke="${C.navy}" stroke-width="4.5"/>`


/** 머리 뒤에 먼저 그리는 것 */
const BACKS = {}

const EXTRAS = {
  '': '',
  jeheon: mugunghwa() + book,
  gaecheon: openSky(),
  hangeul: onPole(giyeok),
  gukgun: helmet + rifle,
  gwangbokgun: cap + onPole(taegeukgi()),
}

/**
 * 삼일절 그림 — 캐릭터 자리에 **태극기와 무궁화 두 송이만** 둔다(사용자 2026-11-18).
 * 로봇에 옷을 입힌 것, 사람을 그린 것 모두 마음에 들지 않아 뺐다 — 삼일절은 캐릭터 없이 간다.
 * 대나무 깃대에 단 태극기(건곤감리) + 깃대 발치의 무궁화 두 송이와 잎. 캔버스 좌표(높이 40)로 바로 그린다.
 */
const SAMIL_FIGURE_W = 38
function samilFigure(tx) {
  const flag = `<g transform="translate(9.5 3.2) rotate(4)"><rect x="-1" y="-1" width="28" height="19.4" fill="#FFFFFF"/><g transform="scale(0.435)"><rect width="60" height="40" fill="#FFFFFF" stroke="${C.navy}" stroke-width="2.4"/>${taegeukgi()}</g></g>`
  const pole = '<g stroke-linecap="round"><path d="M8.5 37.5V3.5" stroke="#FFFFFF" stroke-width="3.6"/><path d="M8.5 37.5V3.5" stroke="#6B5320" stroke-width="2"/><path d="M8.5 37.5V3.5" stroke="#C9A455" stroke-width="1.1"/><path d="M7.6 13H9.4M7.6 23H9.4" stroke="#6B5320" stroke-width="0.6"/><circle cx="8.5" cy="2.8" r="1.5" fill="#FFFFFF"/><circle cx="8.5" cy="2.8" r="1.1" fill="#E0B030"/></g>'
  return `<g transform="translate(${tx} 0)">${pole}${flag}${mugunghwaCluster(-3, 38.5).svg}</g>`
}

// ───────────────────────── 그 밖의 기념일(사용자 2026-11-18 「다 만들어 보자」) ─────────────────────────
// 캐릭터로 표현하기 어려운 날(추모의 날 일부)은 삼일절처럼 캐릭터 없이 그날의 상징만 둔다(FIGURES).
//   추모·역사: jeju43 4·3(동백꽃) · imjeong 임시정부 수립(태극기) · sailgu 4·19(횃불) · oilpal 5·18(흰 국화) · hyeonchung 현충일(조기 + 흰 국화, 캐릭터 없음)
//              yusip 6·10(큰 태극기 + 투표함과 투표용지, 캐릭터 없음) · yugio 6·25(철모를 건 소총 + 흰 국화, 캐릭터 없음) · haksaeng 학생독립운동(학생모 + 태극기) · sunguk 순국선열의 날(촛불)
//   생활: sikmok 식목일(새싹·묘목) · eorini 어린이날(풍선) · eobeoi 어버이날(카네이션 두 송이와 리본, 캐릭터 없음) · seuseung 스승의 날(칠판과 카네이션, 캐릭터 없음) · seollal 설날(붉은·푸른 福 복주머니와 엽전, 방패연, 캐릭터 없음)
//         chuseok 추석(밝은 보름달과 벼 이삭, 캐릭터 없음) · buddha 부처님오신날(부처님 모습의 캐릭터 + 연꽃) · xmas 성탄절(산타 모자·선물) · newyear 새해 첫날(햇무리 번지는 해돋이 — 빛살 무늬 금지, 캐릭터 없음)
//   IT: gwahak 과학의 날(플라스크) · jeongbo 정보통신의 날(송신탑과 전파)

const rad = (a) => (a * Math.PI) / 180
const f1 = (v) => +v.toFixed(1)

/** 동백꽃 — 붉은 꽃잎 다섯 장 + 노란 꽃술 */
function camellia(cx, cy, r) {
  let petals = ''
  for (let k = 0; k < 5; k++) petals += `<circle cx="${f1(cx + Math.cos(rad(-90 + k * 72)) * r * 0.48)}" cy="${f1(cy + Math.sin(rad(-90 + k * 72)) * r * 0.48)}" r="${f1(r * 0.56)}"/>`
  let dots = ''
  for (let k = 0; k < 6; k++) dots += `<circle cx="${f1(cx + Math.cos(rad(k * 60)) * r * 0.16)}" cy="${f1(cy + Math.sin(rad(k * 60)) * r * 0.16)}" r="${f1(r * 0.09)}"/>`
  return `<g fill="#FFFFFF" stroke="#FFFFFF" stroke-width="${f1(r * 0.3)}">${petals}</g><g fill="#D2232A">${petals}</g><g fill="none" stroke="#A3161C" stroke-width="${f1(r * 0.06)}">${petals}</g><circle cx="${cx}" cy="${cy}" r="${f1(r * 0.3)}" fill="#FFD23F"/><g fill="#E0A800">${dots}</g>`
}
/** 흰 국화 — 가는 꽃잎 여러 장 + 옅은 노란 속 */
function chrys(cx, cy, r) {
  let petals = ''
  for (let k = 0; k < 14; k++) petals += `<ellipse cx="${cx}" cy="${f1(cy - r * 0.56)}" rx="${f1(r * 0.17)}" ry="${f1(r * 0.46)}" transform="rotate(${f1(k * 25.7)} ${cx} ${cy})"/>`
  return `<circle cx="${cx}" cy="${cy}" r="${f1(r * 1.1)}" fill="#FFFFFF"/><g fill="#FFFFFF" stroke="#9AA3B5" stroke-width="${f1(r * 0.07)}">${petals}</g><circle cx="${cx}" cy="${cy}" r="${f1(r * 0.3)}" fill="#F3E08A" stroke="#C9B24A" stroke-width="${f1(r * 0.06)}"/>`
}
/** 카네이션 — 겹겹이 주름진 붉은 꽃 + 초록 꽃받침 */
function carnation(cx, cy, r, fill = '#E0344A', edge = '#B81F36') {
  let ruffle = ''
  for (let k = 0; k < 9; k++) ruffle += `<circle cx="${f1(cx + Math.cos(rad(k * 40)) * r * 0.66)}" cy="${f1(cy + Math.sin(rad(k * 40)) * r * 0.66)}" r="${f1(r * 0.34)}"/>`
  let inner = ''
  for (let k = 0; k < 6; k++) inner += `<circle cx="${f1(cx + Math.cos(rad(30 + k * 60)) * r * 0.3)}" cy="${f1(cy + Math.sin(rad(30 + k * 60)) * r * 0.3)}" r="${f1(r * 0.3)}"/>`
  return `<path d="M${f1(cx - r * 0.42)} ${f1(cy + r * 0.5)}L${cx} ${f1(cy + r * 1.5)}L${f1(cx + r * 0.42)} ${f1(cy + r * 0.5)}Z" fill="#4E9A45" stroke="#FFFFFF" stroke-width="${f1(r * 0.14)}"/><g fill="#FFFFFF" stroke="#FFFFFF" stroke-width="${f1(r * 0.28)}">${ruffle}<circle cx="${cx}" cy="${cy}" r="${f1(r * 0.7)}"/></g><g fill="${fill}">${ruffle}<circle cx="${cx}" cy="${cy}" r="${f1(r * 0.7)}"/></g><g fill="none" stroke="${edge}" stroke-width="${f1(r * 0.07)}">${inner}</g>`
}
/** 잎 한 장 — 뿌리 (x, y), 기울기 a, 길이 len */
const leafAt = (x, y, a, len, fill = '#4E9A45') => `<g transform="translate(${x} ${y}) rotate(${a})"><path d="M0 0Q${f1(len * 0.38)} ${f1(-len * 0.4)} 0 ${-len}Q${f1(-len * 0.38)} ${f1(-len * 0.4)} 0 0Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="${f1(len * 0.22)}"/><path d="M0 0Q${f1(len * 0.38)} ${f1(-len * 0.4)} 0 ${-len}Q${f1(-len * 0.38)} ${f1(-len * 0.4)} 0 0Z" fill="${fill}"/></g>`

/** 손에 든 꽃 — 줄기(잎 두 장) 끝에 꽃 */
const heldFlower = (bloom) => `<g stroke-linecap="round"><path d="M189 122L214 66" stroke="#FFFFFF" stroke-width="11"/><path d="M189 122L214 66" stroke="#3F7332" stroke-width="4.5"/></g>${leafAt(199, 100, 62, 22)}${leafAt(205, 86, -42, 20)}${bloom(216, 56, 23)}${hand(120)}`

/** 불꽃 — 바닥 가운데 (x, y), 높이 h */
const flame = (x, y, h) => { const w = h * 0.36; const d = (k) => `M${f1(x - w * k)} ${y}Q${f1(x - w * 1.25 * k)} ${f1(y - h * 0.5 * k)} ${x} ${f1(y - h * k)}Q${f1(x + w * 1.25 * k)} ${f1(y - h * 0.5 * k)} ${f1(x + w * k)} ${y}Q${x} ${f1(y + h * 0.16 * k)} ${f1(x - w * k)} ${y}Z`; return `<path d="${d(1)}" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="7"/><path d="${d(1)}" fill="#FF7A1A"/><path d="${d(0.56)}" fill="#FFD23F"/>` }
/** 횃불 — 나무 자루 + 불받이 + 큰 불꽃 */
const torch = `<g stroke-linecap="round"><path d="M188 124L208 80" stroke="#FFFFFF" stroke-width="17"/><path d="M188 124L208 80" stroke="#7A4E2A" stroke-width="9"/></g><path d="M196 82L226 82L220 66L202 66Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round"/><path d="M196 82L226 82L220 66L202 66Z" fill="#5B5F6B"/>${flame(211, 67, 44)}${hand(120)}`
/** 촛불 — 흰 초 + 작은 불꽃 + 받침 */
const candle = `<rect x="203" y="70" width="18" height="52" rx="3" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="7"/><rect x="203" y="70" width="18" height="52" rx="3" fill="#FBF7EC" stroke="#9AA3B5" stroke-width="2.4"/><path d="M207 76Q209 84 207 92" stroke="#D9D2BF" stroke-width="2" fill="none" stroke-linecap="round"/><path d="M212 70V62" stroke="#3A3C44" stroke-width="2.4" stroke-linecap="round"/>${flame(212, 63, 26)}<ellipse cx="212" cy="124" rx="19" ry="6" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="6"/><ellipse cx="212" cy="124" rx="19" ry="6" fill="#C9A455" stroke="#6B5320" stroke-width="2"/>${hand(120).replace('cx="187"', 'cx="192"')}`
/**
 * 6·10 민주항쟁 — 사용자가 준 사진(1987년, 큰 태극기 앞에서 두 팔을 벌린 사람)을 따른다(2026-11-18).
 * 물결치는 큰 태극기 — 태극과 괘는 물결치는 깃면 안쪽에 들어가게 조금 줄여 넣는다(괘가 깃면 밖으로 나가지 않게 — 사용자 2026-11-18).
 * 투표함과 투표용지는 아래 블록에서 그리고, 합쳐 놓는 것은 FIGURES.yusip.
 */
const flagWave = `<g><path d="M0 0Q30 -8 62 0T124 0V80Q94 88 62 80T0 80Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="10" stroke-linejoin="round"/><path d="M0 0Q30 -8 62 0T124 0V80Q94 88 62 80T0 80Z" fill="#FFFFFF" stroke="${C.navy}" stroke-width="3.4" stroke-linejoin="round"/><g transform="translate(8 4) scale(1.8)">${taegeukgi()}</g></g>`
/**
 * 학생모 — 1929년 무렵 고등보통학교 남학생의 교모. 한국민족문화대백과 「교복」: 고등보통학교까지는 둥근 모자를 썼다(대학생은 사각모), 남학생 교복은 1920년대에 양복으로 바뀌었다.
 * 그래서 위가 넓고 납작한 정모(처음 그린 것 — 고증 없이 그렸다)가 아니라 위가 둥글게 솟은 검은 모자 + 짧은 검은 챙 + 앞의 금빛 모표로 그린다.
 * 사용자가 준 당시 학생 사진(2026-11-18) 기준: 띠보다 넓게 부풀어 덮는 둥글고 부드러운 모자집, 띠를 두른 **흰 줄 두 줄**, 띠 위 앞 가운데의 작은 금빛 모표, 아래로 숙인 짧은 검은 챙.
 * 사진의 검은 세운 깃 윗옷과 큰 금속 단추는 캐릭터에 몸통이 없어 그리지 않는다.
 */
const schoolCap = `<g stroke-linejoin="round" stroke-linecap="round"><path d="M40 67Q32 34 100 31Q168 34 160 67Q100 58 40 67Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="10"/><path d="M46 65Q100 57 154 65L154 78H46Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="10"/><path d="M56 78H144Q138 91 100 91Q62 91 56 78Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="9"/><path d="M46 65Q100 57 154 65L154 78H46Z" fill="#22232B"/><path d="M47 69Q100 61.5 153 69M47 74Q100 66.5 153 74" stroke="#F4F1E8" stroke-width="2.3" fill="none"/><path d="M40 67Q32 34 100 31Q168 34 160 67Q100 58 40 67Z" fill="#1B1C22"/><path d="M56 46Q100 34 144 46" stroke="#3A3C46" stroke-width="3" fill="none"/><path d="M56 78H144Q138 91 100 91Q62 91 56 78Z" fill="#0E0F14"/><path d="M68 82Q100 87 132 82" stroke="#4A4D5A" stroke-width="2.5" fill="none"/><path d="M100 41Q106 48 100 57Q94 48 100 41Z" fill="#E0B030" stroke="#8C6E2C" stroke-width="1.6"/><path d="M95 56H105" stroke="#E0B030" stroke-width="3"/></g>`
/** 새싹 — 안테나 공 위로 돋은 떡잎 두 장 */
const sprout = `${leafAt(100, 27, -52, 24, '#5DAA47')}${leafAt(100, 27, 52, 24, '#5DAA47')}`
/** 손에 든 묘목 — 줄기와 잎 여러 장, 뿌리 쪽 흙 */
const sapling = `<path d="M190 124L212 58" stroke="#FFFFFF" stroke-width="12" stroke-linecap="round"/>${leafAt(198, 100, 68, 24)}${leafAt(203, 86, -40, 24)}${leafAt(207, 74, 62, 22)}${leafAt(210, 64, -34, 22)}${leafAt(212, 58, 12, 24, '#5DAA47')}<path d="M190 124L211 62" stroke="#7A4E2A" stroke-width="4.5" stroke-linecap="round"/>${hand(122)}`
/** 산타 모자 — 머리 윗부분을 덮는 붉은 모자, 흰 털 테와 방울 */
const santaHat = `<g stroke-linejoin="round"><path d="M38 70Q52 20 112 22Q150 24 166 48Q140 40 150 70Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="10"/><path d="M38 70Q52 20 112 22Q150 24 166 48Q140 40 150 70Z" fill="#D2232A"/><path d="M60 46Q84 26 116 28" stroke="#E8585E" stroke-width="4" fill="none" stroke-linecap="round"/><rect x="26" y="62" width="136" height="20" rx="10" fill="#FFFFFF" stroke="#C9D2E3" stroke-width="2.5"/><circle cx="166" cy="50" r="12" fill="#FFFFFF" stroke="#C9D2E3" stroke-width="2.5"/></g>`
/** 선물 상자 */
const gift = `<g stroke-linejoin="round"><rect x="200" y="88" width="48" height="40" rx="3" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="8"/><rect x="200" y="88" width="48" height="40" rx="3" fill="#2E9E5B"/><rect x="196" y="80" width="56" height="13" rx="3" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="7"/><rect x="196" y="80" width="56" height="13" rx="3" fill="#35B56A"/><path d="M224 80V128" stroke="#FFD23F" stroke-width="7"/><path d="M224 80Q208 60 206 74Q208 82 224 80Q240 82 242 74Q240 60 224 80Z" fill="#FFD23F" stroke="#E0A800" stroke-width="1.8"/></g>${hand(112).replace('cx="187"', 'cx="194"')}`
/** 삼각 플라스크 — 청록 용액과 방울 */
const flask = `<g stroke-linejoin="round" stroke-linecap="round"><path d="M216 52H236M219 52V80L198 122Q196 130 204 130H248Q256 130 254 122L233 80V52" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="9"/><path d="M219 54V80L198 122Q196 130 204 130H248Q256 130 254 122L233 80V54Z" fill="#EAF6FF"/><path d="M209 100L198 122Q196 130 204 130H248Q256 130 254 122L243 100Z" fill="#22C3B6"/><circle cx="218" cy="114" r="4" fill="#B8F1EC"/><circle cx="234" cy="108" r="3" fill="#B8F1EC"/><circle cx="228" cy="90" r="3" fill="#22C3B6"/><circle cx="224" cy="70" r="2.4" fill="#22C3B6"/><path d="M219 54V80L198 122Q196 130 204 130H248Q256 130 254 122L233 80V54" fill="none" stroke="${C.navy}" stroke-width="3.4"/><path d="M214 53H238" stroke="${C.navy}" stroke-width="4.5"/></g>${hand(116).replace('cx="187"', 'cx="196"')}`
/** 송신탑과 전파 */
const tower = (() => {
  const arcs = [15, 26].map((r) => `M${f1(228 - Math.cos(rad(40)) * r)} ${f1(56 - Math.sin(rad(40)) * r)}A${r} ${r} 0 0 0 ${f1(228 - Math.cos(rad(40)) * r)} ${f1(56 + Math.sin(rad(40)) * r)}M${f1(228 + Math.cos(rad(40)) * r)} ${f1(56 - Math.sin(rad(40)) * r)}A${r} ${r} 0 0 1 ${f1(228 + Math.cos(rad(40)) * r)} ${f1(56 + Math.sin(rad(40)) * r)}`).join('')
  return `<g stroke-linecap="round" stroke-linejoin="round" fill="none"><path d="M228 60L210 128M228 60L246 128M216 106H240M221 86H235M216 106L235 86M240 106L221 86M210 128H246" stroke="#FFFFFF" stroke-width="10"/><path d="${arcs}" stroke="#FFFFFF" stroke-width="10"/><path d="M228 60L210 128M228 60L246 128M216 106H240M221 86H235M216 106L235 86M240 106L221 86M210 128H246" stroke="${C.navy}" stroke-width="4"/><path d="${arcs}" stroke="${C.orange}" stroke-width="4.5"/></g><circle cx="228" cy="56" r="7.5" fill="#FF5A66" stroke="#FFFFFF" stroke-width="3"/>`
})()

// 표정
const solemnEyes = `${line('M69 106Q80 115 91 106', 6.5)}${line('M109 106Q120 115 131 106', 6.5)}${line('M94 129H106', 5.5)}`
const happyFace = FACES.gwangbok
const dotSmile = FACES.jeheon
const lowered = (svg, dy) => `<g transform="translate(0 ${dy})">${svg}</g>`

Object.assign(FACES, {
  jeju43: solemnEyes, imjeong: FACES.samil, sailgu: FACES.samil, oilpal: solemnEyes, hyeonchung: '', yusip: '', yugio: '',
  haksaeng: lowered(FACES.samil, 5), sunguk: solemnEyes,
  sikmok: eyeArcs + line('M92 124Q100 130 108 124', 5.5), eorini: happyFace, eobeoi: eyeArcs + line('M92 124Q100 130 108 124', 5.5), seuseung: dotSmile,
  seollal: happyFace, chuseok: eyeArcs + line('M92 124Q100 130 108 124', 5.5), buddha: '', xmas: lowered(happyFace, 4), newyear: happyFace,
  gwahak: dotSmile, jeongbo: dotSmile,
})
Object.assign(EXTRAS, {
  jeju43: heldFlower(camellia), imjeong: onPole(taegeukgi(), bamboo), sailgu: torch, oilpal: heldFlower(chrys), yusip: '',
  haksaeng: schoolCap + onPole(taegeukgi()), sunguk: candle,
  sikmok: sprout + sapling, eorini: '', eobeoi: '', seuseung: '',
  seollal: '', chuseok: '', buddha: '', xmas: santaHat + gift, newyear: '',
  gwahak: flask, jeongbo: tower,
})
// 이름은 로고가 길어지지 않게 줄여 쓴다. 가운뎃점(·)은 개구체에 글자가 없어 마침표로 쓴다
Object.assign(THEME_NAMES, {
  jeju43: '4.3 추념일', imjeong: '임시정부 수립일', sailgu: '4.19 혁명', oilpal: '5.18 민주화운동', hyeonchung: '현충일',
  yusip: '6.10 민주항쟁', yugio: '6.25 전쟁일', haksaeng: '학생독립운동', sunguk: '순국선열의 날',
  sikmok: '식목일', eorini: '어린이날', eobeoi: '어버이날', seuseung: '스승의 날', seollal: '설날', chuseok: '추석', buddha: '부처님오신날', xmas: '성탄절', newyear: '새해 첫날',
  gwahak: '과학의 날', jeongbo: '정보통신의 날',
})
Object.assign(THEME_DATES, {
  jeju43: '1948.4.3', imjeong: '1919.4.11', sailgu: '1960.4.19', oilpal: '1980.5.18', hyeonchung: '6.6', yusip: '1987.6.10', yugio: '1950.6.25', haksaeng: '1929.11.3', sunguk: '11.17',
  sikmok: '4.5', eorini: '5.5', eobeoi: '5.8', seuseung: '5.15', seollal: '음력 1.1', chuseok: '음력 8.15', buddha: '음력 4.8', xmas: '12.25', newyear: '1.1',
  gwahak: '4.21', jeongbo: '4.22',
})
/** 영문판 날짜 — 한글이 들어간 날짜만 따로 */
export const THEME_DATES_EN = { seollal: 'Lunar 1.1', chuseok: 'Lunar 8.15', buddha: 'Lunar 4.8' }

// ── 캐릭터 없이 상징만 두는 날(캔버스 좌표, 높이 40) ──
const figPole = '<g stroke-linecap="round"><path d="M8.5 37.5V3.5" stroke="#FFFFFF" stroke-width="3.6"/><path d="M8.5 37.5V3.5" stroke="#6B5320" stroke-width="2"/><path d="M8.5 37.5V3.5" stroke="#C9A455" stroke-width="1.1"/><circle cx="8.5" cy="2.8" r="1.5" fill="#FFFFFF"/><circle cx="8.5" cy="2.8" r="1.1" fill="#E0B030"/></g>'
const figFlag = (y) => `<g transform="translate(9.5 ${y}) rotate(4)"><rect x="-1" y="-1" width="28" height="19.4" fill="#FFFFFF"/><g transform="scale(0.435)"><rect width="60" height="40" fill="#FFFFFF" stroke="${C.navy}" stroke-width="2.4"/>${taegeukgi()}</g></g>`
const figLeaf = (x, y, a) => `<g transform="translate(${x} ${y}) rotate(${a})"><path d="M0 0Q3 -3.2 0 -8Q-3 -3.2 0 0Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="1.6"/><path d="M0 0Q3 -3.2 0 -8Q-3 -3.2 0 0Z" fill="#4E9A45"/></g>`
const FIGURES = {
  samil: { w: SAMIL_FIGURE_W, draw: samilFigure },
  // 현충일: 조기(깃봉에서 깃면 너비만큼 내려 단 태극기) + 깃대 발치의 흰 국화
  hyeonchung: { w: 38, draw: (tx) => `<g transform="translate(${tx} 0)">${figPole}${figFlag(13)}${figLeaf(12, 38, 64)}${figLeaf(4, 38, -62)}${chrys(8.5, 33.5, 5)}</g>` },
  // 6·25 전쟁일: 땅에 꽂은 소총에 철모를 건 전사자 추모의 모습 + 흰 국화
  yugio: { w: 34, draw: (tx) => `<g transform="translate(${tx} 0)" stroke-linecap="round" stroke-linejoin="round"><path d="M15 37.5V10" stroke="#FFFFFF" stroke-width="5.4"/><path d="M15 37.5V22" stroke="#2B2F36" stroke-width="1.8"/><path d="M15 24V11" stroke="#7A4E2A" stroke-width="3.4"/><path d="M13.6 21H16.4" stroke="#2B2F36" stroke-width="1.2"/><path d="M5.5 13Q5 3.5 15 3.5Q25 3.5 24.5 13Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="2.4"/><path d="M5.5 13Q5 3.5 15 3.5Q25 3.5 24.5 13Z" fill="#566A3F"/><path d="M4.4 12.2Q15 14.6 25.6 12.2L25.8 13.6Q15 16.2 4.2 13.6Z" fill="#3B4A2B"/><path d="M9 7Q15 4.8 21 7" stroke="#73895A" stroke-width="0.9" fill="none"/><path d="M6 37.6Q15 34.6 26 37.6" stroke="#8C6E2C" stroke-width="1.4" fill="none"/>${figLeaf(28, 38, 66)}${figLeaf(20.5, 38, -40)}${chrys(24.5, 33, 4.8)}</g>` },
}

/**
 * 부처님 모습 — 부처님오신날은 캐릭터를 부처님처럼 그린다(사용자 2026-11-18 「부처님처럼, 디테일 더」).
 *   머리 뒤: 불꽃 무늬 테를 두른 금빛 광배(BACKS)
 *   머리: 금빛 얼굴(HEADS), 두 줄로 촘촘한 곱슬머리(나발), 정수리의 육계와 붉은 구슬(계주), 이마의 백호, 구멍 뚫린 긴 귓불
 *   얼굴: 가늘고 긴 눈썹, 지그시 내리뜬 눈, 옅은 미소
 *   아래: 연꽃 자리(연화좌 — 위로 핀 꽃잎), 오른손은 손바닥을 펴 보이는 손짓(두려워 말라는 뜻의 시무외인)
 */
const halo = (() => {
  let flames = ''
  for (let k = 0; k < 22; k++) {
    const a = rad(-200 + k * (220 / 21))
    const x = 100 + Math.cos(a) * 78, y = 104 + Math.sin(a) * 68
    flames += `<g transform="translate(${f1(x)} ${f1(y)}) rotate(${f1((a * 180) / Math.PI + 90)})"><path d="M-6 2Q-7 -6 0 -13Q7 -6 6 2Z"/></g>`
  }
  return `<g fill="#FFFFFF" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">${flames}<ellipse cx="100" cy="104" rx="78" ry="68"/></g><g fill="#FF9F2E">${flames}</g><ellipse cx="100" cy="104" rx="78" ry="68" fill="#FFE39A" stroke="#E0A020" stroke-width="3"/><ellipse cx="100" cy="104" rx="69" ry="59" fill="#FFF1C2" stroke="#F0C050" stroke-width="2"/><ellipse cx="100" cy="104" rx="62" ry="52" fill="none" stroke="#F0C050" stroke-width="1.6" stroke-dasharray="1.5 6" stroke-linecap="round"/>`
})()
const buddhaLook = (() => {
  const HAIR = '#262E4A', CURL = '#56618A'
  const cap = 'M29 96Q24 54 100 51Q176 54 171 96Q158 76 100 73Q42 76 29 96Z'
  const curl = (x, y, r) => `<circle cx="${f1(x)}" cy="${f1(y)}" r="${r}" fill="${HAIR}" stroke="${CURL}" stroke-width="1.8"/><path d="M${f1(x - r * 0.45)} ${f1(y)}A${f1(r * 0.45)} ${f1(r * 0.45)} 0 1 1 ${f1(x)} ${f1(y + r * 0.45)}" fill="none" stroke="${CURL}" stroke-width="1.5"/>`
  let curls = ''
  for (let k = 0; k <= 9; k++) { const t = k / 9; curls += curl(34 + t * 132, 87 - Math.sin(t * Math.PI) * 13, 7.5) }
  for (let k = 0; k <= 7; k++) { const t = k / 7; curls += curl(48 + t * 104, 70 - Math.sin(t * Math.PI) * 9, 7.5) }
  for (let k = 0; k <= 4; k++) { const t = k / 4; curls += curl(70 + t * 60, 57 - Math.sin(t * Math.PI) * 4, 7) }
  const knot = curl(89, 44, 6.5) + curl(100, 46, 6.5) + curl(111, 44, 6.5) + curl(94, 33, 6.5) + curl(106, 33, 6.5) + curl(100, 24, 6)
  const lobe = (x) => `<rect x="${x}" y="106" width="18" height="44" rx="9" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="10"/><rect x="${x}" y="106" width="18" height="44" rx="9" fill="#C99A2E"/><ellipse cx="${x + 9}" cy="138" rx="3" ry="6" fill="#8C6A1A"/>`
  const seat = (() => {
    let p = ''
    for (let k = -3; k <= 3; k++) { const x = 100 + k * 23; const h = 30 - Math.abs(k) * 2.5; p += `<path d="M${x - 15} 172Q${x - 17} ${f1(172 - h * 0.6)} ${x} ${f1(172 - h)}Q${x + 17} ${f1(172 - h * 0.6)} ${x + 15} 172Z"/>` }
    return `<g fill="#FFFFFF" stroke="#FFFFFF" stroke-width="8" stroke-linejoin="round">${p}</g><g fill="#F58FB0" stroke="#D4476B" stroke-width="2" stroke-linejoin="round">${p}</g><path d="M40 166Q100 160 160 166" stroke="#F9C6D4" stroke-width="2.4" fill="none" stroke-linecap="round"/>`
  })()
  const palm = `<g stroke-linejoin="round" stroke-linecap="round"><path d="M186 120L198 104" stroke="#FFFFFF" stroke-width="20"/><path d="M186 120L198 104" stroke="#C99A2E" stroke-width="12"/><path d="M190 106Q188 84 196 82V66Q200 60 204 66V62Q208 56 212 62V66Q216 61 220 67V80Q224 76 227 82V100Q226 116 208 116Q194 116 190 106Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="8"/><path d="M190 106Q188 84 196 82V66Q200 60 204 66V62Q208 56 212 62V66Q216 61 220 67V80Q224 76 227 82V100Q226 116 208 116Q194 116 190 106Z" fill="#E9B949" stroke="#8C6A1A" stroke-width="2.4"/><path d="M204 66V84M212 64V84M220 70V86" stroke="#8C6A1A" stroke-width="2" fill="none"/><circle cx="208" cy="100" r="5" fill="none" stroke="#8C6A1A" stroke-width="1.8"/></g>`
  return `${seat}${lobe(16)}${lobe(166)}<g stroke-linejoin="round"><circle cx="100" cy="37" r="20" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="9"/><path d="${cap}" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="9"/><circle cx="100" cy="37" r="20" fill="${HAIR}"/><path d="${cap}" fill="${HAIR}"/>${curls}${knot}<circle cx="100" cy="56" r="4.6" fill="#D2232A" stroke="#FFD95A" stroke-width="1.8"/></g><circle cx="100" cy="93" r="4" fill="${Y}"/><circle cx="100" cy="93" r="1.6" fill="${C.navy}"/>${palm}`
})()
const buddhaFace = `<g stroke="${Y}" fill="none" stroke-linecap="round"><path d="M64 99Q78 92 93 98M136 99Q122 92 107 98" stroke-width="3.6"/><path d="M66 111Q80 120 94 111M106 111Q120 120 134 111" stroke-width="6.5"/><path d="M90 129Q100 136 110 129" stroke-width="5.5"/></g>`

/**
 * 6·10 민주항쟁 — 태극기와 투표함·투표용지를 함께(사용자 2026-11-18). 거리의 항쟁(큰 태극기)이 대통령을 직접 뽑을 권리(투표)로 이어졌다.
 * **캐릭터 없이** 상징만 둔다(사용자 「6·10 은 캐릭터 빼라」): 물결치는 큰 태극기 + 그 앞의 투표함 + 투표함으로 들어가는 기표한 투표용지.
 */
const ballotBox = `<g stroke-linejoin="round" stroke-linecap="round"><rect x="200" y="98" width="58" height="42" rx="4" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="9"/><g transform="rotate(-12 226 80)"><rect x="210" y="56" width="32" height="46" rx="2" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="8"/><rect x="210" y="56" width="32" height="46" rx="2" fill="#FFFFFF" stroke="${C.navy}" stroke-width="3"/><path d="M215 64H237M215 92H231" stroke="#9AA3B5" stroke-width="2.4"/><circle cx="226" cy="78" r="8.5" fill="none" stroke="#D2232A" stroke-width="3"/><path d="M224 72V84M224 78H231" stroke="#D2232A" stroke-width="3" fill="none"/></g><rect x="200" y="98" width="58" height="42" rx="4" fill="#F4F1E8" stroke="${C.navy}" stroke-width="3.4"/><rect x="200" y="98" width="58" height="12" rx="4" fill="${C.blue}" stroke="${C.navy}" stroke-width="3.4"/><path d="M212 104H246" stroke="${C.navy}" stroke-width="4.5"/><path d="M216 122H242M220 130H238" stroke="#9AA3B5" stroke-width="3"/><path d="M229 112V118" stroke="#D2232A" stroke-width="0"/></g>`

// 6·10: 캐릭터 없이 — 큰 태극기(뒤) + 투표함과 투표용지(앞, 오른쪽 아래에 작게 — 오른쪽 아래 괘 곤이 조금은 보이게 깃면 밖으로 반쯤 뺀다, 사용자 2026-11-18). 아바타 좌표로 그린 조각을 캔버스 크기로 줄여 놓는다
FIGURES.yusip = { w: 49, draw: (tx) => `<g transform="translate(${tx} 0)"><g transform="translate(2.5 4.5) rotate(-5) scale(0.3)">${flagWave}</g><g transform="translate(33 18) scale(0.25) translate(-200 -56)">${ballotBox}</g></g>` }

/**
 * 추석 — **캐릭터 없이** 밝은 보름달과 벼 이삭(사용자 2026-11-18: 추석은 추수와, 달이 가장 밝을 때라는 뜻).
 * 달무리가 번지는 환한 보름달(크게 — 그림 높이를 거의 채운다) + 그 앞 오른쪽 아래의 누런 벼 이삭 세 줄기와 잎(작게). 캔버스 좌표(높이 40).
 */
// 달 속의 방아 찧는 토끼(사용자 2026-11-18 「흔히 그렇게 표현한다」) — 달 무늬처럼 짙은 금빛 그림자로. 벼가 달 오른쪽을 가리므로 왼쪽에 둔다
const moonRabbit = `<g fill="#C48A10" stroke="none"><ellipse cx="11.2" cy="19.6" rx="2.7" ry="3.3"/><circle cx="12.3" cy="15" r="2.1"/><ellipse cx="11.3" cy="11.6" rx="0.75" ry="2.3" transform="rotate(-12 11.3 11.6)"/><ellipse cx="13.2" cy="11.5" rx="0.75" ry="2.3" transform="rotate(10 13.2 11.5)"/><circle cx="8.5" cy="21.2" r="1.05"/><ellipse cx="11.6" cy="23.1" rx="2.2" ry="0.9"/><path d="M15.2 21H20.2L19.4 24.6H16Z"/></g><g stroke="#C48A10" stroke-linecap="round" fill="none"><path d="M12.6 17.6L17.2 15.4" stroke-width="1.1"/><path d="M17.7 11.4V20.4" stroke-width="1.25"/><path d="M16.9 11.4H18.5" stroke-width="1.5"/></g>`
FIGURES.chuseok = { w: 46, draw: (tx) => {
  // 이삭: 줄기 곡선(2차 베지어) 끝 쪽에 낟알을 양옆으로 번갈아 단다
  const ear = (p0, p1, p2) => {
    const at = (t) => [(1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0], (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]]
    let grains = ''
    for (let k = 0; k < 9; k++) {
      const t = 0.5 + k * 0.058
      const [x, y] = at(t), [x2, y2] = at(t + 0.02)
      const ang = (Math.atan2(y2 - y, x2 - x) * 180) / Math.PI
      const side = k % 2 ? 1 : -1
      grains += `<ellipse cx="${f1(x)}" cy="${f1(y)}" rx="1.9" ry="0.95" transform="rotate(${f1(ang + side * 38)} ${f1(x)} ${f1(y)}) translate(0.9 ${side * 0.9})"/>`
    }
    const d = `M${p0[0]} ${p0[1]}Q${p1[0]} ${p1[1]} ${p2[0]} ${p2[1]}`
    return { d, grains }
  }
  const ears = [ear([27, 38.5], [25, 12], [38, 15]), ear([30, 38.5], [32, 16], [42, 22]), ear([24, 38.5], [19, 18], [30, 9])]
  const stems = ears.map((e) => e.d).join('')
  const grains = ears.map((e) => e.grains).join('')
  const blade = (d) => `<path d="${d}" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="1.8" stroke-linejoin="round"/><path d="${d}" fill="#9DB53C"/>`
  return `<g transform="translate(${tx} 0)" stroke-linecap="round">
<g transform="translate(-1.5 -2) scale(1.27)"><circle cx="16" cy="17" r="15.5" fill="#FFF6C9"/><circle cx="16" cy="17" r="13.6" fill="#FFEE9E"/><circle cx="16" cy="17" r="11.8" fill="#FFD93D" stroke="#F2BC1B" stroke-width="0.7"/>${moonRabbit}</g>
<g transform="translate(14.5 10.2) scale(0.74)">
${blade('M26 38.5Q20 30 13 29Q21 31 28 38.5Z')}${blade('M29 38.5Q35 31 41 31Q35 33 31 38.5Z')}${blade('M27 38.5Q27 28 22 24Q29 28 29 38.5Z')}
<path d="${stems}" fill="none" stroke="#FFFFFF" stroke-width="2.6"/><g fill="#FFFFFF" stroke="#FFFFFF" stroke-width="1.6">${grains}</g>
<path d="${stems}" fill="none" stroke="#B88A1E" stroke-width="1"/><g fill="#E9B429" stroke="#A9780F" stroke-width="0.35">${grains}</g>
</g>
</g>` } }

/**
 * 스승의 날 — **캐릭터 없이**(사용자 2026-11-18). 교실 칠판과 카네이션.
 * 나무 테의 초록 칠판(분필로 그린 하트와 줄, 아래 받침의 분필과 지우개) + 칠판 앞에 놓인 붉은 카네이션 한 송이(줄기·잎·리본). 캔버스 좌표(높이 40).
 */
FIGURES.seuseung = { w: 46, draw: (tx) => `<g transform="translate(${tx} 0)" stroke-linejoin="round" stroke-linecap="round">
<rect x="1.5" y="3" width="36" height="25" rx="1.6" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="2.4"/><rect x="1.5" y="3" width="36" height="25" rx="1.6" fill="#B07A3C" stroke="#7A4E2A" stroke-width="0.7"/><rect x="3.7" y="5.2" width="31.6" height="20.6" rx="0.6" fill="#2F6B4F" stroke="#1F4D38" stroke-width="0.5"/>
<path d="M12.5 19.5Q6.4 15 9 11.6Q11.2 9.6 12.5 12.4Q13.8 9.6 16 11.6Q18.6 15 12.5 19.5Z" fill="none" stroke="#FFFFFF" stroke-width="1.1"/><path d="M20.5 11H31.5M20.5 14.6H29.5M20.5 18.2H31" stroke="#E8F2EC" stroke-width="1" fill="none"/><path d="M6.5 22.6H17" stroke="#FFE680" stroke-width="0.9" fill="none"/>
<rect x="0.6" y="27.6" width="37.8" height="2.6" rx="1.1" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="2"/><rect x="0.6" y="27.6" width="37.8" height="2.6" rx="1.1" fill="#8C5A2B" stroke="#6B4320" stroke-width="0.5"/><rect x="5" y="26.4" width="5.2" height="1.5" rx="0.6" fill="#FFFFFF" stroke="#9AA3B5" stroke-width="0.35"/><rect x="11.4" y="26.4" width="4" height="1.5" rx="0.6" fill="#FFE680" stroke="#C9A455" stroke-width="0.35"/><rect x="17.5" y="25.6" width="6.4" height="2.3" rx="0.6" fill="#3A3C44"/><rect x="17.5" y="25.6" width="6.4" height="0.9" rx="0.4" fill="#B07A3C"/>
<path d="M25.5 38.6Q30 33 36.5 26.5" stroke="#FFFFFF" stroke-width="3" fill="none"/><path d="M25.5 38.6Q30 33 36.5 26.5" stroke="#3F7332" stroke-width="1.3" fill="none"/>${figLeaf(30.5, 33, 96)}${figLeaf(28.2, 35.6, 8)}
<path d="M27 37.4L23.6 36L24.8 39.4ZM27 37.4L29.8 39.8L26.2 39.8Z" fill="#F7B3C6" stroke="#D4476B" stroke-width="0.4"/>
${carnation(37.6, 23.6, 6.6)}
</g>` }

/**
 * 어버이날 — **캐릭터 없이**(사용자 2026-11-18, 스승의 날과 같은 방식). 부모님 가슴에 달아 드리는 카네이션 두 송이.
 * 붉은 카네이션과 분홍 카네이션(두 분) + 줄기와 잎 + 줄기를 묶은 금빛 리본. 캔버스 좌표(높이 40).
 */
FIGURES.eobeoi = { w: 42, draw: (tx) => `<g transform="translate(${tx} 0)" stroke-linejoin="round" stroke-linecap="round">
<path d="M14 20Q16 28 20.5 32M27 21Q25 28 20.5 32M20.5 32V38.5" stroke="#FFFFFF" stroke-width="3.2" fill="none"/><path d="M14 20Q16 28 20.5 32M27 21Q25 28 20.5 32M20.5 32V38.5" stroke="#3F7332" stroke-width="1.4" fill="none"/>
${figLeaf(16, 27, -62)}${figLeaf(25.5, 27.5, 60)}${figLeaf(20.5, 37, -36)}${figLeaf(20.5, 37, 40)}
${carnation(27.5, 14.5, 7.6, '#F58FB0', '#D4476B')}${carnation(13, 13, 8.6)}
<path d="M20.5 31.5Q13 26.5 12.5 31Q13 35 20.5 32.5Q28 35 28.5 31Q28 26.5 20.5 31.5Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="2.2"/><path d="M19.5 33L15 39.2L18.6 38.4L20.5 34ZM21.5 33L26 39.2L22.4 38.4L20.5 34Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="2.2"/>
<path d="M19.5 33L15 39.2L18.6 38.4L20.5 34ZM21.5 33L26 39.2L22.4 38.4L20.5 34Z" fill="#F2BC1B" stroke="#B88A1E" stroke-width="0.45"/><path d="M20.5 31.5Q13 26.5 12.5 31Q13 35 20.5 32.5Q28 35 28.5 31Q28 26.5 20.5 31.5Z" fill="#FFD23F" stroke="#B88A1E" stroke-width="0.5"/><circle cx="20.5" cy="32" r="1.5" fill="#F2BC1B" stroke="#B88A1E" stroke-width="0.45"/>
</g>` }

/**
 * 설날 — **캐릭터 없이**(사용자 2026-11-18). 복주머니 두 개와 방패연.
 * 복주머니는 사용자가 준 참고 그림의 느낌으로: 옆으로 둥글게 부푼 붉은 주머니와 푸른 주머니가 나란히, 오므려 주름 잡힌 입, 금빛 끈 매듭, 몸통에 바로 쓴 금빛 福, 발치의 금빛 엽전.
 * 방패연: 가운데 방구멍, 이마의 태극 꼭지(붉은 동그라미만 두면 일장기처럼 보인다), 아래 삼색 치마, 대나무 살. 캔버스 좌표(높이 40).
 */
FIGURES.seollal = { w: 57, draw: (tx) => {
  // 福: 10×10 칸에 그린 획(왼쪽 示 변 + 오른쪽 一·口·田) — 글꼴에 한자가 없어 직접 그린다
  const bok = (cx, cy, k) => `<g transform="translate(${f1(cx - 5 * k)} ${f1(cy - 5 * k)}) scale(${k})" fill="none"><g stroke="#8C5A0A" stroke-width="1.75"><path d="M1.6 0.6L2.6 1.5M0.4 3H3.8L1 6M2.3 4.4V9.6M2.9 5.4L3.9 6.4"/><path d="M5.2 1H9.6M5.9 2.5H8.9V4.4H5.9ZM5.2 5.6H9.6V9.5H5.2ZM7.4 5.6V9.5M5.2 7.55H9.6"/></g><g stroke="#FFD94A" stroke-width="1.05"><path d="M1.6 0.6L2.6 1.5M0.4 3H3.8L1 6M2.3 4.4V9.6M2.9 5.4L3.9 6.4"/><path d="M5.2 1H9.6M5.9 2.5H8.9V4.4H5.9ZM5.2 5.6H9.6V9.5H5.2ZM7.4 5.6V9.5M5.2 7.55H9.6"/></g></g>`
  // 주머니: 가운데 (cx, cy), 가로 반지름 rx, 세로 반지름 ry. 위에 오므린 주름 입과 금빛 끈
  const pouch = (cx, cy, rx, ry, fill, edge, hi) => {
    const top = cy - ry * 0.86
    const body = `M${f1(cx - rx * 0.3)} ${f1(top)}Q${f1(cx - rx * 1.22)} ${f1(cy - ry * 0.7)} ${f1(cx - rx)} ${f1(cy + ry * 0.2)}Q${f1(cx - rx * 0.86)} ${f1(cy + ry * 1.04)} ${cx} ${f1(cy + ry)}Q${f1(cx + rx * 0.86)} ${f1(cy + ry * 1.04)} ${f1(cx + rx)} ${f1(cy + ry * 0.2)}Q${f1(cx + rx * 1.22)} ${f1(cy - ry * 0.7)} ${f1(cx + rx * 0.3)} ${f1(top)}Z`
    const ruffle = `M${f1(cx - rx * 0.3)} ${f1(top + 0.6)}L${f1(cx - rx * 0.46)} ${f1(top - ry * 0.42)}Q${f1(cx - rx * 0.3)} ${f1(top - ry * 0.62)} ${f1(cx - rx * 0.15)} ${f1(top - ry * 0.4)}Q${cx} ${f1(top - ry * 0.66)} ${f1(cx + rx * 0.15)} ${f1(top - ry * 0.4)}Q${f1(cx + rx * 0.3)} ${f1(top - ry * 0.62)} ${f1(cx + rx * 0.46)} ${f1(top - ry * 0.42)}L${f1(cx + rx * 0.3)} ${f1(top + 0.6)}Z`
    const cord = `M${f1(cx - rx * 0.34)} ${f1(top + 0.3)}Q${cx} ${f1(top + 1.7)} ${f1(cx + rx * 0.34)} ${f1(top + 0.3)}`
    const loops = `M${cx} ${f1(top + 0.9)}Q${f1(cx - rx * 0.5)} ${f1(top - 1.6)} ${f1(cx - rx * 0.5)} ${f1(top + 1.4)}Q${f1(cx - rx * 0.3)} ${f1(top + 2.6)} ${cx} ${f1(top + 0.9)}Q${f1(cx + rx * 0.5)} ${f1(top - 1.6)} ${f1(cx + rx * 0.5)} ${f1(top + 1.4)}Q${f1(cx + rx * 0.3)} ${f1(top + 2.6)} ${cx} ${f1(top + 0.9)}`
    return `<path d="${body}" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="2.4"/><path d="${ruffle}" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="2.4"/><path d="${ruffle}" fill="${fill}" stroke="${edge}" stroke-width="0.5"/><path d="M${f1(cx - rx * 0.15)} ${f1(top - ry * 0.34)}V${f1(top)}M${f1(cx + rx * 0.15)} ${f1(top - ry * 0.34)}V${f1(top)}" stroke="${edge}" stroke-width="0.45" fill="none"/><path d="${body}" fill="${fill}" stroke="${edge}" stroke-width="0.5"/><path d="M${f1(cx - rx * 0.74)} ${f1(cy - ry * 0.3)}Q${f1(cx - rx * 0.86)} ${f1(cy + ry * 0.3)} ${f1(cx - rx * 0.56)} ${f1(cy + ry * 0.7)}" stroke="${hi}" stroke-width="1.1" fill="none"/><path d="M${f1(cx - rx * 0.12)} ${f1(top + 1.6)}Q${f1(cx - rx * 0.2)} ${f1(cy - ry * 0.3)} ${f1(cx - rx * 0.1)} ${f1(cy - ry * 0.12)}M${f1(cx + rx * 0.12)} ${f1(top + 1.6)}Q${f1(cx + rx * 0.2)} ${f1(cy - ry * 0.3)} ${f1(cx + rx * 0.1)} ${f1(cy - ry * 0.12)}" stroke="${edge}" stroke-width="0.4" fill="none"/><path d="${loops}" fill="none" stroke="#8C5A0A" stroke-width="1.5"/><path d="${loops}" fill="none" stroke="#F2BC1B" stroke-width="0.9"/><path d="${cord}" stroke="#8C5A0A" stroke-width="1.9" fill="none"/><path d="${cord}" stroke="#F2BC1B" stroke-width="1.2" fill="none"/>${bok(cx, cy + ry * 0.2, rx * 0.105)}`
  }
  const coin = (x, y) => `<ellipse cx="${x}" cy="${f1(y + 0.5)}" rx="2.7" ry="1.1" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="1.2"/><ellipse cx="${x}" cy="${f1(y + 0.5)}" rx="2.7" ry="1.1" fill="#C9930F"/><ellipse cx="${x}" cy="${y}" rx="2.7" ry="1.1" fill="#FFD94A" stroke="#C9930F" stroke-width="0.35"/>`
  const k = `<rect x="-8" y="-10.5" width="16" height="21" rx="0.6" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="2.2"/><rect x="-8" y="-10.5" width="16" height="21" rx="0.6" fill="#FDFBF5" stroke="${C.navy}" stroke-width="0.7"/><path d="M-8 4.4H-2.7V10.5H-8Z" fill="#2F6BFF"/><path d="M-2.7 4.4H2.7V10.5H-2.7Z" fill="#D2232A"/><path d="M2.7 4.4H8V10.5H2.7Z" fill="#FFD23F"/><path d="M-8 -10.5L8 10.5M8 -10.5L-8 10.5M0 -10.5V10.5M-8 -7.4H8" stroke="#B88A1E" stroke-width="0.45" fill="none"/>${miniTaegeuk(0, -6.5, 2.4)}<circle cx="0" cy="0.2" r="3.1" fill="#C9D8F2" stroke="${C.navy}" stroke-width="0.6"/><rect x="-8" y="-10.5" width="16" height="21" rx="0.6" fill="none" stroke="${C.navy}" stroke-width="0.7"/>`
  return `<g transform="translate(${tx} 0)" stroke-linejoin="round" stroke-linecap="round">
<path d="M49 13Q47 28 40 36" stroke="#FFFFFF" stroke-width="1.8" fill="none"/><path d="M49 13Q47 28 40 36" stroke="#8E99AE" stroke-width="0.6" fill="none"/><g transform="translate(49 12.5) rotate(14) scale(0.82)">${k}</g>
${pouch(30.5, 27.4, 11, 9.4, '#2B6FD6', '#1A49A0', '#6FA3F0')}${pouch(13.5, 26.4, 12.6, 10.6, '#D2232A', '#9C141B', '#F0666C')}
${coin(24, 37)}${coin(40, 37.4)}${coin(4.4, 37.6)}${coin(34.5, 38.2)}
</g>` } }

/** 어린이날 — 캐릭터가 풍선 세 개를 든다. 캐릭터 없는 풍선·바람개비 그림도 만들어 보았으나 기존대로 두기로 했다(사용자 2026-11-18) */
const balloons = (() => {
  const one = (x, y, fill, hi) => `<path d="M188 118Q${x - 8} ${y + 50} ${x} ${y + 18}" stroke="#FFFFFF" stroke-width="6" fill="none"/><path d="M188 118Q${x - 8} ${y + 50} ${x} ${y + 18}" stroke="#8E99AE" stroke-width="1.8" fill="none"/><ellipse cx="${x}" cy="${y}" rx="15" ry="17.5" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="7"/><path d="M${x - 4} ${y + 17}L${x + 4} ${y + 17}L${x} ${y + 23}Z" fill="${fill}"/><ellipse cx="${x}" cy="${y}" rx="15" ry="17.5" fill="${fill}"/><ellipse cx="${x - 5}" cy="${y - 7}" rx="3.5" ry="5.5" fill="${hi}" transform="rotate(-25 ${x - 5} ${y - 7})"/>`
  return one(244, 72, '#FFC83D', '#FFE39A') + one(210, 62, '#FF5A66', '#FFB3B9') + one(232, 42, '#3FA7FF', '#B8DDFF') + hand()
})()
EXTRAS.eorini = balloons

/**
 * 새해 첫날 — **캐릭터 없이** 희망찬 새해 해돋이(사용자 2026-11-18).
 * 해와 물결만 그리면 날씨 그림처럼 보인다 → 하늘 가득 겹겹이 번지는 햇무리, 수평선 양쪽의 섬, 바다에 길게 비친 햇빛 길, 날아가는 갈매기와 옅은 구름으로 「동해 해돋이」 장면을 만든다.
 * **해에서 사방으로 뻗는 빛살 무늬는 그리지 않는다** — 일본 전범기(욱일기)를 떠올리게 한다(사용자 지적). 햇빛은 둥근 햇무리로만 나타낸다. 생일 폭죽·색종이도 뺐다. 캔버스 좌표(높이 40).
 */
FIGURES.newyear = { w: 52, draw: (tx) => {
  const CX = 26, HY = 25.5
  return `<g transform="translate(${tx} 0)" stroke-linejoin="round" stroke-linecap="round">
<path d="M1 ${HY}A25 25 0 0 1 51 ${HY}Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="1.6"/>
<path d="M1 ${HY}A25 25 0 0 1 51 ${HY}Z" fill="#FFF3D0"/><path d="M8 ${HY}A18 18 0 0 1 44 ${HY}Z" fill="#FFE2A0"/><path d="M12.5 ${HY}A13.5 13.5 0 0 1 39.5 ${HY}Z" fill="#FFC56B"/>
<circle cx="${CX}" cy="${HY}" r="9.6" fill="#FF8A2A"/><circle cx="${CX}" cy="${HY}" r="7" fill="#FFAA3D"/><circle cx="${CX}" cy="${HY}" r="4.2" fill="#FFC95C"/>
<path d="M1 ${HY}H51V35.5Q51 38.6 47.8 38.6H4.2Q1 38.6 1 35.5Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="1.6"/><path d="M1 ${HY}H51V35.5Q51 38.6 47.8 38.6H4.2Q1 38.6 1 35.5Z" fill="#2B6FD6"/>
<path d="M1 ${HY}Q4.5 21.4 8 23.6Q10 24.6 12.5 ${HY}ZM51 ${HY}Q47 20.8 43 23.2Q40.6 24.4 38.5 ${HY}Z" fill="#1E3F8F"/>
<path d="M17.5 27.6H34.5M19.6 30H32.4M21.6 32.4H30.4M23.4 34.8H28.6M24.6 37H27.4" stroke="#FFD23F" stroke-width="1.15" fill="none"/><path d="M4 29.4H10M40 31H47M6 34H12M37.5 35.6H43" stroke="#6FA3F0" stroke-width="0.8" fill="none"/>
<path d="M9 12.5Q10.6 10.6 12.2 12.5Q13.8 10.6 15.4 12.5M36.5 9Q37.9 7.3 39.3 9Q40.7 7.3 42.1 9M41 15.5Q42.1 14.2 43.2 15.5Q44.3 14.2 45.4 15.5" stroke="#3A4A78" stroke-width="0.8" fill="none"/><g fill="#FFFFFF"><ellipse cx="11" cy="20.6" rx="5" ry="1.7"/><ellipse cx="14.4" cy="19.4" rx="3" ry="1.6"/><ellipse cx="41.5" cy="21.4" rx="4.6" ry="1.5"/><ellipse cx="38.6" cy="20.3" rx="2.6" ry="1.4"/></g>
</g>` } }

/**
 * 한반도 윤곽 — 손으로 그리지 않는다(어설프다 — 사용자 2026-11-18). 공개 지도 자료 Natural Earth 1:10m(world-atlas countries-10m, 공공 영역)에서
 * 남·북 경계를 지워 하나로 합치고, 위도 38도 기준 등거리 투영으로 높이 30 에 맞춘 뒤 Douglas–Peucker 로 단순화한 패스다(본토 166점 + 큰 섬 5개 — 제주 포함).
 * 울릉도·독도는 너무 작아 자료의 윤곽 대신 제 위치에 점으로 찍는다. 다시 뽑는 스크립트: robotikon/logo/korea-map.mjs
 */
const KOREA = { w: 19.2, h: 30, ulleung: [16,16.6], dokdo: [18.4,17.4], d: 'M10.1 13.3L12.6 17.9L12.5 21L13 21.1L12.7 22.6L11.6 24L11.5 23.7L10.9 23.9L10.6 23.5L10.8 24L10.1 24.1L10.4 24L10.3 24.6L10 24.3L9.4 24.4L9.4 23.9L9 24.3L8.6 24.2L8.2 24.3L8.4 24.7L8.7 24.6L8.4 25.3L8.1 24.5L7.7 24.7L8 25.4L7.7 25.4L7.9 25.5L7.7 25.8L7.2 25.5L7.7 25.1L7.4 24.8L6.6 25.9L6.3 25.4L5.7 26.3L5.8 25.5L5.2 25.4L5.2 24.9L5.6 25.4L5.4 25L6 25.3L5.3 24.9L6.1 24.7L5.8 24.4L5.7 24.8L5.4 24.8L5.5 24.2L5.2 24.3L5.1 23.8L5.3 23.7L5.5 24.1L5.2 23.5L5.7 22.6L6.1 22.5L5.6 22.2L6.1 21.8L6.3 21.9L6.2 21.6L6.5 21.5L5.7 21.2L6.5 21L6.1 21.2L5.7 20.8L5.9 20.6L5.7 19.9L6 19.7L5.7 19.8L5.6 18.9L5.5 19.3L5.3 18.9L5.2 19.4L5.1 19L4.9 19.1L4.8 18.9L5.2 18.2L5.3 18.8L5.3 18.1L5.6 18.6L5.7 18.3L5.9 18.5L5.7 18L6 18.4L6.4 18.3L6.5 18.9L7 18.2L6.7 18.4L6.6 17.9L6.3 17.9L6.5 17.6L6.1 17.7L6.1 17.4L6.5 17.3L5.9 17L5.7 15.9L6.3 16.2L6.1 15.6L5.9 15.8L5.4 15.5L4.8 15.9L4.6 15.5L4.3 15.6L3.5 15.1L3.9 15.4L2.9 16.1L3.3 15.5L2.9 15.7L2.7 15.4L2.1 15.4L2.8 14.9L1.3 14.8L2.1 14.5L1.8 14.1L2.4 13.2L3.7 13.3L3.2 12.8L2.8 13L2.4 12.7L3.1 11.5L3.2 11L2.9 10.8L3.2 10.4L2.4 10.5L1.5 9.8L1.2 10.4L1.1 9.7L0.5 9.4L0.9 9.1L0.6 8.9L1.9 7.7L2.2 7.8L2.2 7.5L3.1 7.3L3.9 6.5L4.5 6.5L5.7 5.1L6.1 3.9L6.7 3.7L7.2 4.6L9.2 4.7L9.6 5L9.9 4.4L9.3 3.2L11.4 3L12.4 1.8L12.8 2L13.2 1.8L13.7 0.2L14.2 0.2L15.6 2.3L14.9 2.2L13.2 4.2L13.1 4.8L13.5 5L13.3 6.6L12.1 7L11.8 7.7L10.7 8.2L10.7 8.6L10 9.1L9.2 9L8 10L8.2 11.2L8 10.8L7.7 11L8 11.1L7.8 11.5L8.7 11.8ZM6.7 28.7L6.5 29.2L5.9 29.4L4.9 29.3L5.6 28.6L6.4 28.5ZM11 24.5L10.6 25L10.4 24.5L10.7 24.5L10.9 24ZM5.4 25.7L5 26.1L4.7 25.9L5.1 25.4ZM9.4 24.7L9.3 25.1L9 25L8.8 24.5L9 24.3L9 24.6ZM5.7 16.4L5.4 16.3L5.5 15.7Z' }

/** 붓글씨 「광복절」 — 나눔손글씨 붓(OFL) 글자를 패스로 굳힌 것(왼쪽 위가 0,0, 폭 40.9 · 높이 20). 다시 뽑는 스크립트: robotikon/logo/brush-text.mjs */
/** 이름을 붓글씨로 쓰는 날 — 그림 안에 넣으면 실제 헤더 크기에서 글자로 읽히지 않아, 한글판 로고의 작은 이름 자리에 크게 쓴다(사용자 2026-11-18) */
export const THEME_BRUSH = {}
const BRUSH_GWANGBOK = { w: 40.9, h: 20, d: 'M11.1 1.3L11.1 1.3Q11.1 1.5 11.1 1.9Q11.1 2.3 11.1 2.9Q11.1 3.6 11.1 4.3Q11.1 5.1 11 5.8L11 5.8Q11.9 5.5 12.9 5.3Q13.8 5.1 14.7 5L14.7 5Q14.9 5 15.1 5.1Q15.4 5.3 15.6 5.3L15.6 5.3Q15.6 5.3 15.7 5.3Q15.9 5.4 16 5.5Q16.1 5.6 16.2 5.7Q16.3 5.9 16.3 6L16.3 6Q15 6.2 13.7 6.6Q12.3 6.9 10.9 7.5L10.9 7.5Q10.8 8.5 10.7 9.3Q10.6 10.2 10.3 10.6L10.3 10.6Q10.1 10.6 9.9 10.3L9.9 10.3Q9.9 10.1 9.9 10Q9.9 9.9 9.8 9.6L9.8 9.6Q9.8 9.5 9.7 9.4Q9.6 9.2 9.6 9.1L9.6 9.1Q9.5 7.2 9.6 5.2Q9.7 3.2 9.9 1.3L9.9 1.3Q10 1.1 10.1 1.1Q10.2 1.1 10.3 1L10.3 1Q10.5 0.9 10.6 0.8Q10.7 0.8 10.9 0.8L10.9 0.8Q11 0.9 11 1Q11.1 1.1 11.1 1.3ZM12.1 10.8L12.1 10.8Q12.3 10.8 12.3 10.9Q12.4 11 12.5 11L12.5 11Q12.6 11.1 12.7 11.2Q12.8 11.3 12.9 11.5L12.9 11.5Q12.9 11.6 12.9 11.8Q12.9 11.9 12.9 12.1L12.9 12.1Q13 13.5 12.5 14.4Q12.1 15.3 11.2 15.8L11.2 15.8Q11 15.8 10.8 15.9Q10.5 16 10.3 16.1L10.3 16.1Q10.2 16.2 10.1 16.2Q10 16.1 9.9 16.1L9.9 16.1Q9.8 15.9 9.8 15.9Q9.8 15.8 9.7 15.6L9.7 15.6Q9.6 15.3 9.5 14.9L9.5 14.9Q9.4 14.3 9.5 13.7Q9.7 13 9.9 12.4Q10.1 11.8 10.4 11.2Q10.7 10.7 11.1 10.2L11.1 10.2Q11.2 10.1 11.3 10.1L11.3 10.1Q11.4 10.1 11.4 10.2L11.4 10.2Q11.5 10.3 11.5 10.4Q11.5 10.6 11.6 10.6L11.6 10.6Q11.7 10.8 11.6 10.9L11.6 10.9Q11.9 10.8 12.1 10.8ZM2.8 8L2.8 8Q2.8 8.6 2.8 9.2Q2.8 9.9 2.8 10.5Q2.8 11.2 2.7 11.8Q2.6 12.5 2.4 13L2.4 13Q3.3 12.4 4.1 11.9Q4.9 11.4 5.7 10.9Q6.5 10.4 7.2 9.9Q7.9 9.4 8.4 8.7L8.4 8.7Q8.6 8.8 8.5 9Q8.4 9.2 8.4 9.3L8.4 9.3Q8.3 9.8 8.1 10.1L8.1 10.1Q7.4 10.9 6.7 11.6Q6 12.3 5.1 12.9Q4.3 13.5 3.5 13.8Q2.6 14.2 1.7 14.3L1.7 14.3Q1.3 14.2 1.2 14Q1.2 13.7 1.3 13.5L1.3 13.5Q1.4 13.2 1.6 13Q1.7 12.7 1.7 12.5L1.7 12.5Q1.8 12.2 1.8 11.5Q1.9 10.8 1.9 10Q2 9.2 2 8.4Q2.1 7.6 2.1 6.9L2.1 6.9Q2.3 6.9 2.4 7.1Q2.5 7.3 2.6 7.4L2.6 7.4Q2.7 7.5 2.8 7.7Q2.8 7.8 2.8 8ZM4.8 4.7L4.8 4.7Q4.9 4.9 5 5.1Q5 5.3 5.1 5.5L5.1 5.5Q5.3 6.2 5.3 6.9Q5.3 7.6 5.2 8.3Q5.1 9 5 9.7Q4.8 10.4 4.7 11L4.7 11Q4.5 11 4.5 10.6Q4.4 10.3 4.4 10L4.4 10L4.3 9.5Q4.3 9.2 4.3 8.8Q4.4 8.3 4.4 7.8Q4.4 7.3 4.4 6.7Q4.3 6.2 4.3 5.7L4.3 5.7Q3.8 5.7 3.3 5.8Q2.7 5.9 2.1 6Q1.5 6.1 1 6.2Q0.5 6.4 0.2 6.5L0.2 6.5Q0.2 6.5 0.1 6.5Q0 6.5 0 6.4L0 6.4Q0 6.1 0.1 6Q0.2 5.8 0.3 5.6L0.3 5.6Q0.3 5.5 0.4 5.3Q0.4 5.2 0.5 5.1L0.5 5.1Q1.1 4.6 2 4.3Q2.8 4 3.6 4.2L3.6 4.2Q3.8 4.2 4 4.2Q4.2 4.2 4.4 4.3L4.4 4.3Q4.5 4.4 4.6 4.5Q4.7 4.6 4.8 4.7ZM10.4 14.5L10.4 14.5Q10.5 14.4 10.7 14.2Q11 14.1 11.2 13.8Q11.4 13.6 11.5 13.3Q11.7 13.1 11.7 12.8L11.7 12.8Q11.9 12.5 11.7 12.1Q11.6 11.8 11.3 11.5L11.3 11.5Q11 12.1 10.8 12.9Q10.5 13.6 10.4 14.5ZM23.8 11.3L23.8 11.3Q24.1 11.3 24.4 11.3Q24.7 11.4 24.9 11.4L24.9 11.4Q24.7 11.6 24.7 11.6Q24.6 11.6 24.4 11.7L24.4 11.7Q24.2 11.8 23.9 12Q23.7 12.2 23.5 12.3L23.5 12.3Q23 12.5 22.4 12.7Q21.8 12.9 21.1 13.1Q20.4 13.3 19.8 13.4Q19.2 13.6 18.7 13.6L18.7 13.6Q18.4 13.7 18.1 13.7Q17.9 13.6 17.6 13.6L17.6 13.6Q17 13.7 16.8 13.6Q16.6 13.4 16.7 13.3L16.7 13.3Q16.8 13 18.2 12.4L18.2 12.4L18.4 12.3Q18.5 11.9 18.7 11.5Q18.9 11.1 19.1 10.6Q19.4 10.1 19.6 9.6Q19.8 9.1 19.9 8.7L19.9 8.7Q19.6 8.8 19.3 8.9Q18.9 8.9 18.7 8.7L18.7 8.7Q18.5 8.6 18.5 8.4Q18.4 8.2 18.3 8L18.3 8Q17.9 7.2 18.1 6.3L18.1 6.3Q18.1 5.8 18.4 5.3Q18.7 4.8 19.1 4.4Q19.5 4.1 19.9 3.9Q20.3 3.7 20.7 3.7L20.7 3.7Q20.8 3.1 20.9 2.6Q21 2 21.1 1.4L21.1 1.4Q21.2 1.2 21.2 1.1Q21.2 1 21.3 0.8L21.3 0.8Q21.5 0.7 21.6 0.6Q21.8 0.5 21.9 0.4L21.9 0.4Q22 0.3 22.1 0.2Q22.3 0.1 22.4 0L22.4 0Q22.5 0 22.6 0.2Q22.6 0.4 22.6 0.6Q22.6 0.8 22.6 1.1Q22.6 1.3 22.6 1.5L22.6 1.5Q22.4 2.2 22.2 3.1Q22 3.9 21.8 4.8Q21.6 5.6 21.3 6.3Q21.1 7.1 20.8 7.7L20.8 7.7Q20.7 8.1 20.5 8.7Q20.3 9.3 20.1 9.9Q19.9 10.5 19.7 11Q19.5 11.6 19.3 12L19.3 12Q20.5 11.7 21.6 11.5Q22.7 11.4 23.8 11.3ZM23.5 15.7L23.5 15.7Q23.4 16.3 23.2 17.3Q23 18.2 23 19.2L23 19.2Q23 19.4 22.8 19.7Q22.6 20 22.5 20L22.5 20Q22.3 20 22.3 19.8Q22.4 19.7 22.3 19.5L22.3 19.5Q22.3 18.6 22.4 17.6Q22.5 16.6 22.8 15.9L22.8 15.9Q22.2 15.9 21.7 16.1Q21.2 16.2 20.6 16.4Q20.1 16.5 19.7 16.7Q19.2 16.9 18.9 17L18.9 17Q18.8 17 18.9 16.9Q18.9 16.8 18.9 16.7L18.9 16.7Q18.9 16.5 19 16.3L19 16.3Q19.2 15.9 19.6 15.6Q20 15.3 20.5 15.1L20.5 15.1Q21.3 14.7 22 14.7L22 14.7Q22.2 14.7 22.4 14.7Q22.6 14.7 22.7 14.7L22.7 14.7Q22.9 14.8 23.1 14.9Q23.3 15 23.4 15.1L23.4 15.1Q23.5 15.2 23.5 15.4Q23.5 15.6 23.5 15.7ZM16.7 9.1L16.7 9.1Q16.7 9.4 16.5 9.4L16.5 9.4Q16.5 9.4 16.4 9.3Q16.4 9.2 16.3 9.1L16.3 9.1Q15.9 8.5 15.8 7.7Q15.7 6.8 15.8 5.9Q15.9 5 16.2 4.1Q16.4 3.1 16.8 2.4L16.8 2.4Q16.8 2.4 16.9 2.3Q16.9 2.3 17 2.3L17 2.3Q17 2.2 17.2 2.1Q17.4 2 17.4 2.1L17.4 2.1L17.1 3.9Q16.9 5.3 16.8 6.6Q16.7 7.8 16.7 9.1ZM19.3 6L19.3 6Q19.1 6.7 19.2 7.1Q19.3 7.6 19.7 7.5L19.7 7.5Q19.8 7.5 20 6.6Q20.2 5.8 20.6 4.4L20.6 4.4Q20.2 4.6 19.8 5Q19.4 5.5 19.3 6ZM38.2 1.8L38.2 1.8Q38.1 2.7 37.8 3.7Q37.5 4.6 37.1 5.5Q36.7 6.4 36.3 7.1Q35.8 7.8 35.5 8.1L35.5 8.1Q35.5 7.8 35.5 7.7Q35.6 7.6 35.6 7.3L35.6 7.3Q35.6 7.2 35.6 7Q35.6 6.8 35.7 6.7L35.7 6.7Q35.8 6 36 5.3Q36.3 4.6 36.4 3.7L36.4 3.7Q35.3 4.5 34.2 5.3Q33 6.1 31.9 6.6L31.9 6.6Q31.5 6.8 31.2 7Q30.8 7.1 30.4 7.3L30.4 7.3Q30.5 7.2 30.5 7.1Q30.5 7 30.6 6.9Q30.7 6.7 31 6.6Q31.2 6.4 31.6 6.1L31.6 6.1Q32 5.8 32.7 5.3Q33.4 4.8 34.2 4.2Q35 3.6 35.8 2.9Q36.7 2.2 37.4 1.5L37.4 1.5Q37.5 1.4 37.6 1.2Q37.7 1.1 37.8 1.2L37.8 1.2Q37.9 1.2 37.9 1.3Q37.9 1.4 38 1.5L38 1.5Q38.1 1.6 38.1 1.6Q38.2 1.7 38.2 1.8ZM28.5 2.9L28.5 2.9Q28.3 3.4 27.9 4.1Q27.6 4.8 27.2 5.5Q26.8 6.3 26.4 7.1Q26 8 25.6 8.8L25.6 8.8Q25.6 8.9 25.5 8.9Q25.4 9 25.3 9.1L25.3 9.1Q25.3 9.1 25.2 9.2Q25.2 9.3 25.2 9.3L25.2 9.3Q25 9.3 25 9.3Q25 9.3 25 9.2L25 9.2Q25.1 8.6 25.4 7.8Q25.7 7 26 6.2Q26.3 5.4 26.7 4.7Q27 4 27.2 3.4L27.2 3.4Q27.4 3.2 27.4 3Q27.4 2.8 27.3 2.8L27.3 2.8Q27.2 2.8 26.9 2.8Q26.7 2.9 26.4 3Q26.1 3.1 25.9 3.2Q25.6 3.4 25.4 3.5L25.4 3.5Q25.3 3.5 25.2 3.3Q25.1 3.1 25 3L25 3Q24.8 2.9 25 2.6L25 2.6Q25.4 2.3 26 2Q26.6 1.7 27.3 1.7L27.3 1.7Q27.5 1.7 27.8 1.8L27.8 1.8Q28 1.8 28.1 2Q28.2 2.1 28.3 2.2L28.3 2.2Q28.4 2.4 28.5 2.5Q28.6 2.7 28.5 2.9ZM29 11.7L29 11.7Q28.9 11.2 28.8 10.6Q28.7 10 28.5 9.4Q28.4 8.8 28.2 8.3Q28 7.7 27.7 7.3L27.7 7.3Q27.7 7.2 27.8 7.1Q27.8 7 27.9 6.8L27.9 6.8Q28 6.7 28 6.6Q28 6.4 28.1 6.4L28.1 6.4Q28.2 6.3 28.4 6.4Q28.5 6.5 28.7 6.6L28.7 6.6Q29.3 7.6 29.6 8.7Q29.8 9.8 29.7 10.9L29.7 10.9Q29.7 11 29.7 11.1Q29.6 11.2 29.6 11.4L29.6 11.4Q29.5 11.4 29.5 11.5Q29.5 11.7 29.4 11.7L29.4 11.7Q29.3 11.8 29.1 11.8Q29 11.8 29 11.7ZM35.9 10.3L35.9 10.3Q35.5 10.6 35.2 11Q34.9 11.4 34.6 11.8Q34.3 12.1 34 12.5Q33.8 12.8 33.6 13.1L33.6 13.1Q33.8 13 34.1 12.9Q34.4 12.8 34.8 12.8Q35.2 12.7 35.6 12.6Q36 12.5 36.4 12.5L36.4 12.5Q36.6 12.5 36.7 12.5Q36.8 12.5 37 12.5L37 12.5Q37.1 12.5 37.2 12.6Q37.3 12.7 37.4 12.8L37.4 12.8Q37.6 12.9 37.7 13Q37.7 13.1 37.6 13.3Q37.5 13.4 37.4 13.5Q37.3 13.7 37.1 13.8L37.1 13.8Q36.7 14.4 36.3 15Q35.8 15.6 35.4 16.1Q35 16.7 34.7 17.3Q34.4 17.8 34.2 18.2L34.2 18.2Q34.9 18 35.5 17.8Q36.1 17.6 36.7 17.5Q37.3 17.3 37.9 17.2Q38.5 17 39.2 16.9L39.2 16.9Q39.4 16.8 39.6 16.8Q39.8 16.7 40 16.7L40 16.7Q40.3 16.6 40.5 16.6Q40.7 16.5 40.9 16.5L40.9 16.5Q40.6 16.8 39.9 17.1Q39.3 17.4 38.6 17.7Q37.8 18 37 18.2Q36.2 18.5 35.5 18.7Q34.8 18.9 34.2 19.1Q33.6 19.2 33.3 19.3L33.3 19.3Q33.3 19.3 33.3 19.1Q33.3 18.9 33.3 18.8L33.3 18.8Q33.3 18.7 33.3 18.7Q33.3 18.7 33.4 18.5L33.4 18.5Q33.8 17.3 34.6 16.1Q35.4 15 36.2 13.8L36.2 13.8Q35.5 13.9 34.5 14.2Q33.5 14.5 32.4 14.8L32.4 14.8Q32.2 14.8 32.2 14.7Q32.2 14.6 32.1 14.5L32.1 14.5Q32.1 14.4 32.2 14.2Q32.2 14.1 32.3 13.9L32.3 13.9Q32.4 13.7 32.5 13.5Q32.6 13.4 32.7 13.2L32.7 13.2Q33.1 12.5 33.7 11.7Q34.3 11 34.9 10.2L34.9 10.2Q34.9 10.1 34.6 10.1Q34.2 10.1 33.7 10.2Q33.2 10.3 32.6 10.5Q31.9 10.6 31.3 10.8L31.3 10.8Q31.3 10.7 31.4 10.6Q31.4 10.4 31.5 10.3L31.5 10.3Q31.7 10.1 31.7 9.9Q31.8 9.7 32 9.6L32 9.6Q32.2 9.5 32.5 9.4Q32.8 9.3 33.2 9.1Q33.6 9 34 8.9Q34.4 8.8 34.7 8.8L34.7 8.8Q34.9 8.8 35 8.8Q35.1 8.8 35.3 8.9L35.3 8.9Q35.7 9 35.9 9.2L35.9 9.2Q35.9 9.3 36 9.4Q36.1 9.6 36.1 9.7L36.1 9.7Q36.1 9.9 36 10Q35.9 10.1 35.9 10.3Z' }

/**
 * 광복절 — **캐릭터 없이**(사용자 2026-11-18 최종: 「무궁화 배경에 태극기」). 무궁화 꽃밭 앞에서 펄럭이는 태극기.
 * 뒤: 무궁화 여섯 송이와 잎이 둘레를 채운다(나라꽃). 앞: 물결치는 태극기(건곤감리가 깃면 안에). 빛살 무늬는 쓰지 않는다. 캔버스 좌표(높이 40).
 * 이름은 붓글씨로 로고의 이름 자리에 크게 쓴다(THEME_BRUSH). 앞서 그려 본 수채화 태극·한반도 지도 안은 버렸다(한반도 윤곽 KOREA 는 다른 날에 쓸 수 있어 남긴다).
 */
THEME_BRUSH.gwangbok = BRUSH_GWANGBOK
FIGURES.gwangbok = { w: 54, draw: (tx) => {
  const flower = (cx, cy, r, rot = 0) => {
    let petals = '', veins = ''
    for (let k = 0; k < 5; k++) {
      const a = rad(-90 + rot + k * 72)
      petals += `<circle cx="${f1(cx + Math.cos(a) * r * 0.56)}" cy="${f1(cy + Math.sin(a) * r * 0.56)}" r="${f1(r * 0.56)}"/>`
      veins += `M${cx} ${cy}L${f1(cx + Math.cos(a) * r * 0.62)} ${f1(cy + Math.sin(a) * r * 0.62)}`
    }
    return `<g fill="#FFFFFF" stroke="#FFFFFF" stroke-width="1.6">${petals}</g><g fill="#F7B3C6" stroke="#EE8DAA" stroke-width="0.3">${petals}</g><path d="${veins}" stroke="#D4476B" stroke-width="${f1(r * 0.3)}" stroke-linecap="round"/><circle cx="${cx}" cy="${cy}" r="${f1(r * 0.3)}" fill="#D4476B"/><circle cx="${cx}" cy="${cy}" r="${f1(r * 0.13)}" fill="#FFD95A"/>`
  }
  const leaf = (x, y, ang, len = 8.5) => `<g transform="translate(${x} ${y}) rotate(${ang})"><path d="M0 0Q${f1(len * 0.38)} ${f1(-len * 0.4)} 0 ${-len}Q${f1(-len * 0.38)} ${f1(-len * 0.4)} 0 0Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="1.6"/><path d="M0 0Q${f1(len * 0.38)} ${f1(-len * 0.4)} 0 ${-len}Q${f1(-len * 0.38)} ${f1(-len * 0.4)} 0 0Z" fill="#5E9C4B"/><path d="M0 -0.6V${f1(-len * 0.78)}" stroke="#3F7332" stroke-width="0.5"/></g>`
  const leaves = leaf(15, 8, -40) + leaf(39, 7, 44) + leaf(3.5, 23, -96) + leaf(50.5, 22, 96) + leaf(16, 33.5, -140) + leaf(38.5, 34, 140) + leaf(27, 4, 0, 7) + leaf(27, 37, 180, 7)
  const flowers = flower(7.5, 11, 6.4, 10) + flower(46.5, 11.5, 6.2, -8) + flower(7, 30, 6, -12) + flower(47, 30.5, 6.4, 14) + flower(21, 5.6, 4.6, 20) + flower(33.5, 35, 4.8, -20)
  return `<g transform="translate(${tx} 0)" stroke-linejoin="round" stroke-linecap="round">
${leaves}${flowers}
<g transform="translate(9 10.6) rotate(-5) scale(0.285)">${flagWave}</g>
</g>` } }

/**
 * 일본군 '위안부' 피해자 기림의 날(8월 14일) — **캐릭터 없이**(사용자 2026-11-18). 1991년 8월 14일 김학순 할머니의 첫 공개 증언을 기리는 날.
 * 피해자를 상징하는 **노란 나비** 세 마리가 날아오른다(크기와 기울기를 달리해). 추모의 날이라 다른 장식은 넣지 않는다. 캔버스 좌표(높이 40).
 */
FACES.girim = ''
// 법정 이름 그대로: 일본군'위안부' 피해자 기림의 날(작은따옴표는 가해자 쪽 용어를 그대로 받아들이지 않는다는 공식 표기) — 사용자 2026-11-18 「일본군 넣어」
// 이름은 끊지 않고 한 줄에 다 쓴다(사용자 2026-11-18 「풀네임 넣으라고」) — 글자 크기도 줄이지 않는다, 로고가 그만큼 길어진다
THEME_NAMES.girim = "일본군 '위안부' 피해자 기림의 날"
THEME_DATES.girim = '1991.8.14'
FIGURES.girim = { w: 42, draw: (tx) => {
  const wingU = 'M0 0C3.6 -8.6 12.4 -9.6 12.6 -3C12.8 2 6 3.2 0 1.2Z'
  const wingL = 'M0 1.2C5 3 9.6 6 8 9.8C6.2 12.6 1.2 9 0 3.4Z'
  const half = (flip) => `<g transform="scale(${flip} 1)"><path d="${wingU}" fill="#FFD23F" stroke="#E0A010" stroke-width="0.5"/><path d="${wingL}" fill="#FFC21A" stroke="#E0A010" stroke-width="0.5"/><path d="M1.6 -0.4C4.4 -5.6 9.4 -6.6 10 -3.4" fill="none" stroke="#FFE98A" stroke-width="1.1"/><path d="M1.4 2.6C4 4 6.4 6 6 8" fill="none" stroke="#FFE98A" stroke-width="0.9"/></g>`
  const under = (flip) => `<g transform="scale(${flip} 1)"><path d="${wingU}"/><path d="${wingL}"/></g>`
  const butterfly = (x, y, k, rot) => `<g transform="translate(${x} ${y}) rotate(${rot}) scale(${k})"><g fill="#FFFFFF" stroke="#FFFFFF" stroke-width="2">${under(1)}${under(-1)}</g>${half(1)}${half(-1)}<path d="M0 -1.4Q-1.6 -5 -3.4 -5.8M0 -1.4Q1.6 -5 3.4 -5.8" fill="none" stroke="#6B4A12" stroke-width="0.45"/><ellipse cx="0" cy="2.4" rx="0.85" ry="4" fill="#6B4A12"/></g>`
  return `<g transform="translate(${tx} 0)" stroke-linejoin="round" stroke-linecap="round">${butterfly(13.5, 14, 1.12, -18)}${butterfly(33, 9.5, 0.56, 16)}${butterfly(27, 28.5, 0.82, 8)}</g>` } }

/** 머리 색을 바꾸는 날 — 부처님오신날은 금빛 */
const HEADS = { buddha: '#E9B949' }
Object.assign(BACKS, { buddha: halo })
Object.assign(FACES, { buddha: buddhaFace })
Object.assign(EXTRAS, { buddha: buddhaLook })

/** 그림 폭(아바타 좌표). 국경일 변형은 머리 오른쪽에 든 것이 있어 넓다(10..268) */
export const mascotUnits = (theme) => (!theme ? 180 : theme in FIGURES ? FIGURES[theme].w / MASCOT_S : theme === 'gukgun' ? 226 : 258)

/**
 * 무궁화 다발(캔버스 좌표) — 로고 끝에 놓는 꽃 두 송이와 잎. 왼쪽 끝 x, 바닥 y. 돌려주는 것: svg 와 폭
 * 꽃: 연분홍 꽃잎 다섯 장 + 진분홍 속(단심) + 노란 꽃술
 */
export function mugunghwaCluster(x, bottom) {
  const flower = (cx, cy, r) => {
    let petals = ''
    for (let k = 0; k < 5; k++) {
      const a = ((-90 + k * 72) * Math.PI) / 180
      petals += `<circle cx="${+(cx + Math.cos(a) * r * 0.56).toFixed(2)}" cy="${+(cy + Math.sin(a) * r * 0.56).toFixed(2)}" r="${+(r * 0.56).toFixed(2)}"/>`
    }
    let rays = ''
    for (let k = 0; k < 5; k++) {
      const a = ((-90 + k * 72) * Math.PI) / 180
      rays += `M${cx} ${cy}L${+(cx + Math.cos(a) * r * 0.62).toFixed(2)} ${+(cy + Math.sin(a) * r * 0.62).toFixed(2)}`
    }
    return `<g fill="#FFFFFF" stroke="#FFFFFF" stroke-width="1.6">${petals}</g><g fill="#F7B3C6">${petals}</g><path d="${rays}" stroke="#D4476B" stroke-width="${+(r * 0.3).toFixed(2)}" stroke-linecap="round"/><circle cx="${cx}" cy="${cy}" r="${+(r * 0.3).toFixed(2)}" fill="#D4476B"/><circle cx="${cx}" cy="${cy}" r="${+(r * 0.13).toFixed(2)}" fill="#FFD95A"/>`
  }
  const leaf = (cx, cy, a) => `<g transform="translate(${cx} ${cy}) rotate(${a})"><path d="M0 0Q3.2 -3.4 0 -8.5Q-3.2 -3.4 0 0Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="1.6"/><path d="M0 0Q3.2 -3.4 0 -8.5Q-3.2 -3.4 0 0Z" fill="#5E9C4B"/><path d="M0 -0.5V-6.5" stroke="#3F7332" stroke-width="0.5"/></g>`
  const y = bottom
  x += 7 // 왼쪽으로 누운 잎이 앞 글자를 덮지 않게
  const svg = leaf(x + 3.2, y - 1.5, -70) + leaf(x + 15.5, y - 2, 62) + leaf(x + 9, y - 0.5, 8) + flower(x + 12.5, y - 7.2, 5) + flower(x + 5.8, y - 5, 5.8)
  return { svg, width: 27 }
}

// 마스코트 크기(평소·기념일 같음) — 예전 scale 0.26(높이 40)에서 높이를 4 줄였다(가로도 같은 비율 — 사용자 2026-11-18). 머리 몸통 중앙은 그대로 y=23.
// 글자 높이 안에 넣어 보았으나 헤더 크기에서 너무 작아 이 크기로 정했다.
export const MASCOT_S = 0.234
/** 머리 몸통 중앙(아바타 y=107)을 cy 에 놓는 ty */
export const mascotTop = (cy, s) => +(cy - 50 * s).toFixed(2)
export const MASCOT_CY = 23

/** 심볼: 아바타 200 좌표의 머리(10..190 × 57..157) — 안테나 공(y=36, r12+테두리)이 위로, 다리가 아래로 튀어나온다 */
export function mascot(tx, ty, s, head, theme = '') {
  if (!(theme in FACES)) throw new Error('모르는 THEME: ' + theme)
  if (theme in FIGURES) return FIGURES[theme].draw(tx)
  return `<g transform="translate(${tx} ${ty}) scale(${s}) translate(-10 -57)">
${BACKS[theme] ?? ''}
<g stroke="#FFFFFF" stroke-width="10" fill="#FFFFFF" stroke-linejoin="round"><circle cx="${BALL.cx}" cy="${BALL.cy}" r="${BALL.r}"/><rect x="16" y="92" width="18" height="34" rx="9"/><rect x="166" y="92" width="18" height="34" rx="9"/><rect x="28" y="62" width="144" height="90" rx="40"/></g>
${legs('stroke="#FFFFFF" stroke-width="26" stroke-linecap="round"')}
${legs(`stroke="${C.navy}" stroke-width="16" stroke-linecap="round"`)}
<circle cx="${BALL.cx}" cy="${BALL.cy}" r="${BALL.r}" fill="${C.orange}"/>
<rect x="16" y="92" width="18" height="34" rx="9" fill="${C.navy}"/><rect x="166" y="92" width="18" height="34" rx="9" fill="${C.navy}"/>
<rect x="28" y="62" width="144" height="90" rx="40" fill="${HEADS[theme] ?? head}"/>
<rect x="46" y="76" width="108" height="62" rx="28" fill="${C.navy}"/>
${FACES[theme]}
${EXTRAS[theme]}
</g>`
}
