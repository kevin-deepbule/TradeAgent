// Bootstrap and mount the Vue dashboard application.
import { createApp } from "vue";
import App from "./App.vue";
import "./style.css";

function reportAppError(error) {
  // Keep unexpected Vue errors visible during local dashboard development.
  console.error("Dashboard runtime error:", error);
}

const app = createApp(App);
app.config.errorHandler = reportAppError;
app.mount("#app");
