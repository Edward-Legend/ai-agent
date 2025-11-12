<template>
  <div class="game-ranking-container">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">游戏排行智能分析</h1>
      <div class="placeholder"></div>
    </div>
    
    <div class="content-wrapper">
      <div class="chat-area">
        <ChatRoom 
          :messages="messages" 
          :connection-status="connectionStatus"
          ai-type="game-ranking"
          @send-message="sendMessage"
        />
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
import AppFooter from '../components/AppFooter.vue'
import { chatWithGameRanking } from '../api'

// 设置页面标题和元数据
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
let eventSource = null

// 添加消息到列表
const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content,
    isUser,
    type,
    time: new Date().getTime()
  })
}

// 发送消息
const sendMessage = (message) => {
  addMessage(message, true, 'user-question')
  
  // 连接SSE
  if (eventSource) {
    eventSource.close()
  }
  
  // 设置连接状态
  connectionStatus.value = 'connecting'
  
  // 创建一个空的AI回复消息（用于流式显示）
  const aiMessageIndex = messages.value.length
  addMessage('', false, 'ai-answer')
  
  eventSource = chatWithGameRanking(message)
  
  // 监听SSE消息
  eventSource.onmessage = (event) => {
    const data = event.data
    
    if (data && data !== '[DONE]') {
      // 更新最新的AI消息内容，而不是创建新消息（实现流式打字机效果）
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content += data
      }
    }
    
    if (data === '[DONE]') {
      // 完成后关闭连接
      connectionStatus.value = 'disconnected'
      eventSource.close()
      
      // 更新最后一条消息的类型为完成状态
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].type = 'ai-final'
      }
    }
  }
  
  // 监听SSE错误
  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource.close()
    
    // 更新最后一条消息的类型为错误状态
    if (aiMessageIndex < messages.value.length) {
      messages.value[aiMessageIndex].type = 'ai-error'
    }
  }
}

// 返回主页
const goBack = () => {
  router.push('/')
}

// 页面加载时添加欢迎消息
onMounted(() => {
  // 添加欢迎消息
  addMessage('你好！我是游戏排行智能分析助手。我可以帮你：\n1. 自动检索游戏行业的各种榜单\n2. 分析游戏的流量、热度、玩家数据\n3. 比较不同游戏的表现和趋势\n4. 提供游戏行业洞察和建议\n\n请告诉我你想了解什么游戏数据？', false)
})

// 组件销毁前关闭SSE连接
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
}

.chat-area {
  flex: 1;
  padding: 16px;
  overflow: hidden;
  position: relative;
  /* 设置最小高度确保内容显示正常 */
  min-height: calc(100vh - 56px - 180px); /* 100vh减去头部高度和页脚高度 */
  margin-bottom: 16px; /* 为页脚留出空间 */
}

.footer-container {
  margin-top: auto;
}

/* 响应式样式 */
@media (max-width: 768px) {
  .header {
    padding: 12px 16px;
  }
  
  .title {
    font-size: 18px;
  }
  
  .chat-area {
    padding: 12px;
    min-height: calc(100vh - 48px - 160px); /* 调整计算值 */
    margin-bottom: 12px;
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
  
  .chat-area {
    padding: 8px;
    min-height: calc(100vh - 42px - 150px); /* 再次调整计算值 */
    margin-bottom: 8px;
  }
}
</style>

