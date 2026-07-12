import { Box, Paper, Stack, Typography } from '@mui/material';
import { formatDistanceToNow } from 'date-fns';
import { SeverityChip } from './SeverityChip';
import { severityColor } from '../theme/tokens';
import type { AlertResponse } from '../types/api';

export function LiveAlertFeed({ alerts, connected }: { alerts: AlertResponse[]; connected: boolean }) {
  return (
    <Paper sx={{ p: 2.5, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
        <Typography variant="h6" sx={{ fontSize: '1rem' }}>
          Live alert feed
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
          <Box
            sx={{
              width: 7,
              height: 7,
              borderRadius: '50%',
              bgcolor: connected ? 'success.main' : 'text.secondary',
            }}
          />
          <Typography variant="caption" color="text.secondary">
            {connected ? 'Live' : 'Reconnecting…'}
          </Typography>
        </Box>
      </Box>

      <Stack spacing={0} sx={{ overflowY: 'auto', maxHeight: 420 }}>
        {alerts.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
            No alerts yet. This panel updates in real time.
          </Typography>
        )}
        {alerts.map((alert, i) => (
          <Box
            key={alert.id}
            sx={{
              display: 'flex',
              gap: 1.5,
              py: 1.25,
              borderTop: i === 0 ? 'none' : 1,
              borderColor: 'divider',
              borderLeft: '3px solid',
              borderLeftColor: severityColor(alert.severity),
              pl: 1.5,
            }}
          >
            <Box sx={{ flexGrow: 1, minWidth: 0 }}>
              <Typography variant="body2" sx={{ fontWeight: 500 }} noWrap>
                {alert.title}
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                {alert.hostname} · {formatDistanceToNow(new Date(alert.createdAt), { addSuffix: true })}
              </Typography>
            </Box>
            <SeverityChip severity={alert.severity} />
          </Box>
        ))}
      </Stack>
    </Paper>
  );
}
