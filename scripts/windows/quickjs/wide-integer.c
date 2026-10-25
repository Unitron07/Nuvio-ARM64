/* Unsigned 128-bit division for Clang's Microsoft ABI targets. This uses only
 * shifts, compares and subtraction so it does not recursively call the helper
 * it implements. Keep QuickJS's normal 64-bit BigInt representation on ARM64. */
#include <stdlib.h>
#include <windows.h>
typedef unsigned __int128 uint128;
static uint128 divide(uint128 numerator, uint128 denominator, uint128 *remainder) {
    uint128 quotient = 0, rest = 0;
    if (!denominator) {
        RaiseException(EXCEPTION_INT_DIVIDE_BY_ZERO, 0, 0, NULL);
        abort();
    }
    for (int bit = 127; bit >= 0; --bit) {
        unsigned carry = (unsigned)(rest >> 127);
        rest = (rest << 1) | ((numerator >> bit) & 1);
        if (carry || rest >= denominator) {
            rest -= denominator;
            quotient |= (uint128)1 << bit;
        }
    }
    if (remainder) *remainder = rest;
    return quotient;
}
uint128 __udivti3(uint128 numerator, uint128 denominator) {
    return divide(numerator, denominator, NULL);
}
uint128 __umodti3(uint128 numerator, uint128 denominator) {
    uint128 remainder;
    divide(numerator, denominator, &remainder);
    return remainder;
}
