import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  TextField,
  InputAdornment,
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Radio,
  RadioGroup,
  FormControlLabel,
  Chip,
} from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import CategoryRow from '../components/CategoryRow'
import type { SourceInfo, SearchResultItem, MainPageResponse } from '../types'
import { desktopTheme } from '../theme/theme'

interface MainPageCategory {
  title: string
  items: SearchResultItem[]
}

export default function HomeScreen() {
  const navigate = useNavigate()
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<Map<string, SearchResultItem[]>>(new Map())
  const [searching, setSearching] = useState(false)
  const [selectedSource, setSelectedSource] = useState<string | null>(null)
  const [sourceDialogOpen, setSourceDialogOpen] = useState(false)

  const { data: sourcesData } = useApi(() => api.sources(), [])
  const sources: SourceInfo[] = (sourcesData as any)?.sources || []
  useEffect(() => {
    if (sources.length > 0 && !selectedSource) setSelectedSource(sources[0].name)
  }, [sources])

  const { data: mainPageData } = useApi(
    () => selectedSource ? api.mainpage(selectedSource) : Promise.resolve(null as any),
    [selectedSource],
  )
  const categories: MainPageCategory[] = mainPageData
    ? (mainPageData as MainPageResponse).categories.map((c: any) => ({ title: c.name, items: c.items }))
    : []

  useEffect(() => {
    if (!searchQuery.trim()) {
      setSearchResults(new Map())
      return
    }
    setSearching(true)
    const timer = setTimeout(async () => {
      try {
        const res = await api.search(searchQuery)
        const map = new Map<string, SearchResultItem[]>()
        res.results.forEach((item) => {
          const existing = map.get(item.apiName) || []
          existing.push(item)
          map.set(item.apiName, existing)
        })
        setSearchResults(map)
      } catch {
        // ignore
      } finally {
        setSearching(false)
      }
    }, 400)
    return () => clearTimeout(timer)
  }, [searchQuery])

  const handleItemClick = useCallback(
    (item: SearchResultItem) => {
      navigate(`/details?url=${encodeURIComponent(item.url)}&api=${encodeURIComponent(item.apiName)}&name=${encodeURIComponent(item.name)}`)
    },
    [navigate],
  )

  const selectedSourceObj = sources.find((s) => s.name === selectedSource)

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box
        sx={{
          px: 3,
          pt: 2,
          pb: 1,
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          flexShrink: 0,
        }}
      >
        <TextField
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search movies, shows..."
          variant="outlined"
          size="small"
          sx={{
            maxWidth: 480,
            flex: 1,
            '& .MuiOutlinedInput-root': {
              bgcolor: desktopTheme.surfaceCard,
              borderRadius: 3,
              '& fieldset': { borderColor: desktopTheme.divider },
              '&:hover fieldset': { borderColor: desktopTheme.textMuted },
              '&.Mui-focused fieldset': { borderColor: desktopTheme.accent },
            },
          }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon sx={{ color: desktopTheme.textMuted }} />
              </InputAdornment>
            ),
          }}
        />

        <Chip
          label={selectedSourceObj?.url ? `${selectedSourceObj.name}` : 'Select Source'}
          onClick={() => setSourceDialogOpen(true)}
          variant="outlined"
          sx={{
            color: desktopTheme.textPrimary,
            borderColor: desktopTheme.divider,
            fontWeight: 500,
            '&:hover': { borderColor: desktopTheme.accent },
          }}
        />
      </Box>

      <Box
        sx={{
          flex: 1,
          overflow: 'auto',
          '&::-webkit-scrollbar': { width: 8 },
          '&::-webkit-scrollbar-thumb': {
            bgcolor: desktopTheme.divider,
            borderRadius: 4,
          },
        }}
      >
        {searchQuery.trim() ? (
          <Box sx={{ px: 2, py: 1 }}>
            <Typography variant="body2" sx={{ px: 1, mb: 1, color: desktopTheme.textMuted }}>
              {searching ? 'Searching...' : `Results for "${searchQuery}"`}
            </Typography>
            {Array.from(searchResults.entries()).map(([provider, items]) => (
              <CategoryRow
                key={provider}
                title={provider}
                items={items}
                onItemClick={handleItemClick}
              />
            ))}
            {!searching && searchResults.size === 0 && (
              <Typography variant="body1" sx={{ px: 2, color: desktopTheme.textMuted, mt: 4, textAlign: 'center' }}>
                No results found
              </Typography>
            )}
          </Box>
        ) : (
          <Box>
            {categories.map((cat) => (
              <CategoryRow
                key={cat.title}
                title={cat.title}
                items={cat.items}
                onItemClick={handleItemClick}
              />
            ))}
            {categories.length === 0 && selectedSourceObj && !selectedSourceObj.hasMainPage && (
              <Typography
                variant="body1"
                sx={{ px: 2, color: desktopTheme.textMuted, mt: 6, textAlign: 'center' }}
              >
                This provider does not support categories. Use search to find content.
              </Typography>
            )}
            {categories.length === 0 && selectedSourceObj?.hasMainPage && (
              <Typography
                variant="body1"
                sx={{ px: 2, color: desktopTheme.textMuted, mt: 6, textAlign: 'center' }}
              >
                No content available for this provider
              </Typography>
            )}
          </Box>
        )}
      </Box>

      <Dialog
        open={sourceDialogOpen}
        onClose={() => setSourceDialogOpen(false)}
        PaperProps={{
          sx: {
            bgcolor: desktopTheme.surfaceCard,
            borderRadius: 3,
            minWidth: 320,
          },
        }}
      >
        <DialogTitle sx={{ color: desktopTheme.textPrimary, fontWeight: 600 }}>
          Select Provider
        </DialogTitle>
        <DialogContent>
          <RadioGroup value={selectedSource} onChange={(e) => { setSelectedSource(e.target.value); setSourceDialogOpen(false) }}>
            {sources.map((s) => (
              <FormControlLabel
                key={s.name}
                value={s.name}
                control={<Radio sx={{ color: desktopTheme.textMuted, '&.Mui-checked': { color: desktopTheme.accent } }} />}
                label={
                  <Box>
                    <Typography variant="body1" sx={{ color: desktopTheme.textPrimary }}>
                      {s.name}
                    </Typography>
                    <Typography variant="caption" sx={{ color: desktopTheme.textMuted }}>
                      {s.lang} · {s.supportedTypes.join(', ')}
                    </Typography>
                  </Box>
                }
                sx={{ mb: 0.5, borderRadius: 2, '&:hover': { bgcolor: desktopTheme.surfaceElevated } }}
              />
            ))}
          </RadioGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSourceDialogOpen(false)} sx={{ color: desktopTheme.textMuted }}>
            Cancel
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
