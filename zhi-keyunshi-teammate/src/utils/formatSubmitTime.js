/** 作业提交时间展示：兼容 API 已格式化的字符串或 ISO / Date。 */
export function formatSubmitTimeForDisplay(v) {
  if (v == null || v === "") return "";
  if (typeof v === "string") {
    const t = v.trim();
    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(t)) return t;
    const d = new Date(t);
    if (!Number.isNaN(d.getTime())) return beijingYmdHms(d);
    return t;
  }
  if (v instanceof Date) return beijingYmdHms(v);
  if (typeof v === "number") return beijingYmdHms(new Date(v));
  return String(v);
}

function beijingYmdHms(d) {
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  })
    .format(d)
    .replace("T", " ");
}
