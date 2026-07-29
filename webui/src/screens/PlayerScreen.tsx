import { useEffect, useRef, useState } from 'react'
import { useSearchParams, useNavigate, useLocation } from 'react-router-dom'
import { Box, IconButton } from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import type { SubtitleResult } from '../types'

declare const jwplayer: any

export default function PlayerScreen() {
  const [searchParams] = useSearchParams()
  const location = useLocation()
  const navigate = useNavigate()
  const playerRef = useRef<HTMLDivElement>(null)
  const jwRef = useRef<any>(null)
  const [useFallback, setUseFallback] = useState(false)

  const url = searchParams.get('url') || ''
  const directUrl = searchParams.get('directUrl') || ''
  const title = searchParams.get('title') || 'Video Player'
  const image = searchParams.get('poster') || ''
  const isM3u8 = searchParams.get('isM3u8') === 'true'
  const isDash = searchParams.get('isDash') === 'true'
  const subtitles: SubtitleResult[] = (location.state as any)?.subtitles
    || JSON.parse(searchParams.get('subtitles') || '[]')

  const drmParam = searchParams.get('drm') || (location.state as any)?.drm
  const drm = drmParam
    ? (typeof drmParam === 'string' ? JSON.parse(drmParam) : drmParam) as {
        drmKid?: string; drmKey?: string; drmUuid?: string; drmLicenseUrl?: string
      }
    : undefined

  useEffect(() => {
    const activeUrl = useFallback && directUrl ? directUrl : url
    if (!activeUrl || !playerRef.current) return

    const tracks = subtitles.map((s) => ({
      file: s.proxyUrl || s.url,
      label: s.lang,
      kind: 'captions' as const,
    }))

    try {
      const setup: any = {
        file: activeUrl,
        image: image || undefined,
        title: title,
        tracks: tracks.length > 0 ? tracks : undefined,
        autostart: true,
        width: '100%',
        height: '100%',
        stretching: 'uniform',
        aspectratio: '16:9',
        skin: { name: 'netflix' },
      }
      if (isM3u8) setup.type = 'hls'
      if (isDash) setup.type = 'dash'

      // ClearKey DRM
      if (drm?.drmKid && drm?.drmKey) {
        setup.clearkey = {
          keyId: drm.drmKid,
          key: drm.drmKey,
        }
      }

      // Widevine/PlayReady license URL
      if (drm?.drmLicenseUrl) {
        setup.widevineLoading = function (widevineParams: any) {
          return fetch(drm!.drmLicenseUrl!, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(widevineParams),
          }).then((res) => res.arrayBuffer())
        }
      }

      jwRef.current = jwplayer(playerRef.current.id).setup(setup)

      jwRef.current.on('error', (e: any) => {
        console.error('JW Player error:', e)
        if (!useFallback && directUrl) {
          try { jwRef.current?.remove() } catch {}
          jwRef.current = null
          setUseFallback(true)
        }
      })
    } catch (e) {
      console.error('JW Player setup failed:', e)
      if (!useFallback && directUrl) {
        setUseFallback(true)
      }
    }

    return () => {
      try {
        jwRef.current?.remove()
        jwRef.current = null
      } catch {}
    }
  }, [url, useFallback, drm])

  return (
    <Box sx={{ width: '100%', height: '100%', bgcolor: '#000', position: 'relative' }}>
      <IconButton
        onClick={() => navigate(-1)}
        sx={{
          position: 'absolute',
          top: 16,
          left: 16,
          zIndex: 100,
          color: '#fff',
          bgcolor: 'rgba(0,0,0,0.4)',
          '&:hover': { bgcolor: 'rgba(0,0,0,0.6)' },
        }}
      >
        <ArrowBackIcon />
      </IconButton>
      <div
        id="jw-player"
        ref={playerRef}
        style={{ width: '100%', height: '100%' }}
      />
    </Box>
  )
}
