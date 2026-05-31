import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export function Scene3() {
  const [phase, setPhase] = useState(0);

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 400),
      setTimeout(() => setPhase(2), 1200),
      setTimeout(() => setPhase(3), 2500),
      setTimeout(() => setPhase(4), 4500),
    ];
    return () => timers.forEach(clearTimeout);
  }, []);

  const processes = [
    "taskmgr.exe - TERMINATED",
    "regedit.exe - TERMINATED",
    "cmd.exe - TERMINATED",
    "powershell.exe - TERMINATED",
    "wsl.exe - TERMINATED"
  ];

  return (
    <motion.div 
      className="absolute inset-0 flex flex-col justify-center px-[8vw] z-10"
      initial={{ x: '100%' }}
      animate={{ x: 0 }}
      exit={{ x: '-100%' }}
      transition={{ duration: 0.7, ease: [0.16, 1, 0.3, 1] }}
    >
      <motion.h2 
        className="text-[10vw] font-bebas text-[#FFB800] leading-[0.9]"
        initial={{ opacity: 0, y: 30 }}
        animate={phase >= 1 ? { opacity: 1, y: 0 } : { opacity: 0, y: 30 }}
        transition={{ duration: 0.5 }}
      >
        NUCLEAR MODE
      </motion.h2>
      
      <div className="mt-[3vw] flex flex-col gap-[1vw]">
        {processes.map((proc, i) => (
          <motion.div 
            key={i}
            className="flex items-center gap-[1vw]"
            initial={{ opacity: 0, x: -50 }}
            animate={phase >= 2 ? { opacity: 1, x: 0 } : { opacity: 0, x: -50 }}
            transition={{ duration: 0.3, delay: i * 0.1 }}
          >
            <div className="w-[1vw] h-[1vw] bg-[#E8003D]" />
            <span className="font-mono text-[2vw] text-[#F0F0F0] font-bold">{proc}</span>
          </motion.div>
        ))}
      </div>
      
      <motion.div 
        className="mt-[4vw] border-l-[0.5vw] border-[#E8003D] pl-[2vw]"
        initial={{ opacity: 0, height: 0 }}
        animate={phase >= 3 ? { opacity: 1, height: 'auto' } : { opacity: 0, height: 0 }}
        transition={{ duration: 0.5 }}
      >
        <p className="font-mono text-[2.5vw] text-[#F0F0F0]/90">
          30+ escape routes — <span className="text-[#E8003D] font-bold">eliminated.</span>
        </p>
      </motion.div>
    </motion.div>
  );
}