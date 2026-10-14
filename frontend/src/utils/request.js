import axios from 'axios'
import { ElMessage } from 'element-plus'

const request=axios.create({baseURL:import.meta.env.VITE_API_BASE_URL||'/api',timeout:15000})
const notify=name=>window.dispatchEvent(new CustomEvent(name))

request.interceptors.request.use(config=>{
  notify('attendance:request-start')
  const token=localStorage.getItem('attendance_token')
  if(token)config.headers.Authorization=`Bearer ${token}`
  return config
},error=>{notify('attendance:request-end');return Promise.reject(error)})

request.interceptors.response.use(response=>{
  notify('attendance:request-end')
  const body=response.data
  if(body.code!==0){if(!response.config?.silent)ElMessage.error({message:body.message||'请求失败',showClose:true});return Promise.reject(new Error(body.message))}
  return body.data
},error=>{
  notify('attendance:request-end')
  const status=error.response?.status
  const message=error.response?.data?.message||error.message||'网络请求失败'
  if (!error.config?.silent) ElMessage.error({message,showClose:true})
  if(status===401){localStorage.removeItem('attendance_token');localStorage.removeItem('attendance_user');if(location.pathname!='/login')location.href='/login'}
  return Promise.reject(error)
})

export default request
