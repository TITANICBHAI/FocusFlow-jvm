import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export function Scene3() {
  const [phase, setPhase] = useState(0);

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 300),  // Grid appears
      setTimeout(() => setPhase(2), 800),  // Badge pulses
      setTimeout(() => setPhase(3), 1500), // Text appears
      setTimeout(() => setPhase(4), 4500), // Exit starts
    ];
    return () => timers.forEach(t => clearTimeout(t));
  }, []);

  return (
    <motion.div 
      className="absolute inset-0 z-20 flex flex-col items-center justify-center"
      initial={{ scale: 0.8, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      exit={{ scale: 1.2, opacity: 0 }}
      transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
    >
      <img 
        src={`${import.meta.env.BASE_URL}images/kiosk-grid.png`} 
        alt="Kiosk Grid" 
        className="absolute inset-0 w-full h-full object-cover opacity-10 mix-blend-screen -z-10"
      />

      {/* Kiosk UI Frame */}
      <motion.div 
        className="w-[70vw] h-[40vw] border-2 border-[#2D2B3D] bg-[#0C0B14]/90 backdrop-blur-xl rounded-2xl flex flex-col items-center justify-center relative overflow-hidden"
        initial={{ clipPath: 'circle(0% at 50% 50%)' }}
        animate={phase >= 1 ? { clipPath: 'circle(150% at 50% 50%)' } : { clipPath: 'circle(0% at 50% 50%)' }}
        transition={{ duration: 1, ease: [0.16, 1, 0.3, 1] }}
      >
        <motion.div 
          className="px-6 py-2 bg-[#C0392B] text-white font-display text-[3vw] tracking-widest absolute top-8"
          initial={{ y: -50, opacity: 0 }}
          animate={phase >= 2 ? { y: 0, opacity: 1 } : { y: -50, opacity: 0 }}
          transition={{ type: 'spring', stiffness: 400, damping: 15 }}
        >
          HARD LOCKED
        </motion.div>

        <motion.h2
          className="font-display text-[6vw] text-white tracking-wide mt-12"
          initial={{ opacity: 0, y: 20 }}
          animate={phase >= 3 ? { opacity: 1, y: 0 } : { opacity: 0, y: 20 }}
          transition={{ duration: 0.5 }}
        >
          YOUR OS, REPLACED.
        </motion.h2>

        <motion.p
          className="font-body text-[1.5vw] text-[#F0F0F0]/70 text-center max-w-[50vw] mt-6 leading-relaxed"
          initial={{ opacity: 0 }}
          animate={phase >= 3 ? { opacity: 1 } : { opacity: 0 }}
          transition={{ duration: 0.5, delay: 0.2 }}
        >
          100+ Windows processes whitelisted so you can't crash your system — but you can't escape either.
        </motion.p>
      </motion.div>
    </motion.div>
  );
}
