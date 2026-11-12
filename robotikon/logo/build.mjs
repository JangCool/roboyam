// 확정 로고 4종 SVG — 글자는 개구체(Gaegu 700) 윤곽을 패스로 굳힌다(웹 글꼴 없이 <img> 로 보이게).
import fs from 'node:fs'
import path from 'node:path'
import opentype from 'opentype.js'

const dir = path.resolve('node_modules/@fontsource/gaegu/files')
const fonts = fs.readdirSync(dir).filter((f) => f.endsWith('-700-normal.woff')).map((f) => {
  const b = fs.readFileSync(path.join(dir, f))
  return opentype.parse(b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength))
})
const fontFor = (ch) => fonts.find((ft) => ft.charToGlyphIndex(ch) > 0) ?? (() => { throw new Error('no glyph ' + ch) })()

/** 글자열 → [{d, color}] 와 너비. size 는 px(em) */
function textPaths(runs, x0, baseline, size) {
  let x = x0
  const out = []
  for (const { text, color } of runs) {
    for (const ch of text) {
      const ft = fontFor(ch)
      const g = ft.charToGlyph(ch)
      const p = g.getPath(x, baseline, size)
      out.push({ d: p.toPathData(2), color, bb: p.getBoundingBox() })
      x += (g.advanceWidth / ft.unitsPerEm) * size
    }
  }
  return { out, width: x - x0 }
}

const C = { navy: '#0E1F4D', blue: '#2F6BFF', orange: '#FF9F2E', yellow: '#FFC83D', white: '#FFFFFF' }

// 다리: 로보티콘(robotikon/src/parts.mjs body)과 같은 A자 — 꼭짓점 100,42 에서 머리 뒤로 내려가 바닥(46,164 / 154,164 기울기)으로 나온다.
// 안테나 공(100,36 r12)이 꼭짓점에 얹혀 꼭대기를 대부분 가리고, 공과 머리 사이에 A 의 어깨만 살짝 보인다(사용자 2026-11-13 「로보티콘처럼, A 꼭대기는 너무 보이지 않게, 그래도 A 처럼」).
// 머리 몸통 중앙(y=107)을 글자 중앙에 맞추는 정렬은 그대로. 캔버스 높이 40 안에 들도록 바닥 159 이하
const LEG_END = Number(process.env.LEG_END ?? 159)
const legX = (y, side) => +(100 + side * 54 * ((y - 42) / 122)).toFixed(2)
const legs = (attrs) => [-1, 1].map((side) => `<line x1="100" y1="42" x2="${legX(LEG_END, side)}" y2="${LEG_END}" ${attrs}/>`).join('')
const BALL = { cx: 100, cy: 36, r: 12 }

// 심볼: 아바타 200 좌표의 머리(10..190 × 57..157) — 안테나 공(y=36, r12+테두리)이 위로, 다리가 아래로 튀어나온다
function symbol(tx, ty, s, head) {
  return `<g transform="translate(${tx} ${ty}) scale(${s}) translate(-10 -57)">
<g stroke="#FFFFFF" stroke-width="10" fill="#FFFFFF" stroke-linejoin="round"><circle cx="${BALL.cx}" cy="${BALL.cy}" r="${BALL.r}"/><rect x="16" y="92" width="18" height="34" rx="9"/><rect x="166" y="92" width="18" height="34" rx="9"/><rect x="28" y="62" width="144" height="90" rx="40"/></g>
${legs('stroke="#FFFFFF" stroke-width="26" stroke-linecap="round"')}
${legs(`stroke="${C.navy}" stroke-width="16" stroke-linecap="round"`)}
<circle cx="${BALL.cx}" cy="${BALL.cy}" r="${BALL.r}" fill="${C.orange}"/>
<rect x="16" y="92" width="18" height="34" rx="9" fill="${C.navy}"/><rect x="166" y="92" width="18" height="34" rx="9" fill="${C.navy}"/>
<rect x="28" y="62" width="144" height="90" rx="40" fill="${head}"/>
<rect x="46" y="76" width="108" height="62" rx="28" fill="${C.navy}"/>
<path d="M68 112 Q80 94 92 112" stroke="${C.yellow}" stroke-width="8" fill="none" stroke-linecap="round"/><path d="M108 112 Q120 94 132 112" stroke="${C.yellow}" stroke-width="8" fill="none" stroke-linecap="round"/>
</g>`
}

// 캔버스 높이 40: 머리 몸통 높이 26(180×100 → s=0.26 → 너비 46.8), 세로 중앙 y=23, 안테나 꼭대기(공 위 테두리 y=19) ≈ 10+(19-57)*0.26 ≈ 0.1, 다리 바닥(159+13) ≈ 39.9
const H = 40
const CY = 23
const S = 0.26
const SYM_W = 180 * S

function build({ lang, tone }) {
  const dark = tone === 'dark'
  const ink = dark ? C.white : C.navy
  const accent = dark ? C.yellow : C.blue
  const head = dark ? C.white : C.blue
  const gap = 8
  const tx = SYM_W + gap
  let parts = []
  let wordmark = []
  let width
  if (lang === 'ko') {
    const size = 32
    // 한글 가운데를 머리 중앙에 — 개구체 한글 높이 ≈ 0.72em, 기준선 = CY + 0.36em
    const main = textPaths([{ text: '아이로', color: ink }, { text: '빗', color: accent }], tx, CY + size * 0.29, size)
    const subSize = Number(process.env.SUB ?? 15)
    const sub = textPaths([{ text: 'IROBIT', color: ink }], tx + main.width + 6, CY + size * 0.29, subSize)
    parts = [...main.out, ...sub.out.map((p) => ({ ...p, opacity: dark ? 0.85 : 0.6 }))]
    wordmark = [...main.out, ...sub.out]
    width = Math.ceil(tx + main.width + 6 + sub.width + 2)
  } else {
    const size = 34
    // 영문: 소문자 x높이 중심이 머리 중앙에 오게 — 기준선 = CY + 0.24em
    const main = textPaths([{ text: 'IRO', color: ink }, { text: 'BIT', color: accent }], tx, CY + size * 0.24, size)
    parts = main.out
    wordmark = main.out
    width = Math.ceil(tx + main.width + 2)
  }
  const SW = Number(process.env.STROKE ?? 0)
  const paths = parts.map((p) => `<path d="${p.d}" fill="${p.color}"${SW ? ` stroke="${p.color}" stroke-width="${SW}" stroke-linejoin="round"` : ''}${p.opacity ? ` fill-opacity="${p.opacity}"${SW ? ` stroke-opacity="${p.opacity}"` : ''}` : ''}/>`).join('')
  // 모바일용 「글자만」 영역 — <img src="….svg#wordmark"> 가 이 viewBox 로 보인다(심볼만 뺀 글자 — 한글은 작은 IROBIT 포함). 높이는 전체 40 그대로 두어 글자 크기가 같다.
  // ⚠ 루트에 width·height 를 두면 Chrome 이 #wordmark 를 무시한다 — viewBox 만 둔다(그림 비율은 viewBox 로 정해진다)
  const pad = 1 + SW
  const x1 = Math.floor(Math.min(...wordmark.map((p) => p.bb.x1)) - pad)
  const x2 = Math.ceil(Math.max(...wordmark.map((p) => p.bb.x2)) + pad)
  // xMinYMid: 그림 상자가 영역보다 넓게 잡혀도(브라우저가 전체 비율로 상자를 만들 때) 글자가 가운데로 가지 않고 아이콘이 있던 왼쪽 끝에 붙는다
  const view = `<view id="wordmark" viewBox="${x1} 0 ${x2 - x1} ${H}" preserveAspectRatio="xMinYMid meet"/>`
  // 로봇 그림만 — <img src="….svg#symbol">(사이트 소개의 「AI」 카드: 안테나와 A자 다리가 A 를 그린다)
  const symbolView = `<view id="symbol" viewBox="0 0 ${Math.ceil(SYM_W) + 1} ${H}" preserveAspectRatio="xMidYMid meet"/>`
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${H}" role="img" aria-label="${lang === 'ko' ? '아이로빗' : 'IROBIT'}">${view}${symbolView}${symbol(0, CY - 13, S, head)}${paths}</svg>\n`
}

const outDir = process.argv[2]
fs.mkdirSync(outDir, { recursive: true })
for (const lang of ['ko', 'en']) for (const tone of ['light', 'dark']) {
  const svg = build({ lang, tone })
  fs.writeFileSync(path.join(outDir, `irobit-logo-${lang}-${tone}.svg`), svg)
  console.log(lang, tone, svg.length, svg.match(/viewBox="([^"]+)"/)[1])
}
