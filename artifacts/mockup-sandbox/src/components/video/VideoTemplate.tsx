import { motion, AnimatePresence } from 'framer-motion';
import { useVideoPlayer } from '../../lib/video/hooks';
import { Scene1 } from './video_scenes/Scene1';
import { Scene2 } from './video_scenes/Scene2';
import { Scene3 } from './video_scenes/Scene3';
import { Scene4 } from './video_scenes/Scene4';
import { Scene5 } from './video_scenes/Scene5';

const SCENE_DURATIONS = { hook: 4000, blocker: 5000, nuclear: 6000, launcher: 6000, close: 9000 };

export default function VideoTemplate() {
  const { currentScene } = useVideoPlayer({ durations: SCENE_DURATIONS });

  return (
    <div className="relative w-full h-screen overflow-hidden bg-[#0A0A0F]">
      {/* Background Video */}
      <video
        className="absolute inset-0 w-full h-full object-cover opacity-30 mix-blend-screen"
        src={`${import.meta.env.BASE_URL}videos/tech-particles.mp4`}
        autoPlay
        muted
        loop
        playsInline
      />
      
      {/* Background Texture */}
      <div 
        className="absolute inset-0 opacity-10"
        style={{ backgroundImage: `url(${import.meta.env.BASE_URL}images/circuit-overlay.png)`, backgroundSize: 'cover', backgroundPosition: 'center' }}
      />
      
      {/* Dynamic Background Gradients */}
      <div className="absolute inset-0 z-0">
        <motion.div 
          className="absolute w-[80vw] h-[80vw] rounded-full blur-[100px] opacity-20"
          style={{ background: 'radial-gradient(circle, #E8003D, transparent)' }}
          animate={{ 
            x: ['-20%', '50%', '10%', '-20%'], 
            y: ['10%', '-20%', '40%', '10%'],
            scale: [1, 1.2, 0.8, 1],
            opacity: currentScene === 2 ? 0.4 : 0.2
          }}
          transition={{ duration: 15, repeat: Infinity, ease: 'linear' }}
        />
        <motion.div 
          className="absolute w-[60vw] h-[60vw] rounded-full blur-[100px] opacity-20 right-0 bottom-0"
          style={{ background: 'radial-gradient(circle, #FFB800, transparent)' }}
          animate={{ 
            x: ['20%', '-40%', '10%', '20%'], 
            y: ['-10%', '50%', '-20%', '-10%'],
            scale: [1, 0.9, 1.3, 1],
            opacity: currentScene === 1 ? 0.3 : 0.1
          }}
          transition={{ duration: 20, repeat: Infinity, ease: 'linear' }}
        />
      </div>

      <AnimatePresence mode="sync">
        {currentScene === 0 && <Scene1 key="hook" />}
        {currentScene === 1 && <Scene2 key="blocker" />}
        {currentScene === 2 && <Scene3 key="nuclear" />}
        {currentScene === 3 && <Scene4 key="launcher" />}
        {currentScene === 4 && <Scene5 key="close" />}
      </AnimatePresence>
    </div>
  );
}
