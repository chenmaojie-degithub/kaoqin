export const WEEK_DAYS = [
  { value: 1, label: '一' }, { value: 2, label: '二' }, { value: 3, label: '三' },
  { value: 4, label: '四' }, { value: 5, label: '五' }, { value: 6, label: '六' }, { value: 7, label: '日' }
]

export const PERIOD_BLOCKS = [
  { label: '1-2', start: '08:00:00', end: '09:50:00' },
  { label: '3-4', start: '10:10:00', end: '12:00:00' },
  { label: '5-6', start: '13:30:00', end: '15:20:00' },
  { label: '7-8', start: '15:40:00', end: '17:30:00' },
  { label: '9-10', start: '18:30:00', end: '20:20:00' }
]

export const timeToMinutes = value => {
  if (!value) return 0
  const [hour, minute] = value.slice(0, 5).split(':').map(Number)
  return hour * 60 + minute
}
