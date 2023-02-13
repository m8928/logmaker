import { createRouter, createWebHistory } from "vue-router";
import HomeView from "../views/HomeView.vue";
import LogView from "../views/LogView.vue";
import MakerView from "../views/MakerView.vue";
import SenderView from "../views/SenderView.vue";
import PluginView from "../views/PluginView.vue";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "home",
      component: HomeView,
    },
    {
      path: "/maker",
      name: "maker",
      component: MakerView,
    },
    {
      path: "/log",
      name: "log",
      component: LogView,
    },
    {
      path: "/sender",
      name: "sender",
      component: SenderView,
    },
    {
      path: "/plugin",
      name: "plugin",
      component: PluginView,
    },
  ],
});

export default router;
