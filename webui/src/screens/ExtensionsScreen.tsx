import { useEffect, useState, useMemo } from 'react'
import {
  Box,
  Typography,
  Tabs,
  Tab,
  List,
  ListItem,
  ListItemText,
  Switch,
  Chip,
  IconButton,
  TextField,
  Button,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import DownloadIcon from '@mui/icons-material/Download'
import SearchIcon from '@mui/icons-material/Search'
import SettingsIcon from '@mui/icons-material/Settings'
import { api } from '../api/client'
import type { PluginInfo, PluginResponse, RemotePlugin, PluginSetting } from '../types'
import { desktopTheme } from '../theme/theme'

type TabValue = 'browse' | 'installed' | 'repositories'

export default function ExtensionsScreen() {
  const [tab, setTab] = useState<TabValue>('installed')
  const [plugins, setPlugins] = useState<PluginInfo[]>([])
  const [remotePlugins, setRemotePlugins] = useState<RemotePlugin[]>([])
  const [repos, setRepos] = useState<string[]>([])
  const [newRepoUrl, setNewRepoUrl] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [installing, setInstalling] = useState<string | null>(null)
  const [installError, setInstallError] = useState<string | null>(null)
  const [uninstalling, setUninstalling] = useState<string | null>(null)
  const [bypassPlugin, setBypassPlugin] = useState<RemotePlugin | null>(null)
  const [bypassMessage, setBypassMessage] = useState('')
  const [settingsPlugin, setSettingsPlugin] = useState<string | null>(null)
  const [pluginSettings, setPluginSettings] = useState<PluginSetting[]>([])
  const [settingsLoading, setSettingsLoading] = useState(false)
  const [savingSetting, setSavingSetting] = useState<string | null>(null)

  const fetchData = () => {
    api.plugins().then((res: PluginResponse) => setPlugins(res.plugins)).catch(() => {})
    api.repositories().then((res: { repositories: string[] }) => setRepos(res.repositories || [])).catch(() => {})
    api.browse().then((res) => setRemotePlugins(res.plugins || [])).catch(() => {})
  }

  useEffect(() => { fetchData() }, [])

  const handleTogglePlugin = async (plugin: PluginInfo) => {
    const next = !plugin.enabled
    setPlugins((prev) =>
      prev.map((p) => (p.name === plugin.name ? { ...p, enabled: next } : p)),
    )
    try {
      await api.post(`/api/plugins/${encodeURIComponent(plugin.name)}/toggle`, { enabled: next })
    } catch {
      setPlugins((prev) =>
        prev.map((p) => (p.name === plugin.name ? { ...p, enabled: plugin.enabled } : p)),
      )
    }
  }

  const handleAddRepo = async () => {
    if (!newRepoUrl.trim()) return
    try {
      const res = await api.addRepo(newRepoUrl)
      setRepos(res.repositories || [])
      setNewRepoUrl('')
      // Refresh remote plugins after repo change
      api.browse().then((r) => setRemotePlugins(r.plugins || [])).catch(() => {})
    } catch {}
  }

  const handleDeleteRepo = async (url: string) => {
    try {
      await api.deleteRepo(url)
      setRepos((prev) => prev.filter((r) => r !== url))
      api.browse().then((r) => setRemotePlugins(r.plugins || [])).catch(() => {})
    } catch {}
  }

  const handleInstall = async (plugin: RemotePlugin, forceBypass = false) => {
    setInstalling(plugin.internalName)
    setInstallError(null)
    try {
      const res = await api.installPlugin(plugin.internalName, plugin.jarUrl, forceBypass)
      if (res.needsBypass) {
        setBypassPlugin(plugin)
        setBypassMessage(res.message || 'This plugin requires security bypass. Allow?')
        setInstalling(null)
        return
      }
      api.plugins().then((r: PluginResponse) => setPlugins(r.plugins)).catch(() => {})
    } catch (e: any) {
      setInstallError(`Failed to install ${plugin.name}: ${e.message}`)
    }
    setInstalling(null)
  }

  const handleBypassConfirm = () => {
    if (!bypassPlugin) return
    handleInstall(bypassPlugin, true)
    setBypassPlugin(null)
    setBypassMessage('')
  }

  const handleOpenSettings = async (pluginName: string) => {
    setSettingsPlugin(pluginName)
    setSettingsLoading(true)
    try {
      const res = await api.pluginSettings(pluginName)
      setPluginSettings(res.settings || [])
    } catch {
      setPluginSettings([])
    }
    setSettingsLoading(false)
  }

  const handleSaveSetting = async (key: string, value: string) => {
    if (!settingsPlugin) return
    setSavingSetting(key)
    setPluginSettings((prev) =>
      prev.map((s) => (s.key === key ? { ...s, value } : s)),
    )
    try {
      await api.savePluginSetting(settingsPlugin, key, value)
    } catch {
      // revert
    }
    setSavingSetting(null)
  }

  const handleUninstall = async (name: string) => {
    setUninstalling(name)
    try {
      await api.deletePlugin(name)
      setPlugins((prev) => prev.filter((p) => p.name !== name))
    } catch {}
    setUninstalling(null)
  }

  const installed = plugins.filter((p) => p.enabled)
  const installedNames = useMemo(() => new Set(plugins.map((p) => p.name)), [plugins])

  const filteredRemote = useMemo(() => {
    if (!searchQuery.trim()) return remotePlugins
    const q = searchQuery.toLowerCase()
    return remotePlugins.filter(
      (p) =>
        p.name.toLowerCase().includes(q) ||
        p.internalName.toLowerCase().includes(q) ||
        (p.description || '').toLowerCase().includes(q),
    )
  }, [remotePlugins, searchQuery])

  const uniqueLangs = useMemo(() => {
    const langs = new Set(remotePlugins.map((p) => p.language || '').filter(Boolean))
    return Array.from(langs).sort()
  }, [remotePlugins])

  const [langFilter, setLangFilter] = useState('')

  const filteredByLang = useMemo(() => {
    if (!langFilter) return filteredRemote
    return filteredRemote.filter((p) => p.language === langFilter)
  }, [filteredRemote, langFilter])

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box sx={{ px: 3, pt: 2, pb: 0, flexShrink: 0 }}>
        <Typography variant="h2" sx={{ fontWeight: 700, mb: 1 }}>
          Extensions
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
          <Tab label={`Browse (${remotePlugins.length})`} value="browse" />
          <Tab label={`Installed (${installed.length})`} value="installed" />
          <Tab label="Repositories" value="repositories" />
        </Tabs>
      </Box>

      <Box sx={{ flex: 1, overflow: 'auto', px: 3, py: 1 }}>
        {/* Browse Tab */}
        {tab === 'browse' && (
          <>
            <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
              <TextField
                size="small"
                placeholder="Search plugins..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                InputProps={{
                  startAdornment: <SearchIcon sx={{ mr: 1, color: desktopTheme.textMuted, fontSize: 20 }} />,
                }}
                sx={{
                  flex: 1,
                  '& .MuiOutlinedInput-root': {
                    bgcolor: desktopTheme.surfaceCard,
                    '& fieldset': { borderColor: desktopTheme.divider },
                    '&:hover fieldset': { borderColor: desktopTheme.textMuted },
                  },
                }}
              />
              {uniqueLangs.length > 0 && (
                <TextField
                  select
                  size="small"
                  value={langFilter}
                  onChange={(e) => setLangFilter(e.target.value)}
                  SelectProps={{ native: true }}
                  sx={{
                    minWidth: 100,
                    '& .MuiOutlinedInput-root': {
                      bgcolor: desktopTheme.surfaceCard,
                      '& fieldset': { borderColor: desktopTheme.divider },
                    },
                  }}
                >
                  <option value="">All Lang</option>
                  {uniqueLangs.map((l) => (
                    <option key={l} value={l}>{l.toUpperCase()}</option>
                  ))}
                </TextField>
              )}
            </Box>

            {installError && (
              <Alert severity="error" sx={{ mb: 2 }} onClose={() => setInstallError(null)}>
                {installError}
              </Alert>
            )}

            <List dense>
              {filteredByLang.length === 0 && (
                <Typography variant="body1" sx={{ color: desktopTheme.textMuted, textAlign: 'center', mt: 4 }}>
                  {remotePlugins.length === 0 ? 'No repos configured. Add one in the Repositories tab.' : 'No matching plugins'}
                </Typography>
              )}
              {filteredByLang.map((plugin) => {
                const isInstalled = installedNames.has(plugin.name) || installedNames.has(plugin.internalName)
                return (
                  <ListItem
                    key={`${plugin.repoUrl}-${plugin.internalName}`}
                    sx={{
                      borderRadius: 2,
                      mb: 0.5,
                      bgcolor: desktopTheme.surfaceCard,
                      '&:hover': { bgcolor: desktopTheme.surfaceElevated },
                    }}
                    secondaryAction={
                      <Button
                        variant={isInstalled ? 'outlined' : 'contained'}
                        size="small"
                        disabled={isInstalled || installing === plugin.internalName}
                        onClick={() => handleInstall(plugin)}
                        startIcon={!isInstalled ? <DownloadIcon /> : undefined}
                        sx={{
                          borderRadius: 2,
                          borderColor: isInstalled ? desktopTheme.textMuted : undefined,
                          color: isInstalled ? desktopTheme.textMuted : undefined,
                          bgcolor: !isInstalled ? desktopTheme.accent : undefined,
                          '&:hover': isInstalled ? {} : { bgcolor: desktopTheme.accent + 'dd' },
                        }}
                      >
                        {installing === plugin.internalName ? '...' : isInstalled ? 'Installed' : 'Install'}
                      </Button>
                    }
                  >
                    <ListItemText
                      primary={plugin.name}
                      secondary={`v${plugin.version}${plugin.language ? ` · ${plugin.language.toUpperCase()}` : ''}${plugin.tvTypes?.length ? ` · ${plugin.tvTypes.join(', ')}` : ''}${plugin.description ? ` · ${plugin.description}` : ''}`}
                      primaryTypographyProps={{ color: desktopTheme.textPrimary, fontWeight: 500 }}
                      secondaryTypographyProps={{
                        color: desktopTheme.textMuted,
                        sx: { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 500 },
                      }}
                    />
                  </ListItem>
                )
              })}
            </List>
          </>
        )}

        {/* Installed Tab */}
        {tab === 'installed' && (
          <List dense>
            {plugins.length === 0 && (
              <Typography variant="body1" sx={{ color: desktopTheme.textMuted, textAlign: 'center', mt: 4 }}>
                No plugins installed
              </Typography>
            )}
            {plugins.map((plugin) => (
              <ListItem
                key={plugin.name}
                sx={{
                  borderRadius: 2,
                  mb: 0.5,
                  bgcolor: desktopTheme.surfaceCard,
                  '&:hover': { bgcolor: desktopTheme.surfaceElevated },
                }}
                secondaryAction={
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    {plugin.hasUpdate && (
                      <Chip label="Update" size="small" color="warning" sx={{ fontWeight: 600 }} />
                    )}
                    <IconButton
                      size="small"
                      onClick={() => handleOpenSettings(plugin.name)}
                      sx={{ color: desktopTheme.textMuted }}
                    >
                      <SettingsIcon fontSize="small" />
                    </IconButton>
                    {plugin.fileName && (
                      <IconButton
                        size="small"
                        onClick={() => handleUninstall(plugin.name)}
                        disabled={uninstalling === plugin.name}
                        sx={{ color: desktopTheme.textMuted }}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    )}
                    <Switch
                      checked={plugin.enabled}
                      onChange={() => handleTogglePlugin(plugin)}
                      sx={{
                        '& .MuiSwitch-thumb': { bgcolor: plugin.enabled ? desktopTheme.accent : desktopTheme.textMuted },
                        '& .MuiSwitch-track': { bgcolor: plugin.enabled ? `${desktopTheme.accent}60` : desktopTheme.divider },
                      }}
                    />
                  </Box>
                }
              >
                <ListItemText
                  primary={plugin.name}
                  secondary={`v${plugin.version || '?'}${plugin.fileName ? ` · ${plugin.fileName}` : ''}`}
                  primaryTypographyProps={{ color: desktopTheme.textPrimary, fontWeight: 500 }}
                  secondaryTypographyProps={{ color: desktopTheme.textMuted }}
                />
              </ListItem>
            ))}
          </List>
        )}

        {/* Repositories Tab */}
        {tab === 'repositories' && (
          <Box>
            <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
              <TextField
                size="small"
                placeholder="Repository URL"
                value={newRepoUrl}
                onChange={(e) => setNewRepoUrl(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleAddRepo()}
                sx={{
                  flex: 1,
                  '& .MuiOutlinedInput-root': {
                    bgcolor: desktopTheme.surfaceCard,
                    '& fieldset': { borderColor: desktopTheme.divider },
                    '&:hover fieldset': { borderColor: desktopTheme.textMuted },
                  },
                }}
              />
              <Button
                variant="contained"
                onClick={handleAddRepo}
                startIcon={<AddIcon />}
                sx={{ bgcolor: desktopTheme.accent, borderRadius: 2 }}
              >
                Add
              </Button>
            </Box>

            <List dense>
              {repos.length === 0 && (
                <Typography variant="body1" sx={{ color: desktopTheme.textMuted, textAlign: 'center', mt: 4 }}>
                  No repositories configured
                </Typography>
              )}
              {repos.map((repo) => (
                <ListItem
                  key={repo}
                  sx={{
                    borderRadius: 2,
                    mb: 0.5,
                    bgcolor: desktopTheme.surfaceCard,
                    '&:hover': { bgcolor: desktopTheme.surfaceElevated },
                  }}
                  secondaryAction={
                    <IconButton
                      edge="end"
                      size="small"
                      onClick={() => handleDeleteRepo(repo)}
                      sx={{ color: desktopTheme.textMuted }}
                    >
                      <DeleteIcon />
                    </IconButton>
                  }
                >
                  <ListItemText
                    primary={repo}
                    primaryTypographyProps={{ color: desktopTheme.textPrimary, sx: { wordBreak: 'break-all' } }}
                  />
                </ListItem>
              ))}
            </List>
          </Box>
        )}
      </Box>

      <Dialog
        open={bypassPlugin !== null}
        onClose={() => setBypassPlugin(null)}
        PaperProps={{
          sx: { bgcolor: desktopTheme.surfaceCard, borderRadius: 3, maxWidth: 480 },
        }}
      >
        <DialogTitle sx={{ color: desktopTheme.textPrimary, fontWeight: 600 }}>
          Security Bypass Required
        </DialogTitle>
        <DialogContent>
          <Typography sx={{ color: desktopTheme.textMuted }}>
            {bypassMessage}
          </Typography>
          <Alert severity="warning" sx={{ mt: 2 }}>
            Only allow for plugins you trust. Malicious plugins can access your system.
          </Alert>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setBypassPlugin(null)} sx={{ color: desktopTheme.textMuted }}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleBypassConfirm}
            sx={{ bgcolor: '#e53935', '&:hover': { bgcolor: '#c62828' } }}
          >
            Allow Unsafe Plugin
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={settingsPlugin !== null}
        onClose={() => setSettingsPlugin(null)}
        maxWidth="sm"
        fullWidth
        PaperProps={{
          sx: { bgcolor: desktopTheme.surfaceCard, borderRadius: 3 },
        }}
      >
        <DialogTitle sx={{ color: desktopTheme.textPrimary, fontWeight: 600 }}>
          {settingsPlugin} Settings
        </DialogTitle>
        <DialogContent>
          {settingsLoading ? (
            <Typography sx={{ color: desktopTheme.textMuted }}>Loading settings...</Typography>
          ) : pluginSettings.length === 0 ? (
            <Typography sx={{ color: desktopTheme.textMuted }}>No settings available for this plugin.</Typography>
          ) : (
            <List dense>
              {pluginSettings.map((setting) => (
                <ListItem key={setting.key} sx={{ px: 0 }}>
                  <ListItemText
                    primary={setting.key}
                    secondary={`Type: ${setting.type}${setting.defaultValue ? ` · Default: ${setting.defaultValue}` : ''}`}
                    primaryTypographyProps={{ color: desktopTheme.textPrimary, fontWeight: 500, sx: { wordBreak: 'break-all' } }}
                    secondaryTypographyProps={{ color: desktopTheme.textMuted }}
                    sx={{ mr: 2 }}
                  />
                  {setting.type === 'Boolean' ? (
                    <Switch
                      checked={setting.value === 'true'}
                      onChange={(_, checked) => handleSaveSetting(setting.key, String(checked))}
                      disabled={savingSetting === setting.key}
                      sx={{
                        '& .MuiSwitch-thumb': { bgcolor: setting.value === 'true' ? desktopTheme.accent : desktopTheme.textMuted },
                        '& .MuiSwitch-track': { bgcolor: setting.value === 'true' ? `${desktopTheme.accent}60` : desktopTheme.divider },
                      }}
                    />
                  ) : (
                    <TextField
                      size="small"
                      type={setting.type === 'Int' || setting.type === 'Long' || setting.type === 'Float' ? 'number' : 'text'}
                      value={setting.value}
                      onChange={(e) => handleSaveSetting(setting.key, e.target.value)}
                      disabled={savingSetting === setting.key}
                      sx={{
                        minWidth: 120,
                        '& .MuiOutlinedInput-root': {
                          bgcolor: desktopTheme.surfaceElevated,
                          '& fieldset': { borderColor: desktopTheme.divider },
                        },
                      }}
                    />
                  )}
                </ListItem>
              ))}
            </List>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setSettingsPlugin(null)} sx={{ color: desktopTheme.textMuted }}>
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
