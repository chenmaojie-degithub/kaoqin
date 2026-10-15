import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig(({ mode }) => ({plugins:[vue()],server:{host:'0.0.0.0',port:5173,strictPort:true,proxy:{'/api':{target:loadEnv(mode,process.cwd(),'VITE_').VITE_API_PROXY_TARGET || 'http://localhost:8080',changeOrigin:true}}}}))

