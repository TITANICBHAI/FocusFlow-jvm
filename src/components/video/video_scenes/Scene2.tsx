import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export function Scene2() {
  const [phase, setPhase] = useState(0);

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 300),  // "NUCLEAR" text
      setTimeout(() => setPhase(2), 800),  // Line 1
      setTimeout(() => setPhase(3), 1400), // Line 2
      setTimeout(() => setPhase(4), 2000), // Line 3
      setTimeout(() => setPhase(5), 4500), // Exit starts
    ];
    return () => timers.forEach(t => clearTimeout(t));
  }, []);

  return (
    <motion.div 
      className="absolute inset-0 z-20 flex flex-col items-start justify-center pl-[10vw]"
      initial={{ opacity: 0, x: -100 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: 100, filter: 'blur(10px)' }}
      transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
    >
      {/* Background Texture */}
      <img 
        src={`${import.meta.env.BASE_URL}images/process-list.png`} 
        alt="Process List" 
        className="absolute inset-0 w-full h-full object-cover opacity-20 mix-blend-overlay -z-10"
      />

      <motion.h1 
        className="font-display text-[12vw] text-[#C0392B] leading-none mb-8 tracking-tighter"
        initial={{ opacity: 0, scale: 1.5, rotateX: 45 }}
        animate={phase >= 1 ? { opacity: 1, scale: 1, rotateX: 0 } : { opacity: 0, scale: 1.5, rotateX: 45 }}
        transition={{ type: 'spring', stiffness: 200, damping: 20 }}
      >
        NUCLEAR
      </motion.h1>

      <div className="space-y-6 font-body text-[2vw] text-[#F0F0F0]">
        {[
          "Process scan every 500ms — 50+ escape routes killed",
          "Batch kill + firewall rules injected instantly",
          "Task Manager. CMD. PowerShell. All gone."
        ].map((text, i) => (
          <div key={i} className="relative overflow-hidden group">
            <motion.div
              className="absolute left-0 top-0 bottom-0 w-[4px] bg-[#C0392B]"
              initial={{ height: 0 }}
              animate={phase >= i + 2 ? { height: '100%' } : { height: 0 }}
              transition={{ duration: 0.2 }}
            />
            <motion.p
              className="pl-6"
              initial={{ x: -20, opacity: 0 }}
              animate={phase >= i + 2 ? { x: 0, opacity: 1 } : { x: -20, opacity: 0 }}
              transition={{ duration: 0.4, ease: 'easeOut' }}
            >
              {text}
            </motion.p>
          </div>
        ))}
      </div>
    </motion.div>
  );
}
