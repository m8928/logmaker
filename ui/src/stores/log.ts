import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

interface Log {
  name: string;
  format: string;
  sample: string;
  eps: number;
  currentEps: number;
  count: number;
  sender: string[];
}

export const useLogStore = defineStore('log', () => {
  const logs = ref<Log[]>([])

  async function fetchLogs() {
    try {
      const response = await axios.get('/api/v1/log')
      logs.value = response.data
    } catch (error) {
      console.error('Error fetching logs:', error)
    }
  }

  return { logs, fetchLogs }
})
