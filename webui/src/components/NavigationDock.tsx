import { useLocation, useNavigate } from 'react-router-dom'
import {
  Box,
  IconButton,
  Tooltip,
  Badge,
} from '@mui/material'
import RssFeedIcon from '@mui/icons-material/RssFeed'
import HomeIcon from '@mui/icons-material/Home'
import VideoLibraryIcon from '@mui/icons-material/VideoLibrary'
import ExtensionIcon from '@mui/icons-material/Extension'
import SettingsIcon from '@mui/icons-material/Settings'
import LiveTvIcon from '@mui/icons-material/LiveTv'
import DownloadIcon from '@mui/icons-material/Download'
import { desktopTheme } from '../theme/theme'

const navItems = [
  { label: 'Home', icon: <HomeIcon />, path: '/' },
  { label: 'Library', icon: <VideoLibraryIcon />, path: '/library' },
  { label: 'Extensions', icon: <ExtensionIcon />, path: '/extensions' },
  { label: 'IPTV', icon: <LiveTvIcon />, path: '/iptv' },
  { label: 'Downloads', icon: <DownloadIcon />, path: '/downloads' },
  { label: 'Settings', icon: <SettingsIcon />, path: '/settings' },
]

export default function NavigationDock() {
  const location = useLocation()
  const navigate = useNavigate()

  return (
    <Box
      sx={{
        width: 72,
        minWidth: 72,
        height: '100%',
        bgcolor: desktopTheme.surfaceCard,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        py: 2,
        gap: 0.5,
        borderRight: `1px solid ${desktopTheme.divider}`,
        zIndex: 10,
      }}
    >
      <Box sx={{ mb: 2, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <RssFeedIcon sx={{ color: desktopTheme.accent, fontSize: 32 }} />
      </Box>

      {navItems.map((item) => {
        const isActive = location.pathname === item.path
        return (
          <Tooltip key={item.path} title={item.label} placement="right">
            <IconButton
              onClick={() => navigate(item.path)}
              sx={{
                width: 48,
                height: 48,
                borderRadius: 3,
                color: isActive ? desktopTheme.accent : desktopTheme.textMuted,
                bgcolor: isActive ? `${desktopTheme.accent}18` : 'transparent',
                '&:hover': {
                  bgcolor: isActive ? `${desktopTheme.accent}24` : desktopTheme.surfaceElevated,
                },
                transition: 'all 0.2s',
              }}
            >
              {item.label === 'Extensions' ? (
                <Badge color="error" variant="dot" invisible={false}>
                  {item.icon}
                </Badge>
              ) : (
                item.icon
              )}
            </IconButton>
          </Tooltip>
        )
      })}
    </Box>
  )
}
