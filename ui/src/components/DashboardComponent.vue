<template>
  <el-space wrap alignment="center">
    <el-statistic
      title="Maker"
      :value="waitRequest ? 0 : data.maker"
      class="statistic-card"
    >
      <template #prefix
        ><el-icon style="vertical-align: -0.125em"> <SetUp /> </el-icon
      ></template>
    </el-statistic>

    <el-statistic
      title="Log"
      :value="waitRequest ? 0 : data.log"
      class="statistic-card"
    >
      <template #prefix
        ><el-icon style="vertical-align: -0.125em"> <Tickets /> </el-icon
      ></template>
    </el-statistic>

    <el-statistic
      title="Sender"
      :value="waitRequest ? 0 : data.sender"
      class="statistic-card"
    >
      <template #prefix
        ><el-icon style="vertical-align: -0.125em"> <Van /> </el-icon
      ></template>
    </el-statistic>

    <el-statistic
      title="Plugin"
      :value="waitRequest ? 0 : data.plugin"
      class="statistic-card"
    >
      <template #prefix
        ><el-icon style="vertical-align: -0.125em"> <TurnOff /> </el-icon
      ></template>
    </el-statistic>

    <el-statistic
      title="Event/Sec"
      :value="waitRequest ? 0 : data.actualEps"
      class="statistic-card"
    >
      <template #suffix>/Sec</template>
    </el-statistic>

    <el-statistic
      title="CPU"
      :value="waitRequest ? 0 : data.cpu"
      class="statistic-card"
    >
      <template #suffix>%</template>
    </el-statistic>

    <el-statistic
      title="Memory"
      :value="waitRequest ? 0 : data.memory"
      class="statistic-card"
    >
      <template #suffix>MB</template>
    </el-statistic>

    <el-statistic
      title="Thread"
      :value="waitRequest ? 0 : data.thread"
      class="statistic-card"
    >
    </el-statistic>
  </el-space>
</template>

<script setup lang="ts">
import { ref } from "vue";

interface Dashboard {
  maker: number;
  log: number;
  sender: number;
  plugin: number;
  eps: number;
  actualEps: number;
  cpu: number;
  memory: number;
  thread: number;
}

const data = ref<Dashboard>();
const waitRequest = ref(true);

const fetchData = async () => {
  waitRequest.value = true;
  const response = await fetch("/api/v1/dashboard");
  data.value = (await response.json()) as Dashboard;
  waitRequest.value = false;
};

fetchData();
</script>

<style scoped></style>
