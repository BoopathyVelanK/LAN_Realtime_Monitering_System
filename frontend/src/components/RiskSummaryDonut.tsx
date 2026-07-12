import { Box, Paper, Typography, useTheme } from '@mui/material';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip as RechartsTooltip } from 'recharts';
import { riskLevelColor, riskLevelColors } from '../theme/tokens';
import type { RiskScoreResponse } from '../types/api';

const LEVEL_ORDER: Array<keyof typeof riskLevelColors> = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'SAFE'];

export function RiskSummaryDonut({ riskScores }: { riskScores: RiskScoreResponse[] }) {
  const theme = useTheme();

  const counts = LEVEL_ORDER.map((level) => ({
    level,
    count: riskScores.filter((r) => (r.level || 'SAFE').toUpperCase() === level).length,
  }));
  const total = riskScores.length;
  const data = counts.filter((c) => c.count > 0);

  return (
    <Paper sx={{ p: 2.5, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Typography variant="h6" sx={{ fontSize: '1rem', mb: 1 }}>
        Risk summary
      </Typography>

      <Box sx={{ position: 'relative', height: 200 }}>
        {total === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', pt: 6 }}>
            No risk data yet.
          </Typography>
        ) : (
          <>
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={data} dataKey="count" nameKey="level" innerRadius={62} outerRadius={86} paddingAngle={2} stroke="none">
                  {data.map((entry) => (
                    <Cell key={entry.level} fill={riskLevelColor(entry.level)} />
                  ))}
                </Pie>
                <RechartsTooltip
                  contentStyle={{
                    background: theme.palette.background.paper,
                    border: `1px solid ${theme.palette.divider}`,
                    borderRadius: 8,
                    fontSize: 13,
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
            <Box sx={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', pointerEvents: 'none' }}>
              <Typography variant="h4" sx={{ fontSize: '1.75rem' }}>
                {total}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                endpoints
              </Typography>
            </Box>
          </>
        )}
      </Box>

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, justifyContent: 'center', mt: 1 }}>
        {counts.map((c) => (
          <Box key={c.level} sx={{ display: 'flex', alignItems: 'center', gap: 0.6 }}>
            <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: riskLevelColor(c.level) }} />
            <Typography variant="caption" color="text.secondary">
              {c.level} · {c.count}
            </Typography>
          </Box>
        ))}
      </Box>
    </Paper>
  );
}
