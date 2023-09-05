import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus, { ElNotification } from "element-plus";
import "element-plus/dist/index.css";
import "element-plus/theme-chalk/dark/css-vars.css";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";

import App from "./App.vue";
import router from "./router";

import "./assets/main.css";
import axios from "axios";

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.use(ElementPlus);
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

axios.interceptors.response.use(
  function (response) {
    if (response.data.notification) {
      ElNotification({
        title: "Success",
        message: response.data.message,
        type: "success",
        position: "top-left",
      });
    }
    return response;
  },
  function (error) {
    if (error.response.data.notification) {
      ElNotification({
        title: "Error",
        message: error.response.data.message,
        type: "error",
        position: "top-left",
      });
    }
    return Promise.reject(error);
  }
);

app.mount("#app");
