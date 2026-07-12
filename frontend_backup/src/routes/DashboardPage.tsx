import { Box, Grid, Skeleton } from '@mui/material';
import CheckCircleOutlineOutlinedIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import HighlightOffIcon from '@mui/icons-material/HighlightOff';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined';
import { AppShell } from '../components/AppShell';
import { KpiCard } from '../components/KpiCard';
import { LiveAlertFeed } from '../components/LiveAlertFeed';
import { EndpointStatusGrid } from '../components/EndpointStatusGrid';
import { RiskSummaryDonut } from '../components/RiskSummaryDonut';
import { RecentEventsTimeline } from '../components/RecentEventsTimeline';
import { useEndpoints, useAlerts, useRiskScores } from '../api/queries';
import { useLiveFeed } from '../ws/useLiveFeed';

export function DashboardPage() {
  const { connected, recentAlerts } = useLiveFeed();
  const { data: endpoints, isLoading: endpointsLoading } = useEndpoints();
  const { data: alerts, isLoading: alertsLoading } = useAlerts();
  const { data: riskScores } = useRiskScores(endpoints);

  const onlineCount = endpoints?.filter((e) => e.status === 'ONLINE').length ?? 0;
  const offlineCount = (endpoints?.length ?? 0) - onlineCount;
  const openAlertCount = alerts?.filter((a) => a.status === 'OPEN').length ?? 0;
  const criticalRiskCount = riskScores?.filter((r) => (r.level || '').toUpperCase() === 'CRITICAL').length ?? 0;

  // Live-feed alerts (from WebSocket/mock) layered on top of the initial
  // REST-fetched list, most recent first, deduplicated by id.
  const combinedAlerts = [...recentAlerts, ...(alerts ?? [])].filter(
    (alert, index, arr) => arr.findIndex((a) => a.id === alert.id) === index,
  );

  const loading = endpointsLoading || alertsLoading;

  return (
    <AppShell liveAlertCount={openAlertCount}>
      {loading ? (
        <DashboardSkeleton />
      ) : (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          <Grid container spacing={2.5}>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                label="Online endpoints"
                value={onlineCount}
                icon={<CheckCircleOutlineOutlinedIcon />}
                accentColor="success.main"
                caption={`of ${endpoints?.length ?? 0} total`}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard label="Offline endpoints" value={offlineCount} icon={<HighlightOffIcon />} caption="not heartbeating" />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                label="Open alerts"
                value={openAlertCount}
                icon={<WarningAmberOutlinedIcon />}
                accentColor="#F5843D"
                caption="awaiting review"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                label="Critical risk"
                value={criticalRiskCount}
                icon={<ShieldOutlinedIcon />}
                accentColor="#F5426B"
                caption="endpoints at critical level"
              />
            </Grid>
          </Grid>

          <Grid container spacing={2.5}>
            <Grid size={{ xs: 12, lg: 5 }}>
              <LiveAlertFeed alerts={combinedAlerts} connected={connected} />
            </Grid>
            <Grid size={{ xs: 12, lg: 4 }}>
              <EndpointStatusGrid endpoints={endpoints ?? []} />
            </Grid>
            <Grid size={{ xs: 12, lg: 3 }}>
              <RiskSummaryDonut riskScores={riskScores ?? []} />
            </Grid>
          </Grid>

          <RecentEventsTimeline alerts={combinedAlerts} />
        </Box>
      )}
    </AppShell>
  );
}

function DashboardSkeleton() {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
      <Grid container spacing={2.5}>
        {Array.from({ length: 4 }).map((_, i) => (
          <Grid size={{ xs: 12, sm: 6, lg: 3 }} key={i}>
            <Skeleton variant="rounded" height={110} />
          </Grid>
        ))}
      </Grid>
      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, lg: 5 }}>
          <Skeleton variant="rounded" height={420} />
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Skeleton variant="rounded" height={420} />
        </Grid>
        <Grid size={{ xs: 12, lg: 3 }}>
          <Skeleton variant="rounded" height={420} />
        </Grid>
      </Grid>
    </Box>
  );
}
