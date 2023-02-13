<template>
  <el-form-item
    :label="name.toString().toUpperCase()"
    :required="props.required"
  >
    <el-input-number
      v-if="
        props.type === 'java.lang.Number' || props.type === 'java.lang.Integer'
      "
      v-model="localValue"
    />
    <el-input v-if="props.type === 'java.lang.String'" v-model="localValue" />
    <el-switch
      v-if="props.type === 'java.lang.Boolean'"
      v-model="localValue"
      inline-prompt
      :active-icon="Check"
      :inactive-icon="Close"
    />
    <el-select
      v-if="props.type === 'java.util.ArrayList'"
      class="full-width"
      multiple
      filterable
      allow-create
      default-first-option
      :reserve-keyword="false"
      placeholder="Add Data"
      v-model="localValue"
    >
      <el-option
        v-for="item in localValue"
        :key="item"
        :label="item"
        :value="item"
      />
    </el-select>
  </el-form-item>
</template>

<script lang="ts" setup>
import { Check, Close } from "@element-plus/icons-vue";
import { computed } from "vue";

interface Props {
  modelValue: any;
  type: string;
  name: string | number;
  required: boolean;
}

const props = defineProps<Props>();

console.log(props.type);
console.log(props.name);

const emit = defineEmits(["update:modelValue"]);

const localValue = computed({
  get() {
    return props.modelValue;
  },
  set(value) {
    emit("update:modelValue", props.name, value);
  },
});
</script>

<style scoped></style>
