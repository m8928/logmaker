<template>
  <div class="footer">
    <div class="version-info">
      <span>v{{ version }}</span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue';

// 버전 정보
const version = ref('2.0.1'); // 여기에 실제 버전 값을 넣으세요
// const version = ref(import.meta.env.VITE_APP_VERSION || '1.0.0');
</script>

<style scoped>
.footer {
  width: 100%;
  padding: 8px 0;
  text-align: right;
  font-size: 10px;
  color: var(--el-text-color-secondary);
  margin-top: auto;
}

.version-info {
  display: inline-block;
}
</style>