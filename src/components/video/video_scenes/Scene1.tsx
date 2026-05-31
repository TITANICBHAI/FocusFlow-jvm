import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export function Scene1() {
  const [phase, setPhase] = useState(0);

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 500),  // Red block overlay slams
      setTimeout(() => setPhase(2), 700),  // "BLOCKED" text appears
      setTimeout(() => setPhase(3), 1200), // Subtext
      setTimeout(() => setPhase(4), 2500), // Exit start
    ];
    return () => timers.forEach(t => clearTimeout(t));
  }, []);

  return (
    <motion.div 
      className="absolute inset-0 flex items-center justify-center z-20"
      initial={{ clipPath: 'inset(0 100% 0 0)' }}
      animate={{ clipPath: 'inset(0 0% 0 0)' }}
      exit={{ scale: 1.5, opacity: 0, filter: 'blur(20px)' }}
      transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
    >
      {/* Desktop Simulation Window */}
      <motion.div className="w-[60vw] h-[40vw] border border-[#2D2B3D] bg-[#0C0B14]/80 backdrop-blur-md rounded-xl relative overflow-hidden flex items-center justify-center">
        {/* Social Media Icon Mock */}
        <motion.div 
          className="w-[8vw] h-[8vw] rounded-2xl bg-gradient-to-tr from-blue-600 to-purple-500 shadow-[0_0_30px_rgba(59,130,246,0.3)]"
          initial={{ scale: 0, rotate: -20 }}
          animate={{ scale: 1, rotate: 0 }}
          transition={{ type: 'spring', stiffness: 300, damping: 20, delay: 0.2 }}
        />

        {/* The SLAM overlay */}
        <motion.div 
          className="absolute inset-0 bg-[#C0392B] flex flex-col items-center justify-center"
          initial={{ scaleY: 0, originY: 0 }}
          animate={phase >= 1 ? { scaleY: 1 } : { scaleY: 0 }}
          transition={{ duration: 0.15, ease: 'easeOut' }}
        >
          <motion.h1 
            className="text-[8vw] font-display text-[#F0F0F0] tracking-widest leading-none m-0"
            initial={{ opacity: 0, y: 50, scale: 0.8 }}
            animate={phase >= 2 ? { opacity: 1, y: 0, scale: 1 } : { opacity: 0, y: 50, scale: 0.8 }}
            transition={{ type: 'spring', stiffness: 400, damping: 20 }}
          >
            BLOCKED
          </motion.h1>
          <motion.p
            className="font-body text-[1.5vw] text-[#0C0B14] font-bold mt-4 tracking-tight"
            initial={{ opacity: 0 }}
            animate={phase >= 3 ? { opacity: 1 } : { opacity: 0 }}
            transition={{ duration: 0.2 }}
          >
            THE APP IS DEAD THE MOMENT YOU OPEN IT
          </motion.p>
        </motion.div>
      </motion.div>
    </motion.div>
  );
}
