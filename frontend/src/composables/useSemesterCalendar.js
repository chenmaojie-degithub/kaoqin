import { computed, onScopeDispose, ref } from 'vue'
import request from '../utils/request'
import { WEEK_DAYS } from '../constants/schedule'

const parseDate = value => {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day, 12)
}
const dateKey = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const firstMonday = startDate => {
  const date = parseDate(startDate)
  const weekday = date.getDay() || 7
  date.setDate(date.getDate() - weekday + 1)
  return date
}

export function useSemesterCalendar() {
  const calendar = ref(null), selectedWeek = ref(1), startWeek = ref(1), endWeek = ref(1), loading = ref(false), now = ref(new Date())
  const timer = window.setInterval(() => { now.value = new Date() }, 60_000)
  onScopeDispose(() => window.clearInterval(timer))

  const weekOptions = computed(() => Array.from({ length: calendar.value?.totalWeeks || 0 }, (_, index) => index + 1))
  const weekDates = computed(() => {
    if (!calendar.value) return []
    const monday = firstMonday(calendar.value.startDate)
    monday.setDate(monday.getDate() + (selectedWeek.value - 1) * 7)
    return WEEK_DAYS.map((day, index) => {
      const date = new Date(monday); date.setDate(monday.getDate() + index)
      const key = dateKey(date)
      return { ...day, date: key, display: `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`, isToday: key === calendar.value.today, inSemester: key >= calendar.value.startDate && key <= calendar.value.endDate }
    })
  })
  const rangeText = computed(() => `第${startWeek.value}周 ～ 第${endWeek.value}周`)
  const weekDateText = computed(() => weekDates.value.length ? `${weekDates.value[0].display} - ${weekDates.value[6].display}` : '')
  const semesterStateText = computed(() => ({ NOT_STARTED: '本学期尚未开始', ENDED: '本学期已结束', ACTIVE: '' })[calendar.value?.status] || '')

  async function loadSemesterCalendar() {
    loading.value = true
    try {
      calendar.value = await request.get('/teaching/semester-calendar')
      selectedWeek.value = calendar.value.currentWeek
      startWeek.value = calendar.value.currentWeek
      endWeek.value = calendar.value.currentWeek
      return calendar.value
    } finally { loading.value = false }
  }
  function changeStartWeek(value) {
    startWeek.value = value
    if (endWeek.value < value) endWeek.value = value
    if (selectedWeek.value < value || selectedWeek.value > endWeek.value) selectedWeek.value = value
  }
  function changeEndWeek(value) {
    endWeek.value = Math.max(value, startWeek.value)
    if (selectedWeek.value > endWeek.value) selectedWeek.value = endWeek.value
  }
  function previousWeek(useRange = true) { selectedWeek.value = Math.max(useRange ? startWeek.value : 1, selectedWeek.value - 1) }
  function nextWeek(useRange = true) { selectedWeek.value = Math.min(useRange ? endWeek.value : (calendar.value?.totalWeeks || 1), selectedWeek.value + 1) }
  function goCurrentWeek() {
    if (!calendar.value) return
    selectedWeek.value = calendar.value.currentWeek
    if (selectedWeek.value < startWeek.value || selectedWeek.value > endWeek.value) startWeek.value = endWeek.value = selectedWeek.value
  }
  return { calendar, selectedWeek, startWeek, endWeek, loading, now, weekOptions, weekDates, rangeText, weekDateText, semesterStateText, loadSemesterCalendar, changeStartWeek, changeEndWeek, previousWeek, nextWeek, goCurrentWeek }
}
