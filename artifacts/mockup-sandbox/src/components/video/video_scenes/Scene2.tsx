import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export function Scene2() {
  const [phase, setPhase] = useState(0);

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 300),
      setTimeout(() => setPhase(2), 1500),
      setTimeout(() => setPhase(3), 2500),
      setTimeout(() => setPhase(4), 4000),
    ];
    return () => timers.forEach(clearTimeout);
  }, []);

  return (
    <motion.div 
      className="absolute inset-0 flex items-center justify-center z-10"
      initial={{ scale: 0.8, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      exit={{ scale: 1.2, opacity: 0 }}
      transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
    >
      <div className="w-[70vw] h-[40vw] bg-[#0A0A0F]/90 backdrop-blur-xl border border-[#E8003D]/30 flex flex-col items-center justify-center relative overflow-hidden shadow-[0_0_50px_rgba(232,0,61,0.2)] rounded-lg">
        <motion.div 
          className="absolute inset-0 border-[0.5vw] border-[#E8003D]"
          animate={{ opacity: [1, 0.5, 1], scale: [1, 1.02, 1] }}
          transition={{ duration: 2, repeat: Infinity }}
        />
        
        <motion.h2 
          className="text-[15vw] font-bebas text-[#E8003D] leading-none"
          initial={{ y: 50, opacity: 0, rotateX: 45 }}
          animate={phase >= 1 ? { y: 0, opacity: 1, rotateX: 0 } : { y: 50, opacity: 0, rotateX: 45 }}
          transition={{ type: "spring", stiffness: 300, damping: 20 }}
        >
          BLOCKED
        </motion.h2>
        
        <motion.div 
          className="text-[3vw] font-mono text-[#F0F0F0] mt-[2vw] bg-[#E8003D] px-[2vw] py-[0.5vw]"
          initial={{ scaleX: 0 }}
          animate={phase >= 2 ? { scaleX: 1 } : { scaleX: 0 }}
          transition={{ duration: 0.4, ease: "circOut" }}
          style={{ originX: 0 }}
        >
          <motion.span
            initial={{ opacity: 0 }}
            animate={phase >= 2 ? { opacity: 1 } : { opacity: 0 }}
            transition={{ delay: 0.3 }}
          >
            DISCORD.EXE
          </motion.span>
        </motion.div>
        
        <motion.p 
          className="text-[1.8vw] font-mono text-[#F0F0F0]/70 mt-[3vw]"
          initial={{ opacity: 0, y: 20 }}
          animate={phase >= 3 ? { opacity: 1, y: 0 } : { opacity: 0, y: 20 }}
          transition={{ duration: 0.5 }}
        >
          Win32-level enforcement. No workarounds.
        </motion.p>
      </div>
    </motion.div>
  );
}