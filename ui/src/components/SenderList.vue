<template>
  <div class="section">
    <div class="inner-bottom">
      <span
        style="font-size: var(--el-font-size-extra-large); font-weight: bold"
        >Sender</span
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
        >Add Sender</el-button
      >
      <el-button :icon="Upload" :loading="waitRequest">Import Data</el-button>
      <el-button :icon="Download" :loading="waitRequest" @click="downloadData()"
        >Export Data</el-button
      >
    </div>
    <el-divider class="no-margin no-padding" />
    <div class="inner">
      <el-table
        :data="data"
        table-layout="auto"
        :empty-text="waitRequest ? 'Loading...' : 'No Data'"
      >
        <el-table-column prop="name" label="Name" />
        <el-table-column prop="type" label="Type" width="150">
          <template #default="scope">
            <el-tag>{{ scope.row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="Count" width="200" />
        <el-table-column prop="ref" label="Used" width="110" />
        <el-table-column fixed="right" label="" width="160">
          <template #default="scope">
            <el-button
              size="small"
              :loading="waitRequest"
              :icon="CopyDocument"
              @click="
                dialogEditMode = false;
                dialogFormVisible = true;
                formData.update(
                  'copy-of-' + scope.row.name,
                  scope.row.type,
                  scope.row.args
                );
              "
            />
            <el-button
              size="small"
              :loading="waitRequest"
              :icon="Edit"
              @click="
                dialogEditMode = true;
                dialogFormVisible = true;
                formData.update(scope.row.name, scope.row.type, scope.row.args);
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
      :title="dialogEditMode ? 'Edit Sender' : 'Add Sender'"
      @open="fetchSupportData"
      @closed="formData.clean()"
      :close-on-click-modal="false"
      width="350px"
      :show-close="!waitRequest"
      :close-on-press-escape="!waitRequest"
    >
      <el-form :model="formData" label-position="top">
        <el-form-item label="Name" :required="true">
          <el-input v-model="formData.name" :disabled="dialogEditMode" />
        </el-form-item>
        <el-form-item label="Type" :required="true">
          <el-select
            v-model="formData.type"
            :placeholder="waitRequest ? 'Type Loading...' : 'Select Type'"
            :disabled="dialogEditMode"
            :loading="waitRequest"
            @change="formData.cleanArgs()"
          >
            <el-option
              v-for="item in supportData"
              :key="item.type"
              :label="item.type"
              :value="item.type"
            />
          </el-select>
        </el-form-item>
        <el-divider
          v-if="
            Object.keys(filterSupportArgsData(supportData, formData.type))
              .length > 0
          "
          content-position="left"
          >Arguments</el-divider
        >
        <DynamicInput
          v-for="(value, key) in filterSupportArgsData(
            supportData,
            formData.type
          )"
          :key="key"
          :modelValue="formData.args[key]"
          :name="key"
          :type="value.type"
          :required="value.required"
          @update:modelValue="(name, value) => updateModelValue(name, value)"
        />
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button :loading="waitRequest" @click="dialogFormVisible = false"
            >Cancel</el-button
          >
          <el-button
            :loading="waitRequest"
            type="primary"
            v-if="dialogEditMode"
            @click="updateSender"
          >
            Edit
          </el-button>
          <el-button
            :loading="waitRequest"
            type="primary"
            v-if="!dialogEditMode"
            @click="addSender"
          >
            Add
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
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
} from "@element-plus/icons-vue";
import DynamicInput from "@/components/DynamicInput.vue";
import { saveAs } from "file-saver";

interface Argument {
  type: string;
  description: string;
  required: boolean;
}

interface Sender {
  name: string;
  type: string;
  args: { [key: string]: any };
  sample: string;
  size: number;
}

class SenderForm {
  name: string;
  type: string;
  args: { [key: string]: any };

  constructor(name: string, type: string, args: { [key: string]: any }) {
    this.name = name;
    this.type = type;
    this.args = args;
  }

  update(name: string, type: string, args: { [key: string]: any }) {
    this.name = name;
    this.type = type;
    this.args = args;
  }

  clean() {
    this.name = "";
    this.type = "";
    this.args = {};
  }

  cleanArgs() {
    this.args = {};
  }
}

const formData = reactive(new SenderForm("", "", {}));

const data = ref<Sender[]>([]);
const supportData = ref<SupportData[]>([]);
const dialogFormVisible = ref(false);
const dialogEditMode = ref(false);
const waitRequest = ref(false);

const addSender = () => {
  waitRequest.value = true;
  axios
    .post("/api/v1/sender", formData)
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

const updateSender = () => {
  waitRequest.value = true;
  axios
    .put("/api/v1/sender/" + formData.name, formData)
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
  const response = await fetch("/api/v1/sender");
  data.value = (await response.json()) as Sender[];
  waitRequest.value = false;
};

const downloadData = async () => {
  const response = await fetch("/api/v1/sender");
  const file = new File(
    [JSON.stringify(await response.json(), null, 4)],
    "logmaker-sender.json",
    { type: "text/plain;charset=utf-8" }
  );
  saveAs(file);
};

interface SupportData {
  type: string;
  args: { [key: string]: Argument };
}

const fetchSupportData = async () => {
  waitRequest.value = true;
  const response = await fetch("/api/v1/plugin/sender");
  supportData.value = (await response.json()) as SupportData[];
  waitRequest.value = false;
};

const deleteData = async (name: string) => {
  waitRequest.value = true;
  axios
    .delete("/api/v1/sender/" + name)
    .then(() => {
      waitRequest.value = false;
      fetchData();
    })
    .catch((error) => {
      console.error(error);
      waitRequest.value = false;
    });
};

const filterSupportArgsData = (
  supportData: SupportData[],
  type: string
): { [key: string]: Argument } => {
  const filteredValues = !supportData
    ? []
    : Array.from(supportData.values()).filter((value) => value.type === type);

  return filteredValues.length > 0 ? filteredValues[0].args : {};
};

const updateModelValue = (name: string, value: any) => {
  formData.args[name] = value;
};

fetchData();
</script>

<style scoped></style>
