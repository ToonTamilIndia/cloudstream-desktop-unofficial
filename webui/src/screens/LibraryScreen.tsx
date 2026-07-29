
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Typography,
  Grid,
  Card,
  CardMedia,
  CardContent,
  IconButton,
  Button,
  LinearProgress,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import type { LibraryItem } from '../types'
import { desktopTheme } from '../theme/theme'

export default function LibraryScreen() {
  const navigate = useNavigate()
  const { data, loading } = useApi(() => api.library.list(), [])
  const [localItems, setLocalItems] = useState<LibraryItem[] | null>(null)
  const items: LibraryItem[] = localItems ?? ((data as any)?.items || [])

  const handleDelete = async (id: string) => {
    setLocalItems((prev) => (prev ?? items).filter((i) => i.id !== id))
    try { await api.library.delete(id) } catch {
      setLocalItems(null) // revert on error: re-show from server data
    }
  }

  const handleItemClick = (item: LibraryItem) => {
    navigate(`/details?url=${encodeURIComponent(item.url)}&api=${encodeURIComponent(item.apiName)}&name=${encodeURIComponent(item.name)}`)
  }

  if (loading) {
    return (
      <Box sx={{ p: 3, display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
        <Typography variant="body1" sx={{ color: desktopTheme.textMuted }}>Loading library...</Typography>
      </Box>
    )
  }

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box sx={{ px: 3, pt: 2, pb: 1, flexShrink: 0 }}>
        <Typography variant="h2" sx={{ fontWeight: 700 }}>My Library</Typography>
      </Box>
      <Box sx={{ flex: 1, overflow: 'auto', px: 3, py: 1 }}>
        {items.length === 0 ? (
          <Box sx={{ textAlign: 'center', mt: 8 }}>
            <Typography variant="h5" sx={{ color: desktopTheme.textMuted, mb: 2 }}>Your library is empty</Typography>
            <Button variant="contained" onClick={() => navigate('/')} sx={{ bgcolor: desktopTheme.accent, borderRadius: 2 }}>Browse Shows</Button>
          </Box>
        ) : (
          <Grid container spacing={2}>
            {items.map((item) => (
              <Grid item xs={6} sm={4} md={3} lg={2} key={item.id}>
                <Card
                  onClick={() => handleItemClick(item)}
                  sx={{
                    bgcolor: desktopTheme.surfaceCard, borderRadius: 3, cursor: 'pointer',
                    transition: 'transform 0.15s, box-shadow 0.15s',
                    '&:hover': { transform: 'translateY(-2px)', boxShadow: `0 4px 16px ${desktopTheme.accent}30` },
                    position: 'relative', overflow: 'hidden',
                  }}
                >
                  <Box sx={{ position: 'relative', pt: '150%' }}>
                    {item.posterUrl ? (
                      <CardMedia component="img" image={item.posterUrl} alt={item.name}
                        sx={{ position: 'absolute', top: 0, width: '100%', height: '100%', objectFit: 'cover' }} />
                    ) : (
                      <Box sx={{ position: 'absolute', top: 0, width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: desktopTheme.surfaceElevated }}>
                        <Typography variant="h4" sx={{ color: desktopTheme.textMuted, fontWeight: 700 }}>{item.name[0]}</Typography>
                      </Box>
                    )}
                    <IconButton size="small" onClick={(e) => { e.stopPropagation(); handleDelete(item.id) }}
                      sx={{ position: 'absolute', top: 6, right: 6, bgcolor: 'rgba(0,0,0,0.5)', color: '#fff', '&:hover': { bgcolor: 'rgba(0,0,0,0.7)' }, width: 28, height: 28 }}>
                      <DeleteIcon sx={{ fontSize: 16 }} />
                    </IconButton>
                  </Box>
                  <CardContent sx={{ px: 1, py: 0.75, '&:last-child': { pb: 0.75 } }}>
                    <Typography variant="body2" sx={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.name}</Typography>
                    <Typography variant="caption" sx={{ color: desktopTheme.textMuted }}>{item.type}</Typography>
                    {item.progress != null && item.progress > 0 && (
                      <LinearProgress variant="determinate" value={Math.min(item.progress, 100)}
                        sx={{ mt: 0.5, height: 2, borderRadius: 2, bgcolor: desktopTheme.divider, '& .MuiLinearProgress-bar': { bgcolor: desktopTheme.accent } }} />
                    )}
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        )}
      </Box>
    </Box>
  )
}
