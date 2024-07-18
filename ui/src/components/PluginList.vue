<template>
  <div class="section">
    <el-affix>
    <div class="inner-bottom">
      <span
        style="font-size: var(--el-font-size-extra-large); font-weight: bold"
        >Plugin</span
      >
      <div class="flex-grow" />
      <el-button :icon="Refresh" :loading="waitRequest" @click="fetchData"
        >Reload</el-button
      >
      <el-button
        :icon="Plus"
        :loading="waitRequest"
        @click="
          dialogFormVisible = true;
        "
        >Add Plugin</el-button
      >
    </div>
    <el-divider class="no-margin no-padding" />
    </el-affix>
    <div class="inner">
      <el-table
        :data="data"
        table-layout="auto"
        :empty-text="waitRequest ? 'Loading...' : 'No Data'"
      >
        <el-table-column prop="name" label="Name" min-width="120" />
        <el-table-column
          prop="version"
          label="Version"
          width="150"
          align="center"
        >
          <template #default="scope">
            <el-tag>{{ scope.row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="provider" label="Provider" />
        <el-table-column prop="filename" label="Filename" />
        <el-table-column prop="ref" label="Used" align="right" />
        <el-table-column fixed="right" label="" width="60">
          <template #default="scope">
            <el-button
              size="small"
              :loading="waitRequest"
              :disabled="scope.row.ref > 0"
              :icon="Delete"
              @click.prevent="deleteData(scope.row.name)"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog
      v-model="dialogFormVisible"
      title="Add Plugin"
      :close-on-click-modal="false"
      width="350px"
      :show-close="!waitRequest"
      :close-on-press-escape="!waitRequest"
    >
      <div>
        <el-upload
          drag
          action="/api/v1/plugin"
          :show-file-list="false"
          :on-success="addPlugin"
          :on-progress="addPluginProgress"
          :disabled="waitRequest"
          v-loading="waitRequest"
        >
          <div>
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              Drop file here or <em>click to upload</em>
            </div>
          </div>
        </el-upload>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button :loading="waitRequest" @click="dialogFormVisible = false"
            >Cancel</el-button
          >
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ref } from "vue";
import axios from "axios";
import { Refresh, Plus, Delete, UploadFilled } from "@element-plus/icons-vue";

interface Plugin {
  name: string;
  version: string;
  provider: string;
  filename: string;
}

const data = ref<Plugin[] | null>(null);
const dialogFormVisible = ref(false);
const waitRequest = ref(false);

const fetchData = async () => {
  waitRequest.value = true;
  const response = await fetch("/api/v1/plugin");
  data.value = (await response.json()) as Plugin[];
  waitRequest.value = false;
};

const deleteData = async (name: string) => {
  waitRequest.value = true;
  axios
    .delete("/api/v1/plugin/" + name)
    .then(() => {
      waitRequest.value = false;
      fetchData();
    })
    .catch((error) => {
      console.error(error);
      waitRequest.value = false;
    });
};

const addPluginProgress = () => {
  waitRequest.value = true;
};

const addPlugin = (response: { [key: string]: any }) => {
  if (response.type === "ERROR") {
    waitRequest.value = false;
  } else {
    waitRequest.value = false;
    dialogFormVisible.value = false;
    fetchData();
  }
};

fetchData();
</script>

<style scoped></style>
