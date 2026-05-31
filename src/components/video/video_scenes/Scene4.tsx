import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export function Scene4() {
  const [phase, setPhase] = useState(0);

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 400),  // Code snippet
      setTimeout(() => setPhase(2), 1200), // Block overlay expands
      setTimeout(() => setPhase(3), 1800), // Text appears
      setTimeout(() => setPhase(4), 4500), // Exit starts
    ];
    return () => timers.forEach(t => clearTimeout(t));
  }, []);

  return (
    <motion.div 
      className="absolute inset-0 z-20 flex items-center justify-center bg-[#0C0B14]"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ y: '-100%' }}
      transition={{ duration: 0.5, ease: 'easeInOut' }}
    >
      {/* Code background */}
      <motion.div 
        className="font-body text-[#F0F0F0]/20 text-[1.2vw] whitespace-pre p-[5vw] absolute inset-0 -z-10"
        initial={{ y: 50, opacity: 0 }}
        animate={phase >= 1 ? { y: 0, opacity: 1 } : { y: 50, opacity: 0 }}
        transition={{ duration: 0.8, ease: 'easeOut' }}
      >
        {`WinEventHook.SetWinEventHook(
  EVENT_SYSTEM_FOREGROUND,
  EVENT_SYSTEM_FOREGROUND,
  IntPtr.Zero,
  eventCallback,
  0, 0,
  WINEVENT_OUTOFCONTEXT
);`}
      </motion.div>

      {/* The kill switch overlay */}
      <motion.div
        className="absolute w-full h-full bg-[#C0392B] flex flex-col items-center justify-center"
        initial={{ scale: 0, borderRadius: '100%' }}
        animate={phase >= 2 ? { scale: 1.5, borderRadius: '0%' } : { scale: 0, borderRadius: '100%' }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
      >
        <motion.div
          className="text-center"
          initial={{ opacity: 0, scale: 0.9 }}
          animate={phase >= 3 ? { opacity: 1, scale: 1 } : { opacity: 0, scale: 0.9 }}
          transition={{ duration: 0.4 }}
        >
          <h2 className="font-display text-[8vw] text-white leading-none tracking-tight">
            A KILL SWITCH
          </h2>
          <p className="font-body text-[2vw] text-[#0C0B14] font-bold mt-4 tracking-tight">
            Not a timer. Not a notification.
          </p>
        </motion.div>
      </motion.div>
    </motion.div>
  );
}
