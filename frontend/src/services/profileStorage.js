// Browser-local profile storage helpers for the frontend-only personal center.

export const PROFILE_STORAGE_KEY = "trade-agent-profile";

export const DEFAULT_AVATARS = [
  {
    id: "steady",
    label: "稳健",
    text: "稳",
    background: "#146f62",
    color: "#ffffff",
  },
  {
    id: "growth",
    label: "成长",
    text: "成",
    background: "#195fc9",
    color: "#ffffff",
  },
  {
    id: "value",
    label: "价值",
    text: "价",
    background: "#7a4b16",
    color: "#ffffff",
  },
  {
    id: "quant",
    label: "量化",
    text: "量",
    background: "#7f56d9",
    color: "#ffffff",
  },
];

export const DEFAULT_PROFILE = {
  username: "",
  email: "",
  bio: "",
  avatarType: "default",
  avatarId: DEFAULT_AVATARS[0].id,
  avatarDataUrl: "",
};

export function readProfile() {
  // Restore the browser-local profile while tolerating invalid saved data.
  try {
    const savedProfile = JSON.parse(
      window.localStorage.getItem(PROFILE_STORAGE_KEY) || "{}",
    );
    return { ...DEFAULT_PROFILE, ...savedProfile };
  } catch {
    return { ...DEFAULT_PROFILE };
  }
}

export function writeProfile(profile) {
  // Persist a complete profile snapshot in browser storage.
  window.localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profile));
}

export function clearProfile() {
  // Remove the browser-local profile snapshot.
  window.localStorage.removeItem(PROFILE_STORAGE_KEY);
}

export function findDefaultAvatar(avatarId) {
  // Return the matching built-in avatar, falling back to the first option.
  return (
    DEFAULT_AVATARS.find((avatar) => avatar.id === avatarId) || DEFAULT_AVATARS[0]
  );
}

export function resolveAvatar(profile, displayName = "") {
  // Convert saved avatar fields into render-ready avatar information.
  if (profile.avatarType === "uploaded" && profile.avatarDataUrl) {
    return { type: "image", src: profile.avatarDataUrl, alt: displayName };
  }
  return { type: "default", ...findDefaultAvatar(profile.avatarId) };
}
