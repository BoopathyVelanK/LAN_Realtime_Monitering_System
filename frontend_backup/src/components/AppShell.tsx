import { useState, type ReactNode } from 'react';
import {
  AppBar, Avatar, Badge, Box, Divider, Drawer, IconButton, InputBase, List,
  ListItemButton, ListItemIcon, ListItemText, Menu, MenuItem, Toolbar, Tooltip, Typography,
} from '@mui/material';
import SpaceDashboardOutlinedIcon from '@mui/icons-material/SpaceDashboardOutlined';
import SchoolOutlinedIcon from '@mui/icons-material/SchoolOutlined';
import DevicesOutlinedIcon from '@mui/icons-material/DevicesOutlined';
import MonitorHeartOutlinedIcon from '@mui/icons-material/MonitorHeartOutlined';
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined';
import SettingsOutlinedIcon from '@mui/icons-material/SettingsOutlined';
import SearchIcon from '@mui/icons-material/Search';
import NotificationsOutlinedIcon from '@mui/icons-material/NotificationsOutlined';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined';
import { useColorMode } from '../theme/ColorModeContext';
import { useAuth } from '../auth/AuthContext';

const DRAWER_WIDTH = 232;

const NAV_ITEMS = [
  { label: 'Dashboard', icon: SpaceDashboardOutlinedIcon, path: '/dashboard', enabled: true },
  { label: 'Faculty View', icon: SchoolOutlinedIcon, path: null, enabled: false },
  { label: 'Inventory', icon: DevicesOutlinedIcon, path: null, enabled: false },
  { label: 'Monitoring', icon: MonitorHeartOutlinedIcon, path: null, enabled: false },
  { label: 'Analytics', icon: InsightsOutlinedIcon, path: null, enabled: false },
  { label: 'Reports', icon: DescriptionOutlinedIcon, path: null, enabled: false },
  { label: 'Exam Mode', icon: FactCheckOutlinedIcon, path: null, enabled: false },
];

export function AppShell({ children, liveAlertCount }: { children: ReactNode; liveAlertCount: number }) {
  const { mode, toggle } = useColorMode();
  const { user, logout } = useAuth();
  const [profileAnchor, setProfileAnchor] = useState<HTMLElement | null>(null);

  return (
    <Box sx={{ display: 'flex', minHeight: '100%' }}>
      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box', borderRight: 1, borderColor: 'divider' },
        }}
      >
        <Toolbar sx={{ gap: 1, px: 2.5 }}>
          <ShieldOutlinedIcon sx={{ color: 'primary.main' }} />
          <Typography variant="h6" sx={{ fontSize: '1.05rem', letterSpacing: -0.2 }}>
            SecureSOC
          </Typography>
        </Toolbar>
        <Divider />
        <List sx={{ px: 1.5, py: 1.5, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
          {NAV_ITEMS.map((item) => (
            <Tooltip key={item.label} title={item.enabled ? '' : 'Planned for a future phase'} placement="right">
              <span>
                <ListItemButton
                  selected={item.enabled}
                  disabled={!item.enabled}
                  sx={{ borderRadius: 2 }}
                >
                  <ListItemIcon sx={{ minWidth: 36 }}>
                    <item.icon fontSize="small" />
                  </ListItemIcon>
                  <ListItemText slotProps={{ primary: { sx: { fontSize: '0.9rem', fontWeight: 500 } } }}>{item.label}</ListItemText>
                </ListItemButton>
              </span>
            </Tooltip>
          ))}
        </List>
        <Box sx={{ flexGrow: 1 }} />
        <Divider />
        <List sx={{ px: 1.5, py: 1.5 }}>
          <ListItemButton disabled sx={{ borderRadius: 2 }}>
            <ListItemIcon sx={{ minWidth: 36 }}>
              <SettingsOutlinedIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText slotProps={{ primary: { sx: { fontSize: '0.9rem' } } }}>Settings</ListItemText>
          </ListItemButton>
        </List>
      </Drawer>

      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <AppBar
          position="sticky"
          elevation={0}
          color="transparent"
          sx={{ borderBottom: 1, borderColor: 'divider', backdropFilter: 'blur(6px)' }}
        >
          <Toolbar sx={{ gap: 2 }}>
            <Typography variant="h5" sx={{ fontSize: '1.15rem' }}>
              Dashboard
            </Typography>

            <Box
              sx={{
                ml: 2,
                display: { xs: 'none', sm: 'flex' },
                alignItems: 'center',
                gap: 1,
                px: 1.5,
                py: 0.5,
                borderRadius: 2,
                border: 1,
                borderColor: 'divider',
                bgcolor: 'background.paper',
                flexGrow: 1,
                maxWidth: 360,
              }}
            >
              <SearchIcon fontSize="small" sx={{ color: 'text.secondary' }} />
              <InputBase placeholder="Search endpoints, alerts…" fullWidth sx={{ fontSize: '0.875rem' }} />
            </Box>

            <Box sx={{ flexGrow: 1 }} />

            <Tooltip title={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
              <IconButton onClick={toggle} size="small">
                {mode === 'dark' ? <LightModeOutlinedIcon fontSize="small" /> : <DarkModeOutlinedIcon fontSize="small" />}
              </IconButton>
            </Tooltip>

            <Tooltip title={`${liveAlertCount} alert${liveAlertCount === 1 ? '' : 's'} today`}>
              <IconButton size="small">
                <Badge badgeContent={liveAlertCount} color="error" max={99}>
                  <NotificationsOutlinedIcon fontSize="small" />
                </Badge>
              </IconButton>
            </Tooltip>

            <IconButton size="small" onClick={(e) => setProfileAnchor(e.currentTarget)}>
              <Avatar sx={{ width: 30, height: 30, fontSize: '0.85rem', bgcolor: 'primary.main', color: '#0B0F14' }}>
                {(user?.fullName ?? user?.username ?? '?').slice(0, 1).toUpperCase()}
              </Avatar>
            </IconButton>
            <Menu anchorEl={profileAnchor} open={!!profileAnchor} onClose={() => setProfileAnchor(null)}>
              <Box sx={{ px: 2, py: 1 }}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {user?.fullName}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {user?.roles.join(', ')}
                </Typography>
              </Box>
              <Divider />
              <MenuItem onClick={logout}>Sign out</MenuItem>
            </Menu>
          </Toolbar>
        </AppBar>

        <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
          {children}
        </Box>
      </Box>
    </Box>
  );
}
