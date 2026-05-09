<template>
  <router-view v-slot="{ Component }">
    <transition name="page-fade-slide" mode="out-in">
      <component :is="Component" :key="route.fullPath" />
    </transition>
  </router-view>
</template>

<script setup>
import { useRoute } from "vue-router";

const route = useRoute();
</script>

<style>
.page-fade-slide-enter-active,
.page-fade-slide-leave-active {
  transition: transform 0.42s ease, opacity 0.42s ease, filter 0.42s ease;
  transform-origin: center;
}

.page-fade-slide-enter-from {
  opacity: 0;
  transform: perspective(900px) rotateY(-8deg) translateX(18px);
  filter: blur(1px);
}

.page-fade-slide-leave-to {
  opacity: 0;
  transform: perspective(900px) rotateY(8deg) translateX(-18px);
  filter: blur(1px);
}
</style>
