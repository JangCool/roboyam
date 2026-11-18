// 글자 로고 4종 SVG — 마스코트 없이 글자만(IT 사이트 느낌, 사용자 결정 2026-11-17).
// 한글 「아이로빗」(Gothic A1 800) + 작은 「irobit」(Space Grotesk 700), 영문은 「irobit」만. i 의 점은 네모(비트 한 칸).
// 글자는 패스로 굳힌다(웹 글꼴 없이 <img> 로 보이게). 같은 색 글자는 패스 하나로 합치고 소수 한 자리만 남긴다.
import fs from 'node:fs'
import path from 'node:path'
import opentype from 'opentype.js'
import { THEME_DATES, THEME_DATES_EN, THEME_NAMES, THEME_BRUSH, MASCOT_CY, MASCOT_S, mascot, mascotTop, mascotUnits } from './mascot.mjs'

function load(pkg, weight) {
  const dir = path.resolve(process.env.LOGO_MODULES ?? 'node_modules', `@fontsource/${pkg}/files`)
  return fs.readdirSync(dir).filter((f) => f.endsWith(`-${weight}-normal.woff`)).map((f) => {
    const b = fs.readFileSync(path.join(dir, f))
    return opentype.parse(b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength))
  })
}
// 한글 굵기는 확정 800. 굵기를 견줘 볼 때만 KO_WEIGHT 로 바꾼다(500~900)
const KO = load('gothic-a1', Number(process.env.KO_WEIGHT ?? 800))
const EN = load('space-grotesk', 700)
const fontFor = (fonts, ch) => fonts.find((ft) => ft.charToGlyphIndex(ch) > 0) ?? (() => { throw new Error('no glyph ' + ch) })()

const C = { navy: '#0E1F4D', blue: '#2F6BFF', yellow: '#FFC83D', white: '#FFF', subLight: '#5B6474', subDark: '#AAB2C2' }
const H = 40
// 글자 크기·간격은 처음 시안(브라우저에서 한글 24px + 작은 영문 11px, 영문판 26px — 헤더 높이 32px 기준)과 같게 맞춘다.
// 실제 브라우저에서 시안과 로고 파일을 나란히 놓고 쟀다(2026-11-18): 한글 글자 높이 28(= 시안의 22px), 영문판 23.7(= 시안의 19px), 작은 영문은 한글의 0.458 배, 사이 0.15em.
const KO_H = 28
const EN_H = 23.7
const n = (v) => String(+v.toFixed(1))

// 국경일 변형 — THEME=samil|jeheon|gwangbok|gaecheon|hangeul(mascot.mjs): 왼쪽에 그날의 특징을 얹은 마스코트, 작은 글자 자리에 그날의 날짜(영문판에도). 글자 색은 평소 그대로.
// 마스코트 크기·자리는 캐릭터 로고와 같다. 파일 이름은 irobit-wordmark-<THEME>-*.svg
const THEME = process.env.THEME ?? ''


/** 윤곽(명령 묶음)들 → 패스 글자열(절대 좌표, 소수 한 자리) */
function toD(cmds) {
  return cmds.map((c) => {
    if (c.type === 'Z') return 'Z'
    const pts = c.type === 'Q' ? [c.x1, c.y1, c.x, c.y] : c.type === 'C' ? [c.x1, c.y1, c.x2, c.y2, c.x, c.y] : [c.x, c.y]
    return c.type + pts.map(n).join(' ')
  }).join('')
}
function contours(cmds) {
  const out = []
  let cur = []
  for (const c of cmds) { cur.push(c); if (c.type === 'Z') { out.push(cur); cur = [] } }
  if (cur.length) out.push(cur)
  return out
}
function bbox(cmds) {
  let x1 = Infinity, y1 = Infinity, x2 = -Infinity, y2 = -Infinity
  for (const c of cmds) for (const [x, y] of [[c.x, c.y], [c.x1, c.y1], [c.x2, c.y2]]) {
    if (x === undefined) continue
    x1 = Math.min(x1, x); x2 = Math.max(x2, x); y1 = Math.min(y1, y); y2 = Math.max(y2, y)
  }
  return { x1, y1, x2, y2 }
}

/**
 * 글자열을 그린다. runs = [{text, color}], track = 자간(em).
 * 영문 i 는 점 윤곽을 떼고 그 자리에 네모(dot 색)를 놓는다.
 * 돌려주는 것: 색별 패스 조각 {color: [d…]}, 너비, 전체 상자
 */
function draw(fonts, runs, x0, baseline, size, track, dot) {
  let x = x0
  const byColor = {}
  const all = []
  const add = (color, d, cmds) => { (byColor[color] ??= []).push(d); all.push(...cmds) }
  for (const { text, color } of runs) for (const ch of text) {
    const ft = fontFor(fonts, ch)
    const g = ft.charToGlyph(ch)
    const cmds = g.getPath(x, baseline, size).commands
    if (ch === 'i' && dot) {
      const cs = contours(cmds).map((c) => ({ c, bb: bbox(c) })).sort((a, b) => a.bb.y1 - b.bb.y1)
      const [top, ...rest] = cs
      const stem = rest[rest.length - 1].bb
      const side = (stem.x2 - stem.x1) * 1.08
      const cx = (stem.x1 + stem.x2) / 2
      const cy = (top.bb.y1 + top.bb.y2) / 2
      const sq = [{ type: 'M', x: cx - side / 2, y: cy - side / 2 }, { type: 'L', x: cx + side / 2, y: cy - side / 2 }, { type: 'L', x: cx + side / 2, y: cy + side / 2 }, { type: 'L', x: cx - side / 2, y: cy + side / 2 }, { type: 'Z' }]
      const body = rest.flatMap((r) => r.c)
      add(color, toD(body), body)
      const s = n(side)
      add(dot, `M${n(cx - side / 2)} ${n(cy - side / 2)}h${s}v${s}h-${s}Z`, sq)
    } else add(color, toD(cmds), cmds)
    x += (g.advanceWidth / ft.unitsPerEm) * size + track * size
  }
  return { byColor, width: x - x0 - track * size, bb: bbox(all) }
}

function merge(...parts) {
  const out = {}
  for (const p of parts) for (const [color, ds] of Object.entries(p)) (out[color] ??= []).push(...ds)
  return out
}

function build({ lang, tone }) {
  const dark = tone === 'dark'
  const ink = dark ? C.white : C.navy
  const date = (lang === 'en' ? THEME_DATES_EN[THEME] : null) ?? THEME_DATES[THEME] ?? null
  // 기념일 변형도 글자 색은 평소 그대로 둔다(사용자 2026-11-18 「기존 로고 색 유지」)
  const accent = dark ? C.yellow : C.blue
  const dot = accent
  // 기념일 변형의 마스코트 — 크기·자리는 캐릭터 로고와 같다(mascot.mjs). 머리 색도 같다(밝은 바탕 파랑 · 어두운 바탕 흰색).
  // 마스코트 안테나가 캔버스 위쪽 끝까지 올라가므로 글자 가운데를 마스코트 머리 중앙(y=23)에 맞춘다
  const mark = THEME ? mascot(0, mascotTop(MASCOT_CY, MASCOT_S), MASCOT_S, dark ? '#FFFFFF' : C.blue, THEME) : ''
  const mid = THEME ? MASCOT_CY : H / 2
  const sub = dark ? C.subDark : C.subLight
  const lead = THEME ? mascotUnits(THEME) * MASCOT_S + 7 : 1
  let paths
  let width
  let extra = ''
  if (lang === 'ko') {
    // 한글 높이를 KO_H 로 맞추고 캔버스 세로 가운데에 둔다. 작은 영문은 한글의 0.458 배(시안의 11px/24px), 같은 기준선.
    const probe = draw(KO, [{ text: '아이로빗', color: ink }], 0, 0, 100, -0.06)
    const size = (KO_H / (probe.bb.y2 - probe.bb.y1)) * 100
    const baseline = mid - ((probe.bb.y1 + probe.bb.y2) / 2) * (size / 100)
    const x0 = lead - probe.bb.x1 * (size / 100)
    const main = draw(KO, [{ text: '아이로', color: ink }, { text: '빗', color: accent }], x0, baseline, size, -0.06)
    const sx = main.bb.x2 + size * 0.15
    const brush = THEME_BRUSH[THEME]
    if (date && brush) {
      // 이름을 붓글씨로 크게 쓰는 날: 붓글씨(위, 높이 19) + 날짜(아래)
      const bk = 19 / brush.h
      extra = `<path transform="translate(${n(sx)} ${n(mid - 15.5)}) scale(${+bk.toFixed(3)})" d="${brush.d}" fill="${ink}"/>`
      const en = draw([...EN, ...KO], [{ text: date, color: sub }], sx, mid + 13.4, size * 0.3, -0.04, dot)
      paths = merge(main.byColor, en.byColor)
      width = Math.ceil(Math.max(sx + brush.w * bk, en.bb.x2) + 1)
    } else if (date) {
      // 국경일 한글판: 이름(위) + 날짜(아래) 두 줄
      // 이름은 한 줄에 다 쓰고 글자 크기도 줄이지 않는다(사용자 2026-11-18 「이름 글자 줄이지 마」) — 이름이 긴 날은 로고가 그만큼 길어진다
      const name = draw([...KO, ...EN], [{ text: THEME_NAMES[THEME], color: sub }], sx, mid - 1, size * 0.36, -0.04)
      const en = draw([...EN, ...KO], [{ text: date, color: sub }], sx, mid + 12.5, size * 0.34, -0.04, dot)
      paths = merge(main.byColor, name.byColor, en.byColor)
      width = Math.ceil(Math.max(name.bb.x2, en.bb.x2) + 1)
    } else {
      const en = draw(EN, [{ text: 'irobit', color: sub }], sx, baseline, size * 0.458, -0.04, dot)
      paths = merge(main.byColor, en.byColor)
      width = Math.ceil(en.bb.x2 + 1)
    }
  } else {
    const probe = draw(EN, [{ text: 'irobit', color: ink }], 0, 0, 100, -0.04, dot)
    const size = (EN_H / (probe.bb.y2 - probe.bb.y1)) * 100
    const baseline = mid - ((probe.bb.y1 + probe.bb.y2) / 2) * (size / 100)
    const x0 = lead - probe.bb.x1 * (size / 100)
    const main = draw(EN, [{ text: 'iro', color: ink }, { text: 'bit', color: accent }], x0, baseline, size, -0.04, dot)
    // 기념일 날짜는 영문판에도 보인다(사용자 2026-11-18)
    const dated = date ? draw(EN, [{ text: date, color: sub }], main.bb.x2 + size * 0.16, baseline, size * 0.36, -0.04, dot) : null
    paths = dated ? merge(main.byColor, dated.byColor) : main.byColor
    width = Math.ceil((dated ?? main).bb.x2 + 1)
  }
  const body = Object.entries(paths).map(([color, ds]) => `<path fill="${color}" d="${ds.join('')}"/>`).join('')
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${H}" role="img" aria-label="${lang === 'ko' ? '아이로빗' : 'IROBIT'}">${mark}${body}${extra}</svg>\n`
}

const outDir = process.argv[2]
fs.mkdirSync(outDir, { recursive: true })
for (const lang of ['ko', 'en']) for (const tone of ['light', 'dark']) {
  const svg = build({ lang, tone })
  fs.writeFileSync(path.join(outDir, `irobit-wordmark-${THEME ? THEME + '-' : ''}${lang}-${tone}.svg`), svg)
  console.log(lang, tone, svg.length, svg.match(/viewBox="([^"]+)"/)[1])
}
