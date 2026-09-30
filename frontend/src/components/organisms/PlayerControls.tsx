import { useEffect, useState } from 'react';
import {
  Maximize,
  Minimize,
  Pause,
  Play,
  RotateCcw,
  RotateCw,
  Volume2,
  VolumeX,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '../ui/button';

interface PlayerControlsProps {
  videoRef: React.RefObject<HTMLVideoElement | null>;
  className?: string;
}

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) {
    return '0:00';
  }
  const total = Math.floor(seconds);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const secs = total % 60;
  const mm = hours > 0 ? String(minutes).padStart(2, '0') : String(minutes);
  return `${hours > 0 ? `${hours}:` : ''}${mm}:${String(secs).padStart(2, '0')}`;
}

/**
 * The player's controls, over the video element instead of the browser's.
 *
 * A custom bar exists because the session lifecycle has to know what the user
 * did: the position sent on heartbeats comes from the media element, seeking
 * is a normal interaction, and the bar keeps working when `controls` is off
 * (fullscreen, keyboard). State is read from the element's events rather than
 * duplicated, so external pauses (a heartbeat failure, the end of the stream)
 * show up here too.
 */
export default function PlayerControls({ videoRef, className }: PlayerControlsProps) {
  const [playing, setPlaying] = useState(false);
  const [duration, setDuration] = useState(0);
  const [position, setPosition] = useState(0);
  const [buffered, setBuffered] = useState(0);
  const [volume, setVolume] = useState(1);
  const [muted, setMuted] = useState(false);
  const [fullscreen, setFullscreen] = useState(false);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) {
      return;
    }

    const onPlay = () => setPlaying(true);
    const onPause = () => setPlaying(false);
    const onTimeUpdate = () => setPosition(video.currentTime);
    const onDurationChange = () => setDuration(video.duration || 0);
    const onProgress = () => {
      const end = video.buffered.length > 0 ? video.buffered.end(video.buffered.length - 1) : 0;
      setBuffered(end);
    };
    const onVolumeChange = () => {
      setVolume(video.volume);
      setMuted(video.muted);
    };
    const onFullscreenChange = () => setFullscreen(Boolean(document.fullscreenElement));

    video.addEventListener('play', onPlay);
    video.addEventListener('pause', onPause);
    video.addEventListener('timeupdate', onTimeUpdate);
    video.addEventListener('durationchange', onDurationChange);
    video.addEventListener('progress', onProgress);
    video.addEventListener('volumechange', onVolumeChange);
    document.addEventListener('fullscreenchange', onFullscreenChange);
    return () => {
      video.removeEventListener('play', onPlay);
      video.removeEventListener('pause', onPause);
      video.removeEventListener('timeupdate', onTimeUpdate);
      video.removeEventListener('durationchange', onDurationChange);
      video.removeEventListener('progress', onProgress);
      video.removeEventListener('volumechange', onVolumeChange);
      document.removeEventListener('fullscreenchange', onFullscreenChange);
    };
  }, [videoRef]);

  const togglePlay = () => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      void video.play().catch(() => {});
    } else {
      video.pause();
    }
  };

  const seekBy = (seconds: number) => {
    const video = videoRef.current;
    if (!video) return;
    video.currentTime = Math.max(
      0,
      Math.min(video.duration || Infinity, video.currentTime + seconds)
    );
  };

  const seekTo = (value: number) => {
    const video = videoRef.current;
    if (!video) return;
    video.currentTime = value;
    setPosition(value);
  };

  const toggleMute = () => {
    const video = videoRef.current;
    if (!video) return;
    video.muted = !video.muted;
  };

  const changeVolume = (value: number) => {
    const video = videoRef.current;
    if (!video) return;
    video.volume = value;
    video.muted = value === 0;
  };

  const toggleFullscreen = () => {
    const container = videoRef.current?.parentElement;
    if (!container) return;
    if (document.fullscreenElement) {
      void document.exitFullscreen();
    } else {
      void container.requestFullscreen().catch(() => {});
    }
  };

  const progress = duration > 0 ? (position / duration) * 100 : 0;
  const bufferedPercent = duration > 0 ? (buffered / duration) * 100 : 0;

  return (
    <div
      className={cn('flex items-center gap-2 bg-black/80 px-3 py-2 text-white sm:gap-3', className)}
    >
      <Button
        variant="ghost"
        size="icon"
        onClick={togglePlay}
        aria-label={playing ? 'Pausar' : 'Reproducir'}
      >
        {playing ? <Pause /> : <Play />}
      </Button>
      <Button
        variant="ghost"
        size="icon"
        onClick={() => seekBy(-10)}
        aria-label="Retroceder 10 segundos"
      >
        <RotateCcw />
      </Button>
      <Button
        variant="ghost"
        size="icon"
        onClick={() => seekBy(10)}
        aria-label="Avanzar 10 segundos"
      >
        <RotateCw />
      </Button>

      <span className="w-12 text-right text-xs tabular-nums text-white/80">
        {formatTime(position)}
      </span>

      <div className="relative h-1.5 flex-1 rounded bg-white/20">
        <div
          className="absolute inset-y-0 left-0 rounded bg-white/30"
          style={{ width: `${bufferedPercent}%` }}
        />
        <div
          className="absolute inset-y-0 left-0 rounded bg-red-600"
          style={{ width: `${progress}%` }}
        />
        <input
          type="range"
          min={0}
          max={duration || 0}
          step={0.1}
          value={position}
          onChange={(event) => seekTo(Number(event.target.value))}
          aria-label="Posición"
          className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
        />
      </div>

      <span className="w-12 text-xs tabular-nums text-white/80">{formatTime(duration)}</span>

      <div className="hidden items-center gap-2 sm:flex">
        <Button
          variant="ghost"
          size="icon"
          onClick={toggleMute}
          aria-label={muted ? 'Activar sonido' : 'Silenciar'}
        >
          {muted || volume === 0 ? <VolumeX /> : <Volume2 />}
        </Button>
        <input
          type="range"
          min={0}
          max={1}
          step={0.05}
          value={muted ? 0 : volume}
          onChange={(event) => changeVolume(Number(event.target.value))}
          aria-label="Volumen"
          className="h-1 w-20 cursor-pointer accent-red-600"
        />
      </div>

      <Button variant="ghost" size="icon" onClick={toggleFullscreen} aria-label="Pantalla completa">
        {fullscreen ? <Minimize /> : <Maximize />}
      </Button>
    </div>
  );
}
