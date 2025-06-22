# boingDemo TODO

## Native Audio Implementation

- [x] ~~Add C-interop audio library dependency~~ (Used platform commands instead)
- [x] Implement native `playSound` function (Cross-platform using afplay/aplay/powershell)
- [x] Test audio on macOS native target (Uses afplay command)
- [x] Test audio on Linux native target (Uses aplay command)
- [x] Verify resource loading works with embedded audio files (JVM implementation complete)

## Platform Testing

- [ ] Verify consistent bounce physics across all platforms
- [ ] Test window resizing behavior
- [ ] Confirm audio timing synchronization

## Build System

- [ ] Configure native audio library linking
- [ ] Add platform-specific build instructions
- [ ] Set up automated testing for all targets