import { useState, useEffect, useRef } from "react";

export function useVideoPlayer({ durations }: { durations: Record<string, number> }) {
  const [currentScene, setCurrentScene] = useState(0);
  const durationArray = Object.values(durations);

  useEffect(() => {
    if (typeof window !== 'undefined' && (window as any).startRecording) {
      (window as any).startRecording();
    }

    let idx = 0;
    let timer: NodeJS.Timeout;
    let hasCompletedPass = false;

    const tick = () => {
      idx = (idx + 1) % durationArray.length;
      
      if (idx === 0 && !hasCompletedPass) {
        hasCompletedPass = true;
        if (typeof window !== 'undefined' && (window as any).stopRecording) {
          (window as any).stopRecording();
        }
      }
      
      setCurrentScene(idx);
      timer = setTimeout(tick, durationArray[idx]);
    };

    timer = setTimeout(tick, durationArray[0]);

    return () => clearTimeout(timer);
  }, [JSON.stringify(durations)]);

  return { currentScene };
}
