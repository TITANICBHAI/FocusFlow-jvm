import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useVideoPlayer } from '../../lib/video/hooks'; // Adjusting path to avoid alias issues if not configured, or if the template provides it at @/lib/video we can fallback.
import { Scene1 } from './video_scenes/Scene1';
import { Scene2 } from './video_scenes/Scene2';
import { Scene3 } from './video_scenes/Scene3';
import { Scene4 } from './video_scenes/Scene4';
import { Scene5 } from './video_scenes/Scene5';

const SCENE_DURATIONS = { hook: 3000, nuclear: 5000, kiosk: 5000, engine: 5000, outro: 12000 };

export default function VideoTemplate() {
  const { currentScene } = useVideoPlayer({ durations: SCENE_DURATIONS });

  /*
  Voiceover Script:
  Scene 1: "Your apps don't close themselves. FocusFlow does it for them."
  Scene 2: "Three layers. Process kill. Firewall lockdown. Zero escape routes."
  Scene 3: "Your OS, replaced. A kiosk with 100 whitelisted Windows processes."  
  Scene 4: "Zero delay. The moment a blocked app gets focus — it's gone."
  Scene 5: "FocusFlow. Enforced focus. Not suggested."
  */

  return (
    <div className="relative w-full h-screen overflow-hidden bg-[#0C0B14]">
      {/* Background Video */}
      <video
        src={`${import.meta.env.BASE_URL}videos/bg-particles.mp4`}
        autoPlay
        loop
        muted
        playsInline
        className="absolute inset-0 w-full h-full object-cover opacity-30 mix-blend-screen"
      />

      {/* Persistent red accent line */}
      <motion.div
        className="absolute h-[2px] bg-[#C0392B] z-10"
        animate={{
          left: ['0%', '0%', '20%', '0%', '40%'][currentScene],
          width: ['100%', '30%', '60%', '10%', '20%'][currentScene],
          top: ['5%', '15%', '85%', '50%', '50%'][currentScene],
        }}
        transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
      />
      
      {/* Floating circuit elements */}
      <motion.div
        className="absolute w-[30vw] h-[30vw] border-2 border-[#2D2B3D]/30 rounded-full z-0"
        animate={{
          x: ['70vw', '10vw', '50vw', '80vw', '35vw'][currentScene],
          y: ['-10vh', '60vh', '-20vh', '40vh', '15vh'][currentScene],
          scale: [1, 1.5, 0.8, 2, 1],
        }}
        transition={{ duration: 1.5, ease: 'easeInOut' }}
      />

      <AnimatePresence mode="popLayout">
        {currentScene === 0 && <Scene1 key="hook" />}
        {currentScene === 1 && <Scene2 key="nuclear" />}
        {currentScene === 2 && <Scene3 key="kiosk" />}
        {currentScene === 3 && <Scene4 key="engine" />}
        {currentScene === 4 && <Scene5 key="outro" />}
      </AnimatePresence>
    </div>
  );
}
