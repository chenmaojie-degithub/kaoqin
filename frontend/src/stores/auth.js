import { defineStore } from 'pinia'
import request from '../utils/request'
export const useAuthStore=defineStore('auth',{state:()=>({token:localStorage.getItem('attendance_token')||'',user:JSON.parse(localStorage.getItem('attendance_user')||'null')}),getters:{role:s=>s.user?.role},actions:{async login(form){const data=await request.post('/auth/login',form);this.token=data.token;this.user=data.user;localStorage.setItem('attendance_token',data.token);localStorage.setItem('attendance_user',JSON.stringify(data.user))},async restore(){if(this.token)this.user=await request.get('/auth/me')},async logout(){try{await request.post('/auth/logout')}finally{this.token='';this.user=null;localStorage.removeItem('attendance_token');localStorage.removeItem('attendance_user')}}}})

