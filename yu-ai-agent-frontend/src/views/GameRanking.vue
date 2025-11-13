<template>
  <div class="game-ranking-container">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">游戏排行智能分析</h1>
      <div class="placeholder"></div>
    </div>

    <div class="content-wrapper">
      <section class="intro-banner">
        <div class="intro-text">
          <h2>实时榜单 + 智能对话，全面洞察游戏行业趋势</h2>
          <p>
            系统自动爬取Steam、TapTap、3DM等平台榜单数据，结合AI可视化分析能力，
            帮你发现玩家偏好与市场机会。右侧图表可即时刷新，左侧可随时向AI提问，辅助决策。
          </p>
        </div>
        <div class="banner-actions">
          <button class="refresh-button" @click="loadCurrentTabReport" :disabled="reportsLoading">
            {{ reportsLoading ? '正在更新数据...' : '刷新榜单数据' }}
          </button>
          <span class="last-updated" v-if="lastUpdated">
            最近更新：{{ lastUpdated }}
          </span>
        </div>
      </section>

      <div class="main-layout">
        <div class="report-area">
          <div class="report-header">
            <div>
              <h2>游戏榜单可视化看板</h2>
              <p class="report-subtitle">
                数据驱动的分析结果，帮助你更快识别游戏行业的热门赛道与潜力产品。
              </p>
            </div>
          </div>

          <div class="report-content-wrapper">
            <!-- 图表和标签页区域 -->
            <div class="chart-tabs-layout">
              <!-- 标签页 - 移动到左侧 -->
              <div class="tabs-container-vertical">
                <button
                  v-for="tab in tabs"
                  :key="tab.key"
                  :class="['tab-button-vertical', { active: activeTab === tab.key }]"
                  @click="switchTab(tab.key)"
                >
                  {{ tab.label }}
                </button>
              </div>

              <div class="chart-container">
                <div v-if="reportsError" class="report-error">
                  <p>{{ reportsError }}</p>
                  <button @click="loadCurrentTabReport">重新获取数据</button>
                </div>

                <div v-else class="reports-grid">
                  <GameReportCard
                    v-for="(report, index) in reports"
                    :key="report.id"
                    :report="report"
                    :is-first="index === 0"
                  />

                  <div v-if="reportsLoading && !reports.length" class="report-loading">
                    <div class="spinner"></div>
                    <p>正在加载榜单数据...</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-area">
          <ChatRoom
            :messages="messages"
            :connection-status="connectionStatus"
            ai-type="game-ranking"
            @send-message="sendMessage"
          />
        </div>
      </div>
    </div>

    <div class="footer-container">
      <AppFooter />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import GameReportCard from '../components/GameReportCard.vue'
import AppFooter from '../components/AppFooter.vue'
import { chatWithGameRanking, getLatestGameReports, getSteamReport, getTapTapReport, get3DMReport, getAnalysisResult } from '../api'

useHead({
  title: '游戏排行智能分析 - AI智能分析平台',
  meta: [
    {
      name: 'description',
      content: '游戏排行智能分析平台，自动检索游戏行业榜单、流量数据，提供专业的比较和分析功能'
    },
    {
      name: 'keywords',
      content: '游戏排行,游戏分析,游戏榜单,游戏数据,AI分析,游戏行业'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const connectionStatus = ref('disconnected')
const reports = ref([])
const reportsLoading = ref(false)
const reportsError = ref('')
const lastUpdated = ref('')
const activeTab = ref('steam')
let eventSource = null

// 缓存每个标签的数据
const tabDataCache = ref({})
// 记录哪些标签已经加载过
const loadedTabs = ref(new Set())

const tabs = [
  { key: 'steam', label: 'Steam' },
  { key: 'taptap', label: 'TapTap' },
  { key: '3dm', label: '3DM' }
]

const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content,
    isUser,
    type,
    time: new Date().getTime()
  })
}

const sendMessage = (message) => {
  addMessage(message, true, 'user-question')

  if (eventSource) {
    eventSource.close()
  }

  connectionStatus.value = 'connecting'

  const aiMessageIndex = messages.value.length
  addMessage('', false, 'ai-answer')

  eventSource = chatWithGameRanking(message)

  eventSource.onmessage = (event) => {
    const data = event.data

    if (data && data !== '[DONE]') {
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content += data
      }
    }

    if (data === '[DONE]') {
      connectionStatus.value = 'disconnected'
      eventSource.close()

      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].type = 'ai-final'
      }
    }
  }

  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource.close()

    if (aiMessageIndex < messages.value.length) {
      messages.value[aiMessageIndex].type = 'ai-error'
    }
  }
}

const goBack = () => {
  router.push('/')
}

// 轮询获取分析结果
const pollAnalysisResult = async (reportId, retryCount = 0) => {
  const maxRetries = 60 // 最多重试60次（约1分钟，每次1秒）
  
  if (retryCount >= maxRetries) {
    console.warn('获取分析结果超时')
    return
  }
  
  try {
    const response = await getAnalysisResult(reportId)
    if (response && response.data) {
      if (response.data.status === 'completed' && response.data.analysisResult) {
        // 找到对应的报告并更新分析结果
        const reportIndex = reports.value.findIndex(r => r.id === reportId)
        if (reportIndex !== -1) {
          reports.value[reportIndex].analysisResult = response.data.analysisResult
          // 更新缓存
          if (tabDataCache.value[activeTab.value]) {
            tabDataCache.value[activeTab.value].reports[reportIndex].analysisResult = response.data.analysisResult
          }
        }
      } else if (response.data.status === 'pending') {
        // 还在生成中，1秒后重试
        setTimeout(() => pollAnalysisResult(reportId, retryCount + 1), 1000)
      }
    }
  } catch (error) {
    console.error('获取分析结果失败', error)
    // 出错后也继续重试，但间隔稍长
    setTimeout(() => pollAnalysisResult(reportId, retryCount + 1), 2000)
  }
}

const formatDateTime = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

const switchTab = (tabKey) => {
  if (activeTab.value === tabKey) return
  activeTab.value = tabKey
  
  // 如果该标签已经加载过，直接使用缓存数据
  if (loadedTabs.value.has(tabKey) && tabDataCache.value[tabKey]) {
    reports.value = tabDataCache.value[tabKey].reports || []
    lastUpdated.value = tabDataCache.value[tabKey].lastUpdated || ''
    reportsError.value = tabDataCache.value[tabKey].error || ''
    
    // 检查是否有分析结果还在生成中，如果有则启动轮询
    const cachedReports = tabDataCache.value[tabKey].reports || []
    cachedReports.forEach(report => {
      if (report.analysisResult === '正在生成分析结果...' && report.id) {
        pollAnalysisResult(report.id, 0)
      }
    })
  } else {
    // 第一次点击，加载数据
    loadCurrentTabReport()
  }
}

const loadCurrentTabReport = async () => {
  if (reportsLoading.value) return
  reportsLoading.value = true
  reportsError.value = ''

  try {
    let response
    switch (activeTab.value) {
      case 'steam':
        response = await getSteamReport()
        break
      case 'taptap':
        response = await getTapTapReport()
        break
      case '3dm':
        response = await get3DMReport()
        break
      default:
        response = await getSteamReport()
    }

    if (response && response.data) {
      const reportData = [response.data]
      reports.value = reportData
      const updatedTime = response.data.generatedAt ? formatDateTime(response.data.generatedAt) : ''
      lastUpdated.value = updatedTime
      
      // 如果分析结果还在生成中，启动轮询
      if (response.data.analysisResult === '正在生成分析结果...' && response.data.id) {
        pollAnalysisResult(response.data.id, 0)
      }
      
      // 保存到缓存
      tabDataCache.value[activeTab.value] = {
        reports: reportData,
        lastUpdated: updatedTime,
        error: ''
      }
      loadedTabs.value.add(activeTab.value)
    } else {
      reports.value = []
      reportsError.value = '暂未获取到榜单数据，请稍后重试。'
      lastUpdated.value = ''
      
      // 保存错误状态到缓存
      tabDataCache.value[activeTab.value] = {
        reports: [],
        lastUpdated: '',
        error: '暂未获取到榜单数据，请稍后重试。'
      }
      loadedTabs.value.add(activeTab.value)
    }
  } catch (error) {
    console.error('获取榜单数据失败', error)
    reportsError.value = '获取榜单数据失败，请检查网络或稍后再试。'
    
    // 保存错误状态到缓存
    tabDataCache.value[activeTab.value] = {
      reports: [],
      lastUpdated: '',
      error: '获取榜单数据失败，请检查网络或稍后再试。'
    }
    loadedTabs.value.add(activeTab.value)
  } finally {
    reportsLoading.value = false
  }
}

const loadReports = () => {
  loadCurrentTabReport()
}

onMounted(() => {
  addMessage('你好！我是游戏排行智能分析助手。我可以帮你：\n1. 自动检索游戏行业的各种榜单\n2. 分析游戏的流量、热度、玩家数据\n3. 比较不同游戏的表现和趋势\n4. 提供游戏行业洞察和建议\n\n请告诉我你想了解什么游戏数据？', false)
  loadCurrentTabReport()
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style scoped>
.game-ranking-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f9fbff;
}

.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 16px 24px;
  background: linear-gradient(135deg, #ff6b6b, #ffa500);
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-button {
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: opacity 0.2s;
  justify-self: start;
}

.back-button:hover {
  opacity: 0.8;
}

.back-button:before {
  content: '←';
  margin-right: 8px;
}

.title {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
  text-align: center;
  justify-self: center;
}

.placeholder {
  width: 1px;
  justify-self: end;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 0 24px 24px;
  gap: 24px;
}

.footer-container {
  margin-top: auto;
}

.intro-banner {
  background: linear-gradient(135deg, rgba(79, 117, 255, 0.12), rgba(130, 89, 245, 0.12));
  border-radius: 20px;
  padding: 24px;
  display: flex;
  justify-content: space-between;
  gap: 20px;
  border: 1px solid rgba(79, 117, 255, 0.08);
}

.intro-text h2 {
  font-size: 22px;
  color: #1d2547;
  margin-bottom: 12px;
}

.intro-text p {
  color: #49516f;
  line-height: 1.6;
}

.banner-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  min-width: 220px;
}

.refresh-button {
  background: linear-gradient(135deg, #4f75ff, #8259f5);
  color: #fff;
  border: none;
  padding: 12px 20px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 12px 30px rgba(79, 117, 255, 0.3);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.refresh-button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  box-shadow: none;
}

.refresh-button:not(:disabled):hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 36px rgba(79, 117, 255, 0.35);
}

.last-updated {
  font-size: 12px;
  color: #6c7ba1;
}

.main-layout {
  display: grid;
  grid-template-columns: minmax(480px, 1.5fr) minmax(320px, 1fr);
  gap: 24px;
  align-items: start;
}

.chat-area {
  flex: 1 1 400px;
  height: calc(100vh - 200px);
  min-height: 600px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.report-area {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: calc(100vh - 200px);
  min-height: 600px;
}

.report-header {
  flex-shrink: 0;
}

.report-header h2 {
  font-size: 20px;
  color: #1d2547;
  margin-bottom: 6px;
}

.report-subtitle {
  color: #6c7ba1;
  font-size: 14px;
  line-height: 1.5;
}

.report-content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chart-tabs-layout {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 16px;
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.chart-container {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 8px;
  min-height: 0;
}

/* 自定义滚动条样式 */
.chart-container::-webkit-scrollbar {
  width: 8px;
}

.chart-container::-webkit-scrollbar-track {
  background: rgba(79, 117, 255, 0.05);
  border-radius: 4px;
}

.chart-container::-webkit-scrollbar-thumb {
  background: rgba(79, 117, 255, 0.3);
  border-radius: 4px;
  transition: background 0.2s;
}

.chart-container::-webkit-scrollbar-thumb:hover {
  background: rgba(79, 117, 255, 0.5);
}

.tabs-container-vertical {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 0;
  min-width: 120px;
}

.tab-button-vertical {
  background: transparent;
  border: none;
  padding: 14px 16px;
  font-size: 14px;
  font-weight: 500;
  color: #6c7ba1;
  cursor: pointer;
  border-left: 3px solid transparent;
  border-radius: 8px 0 0 8px;
  transition: all 0.3s ease;
  text-align: left;
  white-space: nowrap;
}

.tab-button-vertical:hover {
  color: #4f75ff;
  background: rgba(79, 117, 255, 0.08);
}

.tab-button-vertical.active {
  color: #4f75ff;
  border-left-color: #4f75ff;
  background: rgba(79, 117, 255, 0.12);
  font-weight: 600;
}

.reports-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.report-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: #fff;
  border-radius: 16px;
  border: 1px dashed rgba(79, 117, 255, 0.3);
  gap: 12px;
  color: #4f75ff;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 4px solid rgba(79, 117, 255, 0.2);
  border-top-color: #4f75ff;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

.report-error {
  background: rgba(255, 99, 132, 0.08);
  border: 1px solid rgba(255, 99, 132, 0.2);
  border-radius: 12px;
  padding: 16px;
  color: #c23632;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.report-error button {
  background: transparent;
  color: #c23632;
  border: 1px solid rgba(194, 54, 50, 0.4);
  border-radius: 8px;
  padding: 8px 16px;
  cursor: pointer;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .header {
    padding: 12px 16px;
  }

  .title {
    font-size: 18px;
  }

  .content-wrapper {
    padding: 0 16px 16px;
    gap: 16px;
  }

  .main-layout {
    grid-template-columns: 1fr;
  }

  .intro-banner {
    flex-direction: column;
  }

  .banner-actions {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }

  .report-area {
    height: auto;
    min-height: 500px;
  }

  .chart-tabs-layout {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }

  .tabs-container-vertical {
    flex-direction: row;
    overflow-x: auto;
    padding: 0 0 8px 0;
    min-width: auto;
    border-bottom: 2px solid rgba(79, 117, 255, 0.1);
  }

  .tab-button-vertical {
    border-left: none;
    border-bottom: 3px solid transparent;
    border-radius: 0;
    padding: 12px 20px;
    white-space: nowrap;
  }

  .tab-button-vertical.active {
    border-left: none;
    border-bottom-color: #4f75ff;
    background: rgba(79, 117, 255, 0.08);
  }
  
  /* 确保标签文本在小屏幕上也能完整显示 */
  .tab-button-vertical {
    padding: 12px 16px;
    font-size: 13px;
    min-width: fit-content;
  }
}

@media (max-width: 480px) {
  .header {
    padding: 10px 12px;
  }

  .back-button {
    font-size: 14px;
  }

  .title {
    font-size: 16px;
  }

  .content-wrapper {
    padding: 0 12px 12px;
  }

  .refresh-button {
    width: 100%;
  }

  .report-area {
    height: auto;
    min-height: 400px;
  }
}
</style>

