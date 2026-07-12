import { Chip } from '@mui/material';
import { severityColor } from '../theme/tokens';

export function SeverityChip({ severity }: { severity: string }) {
  const color = severityColor(severity);
  return (
    <Chip
      label={severity}
      size="small"
      sx={{
        color,
        backgroundColor: `${color}1F`, // ~12% alpha wash of the severity color
        border: `1px solid ${color}55`,
        letterSpacing: 0.5,
      }}
    />
  );
}
