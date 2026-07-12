import { Box, Paper, Typography } from '@mui/material';
import { format } from 'date-fns';
import { SeverityChip } from './SeverityChip';
import type { AlertResponse } from '../types/api';

export function RecentEventsTimeline({ alerts }: { alerts: AlertResponse[] }) {
  const recent = [...alerts].sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt)).slice(0, 8);

  return (
    <Paper sx={{ p: 2.5 }}>
      <Typography variant="h6" sx={{ fontSize: '1rem', mb: 1.5 }}>
        Recent events
      </Typography>
      {recent.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          Nothing to show yet.
        </Typography>
      ) : (
        <Box sx={{ display: 'flex', flexDirection: 'column' }}>
          {recent.map((alert, i) => (
            <Box
              key={alert.id}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 2,
                py: 1.1,
                borderTop: i === 0 ? 'none' : 1,
                borderColor: 'divider',
              }}
            >
              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace', width: 64, flexShrink: 0 }}>
                {format(new Date(alert.createdAt), 'HH:mm:ss')}
              </Typography>
              <SeverityChip severity={alert.severity} />
              <Typography variant="body2" sx={{ flexGrow: 1 }} noWrap>
                {alert.title}
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }} noWrap>
                {alert.hostname}
              </Typography>
              <Typography
                variant="caption"
                sx={{
                  color: alert.status === 'RESOLVED' ? 'success.main' : 'text.secondary',
                  width: 90,
                  textAlign: 'right',
                  flexShrink: 0,
                }}
              >
                {alert.status}
              </Typography>
            </Box>
          ))}
        </Box>
      )}
    </Paper>
  );
}
