import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

interface Sender {
  name: string;
  type: string;
  args: { [key: string]: any };
  count: number;
  ref: number;
}

interface SenderType {
  type: string;
  args: any;
}

export const useSenderStore = defineStore('sender', () => {
  const senders = ref<Sender[]>([])
  const senderTypes = ref<SenderType[]>([])

  async function fetchSenders() {
    try {
      const response = await axios.get('/api/v1/sender')
      senders.value = response.data
    } catch (error) {
      console.error('Error fetching senders:', error)
    }
  }

  async function fetchSenderTypes() {
    try {
      const response = await axios.get('/api/v1/plugin/sender')
      senderTypes.value = response.data
    } catch (error) {
      console.error('Error fetching sender types:', error)
    }
  }

  return { senders, senderTypes, fetchSenders, fetchSenderTypes }
})
