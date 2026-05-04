<script setup>
// Frontend-only personal center for editing local user profile details.

import { computed, reactive, ref } from "vue";
import {
  clearProfile,
  DEFAULT_AVATARS,
  DEFAULT_PROFILE,
  readProfile,
  resolveAvatar,
  writeProfile,
} from "../services/profileStorage";

const profile = reactive({ ...DEFAULT_PROFILE });
const saveStatus = ref("");

function loadProfile() {
  // Restore local profile data before rendering the personal center.
  Object.assign(profile, readProfile());
}

function saveProfile() {
  // Persist the profile in the browser until a backend user API exists.
  const trimmedProfile = {
    username: profile.username.trim(),
    email: profile.email.trim(),
    bio: profile.bio.trim(),
    avatarType: profile.avatarType,
    avatarId: profile.avatarId,
    avatarDataUrl: profile.avatarDataUrl,
  };
  Object.assign(profile, trimmedProfile);
  writeProfile(trimmedProfile);
  saveStatus.value = "已保存";
}

function resetProfile() {
  // Clear the local profile form and remove the browser copy.
  Object.assign(profile, DEFAULT_PROFILE);
  clearProfile();
  saveStatus.value = "已清空";
}

function selectDefaultAvatar(avatarId) {
  // Switch to a built-in avatar option and preserve any uploaded image for later.
  profile.avatarType = "default";
  profile.avatarId = avatarId;
  saveStatus.value = "";
}

function readFileDataUrl(file) {
  // Read a local file as a browser data URL for avatar processing.
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ""));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

function loadImage(dataUrl) {
  // Load the selected image so it can be cropped into a compact avatar.
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = reject;
    image.src = dataUrl;
  });
}

async function createAvatarDataUrl(file) {
  // Crop and resize the uploaded image before storing it in localStorage.
  const dataUrl = await readFileDataUrl(file);
  const image = await loadImage(dataUrl);
  const avatarSize = 180;
  const canvas = document.createElement("canvas");
  const context = canvas.getContext("2d");
  const sourceSize = Math.min(image.width, image.height);
  const sourceX = (image.width - sourceSize) / 2;
  const sourceY = (image.height - sourceSize) / 2;
  canvas.width = avatarSize;
  canvas.height = avatarSize;
  context.drawImage(
    image,
    sourceX,
    sourceY,
    sourceSize,
    sourceSize,
    0,
    0,
    avatarSize,
    avatarSize,
  );
  return canvas.toDataURL("image/jpeg", 0.9);
}

async function handleAvatarUpload(event) {
  // Read a selected image file into browser-local profile state.
  const [file] = event.target.files || [];
  if (!file) return;
  if (!file.type.startsWith("image/")) {
    saveStatus.value = "请选择图片文件";
    event.target.value = "";
    return;
  }
  try {
    profile.avatarType = "uploaded";
    profile.avatarDataUrl = await createAvatarDataUrl(file);
    saveStatus.value = "头像待保存";
  } catch {
    saveStatus.value = "头像读取失败";
  }
}

function useUploadedAvatar() {
  // Re-select the uploaded avatar after browsing built-in defaults.
  if (!profile.avatarDataUrl) return;
  profile.avatarType = "uploaded";
  saveStatus.value = "";
}

const displayName = computed(() => {
  // Prefer the configured username and fall back to a neutral empty state.
  return profile.username.trim() || "未设置用户名";
});

const profileAvatar = computed(() => {
  // Resolve the selected avatar into render-ready preview data.
  return resolveAvatar(profile, displayName.value);
});

const profileAvatarStyle = computed(() => {
  // Apply built-in avatar colors without affecting uploaded images.
  if (profileAvatar.value.type === "image") return {};
  return {
    background: profileAvatar.value.background,
    color: profileAvatar.value.color,
  };
});

loadProfile();

defineEmits(["back"]);
</script>

<template>
  <section class="profile-page">
    <header class="profile-page-header">
      <button class="batch-back-button" type="button" @click="$emit('back')">
        返回
      </button>
      <div>
        <span>账户资料</span>
        <h1>个人中心</h1>
        <p>{{ saveStatus || "前端本地资料" }}</p>
      </div>
    </header>

    <div class="profile-layout">
      <section class="profile-card profile-overview">
        <div class="profile-avatar" :style="profileAvatarStyle">
          <img
            v-if="profileAvatar.type === 'image'"
            :alt="displayName"
            :src="profileAvatar.src"
          />
          <span v-else>{{ profileAvatar.text }}</span>
        </div>
        <div class="profile-meta">
          <span>当前用户</span>
          <h2>{{ displayName }}</h2>
          <p>{{ profile.email || "未设置邮箱" }}</p>
          <p>{{ profile.bio || "未设置简介" }}</p>
        </div>
      </section>

      <form class="profile-card profile-form" @submit.prevent="saveProfile">
        <div class="profile-field">
          <label>头像</label>
          <div class="avatar-picker">
            <label
              v-for="avatar in DEFAULT_AVATARS"
              :key="avatar.id"
              :class="[
                'avatar-option',
                {
                  active:
                    profile.avatarType === 'default' &&
                    profile.avatarId === avatar.id,
                },
              ]"
            >
              <input
                v-model="profile.avatarId"
                name="default-avatar"
                type="radio"
                :value="avatar.id"
                @change="selectDefaultAvatar(avatar.id)"
              />
              <span :style="{ background: avatar.background, color: avatar.color }">
                {{ avatar.text }}
              </span>
              <small>{{ avatar.label }}</small>
            </label>
          </div>
        </div>

        <div class="profile-field">
          <label for="profile-avatar-upload">上传头像</label>
          <div class="avatar-upload-row">
            <input
              id="profile-avatar-upload"
              accept="image/*"
              type="file"
              @change="handleAvatarUpload"
            />
            <button
              class="secondary-button"
              type="button"
              :disabled="!profile.avatarDataUrl"
              @click="useUploadedAvatar"
            >
              使用上传头像
            </button>
          </div>
        </div>

        <div class="profile-field">
          <label for="profile-username">用户名</label>
          <input
            id="profile-username"
            v-model="profile.username"
            autocomplete="name"
            maxlength="32"
            placeholder="输入用户名"
          />
        </div>

        <div class="profile-field">
          <label for="profile-email">邮箱</label>
          <input
            id="profile-email"
            v-model="profile.email"
            autocomplete="email"
            maxlength="80"
            placeholder="name@example.com"
            type="email"
          />
        </div>

        <div class="profile-field">
          <label for="profile-bio">个人简介</label>
          <textarea
            id="profile-bio"
            v-model="profile.bio"
            maxlength="240"
            placeholder="记录你的交易偏好、关注方向或风险纪律"
            rows="6"
          ></textarea>
        </div>

        <div class="profile-actions">
          <button class="backtest-run" type="submit">保存资料</button>
          <button class="secondary-button" type="button" @click="resetProfile">
            清空
          </button>
        </div>
      </form>
    </div>
  </section>
</template>
