import { createApp } from 'vue';
import './index.css';
import './styles/portal.css';
import App from './App.vue';
import router from './router';

createApp(App).use(router).mount('#root');
