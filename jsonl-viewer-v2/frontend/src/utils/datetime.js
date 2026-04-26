export const TIMEZONE_LOCAL = "local";

function toDate(value) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date;
}

function resolveFormatterTimeZone(timeZone) {
  const normalized = coerceTimeZoneOrLocal(timeZone);
  if (normalized === TIMEZONE_LOCAL) {
    return undefined;
  }
  return normalized;
}

export function isValidTimeZone(timeZone) {
  if (typeof timeZone !== "string" || !timeZone.trim()) {
    return false;
  }
  try {
    new Intl.DateTimeFormat(undefined, { timeZone }).format(new Date());
    return true;
  } catch {
    return false;
  }
}

export function coerceTimeZoneOrLocal(timeZone) {
  if (timeZone === TIMEZONE_LOCAL) {
    return TIMEZONE_LOCAL;
  }
  if (isValidTimeZone(timeZone)) {
    return timeZone;
  }
  return TIMEZONE_LOCAL;
}

export function formatTime(value, timeZone) {
  const date = toDate(value);
  if (!date) {
    return "--:--:--";
  }

  const formatterTimeZone = resolveFormatterTimeZone(timeZone);
  const options = {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
    ...(formatterTimeZone ? { timeZone: formatterTimeZone } : {}),
  };
  return date.toLocaleTimeString(undefined, options);
}

export function formatDateTimeTooltip(value, timeZone) {
  const date = toDate(value);
  if (!date) {
    return "";
  }

  const formatterTimeZone = resolveFormatterTimeZone(timeZone);
  const options = {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
    timeZoneName: "short",
    ...(formatterTimeZone ? { timeZone: formatterTimeZone } : {}),
  };
  return date.toLocaleString(undefined, options);
}
