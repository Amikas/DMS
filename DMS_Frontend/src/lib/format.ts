const UNITS = ['B', 'KB', 'MB', 'GB', 'TB'];

export function formatBytes(bytes?: number | null): string {
  if (bytes === undefined || bytes === null) return '-';
  if (bytes === 0) return '0 B';
  const unit = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), UNITS.length - 1);
  const value = bytes / 1024 ** unit;
  const rounded = value >= 10 || unit === 0 ? Math.round(value) : Number(value.toFixed(1));
  return `${rounded} ${UNITS[unit]}`;
}

export function fileExtension(fileName?: string | null, contentType?: string | null): string {
  const name = fileName ?? '';
  const dot = name.lastIndexOf('.');
  if (dot > 0 && dot < name.length - 1) {
    return name.slice(dot + 1).toUpperCase();
  }
  const subtype = contentType?.split('/')[1];
  if (!subtype) return 'FILE';
  return subtype
    .replace(/^vnd\./, '')
    .split(/[.+-]/)[0]
    .toUpperCase()
    .slice(0, 5);
}

export function pluralize(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}
