import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export function Scene1() {
  const [phase, setPhase] = useState(0);

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 500),
      setTimeout(() => setPhase(2), 2000),
      setTimeout(() => setPhase(3), 3200),
    ];
    return () => timers.forEach(clearTimeout);
  }, []);

  return (
    <motion.div 
      className="absolute inset-0 flex items-center justify-center z-10 bg-[#0A0A0F]"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ filter: 'blur(20px)', opacity: 0, scale: 1.1 }}
      transition={{ duration: 0.5 }}
    >
      <motion.div 
        className="absolute inset-0 bg-center bg-cover opacity-60 mix-blend-luminosity"
        style={{ backgroundImage: `url(${import.meta.env.BASE_URL}images/chaotic-desktop.png)` }}
        animate={phase >= 2 ? { filter: 'blur(20px) grayscale(1)', opacity: 0.2, scale: 1.1 } : { filter: 'blur(0px) grayscale(0)', opacity: 0.6, scale: 1 }}
        transition={{ duration: 0.2, ease: "circIn" }}
      />
      
      {phase >= 2 && (
        <motion.div 
          className="absolute inset-0 bg-[#E8003D]"
          initial={{ opacity: 0.8 }}
          animate={{ opacity: 0 }}
          transition={{ duration: 0.3 }}
        />
      )}
      
      {phase >= 2 && (
        <div className="relative z-20 flex flex-col items-center">
          <motion.h1 
            className="text-[12vw] font-bebas text-[#F0F0F0] leading-none tracking-tight"
            initial={{ scale: 0.5, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ type: "spring", stiffness: 400, damping: 20 }}
          >
            FOCUSFLOW
          </motion.h1>
          <motion.p 
            className="text-[2vw] font-mono text-[#E8003D] mt-[1vw] uppercase tracking-widest font-bold"
            initial={{ y: 20, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.4, delay: 0.2 }}
          >
            Engaging Enforcement
          </motion.p>
        </div>
      )}
    </motion.div>
  );
}