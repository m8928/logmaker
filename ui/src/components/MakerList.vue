<template>
  <div class="section">
    <div class="inner-bottom">
      <span
        style="font-size: var(--el-font-size-extra-large); font-weight: bold"
        >Maker</span
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
        >Add Maker</el-button
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
        table-layout="auto"
        :empty-text="waitRequest ? 'Loading...' : 'No Data'"
      >
        <el-table-column prop="name" label="Name" min-width="120" />
        <el-table-column prop="type" label="Type" width="150" align="center">
          <template #default="scope">
            <el-tag>{{ scope.row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sample" label="Sample" />
        <el-table-column prop="size" label="Size" width="110" align="right" />
        <el-table-column prop="ref" label="Used" width="110" align="right" />
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
      :title="dialogEditMode ? 'Edit Maker' : 'Add Maker'"
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
            type="primary"
            :loading="waitRequest"
            v-if="dialogEditMode"
            @click="updateMaker"
          >
            Edit
          </el-button>
          <el-button
            type="primary"
            :loading="waitRequest"
            v-if="!dialogEditMode"
            @click="addMaker"
          >
            Add
          </el-button>
        </span>
      </template>
    </el-dialog>
    <el-dialog
        v-model="dialogImportVisible"
        title="Import Maker"
        :close-on-click-modal="false"
        width="350px"
        :show-close="!waitRequest"
        :close-on-press-escape="!waitRequest"
    >
      <div>
        <el-upload
            drag
            action="/api/v1/maker:import-file"
            :show-file-list="false"
            :on-success="importMaker"
            :on-progress="importMakerProgress"
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

interface Maker {
  name: string;
  type: string;
  args: { [key: string]: any };
  sample: string;
  size: number;
}

class MakerForm {
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

const formData = reactive(new MakerForm("", "", {}));

const data = ref<Maker[]>([]);
const supportData = ref<SupportData[]>([]);
const dialogFormVisible = ref(false);
const dialogEditMode = ref(false);
const dialogImportVisible = ref(false);
const waitRequest = ref(false);

const addMaker = () => {
  waitRequest.value = true;
  axios
    .post("/api/v1/maker", formData)
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

const updateMaker = () => {
  waitRequest.value = true;
  axios
    .put("/api/v1/maker/" + formData.name, formData)
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
  const response = await fetch("/api/v1/maker");
  data.value = (await response.json()) as Maker[];
  waitRequest.value = false;
};

const downloadData = async () => {
  const response = await fetch("/api/v1/maker");
  const file = new File(
    [JSON.stringify(await response.json(), null, 4)],
    "logmaker-maker.json",
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
  const response = await fetch("/api/v1/plugin/maker");
  supportData.value = (await response.json()) as SupportData[];
  waitRequest.value = false;
};

const deleteData = async (name: string) => {
  waitRequest.value = true;
  axios
    .delete("/api/v1/maker/" + name)
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

const importMakerProgress = () => {
  waitRequest.value = true;
};

const importMaker = (response: { [key: string]: any }[]) => {
  if (response.length === 1 && response[0].type === "ERROR") {
    waitRequest.value = false;
  } else {
    waitRequest.value = false;
    dialogImportVisible.value = false;
    fetchData();
  }
};


fetchData();
</script>

<style scoped></style>
