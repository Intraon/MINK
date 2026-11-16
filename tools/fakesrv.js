// Falsk rtl_tcp-server, så appen kan prøves i en emulator uden dongle.
//   node tools/fakesrv.js [signal i MHz=869.6125] [periode i s=4] [varighed i s=0.4] [LoRa-kanal i MHz=869.618]
// Sender støj, et smalt FSK-signal med den valgte rytme og LoRa-pakker (62,5 kHz, SF8, rigtig symboltid)
// hvert 7. sekund. Tunerfrekvensen læses fra appens egen kommando, så intet skal passe sammen på forhånd.
const net = require('net')
const [sigMhz = 869.6125, period = 4, on = 0.4, loraMhz = 869.618] = process.argv.slice(2).map(Number)
const RATE = 1024000, BW = 62500, TS = 256 / BW
net.createServer(s => {
  const head = Buffer.alloc(12); head.write('RTL0'); head.writeUInt32BE(5, 4); head.writeUInt32BE(29, 8); s.write(head)
  let tune = 0, n = 0, phS = 0, phL = 0, bit = 1, sym = -1, shift = 0, rest = Buffer.alloc(0)
  s.on('data', d => {
    rest = Buffer.concat([rest, d])
    for (; rest.length >= 5; rest = rest.subarray(5)) {
      const c = rest[0], v = rest.readInt32BE(1)
      if (c === 1) tune = v >>> 0
      console.log({ 1: 'frekvens', 2: 'sample rate', 3: 'gain-tilstand', 4: 'gain (0,1 dB)', 5: 'ppm', 8: 'AGC' }[c] || 'kommando ' + c, c === 1 ? tune : v)
    }
  })
  s.on('error', () => {})
  const iv = setInterval(() => {
    const cnt = RATE / 20, b = Buffer.alloc(cnt * 2)
    for (let i = 0; i < cnt; i++, n++) {
      const t = n / RATE
      let I = (Math.random() + Math.random() + Math.random() - 1.5) * 6, Q = (Math.random() + Math.random() + Math.random() - 1.5) * 6
      if (tune && t % period < on) {
        if (n % 853 === 0) bit = Math.random() < 0.5 ? 1 : -1
        phS += 2 * Math.PI * (sigMhz * 1e6 - tune + bit * 1200) / RATE
        I += 20 * Math.cos(phS); Q += 20 * Math.sin(phS)
      }
      const tl = t % 7
      if (tune && tl > 5 && tl < 5.8) {
        const k = Math.floor(tl / TS)
        if (k !== sym) { sym = k; shift = Math.random() }
        phL += 2 * Math.PI * (loraMhz * 1e6 - tune - BW / 2 + ((shift + tl / TS - k) % 1) * BW) / RATE
        I += 15 * Math.cos(phL); Q += 15 * Math.sin(phL)
      }
      b[2 * i] = Math.max(0, Math.min(255, Math.round(127.4 + I))); b[2 * i + 1] = Math.max(0, Math.min(255, Math.round(127.4 + Q)))
    }
    if (!s.destroyed) s.write(b)
  }, 50)
  s.on('close', () => clearInterval(iv))
}).listen(1234, () => console.log(`rtl_tcp på :1234 · signal ${sigMhz} MHz, ${on} s hvert ${period}. s · LoRa på ${loraMhz} MHz`))
