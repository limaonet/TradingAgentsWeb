<template>
  <div class="gauge-chart">
    <svg viewBox="0 0 200 110" class="gauge-svg">
      <defs>
        <linearGradient id="gaugeGradient" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stop-color="#ef4444" />
          <stop offset="50%" stop-color="#eab308" />
          <stop offset="100%" stop-color="#22c55e" />
        </linearGradient>
        <filter id="glow">
          <feGaussianBlur stdDeviation="3" result="coloredBlur"/>
          <feMerge>
            <feMergeNode in="coloredBlur"/>
            <feMergeNode in="SourceGraphic"/>
          </feMerge>
        </filter>
      </defs>
      
      <!-- 背景弧 -->
      <path
        d="M 20 100 A 80 80 0 0 1 180 100"
        fill="none"
        stroke="var(--bg-input)"
        stroke-width="20"
        stroke-linecap="round"
      />
      
      <!-- 进度弧 -->
      <path
        d="M 20 100 A 80 80 0 0 1 180 100"
        fill="none"
        stroke="url(#gaugeGradient)"
        stroke-width="20"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
        class="gauge-progress"
        filter="url(#glow)"
      />
      
      <!-- 刻度 -->
      <g class="ticks">
        <text x="20" y="115" class="tick-label">0</text>
        <text x="100" y="30" class="tick-label">50</text>
        <text x="180" y="115" class="tick-label">100</text>
      </g>
      
      <!-- 指针 -->
      <g class="needle" :style="{ transform: `rotate(${needleAngle}deg)` }">
        <circle cx="100" cy="100" r="6" fill="var(--text-primary)" />
        <path
          d="M 100 100 L 100 30"
          stroke="var(--text-primary)"
          stroke-width="3"
          stroke-linecap="round"
        />
      </g>
    </svg>
    
    <div class="gauge-value">
      <span class="value-number">{{ value }}</span>
      <span class="value-unit">%</span>
    </div>
    
    <div class="gauge-label">{{ label }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value: number
  label: string
}>()

const radius = 80
const circumference = Math.PI * radius

const dashOffset = computed(() => {
  const progress = Math.max(0, Math.min(100, props.value))
  return circumference - (progress / 100) * circumference
})

const needleAngle = computed(() => {
  const progress = Math.max(0, Math.min(100, props.value))
  return -90 + (progress / 100) * 180
})
</script>

<style scoped>
.gauge-chart {
  position: relative;
  width: 220px;
  margin: 0 auto;
}

.gauge-svg {
  width: 100%;
  height: auto;
}

.gauge-progress {
  transition: stroke-dashoffset 1s ease-out;
}

.ticks {
  font-family: var(--font-mono);
  font-size: 12px;
  fill: var(--text-muted);
}

.tick-label {
  text-anchor: middle;
}

.needle {
  transform-origin: 100px 100px;
  transition: transform 1s ease-out;
}

.gauge-value {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
}

.value-number {
  font-size: 36px;
  font-weight: 700;
  font-family: var(--font-mono);
  background: linear-gradient(135deg, var(--color-info), var(--color-accent));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.value-unit {
  font-size: 18px;
  color: var(--text-secondary);
  margin-left: 2px;
}

.gauge-label {
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: 8px;
}
</style>
