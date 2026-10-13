import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],

  theme: {
    extend: {
      colors: {
        iron: "#241b0e",
        oak: "#704e2e",
        orange: "#bf6715",
        "orange-light": "#d98227",
        cream: "#f3eadb",
      },

      fontFamily: {
        industrial: ["Arial Narrow", "Arial", "sans-serif"],
      },
    },
  },

  plugins: [],
};

export default config;
