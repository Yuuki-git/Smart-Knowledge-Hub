/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./index.html", "./src/**/*.{js,vue}"],
  theme: {
    extend: {
      colors: {
        ink: "#0b0d12",
        midnight: "#0f172a",
        haze: "#94a3b8",
        ember: "#ff6a3d",
        flux: "#48ffbc",
        aurora: "#55b6ff"
      },
      boxShadow: {
        glow: "0 0 40px rgba(72, 255, 188, 0.18)",
        soft: "0 10px 30px rgba(15, 23, 42, 0.3)"
      },
      borderRadius: {
        xl: "1.25rem"
      }
    }
  },
  plugins: []
};
