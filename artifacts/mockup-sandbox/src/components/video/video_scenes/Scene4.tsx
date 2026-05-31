import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export function Scene4() {
  const [phase, setPhase] = useState(0);

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 500),
      setTimeout(() => setPhase(2), 1500),
      setTimeout(() => setPhase(3), 2800),
      setTimeout(() => setPhase(4), 4500),
    ];
    return () => timers.forEach(clearTimeout);
  }, []);

  return (
    <motion.div 
      className="absolute inset-0 flex items-center justify-between px-[10vw] z-10"
      initial={{ scale: 1.2, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.6 }}
    >
      <div className="w-[40vw] flex flex-col justify-center">
        <motion.h2 
          className="text-[8vw] font-bebas text-[#F0F0F0] leading-[0.9]"
          initial={{ opacity: 0, x: -30 }}
          animate={phase >= 1 ? { opacity: 1, x: 0 } : { opacity: 0, x: -30 }}
          transition={{ duration: 0.5 }}
        >
          FOCUS LAUNCHER
        </motion.h2>
        
        <motion.div 
          className="mt-[3vw] space-y-[1.5vw]"
          initial={{ opacity: 0 }}
          animate={phase >= 2 ? { opacity: 1 } : { opacity: 0 }}
          transition={{ duration: 0.5 }}
        >
          <div className="flex items-center gap-[1.5vw]">
            <div className="w-[3vw] h-[3vw] rounded-full border-[0.2vw] border-[#FFB800] flex items-center justify-center">
              <div className="w-[1.5vw] h-[1.5vw] bg-[#FFB800] rounded-full" />
            </div>
            <span className="font-mono text-[1.8vw] text-[#F0F0F0]/80">Registry Locked</span>
          </div>
          <div className="flex items-center gap-[1.5vw]">
            <div className="w-[3vw] h-[3vw] rounded-full border-[0.2vw] border-[#FFB800] flex items-center justify-center">
              <div className="w-[1.5vw] h-[1.5vw] bg-[#FFB800] rounded-full" />
            </div>
            <span className="font-mono text-[1.8vw] text-[#F0F0F0]/80">Taskbar Hidden</span>
          </div>
        </motion.div>
        
        <motion.p 
          className="font-mono text-[2vw] text-[#E8003D] font-bold mt-[4vw]"
          initial={{ opacity: 0, y: 20 }}
          animate={phase >= 3 ? { opacity: 1, y: 0 } : { opacity: 0, y: 20 }}
          transition={{ duration: 0.5 }}
        >
          Your desktop. Locked. Your session. Protected.
        </motion.p>
      </div>
      
      <motion.div 
        className="relative w-[30vw] h-[30vw]"
        initial={{ opacity: 0, scale: 0.5, rotate: -90 }}
        animate={phase >= 1 ? { opacity: 1, scale: 1, rotate: 0 } : { opacity: 0, scale: 0.5, rotate: -90 }}
        transition={{ type: "spring", stiffness: 200, damping: 20 }}
      >
        <svg viewBox="0 0 100 100" className="w-full h-full transform -rotate-90">
          <circle cx="50" cy="50" r="45" fill="none" stroke="#2A2A35" strokeWidth="2" />
          <motion.circle 
            cx="50" cy="50" r="45" fill="none" stroke="#E8003D" strokeWidth="4" strokeLinecap="round"
            strokeDasharray="283"
            initial={{ strokeDashoffset: 283 }}
            animate={phase >= 2 ? { strokeDashoffset: 40 } : { strokeDashoffset: 283 }}
            transition={{ duration: 2, ease: "easeOut" }}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="font-bebas text-[8vw] text-[#F0F0F0] leading-none">85</span>
          <span className="font-mono text-[1.5vw] text-[#F0F0F0]/50 tracking-widest">SCORE</span>
        </div>
      </motion.div>
    </motion.div>
  );
}