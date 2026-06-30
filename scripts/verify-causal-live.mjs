#!/usr/bin/env node
/**
 * 端到端自验证：因果链 + 实时追踪 + 报错修复
 */
import { chromium } from 'playwright'

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080/api'

const results = []

function pass(name, detail) {
  results.push({ name, ok: true, detail })
  console.log(`✅ ${name}: ${detail}`)
}

function fail(name, detail) {
  results.push({ name, ok: false, detail })
  console.log(`❌ ${name}: ${detail}`)
}

async function waitForCausal(aid, maxSec = 300) {
  const start = Date.now()
  while (Date.now() - start < maxSec * 1000) {
    const st = await fetch(`${API}/analysis/${aid}`).then((r) => r.json())
    const n = (st.causalGraph?.nodes || []).length
    if (n > 0) return { nodes: n, edges: (st.causalGraph?.edges || []).length, status: st.status }
    await new Promise((r) => setTimeout(r, 10000))
  }
  return null
}

async function main() {
  console.log('=== TradingAgents 自验证开始 ===\n')

  // 1. 过期 ID → 友好提示，无崩溃
  const browser = await chromium.launch({ headless: true })
  const stalePage = await browser.newPage()
  const staleErrors = []
  stalePage.on('console', (m) => {
    if (m.type() === 'error') staleErrors.push(m.text())
  })
  await stalePage.goto(`${BASE}/?analysisId=00000000-0000-0000-0000-000000000000`)
  await stalePage.waitForTimeout(4000)
  const staleAlert = await stalePage.locator('.hydrate-alert').textContent().catch(() => null)
  if (staleAlert?.includes('已过期') || staleAlert?.includes('重新开始')) {
    pass('过期 analysisId 提示', staleAlert.slice(0, 40))
  } else {
    fail('过期 analysisId 提示', staleAlert || '未显示提示条')
  }
  const stale429 = staleErrors.some((e) => e.includes('429'))
  if (!stale429) pass('过期 ID 无 429', 'GET 未被限流')
  else fail('过期 ID 无 429', staleErrors.find((e) => e.includes('429')))

  // 2. GET 连续请求不限流
  const codes = []
  for (let i = 0; i < 15; i++) {
    const r = await fetch(`${API}/analysis/00000000-0000-0000-0000-000000000000`)
    codes.push(r.status)
  }
  if (!codes.includes(429)) pass('GET 轮询不限流', `15次状态码: ${[...new Set(codes)].join(',')}`)
  else fail('GET 轮询不限流', '出现 429')

  // 3. 启动新分析 → 等因果图
  console.log('\n--- 启动新分析（约 2-4 分钟）---')
  const start = await fetch(`${API}/analysis/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ticker: '600519', date: '2026-06-30' }),
  }).then((r) => r.json())
  const aid = start.analysisId
  console.log(`analysisId: ${aid}`)

  const causal = await waitForCausal(aid)
  if (causal && causal.nodes >= 8) {
    pass('因果图生成', `${causal.nodes} 节点 / ${causal.edges} 边`)
  } else {
    fail('因果图生成', causal ? `仅 ${causal.nodes} 节点` : '超时')
    await browser.close()
    process.exit(1)
  }

  // 4. 开启实时追踪 API
  const liveOn = await fetch(`${API}/analysis/${aid}/causal/live`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled: true }),
  })
  if (liveOn.status === 200) {
    const body = await liveOn.json()
    pass('实时追踪 API', body.message || 'enabled')
  } else {
    fail('实时追踪 API', `HTTP ${liveOn.status}`)
  }

  // 5. 前端 UI
  const page = await browser.newPage()
  const netFails = []
  const consoleErrors = []
  page.on('response', (r) => {
    if (r.status() >= 400) netFails.push({ url: r.url(), status: r.status() })
  })
  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push(m.text())
  })

  await page.goto(`${BASE}/?analysisId=${aid}`)
  await page.waitForTimeout(8000)
  await page.getByRole('tab', { name: '因果链' }).click()
  await page.waitForTimeout(2500)

  const summary = await page.locator('.summary').textContent().catch(() => null)
  const graphChildren = await page.locator('.graph-container').evaluate((el) => el.children.length).catch(() => 0)
  const switchVisible = await page.locator('.live-controls .ant-switch').isVisible().catch(() => false)
  const switchDisabled = await page.locator('.live-controls .ant-switch').isDisabled().catch(() => true)
  const hydrateAlert = await page.locator('.hydrate-alert').textContent().catch(() => null)

  await page.screenshot({ path: '/opt/cursor/artifacts/self-verify-causal.png', fullPage: false })

  if (summary && summary.length > 20) pass('因果链摘要展示', summary.slice(0, 50) + '...')
  else fail('因果链摘要展示', '无摘要')

  if (graphChildren >= 1) pass('因果图画布渲染', `容器子元素 ${graphChildren} 个`)
  else fail('因果图画布渲染', '画布为空')

  if (switchVisible && !switchDisabled) pass('实时开关可用', '可见且未禁用')
  else fail('实时开关可用', `visible=${switchVisible} disabled=${switchDisabled}`)

  const badNet = netFails.filter((f) => f.url.includes('/api/analysis') && f.status !== 404)
  if (badNet.length === 0) pass('页面无 API 报错', `分析相关请求正常`)
  else fail('页面无 API 报错', JSON.stringify(badNet.slice(0, 3)))

  if (!hydrateAlert?.includes('429') && !hydrateAlert?.includes('404')) {
    pass('无 hydrate 错误条', hydrateAlert ? '有其他提示' : '无黄色警告')
  } else {
    fail('无 hydrate 错误条', hydrateAlert)
  }

  await browser.close()

  console.log('\n=== 验证汇总 ===')
  const ok = results.filter((r) => r.ok).length
  const total = results.length
  console.log(`${ok}/${total} 通过`)
  if (ok < total) process.exit(1)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
