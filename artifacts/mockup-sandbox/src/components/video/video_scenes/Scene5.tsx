import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export function Scene5() {
  const [phase, setPhase] = useState(0);

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 500),
      setTimeout(() => setPhase(2), 2000),
      setTimeout(() => setPhase(3), 3500),
      setTimeout(() => setPhase(4), 5000),
    ];
    return () => timers.forEach(clearTimeout);
  }, []);

  return (
    <motion.div 
      className="absolute inset-0 flex flex-col items-center justify-center z-10 bg-[#0A0A0F]"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.8 }}
    >
      <div className="absolute inset-0 flex items-center justify-center opacity-10">
         <img src={`${import.meta.env.BASE_URL}images/logo-mark.png`} className="w-[80vw] h-[80vw] object-contain" alt="Logo background" />
      </div>

      <div className="flex justify-center gap-[8vw] mb-[6vw] relative z-20">
        <motion.div 
          className="text-center"
          initial={{ opacity: 0, y: 30 }}
          animate={phase >= 1 ? { opacity: 1, y: 0 } : { opacity: 0, y: 30 }}
          transition={{ type: "spring", stiffness: 300, damping: 20 }}
        >
          <div className="font-bebas text-[12vw] text-[#FFB800] leading-none">100</div>
          <div className="font-mono text-[1.8vw] text-[#F0F0F0]/60 tracking-widest">FOCUS SCORE</div>
        </motion.div>
        
        <motion.div 
          className="text-center"
          initial={{ opacity: 0, y: 30 }}
          animate={phase >= 1 ? { opacity: 1, y: 0 } : { opacity: 0, y: 30 }}
          transition={{ type: "spring", stiffness: 300, damping: 20, delay: 0.1 }}
        >
          <div className="font-bebas text-[12vw] text-[#E8003D] leading-none">14</div>
          <div className="font-mono text-[1.8vw] text-[#F0F0F0]/60 tracking-widest">DAY STREAK</div>
        </motion.div>
      </div>

      <motion.div
        className="flex flex-col items-center relative z-20"
        initial={{ opacity: 0, scale: 0.8 }}
        animate={phase >= 2 ? { opacity: 1, scale: 1 } : { opacity: 0, scale: 0.8 }}
        transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
      >
        <img src={`${import.meta.env.BASE_URL}images/logo-mark.png`} className="w-[12vw] h-[12vw] object-contain mb-[2vw]" alt="FocusFlow Logo" />
        <h1 className="text-[8vw] font-bebas text-[#F0F0F0] leading-none tracking-wide">FOCUSFLOW</h1>
      </motion.div>

      <motion.div
        className="mt-[3vw] relative z-20"
        initial={{ opacity: 0, filter: 'blur(10px)' }}
        animate={phase >= 3 ? { opacity: 1, filter: 'blur(0px)' } : { opacity: 0, filter: 'blur(10px)' }}
        transition={{ duration: 0.8 }}
      >
        <p className="font-mono text-[2.5vw] text-[#E8003D] font-bold tracking-[0.2em]">FOCUS. ENFORCED.</p>
      </motion.div>
    </motion.div>
  );
}