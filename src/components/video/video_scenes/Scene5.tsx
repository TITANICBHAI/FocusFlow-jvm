import React, { useState, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';

export function Scene5() {
  const [phase, setPhase] = useState(0);

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 500),   // Dashboard stats
      setTimeout(() => setPhase(2), 1500),  // Streak
      setTimeout(() => setPhase(3), 5000),  // Dissolve starts
      setTimeout(() => setPhase(4), 6000),  // Logo reveals
      setTimeout(() => setPhase(5), 11500), // Exit starts
    ];
    return () => timers.forEach(t => clearTimeout(t));
  }, []);

  return (
    <motion.div 
      className="absolute inset-0 z-20 bg-[#0C0B14] flex items-center justify-center"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.8 }}
    >
      {/* Phase 1: Dashboard Mock */}
      <AnimatePresence>
        {phase < 3 && (
          <motion.div 
            className="absolute inset-0 flex items-center justify-center gap-[4vw]"
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 1.1, filter: 'blur(10px)' }}
            transition={{ duration: 0.8 }}
          >
            <motion.div 
              className="w-[20vw] h-[20vw] rounded-full border-[1vw] border-[#2D2B3D] flex items-center justify-center relative"
              initial={{ rotate: -90 }}
              animate={phase >= 1 ? { rotate: 0 } : { rotate: -90 }}
              transition={{ duration: 1, ease: "easeOut" }}
            >
              <div className="absolute inset-0 rounded-full border-[1vw] border-[#C0392B]" style={{ clipPath: 'polygon(50% 0%, 100% 0, 100% 100%, 50% 100%)' }} />
              <span className="font-display text-[8vw] text-white">98</span>
            </motion.div>
            
            <div className="flex flex-col gap-6">
              <motion.div 
                className="bg-[#2D2B3D]/40 p-6 rounded-xl border border-[#2D2B3D]"
                initial={{ opacity: 0, x: 50 }}
                animate={phase >= 2 ? { opacity: 1, x: 0 } : { opacity: 0, x: 50 }}
                transition={{ duration: 0.5 }}
              >
                <div className="font-body text-[#F0F0F0]/60 text-[1vw] uppercase">Current Streak</div>
                <div className="font-display text-[4vw] text-[#C0392B]">42 DAYS</div>
              </motion.div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Phase 2: Final Lockup */}
      <AnimatePresence>
        {phase >= 4 && (
          <motion.div 
            className="absolute inset-0 flex flex-col items-center justify-center bg-[#0C0B14]"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 1 }}
          >
            <motion.h1 
              className="font-display text-[15vw] text-white leading-none tracking-tighter"
              initial={{ scale: 0.8, filter: 'blur(20px)' }}
              animate={{ scale: 1, filter: 'blur(0px)' }}
              transition={{ duration: 1.5, ease: 'easeOut' }}
            >
              FOCUSFLOW
            </motion.h1>
            <motion.div 
              className="font-body text-[2vw] text-[#C0392B] mt-4 tracking-widest uppercase font-bold"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.5 }}
            >
              ENFORCED FOCUS. NOT SUGGESTED.
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
