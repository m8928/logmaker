import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

interface Plugin {
  name: string;
  version: string;
  provider: string;
  filename: string;
  ref: number;
}

export const usePluginStore = defineStore('plugin', () => {
  const plugins = ref<Plugin[]>([])

  async function fetchPlugins() {
    try {
      const response = await axios.get('/api/v1/plugin')
      plugins.value = response.data
    } catch (error) {
      console.error('Error fetching plugins:', error)
    }
  }

  return { plugins, fetchPlugins }
})
