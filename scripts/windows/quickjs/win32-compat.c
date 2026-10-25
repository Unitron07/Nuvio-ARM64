#include "pthread.h"
#include "sys/time.h"
#include <errno.h>
#include <stdint.h>

static BOOL CALLBACK initialize_mutex(PINIT_ONCE once, PVOID parameter, PVOID *context) {
    (void)once; (void)context;
    InitializeCriticalSection(&((pthread_mutex_t *)parameter)->cs);
    return TRUE;
}
int pthread_mutexattr_init(pthread_mutexattr_t *attr) { *attr = 0; return 0; }
int pthread_mutexattr_settype(pthread_mutexattr_t *attr, int type) {
    if (type != PTHREAD_MUTEX_RECURSIVE) return EINVAL;
    *attr = type; return 0;
}
int pthread_mutexattr_destroy(pthread_mutexattr_t *attr) { (void)attr; return 0; }
int pthread_mutex_init(pthread_mutex_t *mutex, const pthread_mutexattr_t *attr) {
    (void)attr;
    InitOnceInitialize(&mutex->once);
    return InitOnceExecuteOnce(&mutex->once, initialize_mutex, mutex, NULL) ? 0 : EINVAL;
}
int pthread_mutex_lock(pthread_mutex_t *mutex) {
    if (!InitOnceExecuteOnce(&mutex->once, initialize_mutex, mutex, NULL)) return EINVAL;
    EnterCriticalSection(&mutex->cs); return 0;
}
int pthread_mutex_unlock(pthread_mutex_t *mutex) { LeaveCriticalSection(&mutex->cs); return 0; }
int pthread_mutex_destroy(pthread_mutex_t *mutex) { DeleteCriticalSection(&mutex->cs); return 0; }
int pthread_cond_init(pthread_cond_t *cond, const void *attr) {
    (void)attr; InitializeConditionVariable(cond); return 0;
}
int pthread_cond_destroy(pthread_cond_t *cond) { (void)cond; return 0; }
int pthread_cond_signal(pthread_cond_t *cond) { WakeConditionVariable(cond); return 0; }
int pthread_cond_wait(pthread_cond_t *cond, pthread_mutex_t *mutex) {
    return SleepConditionVariableCS(cond, &mutex->cs, INFINITE) ? 0 : EINVAL;
}
int clock_gettime(int clock, struct timespec *value) {
    if (clock != CLOCK_REALTIME) return EINVAL;
    return timespec_get(value, TIME_UTC) == TIME_UTC ? 0 : EINVAL;
}
int pthread_cond_timedwait(pthread_cond_t *cond, pthread_mutex_t *mutex, const struct timespec *deadline) {
    struct timespec now;
    if (clock_gettime(CLOCK_REALTIME, &now)) return EINVAL;
    int64_t ns = (int64_t)(deadline->tv_sec - now.tv_sec) * 1000000000 + deadline->tv_nsec - now.tv_nsec;
    /* Round up so sub-millisecond waits do not expire early; reserve INFINITE. */
    uint64_t ms = ns <= 0 ? 0 : ((uint64_t)ns + 999999) / 1000000;
    DWORD timeout = ms >= INFINITE ? INFINITE - 1 : (DWORD)ms;
    if (SleepConditionVariableCS(cond, &mutex->cs, timeout)) return 0;
    return GetLastError() == ERROR_TIMEOUT ? ETIMEDOUT : EINVAL;
}
int gettimeofday(struct timeval *value, void *timezone) {
    (void)timezone;
    struct timespec now;
    if (clock_gettime(CLOCK_REALTIME, &now)) return -1;
    value->tv_sec = now.tv_sec; value->tv_usec = now.tv_nsec / 1000;
    return 0;
}
