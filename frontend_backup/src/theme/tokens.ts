/**
 * Design tokens for SecureSOC. This is a monitoring console meant to be
 * stared at for hours, not a marketing page — dark-first is a deliberate,
 * domain-driven choice (SOC/NOC tools default dark for exactly this
 * reason), not a generic default. Light mode is supported per the SRS but
 * is the secondary mode.
 *
 * The severity scale is the one piece of color doing real information
 * work throughout the app — it's a 5-step progression (Info -> Critical),
 * not a decorative palette, and every alert/risk surface derives its
 * color from this single source of truth.
 */

export const severityColors = {
  INFO: '#4C7EFF',
  LOW: '#35D0BA',
  MEDIUM: '#F5C24D',
  HIGH: '#F5843D',
  CRITICAL: '#F5426B',
} as const;

export type SeverityKey = keyof typeof severityColors;

export function severityColor(severity: string | undefined | null): string {
  const key = (severity ?? '').toUpperCase() as SeverityKey;
  return severityColors[key] ?? severityColors.INFO;
}

// Risk level (Safe/Low/Medium/High/Critical) reuses the same scale, with
// an added "safe" floor below Low for a 0-risk endpoint.
export const riskLevelColors = {
  SAFE: '#3DDAD7',
  LOW: severityColors.LOW,
  MEDIUM: severityColors.MEDIUM,
  HIGH: severityColors.HIGH,
  CRITICAL: severityColors.CRITICAL,
} as const;

export function riskLevelColor(level: string | undefined | null): string {
  const key = (level ?? '').toUpperCase() as keyof typeof riskLevelColors;
  return riskLevelColors[key] ?? riskLevelColors.SAFE;
}

export const darkPalette = {
  background: '#0B0F14',
  surface: '#131A22',
  surfaceRaised: '#182230',
  border: '#25313F',
  textPrimary: '#E6EDF3',
  textSecondary: '#7C8CA0',
  accent: '#3DDAD7',
  accentMuted: 'rgba(61, 218, 215, 0.14)',
  online: '#35D0BA',
  offline: '#4B5768',
};

export const lightPalette = {
  background: '#F3F5F7',
  surface: '#FFFFFF',
  surfaceRaised: '#FFFFFF',
  border: '#DCE2E8',
  textPrimary: '#101820',
  textSecondary: '#556170',
  accent: '#0E9C93',
  accentMuted: 'rgba(14, 156, 147, 0.10)',
  online: '#0E9C93',
  offline: '#9AA5B1',
};

export const fontFamily = {
  display: '"Space Grotesk", "IBM Plex Sans", sans-serif',
  body: '"IBM Plex Sans", "Segoe UI", sans-serif',
  mono: '"IBM Plex Mono", "SFMono-Regular", Consolas, monospace',
};
