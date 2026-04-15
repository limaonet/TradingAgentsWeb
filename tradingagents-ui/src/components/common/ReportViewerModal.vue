<template>
  <a-modal
    v-model:open="reportViewerOpen"
    width="min(1080px, 96vw)"
    :footer="null"
    destroy-on-close
    wrap-class-name="report-viewer-modal"
    @cancel="store.closeReportViewer"
  >
    <template #title>
      <div class="report-viewer-title">
        <FileTextOutlined />
        <span>{{ reportViewerTitle || '报告详情' }}</span>
      </div>
    </template>

    <div class="report-toolbar">
      <a-segmented
        v-model:value="currentTab"
        :options="tabOptions"
        size="small"
        block
      />
      <div class="report-stats">
        <a-tag color="blue">字数 {{ stats.chars }}</a-tag>
        <a-tag color="cyan">行数 {{ stats.lines }}</a-tag>
        <a-tag color="purple">章节 {{ stats.sections }}</a-tag>
      </div>
    </div>

    <div class="report-viewer-body">
      <MdPreview
        v-if="currentTab === 'beautified'"
        editor-id="report-beautified"
        :model-value="beautifiedMarkdown"
        theme="dark"
        preview-theme="github"
        code-theme="atom"
      />
      <MdPreview
        v-else
        editor-id="report-raw"
        :model-value="rawMarkdown"
        theme="dark"
        preview-theme="github"
        code-theme="atom"
      />
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { FileTextOutlined } from '@ant-design/icons-vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useAnalysisStore } from '@/stores/analysisStore'
import { getReportStats, prettifyReportMarkdown } from '@/utils/reportFormatter'

const store = useAnalysisStore()
const { reportViewerOpen, reportViewerTitle, reportViewerBody } = storeToRefs(store)
const currentTab = ref<'beautified' | 'raw'>('beautified')
const tabOptions = [
  { label: '美化视图', value: 'beautified' },
  { label: '原始内容', value: 'raw' },
]

const beautifiedMarkdown = computed(() => prettifyReportMarkdown(reportViewerBody.value))
const rawMarkdown = computed(() => `\`\`\`text\n${reportViewerBody.value || ''}\n\`\`\``)
const stats = computed(() => getReportStats(reportViewerBody.value))

watch(
  () => reportViewerOpen.value,
  (open) => {
    if (open) currentTab.value = 'beautified'
  },
)
</script>
