import { ref, watch, effectScope } from "vue";
import { useAuth } from "./useAuth";
import { portalApi } from "../api/portal";
import { emptyPreferences } from "../utils/portal";
import type { Preferences } from "../model/portal";

const GUEST_KEY = "badge:portal:guest:v1";
const preferences = ref<Preferences>(emptyPreferences());
const preferencesError = ref("");
const preferencesBusy = ref(false);
let initialized = false;
let generation = 0;

function guestPreferences(): Preferences {
  try {
    const data = JSON.parse(localStorage.getItem(GUEST_KEY) || "{}");
    return Object.fromEntries(
      Object.keys(emptyPreferences()).map((key) => [
        key,
        Array.isArray(data[key])
          ? data[key].filter(
              (x: unknown) => typeof x === "string" && x.length > 0,
            )
          : [],
      ]),
    ) as unknown as Preferences;
  } catch {
    return emptyPreferences();
  }
}

export function usePortalPreferences() {
  const { user } = useAuth();
  async function load() {
    const current = ++generation;
    preferencesBusy.value = true;
    preferencesError.value = "";
    preferences.value = emptyPreferences();
    try {
      const value = user.value
        ? await portalApi.preferences()
        : guestPreferences();
      if (current === generation) preferences.value = value;
    } catch {
      if (current === generation)
        preferencesError.value = "账号偏好加载失败，请重试后再修改。";
    } finally {
      if (current === generation) preferencesBusy.value = false;
    }
  }
  if (!initialized) {
    initialized = true;
    // 账号监听属于整个门户，切换页面后仍需处理登录、退出及账号变更。
    effectScope(true).run(() =>
      watch(() => user.value?.username, load, { immediate: true }),
    );
  }

  async function toggle(key: keyof Preferences, value: string) {
    if (preferencesBusy.value || preferencesError.value) return;
    const current = generation;
    const before = preferences.value;
    const list = before[key];
    const next = {
      ...before,
      [key]: list.includes(value)
        ? list.filter((x) => x !== value)
        : [...list, value],
    };
    preferencesBusy.value = true;
    try {
      if (user.value) await portalApi.savePreferences(next);
      else localStorage.setItem(GUEST_KEY, JSON.stringify(next));
      if (current === generation) preferences.value = next;
    } catch {
      if (current === generation)
        preferencesError.value =
          "偏好保存失败，本次修改未保存。点击重试重新加载。";
    } finally {
      if (current === generation) preferencesBusy.value = false;
    }
  }
  return {
    preferences,
    preferencesBusy,
    preferencesError,
    toggle,
    reloadPreferences: load,
  };
}
