// 확정 로고 4종 SVG — 글자는 개구체(Gaegu 700) 윤곽을 패스로 굳힌다(웹 글꼴 없이 <img> 로 보이게).
// 국경일 변형 — THEME=samil|jeheon|gwangbok|gaecheon|hangeul(mascot.mjs): 마스코트에 그날의 특징과 표정, 작은 글자 자리에 그날의 날짜. 글자 색은 평소 그대로. 파일 이름은 irobit-logo-<THEME>-*.svg
import fs from 'node:fs'
import path from 'node:path'
import opentype from 'opentype.js'
import { C, THEME_DATES, THEME_DATES_EN, THEME_NAMES, THEME_BRUSH, MASCOT_S, mascot, mascotTop, mascotUnits } from './mascot.mjs'

const THEME = process.env.THEME ?? ''
const dir = path.resolve(process.env.LOGO_MODULES ?? 'node_modules', '@fontsource/gaegu/files')
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

// 캔버스 높이 40, 머리 몸통 세로 중앙 y=23. 마스코트 크기는 mascot.mjs 의 MASCOT_S(예전 0.26 에서 높이 4 만큼 줄였다 — 사용자 2026-11-18, 평소·기념일 같음)
const H = 40
const CY = 23

function build({ lang, tone }) {
  const dark = tone === 'dark'
  const ink = dark ? C.white : C.navy
  // 기념일 변형도 글자 색은 평소 그대로 둔다(사용자 2026-11-18 「기존 로고 색 유지」) — 달라지는 것은 캐릭터뿐이다
  const accent = dark ? C.yellow : C.blue
  const head = dark ? C.white : C.blue
  const gap = 8
  const size = lang === 'ko' ? 32 : 34
  // 한글 가운데를 머리 중앙에 — 개구체 한글 높이 ≈ 0.72em, 기준선 = CY + 0.36em / 영문: 소문자 x높이 중심이 머리 중앙에 오게 — 기준선 = CY + 0.24em
  const baseline = CY + size * (lang === 'ko' ? 0.29 : 0.24)
  const runs = lang === 'ko' ? [{ text: '아이로', color: ink }, { text: '빗', color: accent }] : [{ text: 'IRO', color: ink }, { text: 'BIT', color: accent }]

  // 마스코트: 평소·기념일 모두 머리 몸통 중앙 = 글자 중앙, 크기 MASCOT_S.
  // 글자 높이에 맞춰 줄이면 헤더 크기에서 너무 작다. 깃발만큼 폭이 넓어질 뿐이다.
  const s = MASCOT_S
  const symW = mascotUnits(THEME) * s
  const sym = mascot(0, mascotTop(CY, s), s, head, THEME)

  const tx = symW + gap
  const main = textPaths(runs, tx, baseline, size)
  let parts = main.out
  let wordmark = main.out
  let width = Math.ceil(tx + main.width + 2)
  // 작은 글자: 한글판은 「IROBIT」, 국경일 변형은 한글·영문 모두 그날의 날짜
  let extra = ''
  const subText = (lang === 'en' ? THEME_DATES_EN[THEME] : null) ?? THEME_DATES[THEME] ?? (lang === 'ko' ? 'IROBIT' : null)
  if (subText) {
    const subSize = Number(process.env.SUB ?? 15)
    // 국경일 한글판: 이름(위) + 날짜(아래) 두 줄. 그 밖에는 한 줄
    const twoLines = THEME && lang === 'ko'
    const sx = tx + main.width + 6
    const brush = lang === 'ko' ? THEME_BRUSH[THEME] : null
    const bk = brush ? 19 / brush.h : 0
    // 이름을 붓글씨로 크게 쓰는 날: 붓글씨(위, 높이 19) + 날짜(아래)
    if (brush) extra = `<path transform="translate(${sx.toFixed(1)} ${(CY - 15.5).toFixed(1)}) scale(${bk.toFixed(3)})" d="${brush.d}" fill="${ink}"/>`
    const sub = brush
      ? (() => { const d = textPaths([{ text: subText, color: ink }], sx, CY + 13.4, subSize * 0.72); return { out: d.out, width: Math.max(d.width, brush.w * bk) } })()
      : twoLines
      ? (() => { const a = textPaths([{ text: THEME_NAMES[THEME], color: ink }], sx, CY - 1.5, subSize * 0.9); const d = textPaths([{ text: subText, color: ink }], sx, CY + 11.5, subSize * 0.8); return { out: [...a.out, ...d.out], width: Math.max(a.width, d.width) } })()
      : textPaths([{ text: subText, color: ink }], sx, baseline, subSize)
    parts = [...main.out, ...sub.out.map((p) => ({ ...p, opacity: dark ? 0.85 : 0.6 }))]
    wordmark = [...main.out, ...sub.out]
    width = Math.ceil(tx + main.width + 6 + sub.width + 2)
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
  const symbolView = `<view id="symbol" viewBox="0 0 ${Math.ceil(symW) + 1} ${H}" preserveAspectRatio="xMidYMid meet"/>`
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${H}" role="img" aria-label="${lang === 'ko' ? '아이로빗' : 'IROBIT'}">${view}${symbolView}${sym}${paths}${extra}</svg>\n`
}

const outDir = process.argv[2]
fs.mkdirSync(outDir, { recursive: true })
for (const lang of ['ko', 'en']) for (const tone of ['light', 'dark']) {
  const svg = build({ lang, tone })
  fs.writeFileSync(path.join(outDir, `irobit-logo-${THEME ? THEME + '-' : ''}${lang}-${tone}.svg`), svg)
  console.log(lang, tone, svg.length, svg.match(/viewBox="([^"]+)"/)[1])
}
