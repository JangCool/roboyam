// 로봇 특화 8종(25~32). 각 frame(t)는 t=0~1 의 한 장면이고, t=0 과 t=1 이 같은 장면이라 루프가 이어진다.
import {
  C, FONT, V, say, MIN_TEXT, SCREEN, TAU, battery, body, canvas, clamp, easeInOut, eyes, f, gear,
  lerp, lightning, pixels, puff, rng, sliced, span, sparkle, warning, xEyes,
} from '../parts.mjs'

const blink = (t, n) => Math.floor(t * n * 2) % 2 === 0

// 25 로딩중 — 화면에 도는 점 8개, 안테나 깜빡
const loading = {
  id: '25',
  name: '로딩중',
  frame(t) {
    const head = t * TAU * 2
    const dots = []
    for (let k = 0; k < 8; k++) {
      const a = (k / 8) * TAU - Math.PI / 2
      const d = (((head - (k / 8) * TAU) % TAU) + TAU) % TAU
      const w = clamp(1 - d / (TAU * 0.8))
      dots.push(`<circle cx="${f(SCREEN.cx + Math.cos(a) * 22)}" cy="${f(SCREEN.cy + Math.sin(a) * 22)}" r="${f(4 + 3.2 * w)}" fill="${C.yellow}" opacity="${f(0.3 + 0.7 * w)}"/>`)
    }
    const on = blink(t, 4)
    return canvas([
      body({ dy: -2 * Math.sin(TAU * 2 * t), ball: on ? '#FFE14D' : C.orange, ballGlow: on ? 1 : 0, face: dots.join('') }),
      say(100, 2, `로딩중${'.'.repeat(1 + (Math.floor(t * 6) % 3))}`, 24, C.yellow),
    ])
  },
}

// 26 에러 — X 눈, 지직 노이즈, 몸 떨림
const error = {
  id: '26',
  name: '에러',
  frame(t, i) {
    const r = rng(2600 + i)
    const noise = []
    for (let k = 0; k < 4; k++) {
      const y = SCREEN.y + 4 + r() * (SCREEN.h - 8)
      noise.push(`<rect x="${f(SCREEN.x + r() * 40 - 10)}" y="${f(y)}" width="${f(36 + r() * 70)}" height="${f(3 + r() * 3)}" fill="${r() < 0.5 ? '#FFFFFF' : '#5CE1FF'}" opacity="${f(0.3 + r() * 0.35)}"/>`)
    }
    const jx = (r() - 0.5) * 6
    const face = noise.join('') + xEyes({ lx: jx * 0.6, size: 11, width: 8.5 })
    const robot = body({
      dx: (r() - 0.5) * 6, dy: (r() - 0.5) * 3, rot: (r() - 0.5) * 4,
      ball: blink(t, 3) ? C.red : '#7A1A16', ballGlow: blink(t, 3) ? 0.8 : 0, face,
    })
    // 두 프레임에 한 번 몸을 가로 띠로 어긋나게
    const bands = i % 3 === 1 ? [{ y: 70 + r() * 60, h: 6 + r() * 10, dx: (r() - 0.5) * 18 }] : []
    const pulse = 1 + 0.08 * Math.sin(TAU * 3 * t)
    return canvas([
      sliced(robot, bands),
      `<g transform="translate(164 22) scale(${f(pulse)})">${warning(0, 0, 42)}</g>`,
      say(44, 16, '에러!', 28, C.red, -8),
    ])
  },
}

// 27 과열 — 화면 빨강, 머리에서 김 폭발, 안테나 빨강 점멸
const overheat = {
  id: '27',
  name: '과열',
  frame(t) {
    const puffs = []
    const sources = [[52, 70, -1], [148, 70, 1], [76, 60, -0.5], [124, 60, 0.5]]
    sources.forEach(([x, y, dir], k) => {
      const p = (t * 2 + k * 0.25) % 1
      // 솟으며 커지다 끝에서 작아져 사라짐(GIF 는 반투명이 없다)
      const size = Math.min(1, p / 0.12) * (p > 0.75 ? 1 - (p - 0.75) / 0.25 : 1)
      if (size > 0.05) puffs.push(puff(x + dir * (6 + 28 * p), y - 18 - 62 * p, (7 + 11 * p) * size))
    })
    const hot = 0.5 + 0.5 * Math.sin(TAU * 4 * t)
    const screen = `rgb(${Math.round(lerp(150, 215, hot))},${Math.round(lerp(22, 40, hot))},${Math.round(lerp(30, 44, hot))})`
    // 찡그린 > < 눈
    const face = [
      `<path d="M70 94 L90 105 L70 116" fill="none" stroke="${C.yellow}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>`,
      `<path d="M130 94 L110 105 L130 116" fill="none" stroke="${C.yellow}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>`,
      `<path d="M62 128 q6 -5 12 0 t12 0 t12 0 t12 0 t12 0 t12 0 t12 0" fill="none" stroke="#FFB199" stroke-width="4.5" opacity="${f(0.4 + 0.5 * hot)}"/>`,
    ].join('')
    const on = blink(t, 4)
    return canvas([
      puffs.slice(0, 2).join(''),
      body({
        dx: 1.6 * Math.sin(TAU * 8 * t), screen, head: V.headHot,
        ball: on ? C.red : '#FFB3AE', ballGlow: on ? 1 : 0, face,
      }),
      puffs.slice(2).join(''),
    ])
  },
}

// 28 배터리 부족 — 잔량 1칸 깜빡, 반쯤 감긴 눈, 몸 축 처짐
const lowBattery = {
  id: '28',
  name: '배터리부족',
  frame(t) {
    const sag = Math.sin(TAU * t)
    const face = eyes({ color: '#C9A23A', lid: 0.55 + 0.1 * (0.5 + 0.5 * sag), ly: 4 })
    const on = (t * 4) % 1 < 0.5
    return canvas([
      body({ dy: 8 + 2.5 * sag, sy: 0.93 - 0.015 * sag, ears: 5, ball: C.grey, bx: -16, by: 6 + 2 * sag, face }),
      battery(122, -6 + 2 * sag, { w: 64, h: 34, level: on ? 1 : 0, barColor: C.red }),
      say(52, 18 + 2 * sag, '방전..', 26, '#A3A9B6'),
    ])
  },
}

// 29 충전 완료 — 배터리 가득, 번개 이펙트, 눈 반짝
const charged = {
  id: '29',
  name: '충전완료',
  frame(t) {
    const hop = Math.abs(Math.sin(TAU * t))
    const land = 1 - hop
    const tw = 0.5 + 0.5 * Math.sin(TAU * 4 * t)
    const face = eyes({ hl: false })
      + sparkle(80, 101, 6 + 4 * tw, C.white, 45 * t * 4)
      + sparkle(120, 101, 6 + 4 * (1 - tw), C.white, -45 * t * 4)
    const flash = blink(t, 4)
    return canvas([
      body({ dy: -12 * hop, sy: 1 - 0.07 * land ** 6, ball: '#FFE14D', ballGlow: 0.6 + 0.4 * tw, face }),
      battery(122, -10 - 12 * hop, { w: 64, h: 34, level: 4, bolt: true }),
      lightning(14, 80 - 12 * hop, flash ? 1.15 : 0.85, flash ? C.yellow : '#FFE9A8'),
      lightning(186, 74 - 12 * hop, flash ? 0.85 : 1.15, flash ? '#FFE9A8' : C.yellow),
      sparkle(30, 44, 10 * tw, C.yellow),
      sparkle(178, 136, 9 * (1 - tw), C.yellow),
    ])
  },
}

// 30 업데이트중 — 진행률 바가 차오르고 머리 위 톱니 회전
const updating = {
  id: '30',
  name: '업데이트중',
  frame(t) {
    const p = t < 0.8 ? easeInOut(t / 0.8) : 1
    const done = t >= 0.8
    const pct = Math.round(p * 100)
    const bx = 56
    const bw = 88
    const fill = done && blink(t, 6) ? C.white : C.yellow
    const face = [
      `<text x="100" y="109" text-anchor="middle" font-family="${FONT}" font-weight="700" font-size="${f(Math.max(MIN_TEXT, 29))}" fill="${C.yellow}">${pct}%</text>`,
      `<rect x="${bx}" y="116" width="${bw}" height="13" rx="6.5" fill="#22357A"/>`,
      `<rect x="${bx}" y="116" width="${f(Math.max(13, bw * p))}" height="13" rx="6.5" fill="${fill}" opacity="${p > 0 ? 1 : 0}"/>`,
    ].join('')
    // 큰 톱니 8개·작은 톱니 6개, 한 루프에 이빨 두 칸씩 돌아 이어진다
    return canvas([
      gear(56, 10, { r: 22, teeth: 8, rot: 90 * t, fill: C.yellow }),
      gear(77.5, -12.5, { r: 15, depth: 5, teeth: 6, rot: -120 * t + 15, fill: C.orange, hole: 5 }),
      body({ dy: -1.5 * Math.sin(TAU * 2 * t), ball: blink(t, 2) ? '#FFE14D' : C.orange, face }),
    ])
  },
}

// 31 재부팅 — 화면이 꺼졌다 켜지며 눈을 다시 뜸
const reboot = {
  id: '31',
  name: '재부팅',
  frame(t) {
    let face
    let screen = C.navy
    let ballOn = true
    let slump = 0
    if (t < 0.1) {
      face = eyes()
    } else if (t < 0.2) {
      // 브라운관 꺼짐: 세로로 납작 → 점
      const k = span(t, 0.1, 0.2)
      const sy = Math.max(0.04, 1 - k * 2)
      const sx = k < 0.5 ? 1 : 1 - (k - 0.5) * 1.9
      face = `<g transform="translate(100 107) scale(${f(sx)} ${f(sy)}) translate(-100 -107)"><rect x="40" y="70" width="120" height="74" fill="#DDE6FF"/></g>`
      screen = '#05091A'
      slump = k
    } else if (t < 0.5) {
      const on = Math.floor(t * 16) % 2 === 0
      face = `<g opacity="${on ? 0.9 : 0.35}" fill="none" stroke="${C.yellow}" stroke-width="5" stroke-linecap="round"><path d="M91 97 A14 14 0 1 0 109 97"/><line x1="100" y1="90" x2="100" y2="106"/></g>`
      screen = '#05091A'
      ballOn = false
      slump = 1
    } else if (t < 0.6) {
      // 켜짐: 가로선 → 화면 가득
      const k = span(t, 0.5, 0.6)
      const sx = k < 0.5 ? 0.1 + k * 1.8 : 1
      const sy = k < 0.5 ? 0.05 : (k - 0.5) * 2
      face = `<g transform="translate(100 107) scale(${f(sx)} ${f(Math.max(0.05, sy))}) translate(-100 -107)"><rect x="40" y="70" width="120" height="74" fill="#FFFFFF" opacity="${f(1 - sy * 0.6)}"/></g>`
      screen = '#05091A'
      slump = 1 - k
    } else if (t < 0.82) {
      const k = easeInOut(span(t, 0.6, 0.8))
      face = eyes({ open: k, hl: k > 0.8 })
    } else if (t < 0.9) {
      const k = span(t, 0.82, 0.9)
      face = eyes({ open: Math.abs(1 - 2 * k) })
    } else {
      face = eyes()
    }
    return canvas([
      body({ dy: 4 * slump, sy: 1 - 0.03 * slump, ball: ballOn ? C.orange : C.grey, ballGlow: ballOn && t >= 0.6 && t < 0.7 ? 1 : 0, face, screen }),
      t >= 0.14 && t < 0.6 ? say(100, 6, '재부팅..', 26, '#9FB4FF') : '',
    ])
  },
}

// 32 해킹당함 — 화면에 깨진 글자와 해골, 몸이 지직거리며 흔들림
const SKULL = [
  '..#####..',
  '.#######.',
  '#########',
  '##..#..##',
  '##..#..##',
  '#########',
  '.###.###.',
  '..#.#.#..',
  '..#####..',
]
const hacked = {
  id: '32',
  name: '해킹당함',
  frame(t, i) {
    const r = rng(3200 + i)
    const glyphs = '#%&@$!?01<>/'
    const rain = []
    for (let k = 0; k < 8; k++) {
      const ch = glyphs[Math.floor(r() * glyphs.length)]
      rain.push(`<text x="${f(SCREEN.x + 6 + r() * 92)}" y="${f(SCREEN.y + 18 + r() * 44)}" font-family="Consolas, monospace" font-weight="700" font-size="17" fill="#22B455" opacity="${f(0.45 + r() * 0.4)}">${ch === '<' ? '&lt;' : ch === '>' ? '&gt;' : ch === '&' ? '&amp;' : ch}</text>`)
    }
    const cell = 5
    const sk = pixels(SKULL, 100 - 4.5 * cell, 107 - 4.5 * cell, cell, '#3DFF7A')
    const eyesOn = blink(t, 3)
    const skEyes = eyesOn
      ? `<rect x="${f(100 - 2.5 * cell)}" y="${f(107 - 1.5 * cell)}" width="${f(cell * 2)}" height="${f(cell * 2)}" fill="${C.red}"/><rect x="${f(100 + 0.5 * cell)}" y="${f(107 - 1.5 * cell)}" width="${f(cell * 2)}" height="${f(cell * 2)}" fill="${C.red}"/>`
      : ''
    const face = rain.join('') + sk + skEyes
    const jx = (r() - 0.5) * 7
    const opts = { dx: jx, dy: (r() - 0.5) * 3, rot: (r() - 0.5) * 3, screen: '#03140A', ball: eyesOn ? '#3DFF7A' : C.red, face }
    const split = 3 + r() * 4
    const ghost = (color, dx) => `<g opacity="0.55" transform="translate(${f(dx)} 0)">${body({ ...opts, head: color, limb: color, ball: color, screen: color, face: '', outline: color })}</g>`
    const robot = ghost('#FF2BD6', -split) + ghost('#22E6FF', split) + body(opts)
    const bands = i % 2 === 0
      ? [{ y: 60 + r() * 40, h: 5 + r() * 9, dx: (r() - 0.5) * 22 }, { y: 112 + r() * 40, h: 4 + r() * 8, dx: (r() - 0.5) * 22 }]
      : []
    return canvas([sliced(robot, bands)])
  },
}

export default [loading, error, overheat, lowBattery, charged, updating, reboot, hacked]
