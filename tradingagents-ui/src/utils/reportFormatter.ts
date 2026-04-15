const TITLE_PATTERN =
  /^((第[一二三四五六七八九十\d]+[章节部分]|[一二三四五六七八九十\d]+[、.．)]|\([一二三四五六七八九十\d]+\))\s*)(.+)$/
const KEY_VALUE_PATTERN = /^([\u4e00-\u9fa5A-Za-z0-9_/\s]{2,24})[:：]\s*(.+)$/

/**
 * 将后端返回的纯文本报告转换为更稳定的 Markdown 结构，便于统一样式渲染。
 */
export function prettifyReportMarkdown(raw: string): string {
  const source = (raw || '').replace(/\r\n/g, '\n').trim()
  if (!source) return '暂无报告内容。'

  const hasMarkdownHint = /(^#{1,6}\s)|(\n-\s)|(\n\*\s)|(\n\d+\.)|(```)/m.test(source)
  if (hasMarkdownHint) return source

  const lines = source.split('\n')
  const output: string[] = []

  for (const original of lines) {
    const line = original.trim()
    if (!line) {
      if (output.length && output[output.length - 1] !== '') output.push('')
      continue
    }

    const titleMatch = line.match(TITLE_PATTERN)
    if (titleMatch) {
      const title = titleMatch[3] ?? line
      output.push(`### ${title.trim()}`)
      continue
    }

    const kvMatch = line.match(KEY_VALUE_PATTERN)
    if (kvMatch) {
      const key = kvMatch[1] ?? ''
      const value = kvMatch[2] ?? ''
      if (value.length <= 200) {
        output.push(`- **${key.trim()}**：${value.trim()}`)
        continue
      }
    }

    if (/^[-*•]\s+/.test(line)) {
      output.push(`- ${line.replace(/^[-*•]\s+/, '').trim()}`)
      continue
    }

    output.push(line)
  }

  return output.join('\n').replace(/\n{3,}/g, '\n\n')
}

export function getReportStats(raw: string) {
  const text = (raw || '').trim()
  if (!text) {
    return { chars: 0, lines: 0, sections: 0 }
  }

  const lines = text.split(/\r?\n/)
  const sections = lines.filter((line) => TITLE_PATTERN.test(line.trim()) || /^#{1,6}\s/.test(line.trim())).length
  return {
    chars: text.length,
    lines: lines.filter((line) => line.trim().length > 0).length,
    sections,
  }
}
