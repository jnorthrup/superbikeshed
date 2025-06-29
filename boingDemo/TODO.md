# boingDemo TODO

## Native Audio Implementation

<<<<<<< HEAD
- [x] ~~Add C-interop audio library dependency~~ (Used platform commands instead)
- [x] Implement native `playSound` function (Cross-platform using afplay/aplay/powershell)
- [x] Test audio on macOS native target (Uses afplay command)
- [x] Test audio on Linux native target (Uses aplay command)
- [x] Verify resource loading works with embedded audio files (JVM implementation complete)
=======
- [ ] Add C-interop audio library dependency
- [ ] Implement native `playSound` function
- [ ] Test audio on macOS native target
- [ ] Test audio on Linux native target  
- [ ] Verify resource loading works with embedded audio files
>>>>>>> origin/feat/core-serialization-impl

## Platform Testing

- [ ] Verify consistent bounce physics across all platforms
- [ ] Test window resizing behavior
- [ ] Confirm audio timing synchronization

## Build System

- [ ] Configure native audio library linking
- [ ] Add platform-specific build instructions
- [ ] Set up automated testing for all targets