import { useState, useEffect, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";

const SCENE_DURATIONS = [3000, 5000, 5000, 5000, 12000];
const TOTAL = SCENE_DURATIONS.reduce((a, b) => a + b, 0);

function useSceneCycle() {
  const [scene, setScene] = useState(0);
  useEffect(() => {
    let idx = 0;
    const tick = () => {
      idx = (idx + 1) % SCENE_DURATIONS.length;
      setScene(idx);
      timer = setTimeout(tick, SCENE_DURATIONS[idx]);
    };
    let timer = setTimeout(tick, SCENE_DURATIONS[0]);
    return () => clearTimeout(timer);
  }, []);
  return scene;
}

function Scene1() {
  const [phase, setPhase] = useState(0);
  useEffect(() => {
    const t = [
      setTimeout(() => setPhase(1), 400),
      setTimeout(() => setPhase(2), 600),
      setTimeout(() => setPhase(3), 1100),
    ];
    return () => t.forEach(clearTimeout);
  }, []);

  return (
    <motion.div
      className="absolute inset-0 flex items-center justify-center z-20"
      initial={{ clipPath: "inset(0 100% 0 0)" }}
      animate={{ clipPath: "inset(0 0% 0 0)" }}
      exit={{ scale: 1.4, opacity: 0, filter: "blur(16px)" }}
      transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
    >
      <motion.div
        className="w-[60vw] h-[38vw] border border-[#2D2B3D] rounded-xl relative overflow-hidden flex items-center justify-center"
        style={{ background: "rgba(12,11,20,0.85)", backdropFilter: "blur(12px)" }}
      >
        <motion.div
          className="w-[8vw] h-[8vw] rounded-2xl"
          style={{ background: "linear-gradient(135deg, #2563eb, #7c3aed)", boxShadow: "0 0 30px rgba(99,102,241,0.4)" }}
          initial={{ scale: 0, rotate: -20 }}
          animate={{ scale: 1, rotate: 0 }}
          transition={{ type: "spring", stiffness: 300, damping: 20, delay: 0.15 }}
        />

        <motion.div
          className="absolute inset-0 flex flex-col items-center justify-center"
          style={{ background: "#C0392B" }}
          initial={{ scaleY: 0, originY: "top" }}
          animate={phase >= 1 ? { scaleY: 1 } : { scaleY: 0 }}
          transition={{ duration: 0.12, ease: "easeOut" }}
        >
          <motion.h1
            style={{ fontFamily: "'Bebas Neue', sans-serif", fontSize: "9vw", color: "#F0F0F0", letterSpacing: "0.12em", lineHeight: 1, margin: 0 }}
            initial={{ opacity: 0, y: 40, scale: 0.85 }}
            animate={phase >= 2 ? { opacity: 1, y: 0, scale: 1 } : { opacity: 0, y: 40, scale: 0.85 }}
            transition={{ type: "spring", stiffness: 420, damping: 22 }}
          >
            BLOCKED
          </motion.h1>
          <motion.p
            style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "1.4vw", color: "#0C0B14", fontWeight: 700, marginTop: "1vw", letterSpacing: "0.05em" }}
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

function Scene2() {
  const [phase, setPhase] = useState(0);
  useEffect(() => {
    const t = [
      setTimeout(() => setPhase(1), 250),
      setTimeout(() => setPhase(2), 750),
      setTimeout(() => setPhase(3), 1300),
      setTimeout(() => setPhase(4), 1850),
    ];
    return () => t.forEach(clearTimeout);
  }, []);

  const lines = [
    "Process scan every 500ms — 50+ escape routes killed",
    "Batch kill + firewall rules injected instantly",
    "Task Manager. CMD. PowerShell. All gone.",
  ];

  return (
    <motion.div
      className="absolute inset-0 z-20 flex flex-col items-start justify-center"
      style={{ paddingLeft: "10vw" }}
      initial={{ opacity: 0, x: -80 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: 80, filter: "blur(8px)" }}
      transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
    >
      <motion.h1
        style={{ fontFamily: "'Bebas Neue', sans-serif", fontSize: "13vw", color: "#C0392B", lineHeight: 0.9, marginBottom: "2vw", letterSpacing: "-0.02em" }}
        initial={{ opacity: 0, scale: 1.4, rotateX: 35 }}
        animate={phase >= 1 ? { opacity: 1, scale: 1, rotateX: 0 } : { opacity: 0, scale: 1.4, rotateX: 35 }}
        transition={{ type: "spring", stiffness: 200, damping: 22 }}
      >
        NUCLEAR
      </motion.h1>

      <div style={{ display: "flex", flexDirection: "column", gap: "1.5vw" }}>
        {lines.map((text, i) => (
          <motion.div
            key={i}
            style={{ display: "flex", alignItems: "center", gap: "1vw", overflow: "hidden" }}
            initial={{ x: -30, opacity: 0 }}
            animate={phase >= i + 2 ? { x: 0, opacity: 1 } : { x: -30, opacity: 0 }}
            transition={{ duration: 0.35, ease: "easeOut" }}
          >
            <motion.div
              style={{ width: "4px", background: "#C0392B", flexShrink: 0 }}
              initial={{ height: 0 }}
              animate={phase >= i + 2 ? { height: "2.5vw" } : { height: 0 }}
              transition={{ duration: 0.18 }}
            />
            <p style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "1.8vw", color: "#F0F0F0", margin: 0 }}>
              {text}
            </p>
          </motion.div>
        ))}
      </div>
    </motion.div>
  );
}

function Scene3() {
  const [phase, setPhase] = useState(0);
  useEffect(() => {
    const t = [
      setTimeout(() => setPhase(1), 250),
      setTimeout(() => setPhase(2), 700),
      setTimeout(() => setPhase(3), 1300),
    ];
    return () => t.forEach(clearTimeout);
  }, []);

  return (
    <motion.div
      className="absolute inset-0 z-20 flex flex-col items-center justify-center"
      initial={{ scale: 0.85, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      exit={{ scale: 1.15, opacity: 0 }}
      transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
    >
      <motion.div
        style={{ width: "70vw", height: "38vw", border: "2px solid #2D2B3D", background: "rgba(12,11,20,0.92)", backdropFilter: "blur(16px)", borderRadius: "1.5vw", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", position: "relative", overflow: "hidden" }}
        initial={{ clipPath: "circle(0% at 50% 50%)" }}
        animate={phase >= 1 ? { clipPath: "circle(150% at 50% 50%)" } : { clipPath: "circle(0% at 50% 50%)" }}
        transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
      >
        {/* Grid lines */}
        {[...Array(6)].map((_, i) => (
          <div key={i} style={{ position: "absolute", left: `${(i + 1) * 14}%`, top: 0, bottom: 0, width: "1px", background: "rgba(45,43,61,0.4)" }} />
        ))}
        {[...Array(4)].map((_, i) => (
          <div key={i} style={{ position: "absolute", top: `${(i + 1) * 20}%`, left: 0, right: 0, height: "1px", background: "rgba(45,43,61,0.4)" }} />
        ))}

        <motion.div
          style={{ padding: "0.5vw 1.5vw", background: "#C0392B", position: "absolute", top: "2vw" }}
          initial={{ y: -50, opacity: 0 }}
          animate={phase >= 2 ? { y: 0, opacity: 1 } : { y: -50, opacity: 0 }}
          transition={{ type: "spring", stiffness: 420, damping: 18 }}
        >
          <motion.span
            style={{ fontFamily: "'Bebas Neue', sans-serif", fontSize: "3vw", color: "white", letterSpacing: "0.2em" }}
            animate={{ opacity: [1, 0.6, 1] }}
            transition={{ duration: 1.2, repeat: Infinity }}
          >
            HARD LOCKED
          </motion.span>
        </motion.div>

        <motion.h2
          style={{ fontFamily: "'Bebas Neue', sans-serif", fontSize: "5.5vw", color: "white", letterSpacing: "0.08em", marginTop: "3vw" }}
          initial={{ opacity: 0, y: 20 }}
          animate={phase >= 3 ? { opacity: 1, y: 0 } : { opacity: 0, y: 20 }}
          transition={{ duration: 0.45 }}
        >
          YOUR OS, REPLACED.
        </motion.h2>

        <motion.p
          style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "1.3vw", color: "rgba(240,240,240,0.65)", textAlign: "center", maxWidth: "48vw", marginTop: "1.5vw", lineHeight: 1.6 }}
          initial={{ opacity: 0 }}
          animate={phase >= 3 ? { opacity: 1 } : { opacity: 0 }}
          transition={{ duration: 0.45, delay: 0.2 }}
        >
          100+ Windows processes whitelisted so you can't crash your system —<br />but you can't escape either.
        </motion.p>
      </motion.div>
    </motion.div>
  );
}

function Scene4() {
  const [phase, setPhase] = useState(0);
  useEffect(() => {
    const t = [
      setTimeout(() => setPhase(1), 350),
      setTimeout(() => setPhase(2), 1100),
      setTimeout(() => setPhase(3), 1650),
    ];
    return () => t.forEach(clearTimeout);
  }, []);

  return (
    <motion.div
      className="absolute inset-0 z-20 flex items-center justify-center"
      style={{ background: "#0C0B14" }}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ y: "-100%" }}
      transition={{ duration: 0.45, ease: "easeInOut" }}
    >
      <motion.pre
        style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "1.1vw", color: "rgba(240,240,240,0.18)", position: "absolute", inset: 0, padding: "5vw", zIndex: 0, margin: 0 }}
        initial={{ y: 40, opacity: 0 }}
        animate={phase >= 1 ? { y: 0, opacity: 1 } : { y: 40, opacity: 0 }}
        transition={{ duration: 0.7, ease: "easeOut" }}
      >{`WinEventHook.SetWinEventHook(
  EVENT_SYSTEM_FOREGROUND,
  EVENT_SYSTEM_FOREGROUND,
  IntPtr.Zero,
  eventCallback,
  0, 0,
  WINEVENT_OUTOFCONTEXT
);

// Fires the MILLISECOND a blocked app
// gets focus. No timer. No poll.`}</motion.pre>

      <motion.div
        className="absolute flex flex-col items-center justify-center"
        style={{ width: "100%", height: "100%", background: "#C0392B" }}
        initial={{ scale: 0, borderRadius: "100%" }}
        animate={phase >= 2 ? { scale: 1.6, borderRadius: "0%" } : { scale: 0, borderRadius: "100%" }}
        transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
      >
        <motion.div
          initial={{ opacity: 0, scale: 0.92 }}
          animate={phase >= 3 ? { opacity: 1, scale: 1 } : { opacity: 0, scale: 0.92 }}
          transition={{ duration: 0.35 }}
          style={{ textAlign: "center" }}
        >
          <h2 style={{ fontFamily: "'Bebas Neue', sans-serif", fontSize: "8vw", color: "white", lineHeight: 0.95, letterSpacing: "-0.01em", margin: 0 }}>
            A KILL SWITCH
          </h2>
          <p style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "1.9vw", color: "#0C0B14", fontWeight: 700, marginTop: "1vw", letterSpacing: "0.04em" }}>
            Not a timer. Not a notification.
          </p>
        </motion.div>
      </motion.div>
    </motion.div>
  );
}

function Scene5() {
  const [phase, setPhase] = useState(0);
  useEffect(() => {
    const t = [
      setTimeout(() => setPhase(1), 400),
      setTimeout(() => setPhase(2), 1300),
      setTimeout(() => setPhase(3), 4800),
      setTimeout(() => setPhase(4), 5800),
    ];
    return () => t.forEach(clearTimeout);
  }, []);

  return (
    <motion.div
      className="absolute inset-0 z-20 flex items-center justify-center"
      style={{ background: "#0C0B14" }}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.7 }}
    >
      <AnimatePresence mode="sync">
        {phase < 3 && (
          <motion.div
            key="dashboard"
            className="absolute inset-0 flex items-center justify-center"
            style={{ gap: "4vw" }}
            initial={{ opacity: 0, scale: 0.92 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 1.08, filter: "blur(10px)" }}
            transition={{ duration: 0.7 }}
          >
            {/* Focus Score ring */}
            <motion.div
              style={{ width: "20vw", height: "20vw", borderRadius: "50%", border: "1vw solid #2D2B3D", display: "flex", alignItems: "center", justifyContent: "center", position: "relative" }}
              initial={{ rotate: -90 }}
              animate={phase >= 1 ? { rotate: 0 } : { rotate: -90 }}
              transition={{ duration: 1.1, ease: "easeOut" }}
            >
              <svg style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} viewBox="0 0 100 100">
                <circle cx="50" cy="50" r="45" fill="none" stroke="#C0392B" strokeWidth="5" strokeDasharray="283" strokeDashoffset="5" strokeLinecap="round" transform="rotate(-90 50 50)" />
              </svg>
              <span style={{ fontFamily: "'Bebas Neue', sans-serif", fontSize: "7.5vw", color: "white" }}>98</span>
            </motion.div>

            <div style={{ display: "flex", flexDirection: "column", gap: "1.5vw" }}>
              <motion.div
                style={{ background: "rgba(45,43,61,0.45)", padding: "1.5vw 2vw", borderRadius: "0.8vw", border: "1px solid #2D2B3D" }}
                initial={{ opacity: 0, x: 40 }}
                animate={phase >= 2 ? { opacity: 1, x: 0 } : { opacity: 0, x: 40 }}
                transition={{ duration: 0.45 }}
              >
                <div style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "0.9vw", color: "rgba(240,240,240,0.5)", textTransform: "uppercase", letterSpacing: "0.1em" }}>Current Streak</div>
                <div style={{ fontFamily: "'Bebas Neue', sans-serif", fontSize: "4vw", color: "#C0392B", lineHeight: 1.1 }}>42 DAYS</div>
              </motion.div>

              <motion.div
                style={{ background: "rgba(45,43,61,0.45)", padding: "1.5vw 2vw", borderRadius: "0.8vw", border: "1px solid #2D2B3D" }}
                initial={{ opacity: 0, x: 40 }}
                animate={phase >= 2 ? { opacity: 1, x: 0 } : { opacity: 0, x: 40 }}
                transition={{ duration: 0.45, delay: 0.1 }}
              >
                <div style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "0.9vw", color: "rgba(240,240,240,0.5)", textTransform: "uppercase", letterSpacing: "0.1em" }}>Peak Hour</div>
                <div style={{ fontFamily: "'Bebas Neue', sans-serif", fontSize: "4vw", color: "white", lineHeight: 1.1 }}>9AM–11AM</div>
              </motion.div>

              <motion.div
                style={{ background: "rgba(192,57,43,0.15)", padding: "1vw 2vw", borderRadius: "0.8vw", border: "1px solid rgba(192,57,43,0.4)" }}
                initial={{ opacity: 0, x: 40 }}
                animate={phase >= 2 ? { opacity: 1, x: 0 } : { opacity: 0, x: 40 }}
                transition={{ duration: 0.45, delay: 0.2 }}
              >
                <div style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "0.9vw", color: "rgba(240,240,240,0.5)", textTransform: "uppercase", letterSpacing: "0.1em" }}>Blocked Today</div>
                <div style={{ fontFamily: "'Bebas Neue', sans-serif", fontSize: "4vw", color: "#C0392B", lineHeight: 1.1 }}>47 ATTEMPTS</div>
              </motion.div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence mode="sync">
        {phase >= 4 && (
          <motion.div
            key="lockup"
            className="absolute inset-0 flex flex-col items-center justify-center"
            style={{ background: "#0C0B14" }}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.9 }}
          >
            <motion.h1
              style={{ fontFamily: "'Bebas Neue', sans-serif", fontSize: "14vw", color: "white", lineHeight: 0.9, letterSpacing: "-0.01em", margin: 0 }}
              initial={{ scale: 0.82, filter: "blur(18px)" }}
              animate={{ scale: 1, filter: "blur(0px)" }}
              transition={{ duration: 1.4, ease: "easeOut" }}
            >
              FOCUSFLOW
            </motion.h1>
            <motion.div
              style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "1.9vw", color: "#C0392B", marginTop: "1.2vw", letterSpacing: "0.18em", fontWeight: 700, textTransform: "uppercase" }}
              initial={{ opacity: 0, y: 18 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.5 }}
            >
              ENFORCED FOCUS. NOT SUGGESTED.
            </motion.div>
            <motion.div
              style={{ width: "8vw", height: "2px", background: "#C0392B", marginTop: "2vw" }}
              initial={{ scaleX: 0 }}
              animate={{ scaleX: 1 }}
              transition={{ duration: 0.6, delay: 0.9 }}
            />
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

export function PromoVideo() {
  const scene = useSceneCycle();

  const sceneAccentLine = {
    left: ["0%", "0%", "20%", "0%", "40%"][scene],
    width: ["100%", "30%", "60%", "10%", "20%"][scene],
    top: ["5%", "15%", "85%", "50%", "50%"][scene],
  };

  const circlePos = {
    x: ["70vw", "10vw", "50vw", "80vw", "35vw"][scene],
    y: ["-10vh", "60vh", "-20vh", "40vh", "15vh"][scene],
    scale: [1, 1.5, 0.8, 2, 1][scene],
  };

  return (
    <div style={{ position: "relative", width: "100%", height: "100vh", overflow: "hidden", background: "#0C0B14" }}>
      {/* Ambient background pulse */}
      <motion.div
        style={{ position: "absolute", width: "50vw", height: "50vw", borderRadius: "50%", background: "radial-gradient(circle, rgba(192,57,43,0.12), transparent)", filter: "blur(60px)", pointerEvents: "none", zIndex: 0 }}
        animate={{ x: ["-20%", "60%", "10%"], y: ["10%", "50%", "20%"], scale: [1, 1.3, 0.9] }}
        transition={{ duration: 14, repeat: Infinity, ease: "easeInOut" }}
      />
      <motion.div
        style={{ position: "absolute", right: 0, bottom: 0, width: "35vw", height: "35vw", borderRadius: "50%", background: "radial-gradient(circle, rgba(45,43,61,0.5), transparent)", filter: "blur(50px)", pointerEvents: "none", zIndex: 0 }}
        animate={{ x: ["10%", "-30%", "5%"], y: ["-10%", "-40%", "-15%"] }}
        transition={{ duration: 18, repeat: Infinity, ease: "easeInOut" }}
      />

      {/* Persistent red accent line */}
      <motion.div
        style={{ position: "absolute", height: "2px", background: "#C0392B", zIndex: 10 }}
        animate={sceneAccentLine}
        transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
      />

      {/* Floating circuit ring */}
      <motion.div
        style={{ position: "absolute", width: "28vw", height: "28vw", border: "2px solid rgba(45,43,61,0.35)", borderRadius: "50%", zIndex: 0 }}
        animate={circlePos}
        transition={{ duration: 1.4, ease: "easeInOut" }}
      />

      {/* Scene label (top-right watermark) */}
      <div style={{ position: "absolute", top: "2vw", right: "2vw", zIndex: 30, fontFamily: "'JetBrains Mono', monospace", fontSize: "0.85vw", color: "rgba(240,240,240,0.3)", letterSpacing: "0.1em" }}>
        {["HOOK", "NUCLEAR", "KIOSK", "ENGINE", "OUTRO"][scene]} · {scene + 1}/5
      </div>

      <AnimatePresence mode="popLayout">
        {scene === 0 && <Scene1 key="hook" />}
        {scene === 1 && <Scene2 key="nuclear" />}
        {scene === 2 && <Scene3 key="kiosk" />}
        {scene === 3 && <Scene4 key="engine" />}
        {scene === 4 && <Scene5 key="outro" />}
      </AnimatePresence>
    </div>
  );
}
