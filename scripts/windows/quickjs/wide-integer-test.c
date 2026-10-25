#include <stdint.h>
#include <stdio.h>
typedef unsigned __int128 uint128;
uint128 __udivti3(uint128, uint128);
uint128 __umodti3(uint128, uint128);
/* Golden vectors generated with arbitrary-precision integer arithmetic. */
static const uint64_t cases[][8] = {
    {0x0ULL, 0x0ULL, 0x0ULL, 0x1ULL, 0x0ULL, 0x0ULL, 0x0ULL, 0x0ULL},
    {0xffffffffffffffffULL, 0xffffffffffffffffULL, 0x0ULL, 0x1ULL, 0xffffffffffffffffULL, 0xffffffffffffffffULL, 0x0ULL, 0x0ULL},
    {0xffffffffffffffffULL, 0xffffffffffffffffULL, 0xffffffffffffffffULL, 0xffffffffffffffffULL, 0x0ULL, 0x1ULL, 0x0ULL, 0x0ULL},
    {0xffffffffffffffffULL, 0xffffffffffffffffULL, 0x8000000000000000ULL, 0x1ULL, 0x0ULL, 0x1ULL, 0x7fffffffffffffffULL, 0xfffffffffffffffeULL},
    {0xffffffffffffffffULL, 0xffffffffffffffffULL, 0x0ULL, 0xffffffffffffffffULL, 0x1ULL, 0x1ULL, 0x0ULL, 0x0ULL},
    {0x8000000000000000ULL, 0x0ULL, 0x1ULL, 0x1ULL, 0x0ULL, 0x7fffffffffffffffULL, 0x0ULL, 0x8000000000000001ULL},
    {0x647d1a5ec6a344ULL, 0x2ce32d2335df4552ULL, 0xa4be2de6458860e2ULL, 0xca18dd5ae3c45eb9ULL, 0x0ULL, 0x0ULL, 0x647d1a5ec6a344ULL, 0x2ce32d2335df4552ULL},
    {0x3469f18f1a6fa812ULL, 0x860f53667996eed4ULL, 0x775cdfb425fdff32ULL, 0xe21299d828cea893ULL, 0x0ULL, 0x0ULL, 0x3469f18f1a6fa812ULL, 0x860f53667996eed4ULL},
    {0xbf217e4cc64e39d2ULL, 0xc60c9ae76aeb1026ULL, 0xaebbfc9f48be2c23ULL, 0x44096810dda31fdULL, 0x0ULL, 0x1ULL, 0x106581ad7d900dafULL, 0xc1cc04665d10de29ULL},
    {0x57b5e741815da0a5ULL, 0xf750ba5cd9cfcdc8ULL, 0x9055aa61433e1796ULL, 0xd85091117d6ce577ULL, 0x0ULL, 0x0ULL, 0x57b5e741815da0a5ULL, 0xf750ba5cd9cfcdc8ULL},
    {0x3aa3d4a5dcb616beULL, 0xc11bf36eba4e03aULL, 0x77513ca3343cc3fdULL, 0xdf922c6e861b181ULL, 0x0ULL, 0x0ULL, 0x3aa3d4a5dcb616beULL, 0xc11bf36eba4e03aULL},
    {0x1e36c5bcd119e41fULL, 0xe3ffaef9e3b7b3fcULL, 0x342558aee208a970ULL, 0xb8641c0ab2a8289bULL, 0x0ULL, 0x0ULL, 0x1e36c5bcd119e41fULL, 0xe3ffaef9e3b7b3fcULL},
    {0x938574187e0d3435ULL, 0x76197f2e7152898eULL, 0x9e531bc53bbfdb28ULL, 0x38d0dcb70ccd2145ULL, 0x0ULL, 0x0ULL, 0x938574187e0d3435ULL, 0x76197f2e7152898eULL},
    {0x4fc91837ff206210ULL, 0x7f2c0d6bd4209570ULL, 0xeaca7c088a44f86eULL, 0x3cc17db3a57455ffULL, 0x0ULL, 0x0ULL, 0x4fc91837ff206210ULL, 0x7f2c0d6bd4209570ULL},
    {0xfdd177b5781ad31aULL, 0x132bb33884ef2022ULL, 0x843efc0e2f63cf14ULL, 0x5b647112c22b0549ULL, 0x0ULL, 0x1ULL, 0x79927ba748b70405ULL, 0xb7c74225c2c41ad9ULL},
    {0x6336cde8705d7dbdULL, 0x4107f48f3013a624ULL, 0x2df1752e1d98a215ULL, 0x6d8dd69a8d3b91a3ULL, 0x0ULL, 0x2ULL, 0x753e38c352c3992ULL, 0x65ec475a159c82deULL},
    {0x15f5599419293012ULL, 0x3427b9497ffef7f6ULL, 0xd3fb0bee29016607ULL, 0x26b1c32b4e6a218dULL, 0x0ULL, 0x0ULL, 0x15f5599419293012ULL, 0x3427b9497ffef7f6ULL},
    {0xbbe4a0c8476fa752ULL, 0x8ad2b586c9c55a18ULL, 0xc7abc932d7f3c654ULL, 0x19eff1de6c23f87ULL, 0x0ULL, 0x0ULL, 0xbbe4a0c8476fa752ULL, 0x8ad2b586c9c55a18ULL},
    {0x74bbc0261881d058ULL, 0xd0d9f9e0f2a3a50aULL, 0x4c11c3cd32f04e97ULL, 0x6d7e5968d3dd7a11ULL, 0x0ULL, 0x1ULL, 0x28a9fc58e59181c1ULL, 0x635ba0781ec62af9ULL},
    {0xfb836d3add4bf96aULL, 0xf826ed292cc9654cULL, 0x767a748f6e543b71ULL, 0x45c6b29a454b03abULL, 0x0ULL, 0x2ULL, 0xe8e841c00a38288ULL, 0x6c9987f4a2335df6ULL},
    {0x6bdfa84c98ba635bULL, 0x49b3a3cc42effb5eULL, 0xed695aa6756721d6ULL, 0x7920976eff0052d5ULL, 0x0ULL, 0x0ULL, 0x6bdfa84c98ba635bULL, 0x49b3a3cc42effb5eULL},
    {0x8b1b5c17978bbadaULL, 0x309541bb6586bbc0ULL, 0x929c109b80c147b8ULL, 0x397618b5edfac20fULL, 0x0ULL, 0x0ULL, 0x8b1b5c17978bbadaULL, 0x309541bb6586bbc0ULL},
};
int main(void) {
    for (unsigned i = 0; i < sizeof(cases)/sizeof(cases[0]); ++i) {
        const uint64_t *v = cases[i];
        uint128 a = ((uint128)v[0] << 64) | v[1];
        uint128 b = ((uint128)v[2] << 64) | v[3];
        uint128 q = ((uint128)v[4] << 64) | v[5];
        uint128 r = ((uint128)v[6] << 64) | v[7];
        if (__udivti3(a,b) != q || __umodti3(a,b) != r) {
            fprintf(stderr, "Failed wide division vector %u\n", i); return 1;
        }
    }
    puts("PASS: 22 unsigned 128-bit division/remainder golden vectors");
    return 0;
}
