<template>
  <div class="section">
    <div class="inner-bottom">
      <span
        style="font-size: var(--el-font-size-extra-large); font-weight: bold"
        >Log</span
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
          dialogEditMode = false;
        "
        >Add Log</el-button
      >
      <el-button
          :icon="Upload"
          :loading="waitRequest"
          @click="dialogImportVisible = true"
      >Import Data</el-button
      >
      <el-button :icon="Download" :loading="waitRequest" @click="downloadData()"
        >Export Data</el-button
      >
    </div>
    <el-divider class="no-margin no-padding" />
    <div class="inner">
      <el-table
        :data="data"
        :default-expand-all="true"
        :empty-text="waitRequest ? 'Loading...' : 'No Data'"
      >
        <el-table-column type="expand" width="30">
          <template #default="props">
            <el-table
              :data="transformExpandData(props.row)"
              style="width: 100%"
              :show-header="false"
            >
              <el-table-column width="30" />
              <el-table-column prop="key" label="" width="100" align="center">
                <template #default="scope">
                  <el-tag type="info">{{ scope.row.key }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="value" label="">
                <template #default="scope">
                  <div v-if="Array.isArray(scope.row.value)">
                    <el-tag :key="value" v-for="value in scope.row.value">{{
                      value
                    }}</el-tag>
                  </div>
                  <div class="preview-pre" v-else>
                    {{ scope.row.value }}
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="Name" />
        <el-table-column prop="eps" label="Event/Sec" align="right" />
        <el-table-column
          prop="currentEps"
          label="Actual Event/Sec"
          align="right"
        />
        <el-table-column prop="count" label="Count" align="right" />
        <el-table-column fixed="right" label="" width="160">
          <template #default="scope">
            <el-button
              size="small"
              :icon="CopyDocument"
              :loading="waitRequest"
              @click="
                dialogEditMode = false;
                dialogFormVisible = true;
                formData.update(
                  'copy-of-' + scope.row.name,
                  scope.row.format,
                  scope.row.eps,
                  scope.row.sender
                );
              "
            />
            <el-button
              size="small"
              :icon="Edit"
              :loading="waitRequest"
              @click="
                dialogEditMode = true;
                dialogFormVisible = true;
                formData.update(
                  scope.row.name,
                  scope.row.format,
                  scope.row.eps,
                  scope.row.sender
                );
                previewLog();
              "
            />
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
      :title="dialogEditMode ? 'Edit Log' : 'Add Log'"
      @open="fetchSupportData"
      @close="
        formData.clean();
        previewData = '';
      "
      :close-on-click-modal="false"
      width="80%"
      :show-close="!waitRequest"
      :close-on-press-escape="!waitRequest"
    >
      <el-form :model="formData" label-position="top">
        <el-form-item label="Name" :required="true">
          <el-input v-model="formData.name" :disabled="dialogEditMode" />
        </el-form-item>
        <el-form-item label="Format" :required="true">
          <el-input
            v-model="formData.format"
            @input="previewLog"
            autosize
            type="textarea"
            ref="logFormat"
            @blur="previewLog"
          />
        </el-form-item>
        <el-form-item>
          <el-collapse v-model="makerHelper" class="full-width">
            <el-collapse-item name="1">
              <template #title>
                <el-icon class="helper-icon-margin"><EditPen /></el-icon> Maker
              </template>
              <div class="helper-command-button">
                <div>
                  <el-switch
                    v-model="makerHelperToggle"
                    inactive-text="Sample Value"
                    active-text="Maker Name"
                  />
                </div>
                <div class="flex-grow" />
                <div>
                  <el-button
                    @click="fetchHelperData"
                    :icon="Refresh"
                    :loading="makerHelperRequest"
                    >Reload</el-button
                  >
                </div>
              </div>
              <el-divider class="no-margin no-margin helper-divider-margin" />
              <div>
                <span
                  class="helper"
                  v-for="value in helperData"
                  :key="value.name"
                >
                  <el-tooltip
                    :content="
                      makerHelperToggle ? value.sample.toString() : value.name
                    "
                  >
                    <el-button @click="appendFormat(value.name)">
                      {{ makerHelperToggle ? value.name : value.sample }}
                    </el-button>
                  </el-tooltip>
                </span>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-form-item>
        <el-form-item label="Preview">
          <el-input
            readonly
            :model-value="previewData"
            autosize
            type="textarea"
          />
        </el-form-item>
        <el-form-item label="Event/Sec" :required="true">
          <el-input-number v-model="formData.eps" />
        </el-form-item>
        <el-form-item label="Sender" :required="true">
          <el-select
            v-model="formData.sender"
            :placeholder="
              formData.sender.length > 0
                ? ''
                : waitRequest
                ? 'Sender Loading...'
                : 'Select Sender'
            "
            multiple
            :loading="waitRequest"
          >
            <el-option
              v-for="item in supportData"
              :key="item.name"
              :label="item.name"
              :value="item.name"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button :loading="waitRequest" @click="dialogFormVisible = false"
            >Cancel</el-button
          >
          <el-button
            type="primary"
            :loading="waitRequest"
            v-if="dialogEditMode"
            @click="updateLog"
          >
            Edit
          </el-button>
          <el-button
            type="primary"
            :loading="waitRequest"
            v-if="!dialogEditMode"
            @click="addLog"
          >
            Add
          </el-button>
        </span>
      </template>
    </el-dialog>
    <el-dialog
        v-model="dialogImportVisible"
        title="Import Log"
        :close-on-click-modal="false"
        width="350px"
        :show-close="!waitRequest"
        :close-on-press-escape="!waitRequest"
    >
      <div>
        <el-upload
            drag
            action="/api/v1/log:import-file"
            :show-file-list="false"
            :on-success="importLog"
            :on-progress="importLogProgress"
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
          <el-button :loading="waitRequest" @click="dialogImportVisible = false"
          >Cancel</el-button
          >
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { saveAs } from "file-saver";
import { ref, reactive } from "vue";
import axios from "axios";
import {
  Refresh,
  Plus,
  Delete,
  Edit,
  CopyDocument,
  Download,
  Upload,
  EditPen,
} from "@element-plus/icons-vue";

interface Log {
  name: string;
  format: string;
  sample: string;
  eps: number;
  currentEps: number;
  count: number;
  sender: [string];
}

class LogForm {
  name: string;
  format: string;
  eps: number;
  sender: string[];

  constructor(name: string, format: string, eps: number, sender: string[]) {
    this.name = name;
    this.format = format;
    this.eps = eps;
    this.sender = sender;
  }

  update(name: string, format: string, eps: number, sender: string[]) {
    this.name = name;
    this.format = format;
    this.eps = eps;
    this.sender = sender;
  }

  clean() {
    this.name = "";
    this.format = "";
    this.eps = 0;
    this.sender = [];
  }
}

interface Maker {
  name: string;
  type: string;
  args: { [key: string]: any };
  sample: string;
  size: number;
}

const formData = reactive(new LogForm("", "", 0, []));

const data = ref<Log[] | null>(null);
const helperData = ref<Maker[] | null>(null);
const supportData = ref<SupportData[] | null>(null);
const dialogFormVisible = ref(false);
const dialogEditMode = ref(false);
const dialogImportVisible = ref(false);
const previewData = ref("");
const waitRequest = ref(false);
const makerHelper = ref("");
const makerHelperToggle = ref(true);
const makerHelperRequest = ref(false);

const addLog = () => {
  console.log(formData);
  waitRequest.value = true;
  axios
    .post("/api/v1/log", formData)
    .then((response) => {
      console.log(response.data);
      if (response.data.type === "SUCCESS") {
        dialogFormVisible.value = false;
        formData.clean();
      }
      waitRequest.value = false;
      fetchData();
    })
    .catch((error) => {
      console.error(error);
      waitRequest.value = false;
    });
};

const previewLog = () => {
  axios
    .post("/api/v1/log:preview", formData)
    .then((response) => {
      console.log(response.data);
      previewData.value = response.data.message;
    })
    .catch((error) => {
      console.error(error.response.data);
      previewData.value = error.response.data.message;
    });
};

const updateLog = () => {
  waitRequest.value = true;
  axios
    .put("/api/v1/log/" + formData.name, formData)
    .then((response) => {
      console.log(response.data);
      if (response.data.type === "SUCCESS") {
        dialogFormVisible.value = false;
        formData.clean();
      }
      waitRequest.value = false;
      fetchData();
    })
    .catch((error) => {
      console.error(error);
      waitRequest.value = false;
    });
};

const fetchData = async () => {
  waitRequest.value = true;
  const response = await fetch("/api/v1/log");
  data.value = (await response.json()) as Log[];
  waitRequest.value = false;
};

const fetchHelperData = async () => {
  makerHelperRequest.value = true;
  const response = await fetch("/api/v1/maker");
  helperData.value = (await response.json()) as Maker[];
  makerHelperRequest.value = false;
};

const downloadData = async () => {
  const response = await fetch("/api/v1/log");
  const file = new File(
    [JSON.stringify(await response.json(), null, 4)],
    "logmaker-log.json",
    { type: "text/plain;charset=utf-8" }
  );
  saveAs(file);
};

interface SupportData {
  name: string;
  type: string;
}

const fetchSupportData = async () => {
  waitRequest.value = true;
  const response = await fetch("/api/v1/sender");
  supportData.value = (await response.json()) as SupportData[];
  waitRequest.value = false;
};

const deleteData = async (name: string) => {
  waitRequest.value = true;
  axios
    .delete("/api/v1/log/" + name)
    .then(() => {
      waitRequest.value = false;
      fetchData();
    })
    .catch((error) => {
      console.error(error);
      waitRequest.value = false;
    });
};

const transformExpandData = (log: Log): { [key: string]: any }[] => {
  const result = [
    {
      key: "Format",
      value: log.format,
    },
    {
      key: "Sample",
      value: log.sample,
    },
    {
      key: "Sender",
      value: log.sender,
    },
  ];
  return result;
};

const importLogProgress = () => {
  waitRequest.value = true;
};

const importLog = (response: { [key: string]: any }[]) => {
  if (response.length === 1 && response[0].type === "ERROR") {
    waitRequest.value = false;
  } else {
    waitRequest.value = false;
    dialogImportVisible.value = false;
    fetchData();
  }
};

const logFormat = ref<HTMLDivElement | null>(null);

const appendFormat = (maker: string) => {
  formData.format = formData.format.concat("<" + maker + ">");
  previewLog();
};

fetchData();
fetchHelperData();
</script>

<style scoped>
.preview-pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: inherit;
}

.el-tag + .el-tag {
  margin-left: 0.2rem;
}

.helper + .helper {
  margin-left: 0.2rem;
  line-height: 35px;
}
</style>
