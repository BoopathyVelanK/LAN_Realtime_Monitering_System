import { Box, useTheme } from '@mui/material';
import { darkPalette, lightPalette } from '../theme/tokens';

/**
 * The dashboard's signature element. "Heartbeat" isn't a metaphor here —
 * it's literally what the agent sends every heartbeat_interval_seconds —
 * so an online endpoint's status dot does a genuine periodic pulse (like
 * an ECG blip) rather than sitting as a static colored circle. Offline
 * endpoints get a flat, motionless dot — the absence of a pulse *is* the
 * signal, which mirrors what "offline" actually means for this system.
 */
export function StatusPulseDot({ online, size = 10 }: { online: boolean; size?: number }) {
  const theme = useTheme();
  const palette = theme.palette.mode === 'dark' ? darkPalette : lightPalette;
  const color = online ? palette.online : palette.offline;

  return (
    <Box
      sx={{
        position: 'relative',
        width: size,
        height: size,
        flexShrink: 0,
        '&::before': online
          ? {
              content: '""',
              position: 'absolute',
              inset: 0,
              borderRadius: '50%',
              backgroundColor: color,
              animation: 'securesoc-pulse-ring 2.2s ease-out infinite',
              '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
            }
          : undefined,
      }}
    >
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          borderRadius: '50%',
          backgroundColor: color,
        }}
      />
    </Box>
  );
}
