<template>
  <div class="report-card">
    <div class="report-header">
      <div class="report-meta">
        <span class="source-tag">{{ report?.source ?? '未知来源' }}</span>
        <span class="timestamp">{{ formattedTime }}</span>
      </div>
      <h3 class="report-title">{{ report?.title ?? '游戏榜单分析' }}</h3>
    </div>
    <div ref="chartRef" class="chart-canvas">
      <div v-if="!hasValidChart" class="chart-empty">暂无图表数据</div>
    </div>
    <div class="analysis-section">
      <h4>分析洞察</h4>
      <p>{{ report?.analysisResult ?? '暂无分析结果' }}</p>
    </div>
    <div class="metadata" v-if="metadataEntries.length">
      <span v-for="(value, key) in metadataEntries" :key="key" class="meta-item">
        <strong>{{ key }}：</strong>{{ value }}
      </span>
    </div>
    <details class="raw-data" v-if="report?.rawData">
      <summary>查看原始数据 (CSV)</summary>
      <pre>{{ report.rawData }}</pre>
    </details>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  report: {
    type: Object,
    default: () => ({})
  }
})

const chartRef = ref(null)
let chartInstance = null
let resizeObserver = null

const hasValidChart = computed(() => {
  return props.report && props.report.chartConfig && !props.report.chartConfig.error
})

const formattedTime = computed(() => {
  if (!props.report?.generatedAt) {
    return ''
  }
  const date = new Date(props.report.generatedAt)
  if (Number.isNaN(date.getTime())) {
    return props.report.generatedAt
  }
  return date.toLocaleString('zh-CN', { hour12: false })
})

const metadataEntries = computed(() => {
  if (!props.report?.metadata) {
    return []
  }
  return Object.entries(props.report.metadata).reduce((acc, [key, value]) => {
    if (value === undefined || value === null || value === '') {
      return acc
    }
    const transformedKey = key === 'sourceUrl' ? '来源链接' :
      key === 'fallback' ? '是否为演示数据' :
      key === 'sampleCount' ? '样本量' :
      key
    const transformedValue = key === 'fallback'
      ? (value ? '是' : '否')
      : value
    return {
      ...acc,
      [transformedKey]: transformedValue
    }
  }, {})
})

const disposeChart = () => {
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
}

const renderChart = async () => {
  if (!hasValidChart.value) {
    disposeChart()
    return
  }
  await nextTick()
  if (!chartRef.value) {
    return
  }

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  try {
    const option = JSON.parse(JSON.stringify(props.report.chartConfig))
    chartInstance.setOption(option, true)
  } catch (error) {
    console.error('渲染图表失败', error)
  }
}

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', handleResize)
  if (window.ResizeObserver) {
    resizeObserver = new ResizeObserver(() => handleResize())
    if (chartRef.value) {
      resizeObserver.observe(chartRef.value)
    }
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeObserver && chartRef.value) {
    resizeObserver.unobserve(chartRef.value)
  }
  disposeChart()
})

watch(() => props.report?.chartConfig, () => {
  renderChart()
}, { deep: true })
</script>

<style scoped>
.report-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 12px 40px rgba(15, 35, 95, 0.08);
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 400px;
  border: 1px solid rgba(15, 35, 95, 0.05);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.report-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 18px 60px rgba(15, 35, 95, 0.12);
}

.report-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.report-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #6c7ba1;
}

.source-tag {
  background: linear-gradient(120deg, rgba(79, 117, 255, 0.15), rgba(130, 89, 245, 0.15));
  border-radius: 12px;
  padding: 4px 10px;
  color: #4f75ff;
  font-weight: 600;
}

.timestamp {
  font-size: 12px;
  color: #8a94b4;
}

.report-title {
  font-size: 18px;
  color: #1d2547;
  font-weight: 700;
}

.chart-canvas {
  width: 100%;
  height: 260px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(79, 117, 255, 0.08), rgba(130, 89, 245, 0.08));
  position: relative;
  overflow: hidden;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8a94b4;
  font-size: 14px;
}

.analysis-section {
  background: rgba(79, 117, 255, 0.06);
  border-radius: 12px;
  padding: 12px 16px;
  color: #273160;
  line-height: 1.6;
}

.analysis-section h4 {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #4f75ff;
}

.metadata {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  font-size: 12px;
  color: #5c678a;
}

.metadata .meta-item strong {
  font-weight: 600;
}

.raw-data {
  font-size: 12px;
  color: #5c678a;
}

.raw-data pre {
  background: #f7f8fb;
  padding: 12px;
  border-radius: 8px;
  max-height: 200px;
  overflow: auto;
  margin-top: 8px;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 768px) {
  .chart-canvas {
    height: 220px;
  }
}
</style>

