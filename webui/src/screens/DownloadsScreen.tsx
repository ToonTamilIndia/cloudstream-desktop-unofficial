import { useEffect, useRef, useState } from 'react'
import {
  Box,
  Typography,
  Button,
  LinearProgress,
  Chip,
  Paper,
} from '@mui/material'
import DownloadIcon from '@mui/icons-material/Download'
import { api } from '../api/client'
import { desktopTheme } from '../theme/theme'
import type { DownloadTask } from '../types'

const statusColor: Record<string, string> = {
  DOWNLOADING: desktopTheme.accent,
  QUEUED: '#9499A6',
  COMPLETED: '#4CAF50',
  FAILED: '#EF4444',
  CANCELLED: '#9499A6',
}

function formatBytes(n: number): string {
  if (!n) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let v = n
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++ }
  return `${v.toFixed(v >= 10 || i === 0 ? 0 : 1)} ${units[i]}`
}

function statusLabel(s: string): string {
  switch (s) {
    case 'DOWNLOADING': return 'Downloading'
    case 'QUEUED': return 'Queued'
    case 'COMPLETED': return 'Completed'
    case 'FAILED': return 'Failed'
    case 'CANCELLED': return 'Cancelled'
    default: return s
  }
}

export default function DownloadsScreen() {
  const [tasks, setTasks] = useState<DownloadTask[]>([])
  const [loading, setLoading] = useState(true)

  const load = useRef(async () => {
    try {
      const res = await api.downloads.list()
      setTasks(res.downloads)
    } catch (e: any) {
      setTasks((prev) => prev)
    } finally {
      setLoading(false)
    }
  }).current

  useEffect(() => {
    load()
    const iv = setInterval(load, 1500)
    return () => clearInterval(iv)
  }, [load])

  const handleCancel = async (id: string) => {
    try { await api.downloads.cancel(id) } catch {}
    load()
  }

  const active = tasks.filter((t) => t.status === 'DOWNLOADING' || t.status === 'QUEUED')
  const done = tasks.filter((t) => t.status === 'COMPLETED')
  const failed = tasks.filter((t) => t.status === 'FAILED' || t.status === 'CANCELLED')

  if (loading) {
    return (
      <Box sx={{ p: 3, display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
        <Typography variant="body1" sx={{ color: desktopTheme.textMuted }}>Loading downloads...</Typography>
      </Box>
    )
  }

  const renderRow = (t: DownloadTask) => {
    const color = statusColor[t.status] || desktopTheme.textMuted
    const pct = Math.round((t.progress || 0) * 100)
    const isDone = t.status === 'COMPLETED'
    return (
      <Paper key={t.id} sx={{ bgcolor: desktopTheme.surfaceCard, borderRadius: 3, p: 2, mb: 1.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="body1" sx={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {t.title}
            </Typography>
            <Typography variant="caption" sx={{ color: desktopTheme.textMuted }}>{t.filename}</Typography>
          </Box>
          <Chip
            label={statusLabel(t.status)}
            size="small"
            sx={{ color: '#fff', bgcolor: `${color}2e`, border: `1px solid ${color}66`, fontWeight: 600 }}
          />
          {t.status === 'DOWNLOADING' && (
            <Button size="small" variant="outlined" color="inherit"
              onClick={() => handleCancel(t.id)}
              sx={{ color: '#EF4444', borderColor: '#EF444466', '&:hover': { bgcolor: '#EF44441a', borderColor: '#EF4444' } }}>
              Cancel
            </Button>
          )}
        </Box>

        {t.status === 'DOWNLOADING' && (
          <Box sx={{ mt: 1.5 }}>
            <LinearProgress variant="determinate" value={Math.min(pct, 100)}
              sx={{ height: 6, borderRadius: 3, bgcolor: desktopTheme.divider, '& .MuiLinearProgress-bar': { bgcolor: desktopTheme.accent } }} />
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.5 }}>
              <Typography variant="caption" sx={{ color: desktopTheme.textMuted }}>{formatBytes(t.bytesDownloaded)}</Typography>
              {t.totalBytes > 0 && (
                <Typography variant="caption" sx={{ color: desktopTheme.textMuted }}>{formatBytes(t.totalBytes)} · {pct}%</Typography>
              )}
            </Box>
          </Box>
        )}

        {t.status === 'FAILED' && t.error && (
          <Typography variant="caption" sx={{ color: '#EF4444', display: 'block', mt: 1 }}>{t.error}</Typography>
        )}

        {isDone && (
          <Box sx={{ mt: 1.5, textAlign: 'right' }}>
            <Button
              size="small"
              variant="contained"
              startIcon={<DownloadIcon />}
              onClick={() => window.open(api.downloads.fileUrl(t.id), '_blank')}
              sx={{ bgcolor: desktopTheme.accent, borderRadius: 2, textTransform: 'none' }}
            >
              Save file
            </Button>
          </Box>
        )}
      </Paper>
    )
  }

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box sx={{ px: 3, pt: 2, pb: 1, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h2" sx={{ fontWeight: 700 }}>Downloads</Typography>
      </Box>
      <Box sx={{ flex: 1, overflow: 'auto', px: 3, py: 1 }}>
        {tasks.length === 0 ? (
          <Box sx={{ textAlign: 'center', mt: 8 }}>
            <Typography variant="h5" sx={{ color: desktopTheme.textMuted, mb: 2 }}>No downloads yet</Typography>
            <Typography variant="body2" sx={{ color: desktopTheme.textMuted }}>Start a download from any episode or provider to see it here.</Typography>
          </Box>
        ) : (
          <>
            {active.map(renderRow)}
            {done.map(renderRow)}
            {failed.map(renderRow)}
          </>
        )}
      </Box>
    </Box>
  )
}