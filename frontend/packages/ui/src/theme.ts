import { reactive, watch } from "vue";

export type ColorMode = "system" | "light" | "dark";
export type LayoutStyle = "islands" | "classic";
export const accentColors = {
  blue: "#2563eb",
  violet: "#7c3aed",
  green: "#15803d",
  rose: "#be185d",
  orange: "#c2410c",
} as const;
export const darkAccentColors = {
  blue: "#60a5fa",
  violet: "#a78bfa",
  green: "#4ade80",
  rose: "#fb7185",
  orange: "#fb923c",
} as const;
interface ThemePreferences {
  mode: ColorMode;
  accent: keyof typeof accentColors;
  radius: number;
  compact: boolean;
  layoutStyle: LayoutStyle;
}
const storageKey = "cloudlane.theme";
function preferences(raw: string | null): ThemePreferences {
  let saved: Record<string, unknown> = {};
  try {
    const value = JSON.parse(raw || "{}");
    if (value && typeof value === "object" && !Array.isArray(value))
      saved = value;
  } catch {
    /* Unsupported or corrupt preferences use the defaults. */
  }
  return {
    mode: (["system", "light", "dark"].includes(saved.mode as string)
      ? saved.mode
      : "system") as ColorMode,
    accent: (typeof saved.accent === "string" &&
    Object.hasOwn(accentColors, saved.accent)
      ? saved.accent
      : "blue") as keyof typeof accentColors,
    radius: [0, 0.5, 0.75, 1].includes(saved.radius as number)
      ? (saved.radius as number)
      : 0.75,
    compact: saved.compact === true,
    layoutStyle: (["islands", "classic"].includes(saved.layoutStyle as string)
      ? saved.layoutStyle
      : "islands") as LayoutStyle,
  };
}
const saved = (() => {
  try {
    return localStorage.getItem(storageKey);
  } catch {
    return null;
  }
})();
export const theme = reactive(preferences(saved));
const media = window.matchMedia("(prefers-color-scheme: dark)");
let initialized = false;
let syncing = false;
function apply() {
  const root = document.documentElement;
  const dark =
    theme.mode === "dark" || (theme.mode === "system" && media.matches);
  root.classList.toggle("dark", dark);
  root.style.setProperty(
    "--primary",
    (dark ? darkAccentColors : accentColors)[theme.accent],
  );
  root.style.setProperty("--primary-foreground", dark ? "#101114" : "#ffffff");
  root.style.setProperty(
    "--ring",
    (dark ? darkAccentColors : accentColors)[theme.accent],
  );
  root.style.setProperty("--radius", theme.radius + "rem");
  root.dataset.density = theme.compact ? "compact" : "comfortable";
  root.dataset.layout = theme.layoutStyle;
}
export function initTheme() {
  if (initialized) return;
  initialized = true;
  apply();
  media.addEventListener("change", apply);
  watch(
    theme,
    () => {
      apply();
      if (!syncing) {
        try {
          localStorage.setItem(storageKey, JSON.stringify(theme));
        } catch {
          /* Keep the active appearance when storage is unavailable. */
        }
      }
    },
    { flush: "sync" },
  );
  window.addEventListener("storage", (event) => {
    if (event.key !== storageKey && event.key !== null) return;
    syncing = true;
    Object.assign(theme, preferences(event.newValue));
    syncing = false;
    apply();
  });
}
