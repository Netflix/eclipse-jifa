<!--
    Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation

    See the NOTICE file(s) distributed with this work for additional
    information regarding copyright ownership.

    This program and the accompanying materials are made available under the
    terms of the Eclipse Public License 2.0 which is available at
    http://www.eclipse.org/legal/epl-2.0

    SPDX-License-Identifier: EPL-2.0
 -->
<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{
  threads: {
    tid: string;
    name: string;
    state: string;
    stackDepth: number;
    frames: any[];
    cpuTime: string;
    elapsedTime: string;
  }[];
}>();

const emit = defineEmits(['row-click']);

const selectedThread = ref(null);

function onRowClick(row) {
  emit('row-click', row);
  selectedThread.value = row;
}

function rowClassName({ row, rowIndex }) {
  if (selectedThread.value) {
    return row === selectedThread.value ? 'selected-row' : '';
  } else {
    return rowIndex === 0 ? 'selected-row' : '';
  }
}
</script>

<template>
  <el-table :data="props.threads" stripe @row-click="onRowClick" :row-class-name="rowClassName">
    <el-table-column prop="name" label="Name" sortable />
    <el-table-column prop="state" label="State" sortable />
    <el-table-column prop="stackDepth" label="Stack Depth" sortable />
    <el-table-column prop="cpuTime" label="CPU Time (ms)" sortable />
    <el-table-column prop="elapsedTime" label="Elapsed Time (ms)" sortable />
  </el-table>

  <div v-if="selectedThread" class="thread-details">
  </div>
</template>

<style scoped>
.selected-row {
  border: 2px solid #409EFF;
}
</style>