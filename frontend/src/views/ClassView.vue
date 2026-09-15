<template><CrudTable title="班级" endpoint="/classes" :paged="false" :fields="fields"/></template>
<script setup>
import CrudTable from '../components/CrudTable.vue'
const fields=[
  {key:'classCode',label:'班级编号',required:true},
  {key:'name',label:'班级名称',required:true},
  {key:'grade',label:'年级',required:true},
  {key:'major',label:'专业'},
  {key:'advisorTeacherId',label:'班主任',type:'select',optionsEndpoint:'/teachers',optionLabel:'name',table:false},
  {key:'status',label:'状态',type:'select',default:1,options:[{label:'启用',value:1},{label:'停用',value:0}]}
]
</script>
