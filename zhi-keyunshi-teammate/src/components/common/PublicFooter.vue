<template>
  <footer class="public-footer">
    <p>{{ copyrightText }}</p>
  </footer>
</template>

<script setup>
const currentYear = new Date().getFullYear();
const copyrightText = `Copyright © ${currentYear} 智课云师. All Rights Reserved.`;
</script>

<style scoped lang="scss">
.public-footer {
  width: 100%;
  text-align: center;
  padding: 16px 12px 18px;
  color: #6f87a0;
  font-size: 13px;
  letter-spacing: 0.2px;
}
</style>
