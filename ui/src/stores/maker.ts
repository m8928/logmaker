import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

interface Maker {
  name: string;
  type: string;
  args: { [key: string]: any };
  sample: string;
  size: number;
  ref: number;
}

interface MakerType {
  type: string;
  args: any;
}

export const useMakerStore = defineStore('maker', () => {
  const makers = ref<Maker[]>([])
  const makerTypes = ref<MakerType[]>([])

  async function fetchMakers() {
    try {
      const response = await axios.get('/api/v1/maker')
      makers.value = response.data
    } catch (error) {
      console.error('Error fetching makers:', error)
    }
  }

  async function fetchMakerTypes() {
    try {
      const response = await axios.get('/api/v1/plugin/maker')
      makerTypes.value = response.data
    } catch (error) {
      console.error('Error fetching maker types:', error)
    }
  }

  return { makers, makerTypes, fetchMakers, fetchMakerTypes }
})
