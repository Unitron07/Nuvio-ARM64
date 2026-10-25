#ifndef NUVIO_QUICKJS_SYS_TIME_H
#define NUVIO_QUICKJS_SYS_TIME_H
#include <stdint.h>
struct timeval { int64_t tv_sec; long tv_usec; };
int gettimeofday(struct timeval *value, void *timezone);
#endif
