import { Box, Paper, Typography } from '@mui/material';
import type { ReactNode } from 'react';

export function KpiCard({
  label,
  value,
  icon,
  accentColor,
  caption,
}: {
  label: string;
  value: ReactNode;
  icon: ReactNode;
  accentColor?: string;
  caption?: string;
}) {
  return (
    <Paper sx={{ p: 2.5, display: 'flex', flexDirection: 'column', gap: 1, height: '100%' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>
          {label}
        </Typography>
        <Box sx={{ color: accentColor ?? 'text.secondary', display: 'flex' }}>{icon}</Box>
      </Box>
      <Typography variant="h4" sx={{ fontSize: '2rem' }}>
        {value}
      </Typography>
      {caption && (
        <Typography variant="caption" color="text.secondary">
          {caption}
        </Typography>
      )}
    </Paper>
  );
}
