import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Typography,
  TextField,
  Button,
  List,
  ListItem,
  ListItemText,
  Chip,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import { api } from '../api/client'
import { desktopTheme } from '../theme/theme'

interface Channel {
  name: string
  url: string
  logo: string
  group: string
  proxyUrl?: string
}

interface Playlist {
  url: string
  name: string
  channels: Channel[]
  groups: string[]
}

export default function IptvScreen() {
  const navigate = useNavigate()
  const [playlistUrl, setPlaylistUrl] = useState('')
  const [loading, setLoading] = useState(false)
  const [playlists, setPlaylists] = useState<Playlist[]>([])
  const [selectedGroup, setSelectedGroup] = useState<string | null>(null)

  const loadPlaylist = async () => {
    if (!playlistUrl.trim()) return
    setLoading(true)
    try {
      const res = await api.post<Playlist>('/api/iptv/load', { url: playlistUrl })
      setPlaylists(prev => {
        const filtered = prev.filter(p => p.url !== res.url)
        return [...filtered, res]
      })
      setPlaylistUrl('')
    } catch (e) {
      console.error('Failed to load playlist', e)
    }
    setLoading(false)
  }

  const playChannel = (channel: Channel) => {
    const playUrl = channel.proxyUrl || channel.url
    navigate(`/player?url=${encodeURIComponent(playUrl)}&title=${encodeURIComponent(channel.name)}&source=IPTV&isM3u8=true`)
  }

  const currentChannels = playlists.length > 0
    ? playlists.flatMap(p =>
        selectedGroup
          ? p.channels.filter(c => c.group === selectedGroup)
          : p.channels
      )
    : []

  const allGroups = [...new Set(playlists.flatMap(p => p.groups))].sort()

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box sx={{ px: 3, pt: 2, pb: 0, flexShrink: 0 }}>
        <Typography variant="h2" sx={{ fontWeight: 700, mb: 1 }}>
          IPTV
        </Typography>
      </Box>

      <Box sx={{ px: 3, py: 1, flexShrink: 0 }}>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <TextField
            size="small"
            placeholder="M3U Playlist URL"
            value={playlistUrl}
            onChange={(e) => setPlaylistUrl(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && loadPlaylist()}
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
            onClick={loadPlaylist}
            disabled={loading}
            startIcon={<AddIcon />}
            sx={{ bgcolor: desktopTheme.accent, borderRadius: 2 }}
          >
            {loading ? 'Loading...' : 'Load'}
          </Button>
        </Box>
      </Box>

      {allGroups.length > 0 && (
        <Box sx={{ px: 3, py: 1, flexShrink: 0, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <Chip
            label="All"
            variant={selectedGroup === null ? 'filled' : 'outlined'}
            onClick={() => setSelectedGroup(null)}
            sx={{ color: selectedGroup === null ? '#fff' : desktopTheme.textPrimary, bgcolor: selectedGroup === null ? desktopTheme.accent : 'transparent', borderColor: desktopTheme.divider }}
          />
          {allGroups.map(group => (
            <Chip
              key={group}
              label={group}
              variant={selectedGroup === group ? 'filled' : 'outlined'}
              onClick={() => setSelectedGroup(group)}
              sx={{ color: selectedGroup === group ? '#fff' : desktopTheme.textPrimary, bgcolor: selectedGroup === group ? desktopTheme.accent : 'transparent', borderColor: desktopTheme.divider }}
            />
          ))}
        </Box>
      )}

      <Box sx={{ flex: 1, overflow: 'auto', px: 3, py: 1 }}>
        {currentChannels.length === 0 && !loading && (
          <Typography variant="body1" sx={{ color: desktopTheme.textMuted, textAlign: 'center', mt: 4 }}>
            {playlists.length === 0
              ? 'Enter an M3U playlist URL above to get started'
              : 'No channels in this group'}
          </Typography>
        )}

        <List dense>
          {currentChannels.map((channel, i) => (
            <ListItem
              key={`${channel.name}-${i}`}
              sx={{
                borderRadius: 2,
                mb: 0.5,
                bgcolor: desktopTheme.surfaceCard,
                '&:hover': { bgcolor: desktopTheme.surfaceElevated },
                cursor: 'pointer',
              }}
              secondaryAction={
                <Button
                  size="small"
                  onClick={() => playChannel(channel)}
                  startIcon={<PlayArrowIcon />}
                  sx={{ color: desktopTheme.accent }}
                >
                  Play
                </Button>
              }
              onClick={() => playChannel(channel)}
            >
              {channel.logo && (
                <Box
                  component="img"
                  src={channel.logo}
                  alt=""
                  sx={{ width: 28, height: 28, mr: 1.5, objectFit: 'contain', borderRadius: 0.5 }}
                />
              )}
              <ListItemText
                primary={channel.name}
                secondary={channel.group}
                primaryTypographyProps={{ color: desktopTheme.textPrimary, fontWeight: 500 }}
                secondaryTypographyProps={{ color: desktopTheme.textMuted }}
              />
            </ListItem>
          ))}
        </List>
      </Box>
    </Box>
  )
}
