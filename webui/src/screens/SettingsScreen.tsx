import { useEffect, useState } from 'react'
import {
  Box,
  Typography,
  Tabs,
  Tab,
  Switch,
  Card,
  Chip,
  Button,
  FormControl,
  Select,
  MenuItem,
  InputLabel,
  Snackbar,
} from '@mui/material'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { desktopTheme } from '../theme/theme'

type TabValue = 'appearance' | 'network' | 'advanced' | 'about'

export default function SettingsScreen() {
  const [tab, setTab] = useState<TabValue>('appearance')
  const [globalSearch, setGlobalSearch] = useState(false)
  const [dohProvider, setDohProvider] = useState(0)
  const [imageCacheSize, setImageCacheSize] = useState('Calculating...')
  const [saveSnackbar, setSaveSnackbar] = useState(false)

  const { data: settings } = useApi(() => api.get<any>('/api/settings'), [])
  useEffect(() => {
    if (settings) {
      setGlobalSearch(settings.global_search_enabled ?? false)
      setDohProvider(settings.doh_provider ?? 0)
    }
  }, [settings])

  const toggleGlobalSearch = () => {
    const next = !globalSearch
    setGlobalSearch(next)
    api.post('/api/settings/global_search_enabled', { value: next }).catch(() => {})
  }

  const changeDohProvider = (val: number) => {
    setDohProvider(val)
    api.post('/api/settings/doh_provider', { value: val }).catch(() => {})
  }

  const clearCache = () => {
    api.post('/api/settings/clear_image_cache', {}).then((res: any) => {
      setImageCacheSize(res.size || '0 MB')
    }).catch(() => {})
  }

  const tabs: { label: string; value: TabValue }[] = [
    { label: 'Appearance', value: 'appearance' },
    { label: 'Network', value: 'network' },
    { label: 'Advanced', value: 'advanced' },
    { label: 'About', value: 'about' },
  ]

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box sx={{ px: 3, pt: 2, pb: 0, flexShrink: 0 }}>
        <Typography variant="h2" sx={{ fontWeight: 700, mb: 1 }}>
          Settings
        </Typography>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          sx={{
            minHeight: 36,
            '& .MuiTab-root': { minHeight: 36, py: 0.5, color: desktopTheme.textMuted },
            '& .Mui-selected': { color: desktopTheme.accent },
            '& .MuiTabs-indicator': { bgcolor: desktopTheme.accent },
          }}
        >
          {tabs.map(t => (
            <Tab key={t.value} label={t.label} value={t.value} />
          ))}
        </Tabs>
      </Box>

      <Box sx={{ flex: 1, overflow: 'auto', px: 3, py: 2 }}>
        {tab === 'appearance' && (
          <Box>
            <Card sx={{ p: 2, mb: 2, bgcolor: desktopTheme.surfaceCard, borderRadius: 2 }}>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>Theme</Typography>
              <Typography variant="body2" sx={{ color: desktopTheme.textMuted, mb: 2 }}>
                Theme settings apply to the desktop app. WebUI always uses dark theme.
              </Typography>
              <Typography variant="body2" sx={{ color: desktopTheme.textMuted }}>
                Appearance settings (accent color, light mode, AMOLED mode, grid size) are available in the desktop app under Settings &gt; Appearance.
              </Typography>
            </Card>
          </Box>
        )}

        {tab === 'network' && (
          <Box>
            <Card sx={{ p: 2, mb: 2, bgcolor: desktopTheme.surfaceCard, borderRadius: 2 }}>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>DNS over HTTPS (DoH)</Typography>
              <Typography variant="body2" sx={{ color: desktopTheme.textMuted, mb: 2 }}>
                Bypass ISP DNS blocking by encrypting your DNS queries.
              </Typography>
              <FormControl size="small" sx={{ minWidth: 240 }}>
                <InputLabel>Provider</InputLabel>
                <Select
                  value={dohProvider}
                  label="Provider"
                  onChange={(e) => changeDohProvider(Number(e.target.value))}
                  sx={{
                    bgcolor: desktopTheme.surfaceElevated,
                    '& .MuiOutlinedInput-notchedOutline': { borderColor: desktopTheme.divider },
                  }}
                >
                  <MenuItem value={0}>None</MenuItem>
                  <MenuItem value={1}>Cloudflare</MenuItem>
                  <MenuItem value={2}>Google</MenuItem>
                  <MenuItem value={3}>Quad9</MenuItem>
                </Select>
              </FormControl>
            </Card>
          </Box>
        )}

        {tab === 'advanced' && (
          <Box>
            <Card sx={{ p: 2, mb: 2, bgcolor: desktopTheme.surfaceCard, borderRadius: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>Advanced Global Search</Typography>
                  <Typography variant="body2" sx={{ color: desktopTheme.textMuted }}>
                    Search all providers simultaneously
                  </Typography>
                </Box>
                <Switch
                  checked={globalSearch}
                  onChange={toggleGlobalSearch}
                  sx={{
                    '& .MuiSwitch-thumb': { bgcolor: globalSearch ? desktopTheme.accent : desktopTheme.textMuted },
                    '& .MuiSwitch-track': { bgcolor: globalSearch ? `${desktopTheme.accent}60` : desktopTheme.divider },
                  }}
                />
              </Box>
            </Card>

            <Card sx={{ p: 2, mb: 2, bgcolor: desktopTheme.surfaceCard, borderRadius: 2 }}>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>Image Cache</Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Typography variant="body2" sx={{ color: desktopTheme.textMuted }}>{imageCacheSize}</Typography>
                <Button
                  variant="outlined"
                  size="small"
                  onClick={clearCache}
                  sx={{ borderColor: desktopTheme.divider, color: desktopTheme.textPrimary }}
                >
                  Clear
                </Button>
              </Box>
            </Card>

            <Card sx={{ p: 2, bgcolor: desktopTheme.surfaceCard, borderRadius: 2 }}>
              <Box sx={{ display: 'flex', gap: 2 }}>
                <Button
                  variant="contained"
                  onClick={() => { setSaveSnackbar(true) }}
                  sx={{ bgcolor: desktopTheme.accent, '&:hover': { opacity: 0.9 } }}
                >
                  Save
                </Button>
                <Button
                  variant="outlined"
                  onClick={() => {}}
                  sx={{ borderColor: desktopTheme.divider, color: desktopTheme.textPrimary }}
                >
                  Refresh
                </Button>
              </Box>
            </Card>
          </Box>
        )}

        <Snackbar
          open={saveSnackbar}
          autoHideDuration={2000}
          onClose={() => setSaveSnackbar(false)}
          message="Settings saved"
          anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
          ContentProps={{ sx: { bgcolor: desktopTheme.accent } }}
        />

        {tab === 'about' && (
          <Box>
            <Card sx={{ p: 2, mb: 2, bgcolor: desktopTheme.surfaceCard, borderRadius: 2 }}>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>About CloudStream Desktop</Typography>
              <Typography variant="body2" sx={{ color: desktopTheme.textMuted, lineHeight: 1.7 }}>
                This is an UNOFFICIAL Desktop client. Please use the official CloudStream Android app for the best experience.
              </Typography>
              <Typography variant="body2" sx={{ color: desktopTheme.textMuted, mt: 2, lineHeight: 1.7 }}>
                PRE-ALPHA BUILD: This software is provided 'as is'. We do not guarantee that any features will work correctly, and there is no guarantee of future updates or ongoing maintenance.
              </Typography>
              <Box sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
                <Chip label="Discord" clickable component="a" href="https://discord.gg/5Hus6fM" target="_blank" variant="outlined" sx={{ color: desktopTheme.textPrimary, borderColor: desktopTheme.divider }} />
                <Chip label="CloudStream Wiki" clickable component="a" href="https://recloudstream.github.io/csdocs/" target="_blank" variant="outlined" sx={{ color: desktopTheme.textPrimary, borderColor: desktopTheme.divider }} />
                <Chip label="Android Repo" clickable component="a" href="https://github.com/recloudstream/cloudstream" target="_blank" variant="outlined" sx={{ color: desktopTheme.textPrimary, borderColor: desktopTheme.divider }} />
              </Box>
              <Typography variant="body2" sx={{ color: desktopTheme.textMuted, mt: 2, lineHeight: 1.7 }}>
                This product uses the TMDB API but is not endorsed or certified by TMDB.
              </Typography>
            </Card>
          </Box>
        )}
      </Box>
    </Box>
  )
}
