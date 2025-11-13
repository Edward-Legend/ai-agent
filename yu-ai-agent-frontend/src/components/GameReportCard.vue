<template>
  <div class="report-card" :class="{ 'first-card': isFirst }">
    <div class="report-header">
      <div class="report-meta">
        <span class="source-tag">{{ report?.source ?? '未知来源' }}</span>
        <span class="timestamp">{{ formattedTime }}</span>
      </div>
      <h3 class="report-title">{{ report?.title ?? '游戏榜单分析' }}</h3>
    </div>
    <div ref="chartRef" class="chart-canvas" :class="{ 'first-chart': isFirst }">
      <div v-if="!hasValidChart" class="chart-empty">暂无图表数据</div>
    </div>
    <div class="analysis-section">
      <h4>分析洞察</h4>
      <p ref="analysisTextRef" class="analysis-text">{{ displayedAnalysis }}</p>
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
  },
  isFirst: {
    type: Boolean,
    default: false
  }
})

const chartRef = ref(null)
const analysisTextRef = ref(null)
let chartInstance = null
let resizeObserver = null
let typewriterTimer = null
const displayedAnalysis = ref('')
const isChartRendered = ref(false)

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
    
    // 处理横向柱状图的标签显示：如果数据项包含rank字段，显示原始排名
    if (option.series && Array.isArray(option.series)) {
      option.series.forEach(series => {
        if (series.type === 'bar' && series.data && Array.isArray(series.data)) {
          // 检查数据项是否包含rank字段
          const hasRankField = series.data.some(item => 
            item && typeof item === 'object' && 'rank' in item
          )
          
          if (hasRankField && series.label) {
            // 使用函数形式的formatter来显示原始排名
            series.label.formatter = (params) => {
              if (params.data && typeof params.data === 'object' && params.data.rank) {
                return `排名 ${params.data.rank}`
              }
              return params.value
            }
          }
        }
      })
    }
    
    chartInstance.setOption(option, true)
    // 标记图表已渲染
    isChartRendered.value = true
    // 图表渲染完成后，如果分析结果已准备好（不是"正在生成分析结果..."），开始打字机效果
    if (props.report?.analysisResult && props.report.analysisResult !== '正在生成分析结果...') {
      startTypewriter()
    } else if (props.report?.analysisResult === '正在生成分析结果...') {
      // 显示加载提示
      displayedAnalysis.value = '正在生成分析结果...'
    }
  } catch (error) {
    console.error('渲染图表失败', error)
    isChartRendered.value = true
    // 即使图表渲染失败，也显示分析结果
    if (props.report?.analysisResult) {
      startTypewriter()
    }
  }
}

const startTypewriter = () => {
  // 清除之前的定时器
  if (typewriterTimer) {
    clearInterval(typewriterTimer)
  }
  
  const fullText = props.report?.analysisResult ?? '暂无分析结果'
  displayedAnalysis.value = ''
  let currentIndex = 0
  
  // 如果图表还没渲染完成，等待一下
  if (!isChartRendered.value) {
    const checkInterval = setInterval(() => {
      if (isChartRendered.value) {
        clearInterval(checkInterval)
        typewriterTimer = setInterval(() => {
          if (currentIndex < fullText.length) {
            displayedAnalysis.value += fullText[currentIndex]
            currentIndex++
          } else {
            clearInterval(typewriterTimer)
            typewriterTimer = null
          }
        }, 30) // 每30ms显示一个字符
      }
    }, 100)
  } else {
    // 图表已渲染，直接开始打字机效果
    typewriterTimer = setInterval(() => {
      if (currentIndex < fullText.length) {
        displayedAnalysis.value += fullText[currentIndex]
        currentIndex++
      } else {
        clearInterval(typewriterTimer)
        typewriterTimer = null
      }
    }, 30) // 每30ms显示一个字符
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
  if (typewriterTimer) {
    clearInterval(typewriterTimer)
  }
  disposeChart()
})

watch(() => props.report?.chartConfig, () => {
  isChartRendered.value = false
  displayedAnalysis.value = ''
  renderChart()
}, { deep: true })

watch(() => props.report?.analysisResult, (newVal, oldVal) => {
  // 如果分析结果从"正在生成分析结果..."变为实际内容，且图表已渲染，重新开始打字机效果
  if (newVal && newVal !== oldVal && newVal !== '正在生成分析结果...' && isChartRendered.value) {
    displayedAnalysis.value = '' // 重置显示内容
    startTypewriter()
  } else if (newVal === '正在生成分析结果...' && isChartRendered.value) {
    // 显示加载提示
    displayedAnalysis.value = '正在生成分析结果...'
  }
}, { immediate: true })
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

.chart-canvas.first-chart {
  height: 400px;
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

.analysis-text {
  min-height: 1.6em;
  white-space: pre-wrap;
  word-break: break-word;
}

.report-card.first-card {
  width: 100%;
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

