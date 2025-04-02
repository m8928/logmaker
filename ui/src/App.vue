<script setup lang="ts">
import { RouterView } from "vue-router";
import NavView from "./views/NavView.vue";
import FooterView from "./views/FooterView.vue";
</script>

<template>
  <NavView />
  <RouterView />
  <FooterView />
</template>

<style scoped></style>
