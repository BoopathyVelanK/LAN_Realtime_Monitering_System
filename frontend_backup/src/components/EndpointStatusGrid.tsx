import { Box, Paper, Tooltip, Typography } from '@mui/material';
import { formatDistanceToNow } from 'date-fns';
import { StatusPulseDot } from './StatusPulseDot';
import type { EndpointSummaryResponse } from '../types/api';

export function EndpointStatusGrid({ endpoints }: { endpoints: EndpointSummaryResponse[] }) {
  return (
    <Paper sx={{ p: 2.5, height: '100%' }}>
      <Typography variant="h6" sx={{ fontSize: '1rem', mb: 1.5 }}>
        Endpoint status
      </Typography>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
          gap: 1,
          maxHeight: 420,
          overflowY: 'auto',
        }}
      >
        {endpoints.map((endpoint) => {
          const online = endpoint.status === 'ONLINE';
          return (
            <Tooltip
              key={endpoint.id}
              title={
                online
                  ? 'Online'
                  : `Offline${endpoint.lastHeartbeatAt ? ` · last seen ${formatDistanceToNow(new Date(endpoint.lastHeartbeatAt), { addSuffix: true })}` : ''}`
              }
            >
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                  px: 1.25,
                  py: 1,
                  borderRadius: 1.5,
                  border: 1,
                  borderColor: 'divider',
                  minWidth: 0,
                }}
              >
                <StatusPulseDot online={online} />
                <Typography
                  variant="caption"
                  noWrap
                  sx={{ fontFamily: 'monospace', color: online ? 'text.primary' : 'text.secondary' }}
                >
                  {endpoint.hostname}
                </Typography>
              </Box>
            </Tooltip>
          );
        })}
      </Box>
    </Paper>
  );
}
