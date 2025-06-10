#!/usr/bin/env node
const { spawn } = require('child_process');
const path = require('path');

const jarPath = path.join(__dirname, 'k2script.jar');
const args = process.argv.slice(2);

const k2scriptProcess = spawn('java', ['--enable-native-access=ALL-UNNAMED', '-jar', jarPath, ...args], { stdio: 'inherit' });

k2scriptProcess.on('close', (code) => {
  process.exit(code);
});
