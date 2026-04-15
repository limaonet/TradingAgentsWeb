<template>
  <div ref="rootRef" class="gauge-chart">
    <div class="gauge-progress-wrap">
      <a-progress
        type="dashboard"
        :percent="safeValue"
        :width="gaugePx"
        :gap-degree="80"
        :stroke-width="12"
        :trail-color="'rgba(60, 84, 121, 0.45)'"
        :stroke-color="{
          '0%': '#ef4444',
          '50%': '#eab308',
          '100%': '#22c55e',
        }"
        :format="() => ''"
      />

      <div class="gauge-value">
        <span class="value-number">{{ safeValue }}</span>
        <span class="value-unit">%</span>
      </div>

      <div class="gauge-ticks">
        <span class="tick tick-left">0</span>
        <span class="tick tick-mid">50</span>
        <span class="tick tick-right">100</span>
      </div>
    </div>

    <div class="gauge-label">{{ label }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useResizeObserver } from '@vueuse/core'

const props = defineProps<{
  value: number
  label: string
}>()
const safeValue = computed(() => Math.max(0, Math.min(100, Math.round(props.value))))
const rootRef = ref<HTMLElement | null>(null)
const gaugePx = ref(220)
useResizeObserver(rootRef, (entries) => {
  const w = entries[0]?.contentRect.width ?? 240
  gaugePx.value = Math.max(160, Math.min(220, Math.floor(w - 20)))
})
</script>

<style scoped>
.gauge-chart {
  width: 100%;
  max-width: 240px;
  margin: 0 auto;
  box-sizing: border-box;
}

.gauge-progress-wrap {
  position: relative;
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

.gauge-progress-wrap::before {
  content: '';
  position: absolute;
  top: 8px;
  width: 180px;
  height: 86px;
  border-radius: 90px 90px 0 0;
  box-shadow: 0 -10px 24px rgba(56, 189, 248, 0.1);
  pointer-events: none;
}

.gauge-value {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
  line-height: 1;
  pointer-events: none;
}

.value-number {
  font-size: 42px;
  font-weight: 700;
  font-family: var(--font-mono);
  background: linear-gradient(135deg, var(--color-info), var(--color-accent));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.value-unit {
  font-size: 20px;
  color: var(--text-secondary);
  margin-left: 3px;
}

.gauge-ticks {
  position: absolute;
  inset: 0;
  pointer-events: none;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-muted);
}

.tick {
  position: absolute;
}

.tick-left {
  left: 24px;
  bottom: 26px;
}

.tick-mid {
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
}

.tick-right {
  right: 22px;
  bottom: 26px;
}

.gauge-label {
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: -4px;
}

.gauge-chart :deep(.ant-progress) {
  line-height: 1;
}
</style>
