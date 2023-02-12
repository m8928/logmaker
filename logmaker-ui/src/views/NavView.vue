<template>
  <div class="menu">
    <el-menu
      :default-active="'/' + $route.path.split('/')[1]"
      mode="horizontal"
      :ellipsis="false"
      :router="true"
    >
      <el-menu-item index="/"
        ><el-icon><HomeFilled /></el-icon>Dashboard</el-menu-item
      >
      <el-menu-item index="/maker"
        ><el-icon><SetUp /></el-icon>Maker</el-menu-item
      >
      <el-menu-item index="/log"
        ><el-icon><Tickets /></el-icon>Log</el-menu-item
      >
      <el-menu-item index="/sender"
        ><el-icon><Platform /></el-icon>Sender</el-menu-item
      >
      <el-menu-item index="/plugin"
        ><el-icon><TurnOff /></el-icon>Plugin</el-menu-item
      >
    </el-menu>
  </div>
</template>

<script lang="ts" setup></script>

<style>
.flex-grow {
  flex-grow: 1;
}
</style>

<style scoped>
.menu {
  padding: 0.5rem 0.5rem 1rem;
}
</style>
