export const jobTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '发布者暂未补充'
export const jobDuration = value => value ? `${Math.floor(value / 60) ? Math.floor(value / 60) + '小时' : ''}${value % 60 ? value % 60 + '分钟' : ''}` : '发布者暂未补充'
export const payUnit = value => value === 'DAY' ? '天' : '次'
