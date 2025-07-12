#ifndef SIMD_H
#define SIMD_H
#ifdef __cplusplus
extern "C" {
#endif

// Find all occurrences of a byte value in a buffer
typedef struct { int* indices; int count; } SimdFindResult;
SimdFindResult simd_find_byte(const unsigned char* data, int length, unsigned char target, int offset);

// Find any of multiple byte values
typedef struct { int* indices; int count; } SimdFindAnyResult;
SimdFindAnyResult simd_find_any_byte(const unsigned char* data, int length, const unsigned char* targets, int num_targets, int offset);

// Parallel string comparison
typedef struct { int* matches; int count; } SimdCompareResult;
SimdCompareResult simd_compare_bytes(const unsigned char* data, int length, const unsigned char* pattern, int pattern_length, const int* positions, int num_positions);

// Population count
int simd_popcount(const int* bitmap, int length);

// Gather bytes from positions
typedef struct { unsigned char* bytes; int count; } SimdGatherResult;
SimdGatherResult simd_gather_bytes(const unsigned char* data, int length, const int* positions, int num_positions);

// Capabilities struct
typedef struct {
    int vectorBits;
    int hasPopcount;
    int hasGather;
    int hasMaskOps;
    int hasVariableLength;
    const char* name;
} SimdCapabilitiesC;

SimdCapabilitiesC simd_get_capabilities();

#ifdef __cplusplus
}
#endif
#endif // SIMD_H 