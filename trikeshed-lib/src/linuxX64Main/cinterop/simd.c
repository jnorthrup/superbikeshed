#include "include/simd.h"
#include <stdlib.h>
#include <string.h>

SimdFindResult simd_find_byte(const unsigned char* data, int length, unsigned char target, int offset) {
    SimdFindResult result;
    result.indices = (int*)malloc(length * sizeof(int));
    result.count = 0;
    for (int i = offset; i < length; i++) {
        if (data[i] == target) {
            result.indices[result.count++] = i;
        }
    }
    return result;
}

SimdFindAnyResult simd_find_any_byte(const unsigned char* data, int length, const unsigned char* targets, int num_targets, int offset) {
    SimdFindAnyResult result;
    result.indices = (int*)malloc(length * sizeof(int));
    result.count = 0;
    for (int i = offset; i < length; i++) {
        for (int j = 0; j < num_targets; j++) {
            if (data[i] == targets[j]) {
                result.indices[result.count++] = i;
                break;
            }
        }
    }
    return result;
}

SimdCompareResult simd_compare_bytes(const unsigned char* data, int length, const unsigned char* pattern, int pattern_length, const int* positions, int num_positions) {
    SimdCompareResult result;
    result.matches = (int*)malloc(num_positions * sizeof(int));
    result.count = 0;
    for (int i = 0; i < num_positions; i++) {
        int pos = positions[i];
        int match = 1;
        if (pos + pattern_length > length) {
            match = 0;
        } else {
            for (int j = 0; j < pattern_length; j++) {
                if (data[pos + j] != pattern[j]) {
                    match = 0;
                    break;
                }
            }
        }
        result.matches[i] = match;
        result.count++;
    }
    return result;
}

int simd_popcount(const int* bitmap, int length) {
    int count = 0;
    for (int i = 0; i < length; i++) {
        int word = bitmap[i];
        while (word != 0) {
            count += word & 1;
            word = word >> 1;
        }
    }
    return count;
}

SimdGatherResult simd_gather_bytes(const unsigned char* data, int length, const int* positions, int num_positions) {
    SimdGatherResult result;
    result.bytes = (unsigned char*)malloc(num_positions * sizeof(unsigned char));
    result.count = num_positions;
    for (int i = 0; i < num_positions; i++) {
        int pos = positions[i];
        result.bytes[i] = (pos < length) ? data[pos] : 0;
    }
    return result;
}

SimdCapabilitiesC simd_get_capabilities() {
    SimdCapabilitiesC caps;
    caps.vectorBits = 256;
    caps.hasPopcount = 1;
    caps.hasGather = 1;
    caps.hasMaskOps = 1;
    caps.hasVariableLength = 0;
    caps.name = "Linux x86 (SSE/AVX/AVX2)";
    return caps;
} 